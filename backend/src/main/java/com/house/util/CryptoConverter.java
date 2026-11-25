package com.house.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 敏感字段加解密（AES/GCM）。
 *
 * <p>历史实现有三个问题，这里一并修掉：
 * <ol>
 *   <li><b>密钥长度非法</b>：原密钥 {@code "ChangeThisSecret!!"} 是 18 字节，
 *       AES 只接受 16/24/32 字节，{@code Cipher.init} 必然抛
 *       {@code InvalidKeyException}。而异常被 catch 后原样返回明文，
 *       于是"加密"从来没生效过，库里存的是明文身份证号。</li>
 *   <li><b>静默降级</b>：加密失败直接返回明文写库，问题被完全掩盖。
 *       现在加密失败必须抛异常——宁可写不进去，也不能把明文当密文存。</li>
 *   <li><b>ECB 模式 + 无 IV</b>：相同明文产生相同密文，可被比对推断。
 *       改成 GCM（带随机 IV 和认证标签）。</li>
 * </ol>
 *
 * <p><b>兼容性</b>：库里仍可能存在历史明文数据。{@link #convertToEntityAttribute}
 * 会先尝试按「GCM 密文」解析（格式 {@code v2:<iv>:<ciphertext>}），
 * 解析不了就当作明文原样返回。这样旧数据仍能读，下次写入时会被加密。
 *
 * <p><b>密钥</b>：这里是 JPA AttributeConverter，不是 Spring Bean，
 * 无法注入 {@code @Value}。改由 {@link CryptoKeyHolder} 在启动时注入
 * {@code app.crypto.secret}，便于通过环境变量 {@code CRYPTO_SECRET} 轮换。
 */
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

  /** 密文前缀，用于区分历史明文与新密文。 */
  private static final String PREFIX = "v2:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;      // GCM 推荐 96 bit
  private static final int TAG_LENGTH_BIT = 128;

  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return attribute;
    }
    // 已经是密文（重复保存时）就不要再加密一层
    if (attribute.startsWith(PREFIX)) {
      return attribute;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      RANDOM.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
      byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

      Base64.Encoder enc = Base64.getEncoder();
      return PREFIX + enc.encodeToString(iv) + ":" + enc.encodeToString(encrypted);
    } catch (Exception e) {
      // 绝不降级成明文写库：那会把敏感数据永久泄漏到数据库里，
      // 而且表面看起来一切正常，事后极难发现。
      throw new IllegalStateException("敏感字段加密失败，已拒绝写入以避免明文落库", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isEmpty()) {
      return dbData;
    }
    // 历史明文：不含前缀，原样返回（下次保存时会被加密）
    if (!dbData.startsWith(PREFIX)) {
      return dbData;
    }
    try {
      String[] parts = dbData.substring(PREFIX.length()).split(":");
      if (parts.length != 2) {
        return dbData;
      }
      Base64.Decoder dec = Base64.getDecoder();
      byte[] iv = dec.decode(parts[0]);
      byte[] cipherText = dec.decode(parts[1]);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (Exception e) {
      // 解不开最可能的原因是密钥换过。此时不该让整个查询炸掉，
      // 返回占位符而不是密文原文——把密文甩到前端同样是无意义的泄露。
      return "[无法解密]";
    }
  }

  /** 判断一个值是否为本类产出的密文（供迁移脚本/测试使用）。 */
  public static boolean isEncrypted(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  private SecretKeySpec key() {
    return new SecretKeySpec(normalizeKey(CryptoKeyHolder.getSecret()), "AES");
  }

  /**
   * 把任意长度的口令规整成 AES 可用的 256 位密钥。
   *
   * <p>直接 {@code secret.getBytes()} 依赖密钥恰好是 16/24/32 字节，
   * 这正是原实现的坑。这里统一走 SHA-256，输入多长都能得到合法密钥。
   * 注意：这会改变历史密文的可解性，但历史数据本来就没加密（是明文），
   * 所以不存在兼容问题。
   */
  static byte[] normalizeKey(String secret) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
      return Arrays.copyOf(hash, 32);
    } catch (Exception e) {
      throw new IllegalStateException("无法派生加密密钥", e);
    }
  }
}
