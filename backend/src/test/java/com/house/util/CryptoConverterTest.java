package com.house.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感字段加解密测试。
 *
 * <p>这组测试守护的是一个真实事故：转换器密钥长度非法，
 * AES 初始化失败后异常被吞掉，于是"加密"从未生效，
 * 数据库里一直是明文身份证号。所以这里的重点是
 * <b>必须真的加密</b>，而不是"看起来没报错"。
 */
@DisplayName("敏感字段加解密")
class CryptoConverterTest {

  private final CryptoConverter converter = new CryptoConverter();

  @Test
  @DisplayName("身份证号加密后可正确还原")
  void roundTrip() {
    String plain = "330106199001011234";
    String encrypted = converter.convertToDatabaseColumn(plain);

    assertNotEquals(plain, encrypted, "密文不能等于明文");
    assertTrue(CryptoConverter.isEncrypted(encrypted), "应带密文前缀");
    assertFalse(encrypted.contains(plain), "密文里不能包含明文");
    assertEquals(plain, converter.convertToEntityAttribute(encrypted), "解密应还原");
  }

  @Test
  @DisplayName("同一明文两次加密结果不同（有随机 IV）")
  void differsEachTime() {
    String plain = "330106199001011234";
    String a = converter.convertToDatabaseColumn(plain);
    String b = converter.convertToDatabaseColumn(plain);

    assertNotEquals(a, b, "GCM 每次使用随机 IV，相同明文应产生不同密文");
    assertEquals(plain, converter.convertToEntityAttribute(a));
    assertEquals(plain, converter.convertToEntityAttribute(b));
  }

  @Test
  @DisplayName("密钥长度不是 16/24/32 字节也能正常工作")
  void worksWithArbitrarySecretLength() {
    // 原实现硬编码 18 字节密钥，正是这个长度导致 AES 初始化失败
    for (String secret : new String[] { "ChangeThisSecret!!", "short", "一个中文密钥", "x".repeat(100) }) {
      CryptoKeyHolder.overrideForTest(secret);
      String plain = "330106199001011234";
      String encrypted = converter.convertToDatabaseColumn(plain);

      assertTrue(CryptoConverter.isEncrypted(encrypted), "密钥长度 " + secret.length() + " 应能加密");
      assertEquals(plain, converter.convertToEntityAttribute(encrypted), "密钥长度 " + secret.length() + " 应能解密");
    }
    CryptoKeyHolder.overrideForTest("test-only-secret-placeholder");
  }

  @Test
  @DisplayName("历史明文数据仍可读出（向后兼容）")
  void readsLegacyPlainText() {
    // 库里可能存在加密修复前写入的明文
    assertEquals("330106199001011234", converter.convertToEntityAttribute("330106199001011234"));
    assertEquals("明文", converter.convertToEntityAttribute("明文"));
  }

  @Test
  @DisplayName("已是密文的值不会被二次加密")
  void doesNotDoubleEncrypt() {
    String encrypted = converter.convertToDatabaseColumn("330106199001011234");
    String again = converter.convertToDatabaseColumn(encrypted);

    assertEquals(encrypted, again, "重复保存不应再包一层");
    assertEquals("330106199001011234", converter.convertToEntityAttribute(again));
  }

  @Test
  @DisplayName("空值原样返回，不抛异常")
  void handlesNullAndEmpty() {
    assertNull(converter.convertToDatabaseColumn(null));
    assertNull(converter.convertToEntityAttribute(null));
    assertEquals("", converter.convertToDatabaseColumn(""));
    assertEquals("", converter.convertToEntityAttribute(""));
  }

  @Test
  @DisplayName("密钥不匹配时返回占位符而不是密文原文")
  void wrongKeyDoesNotLeakCipherText() {
    String encrypted = converter.convertToDatabaseColumn("330106199001011234");

    CryptoKeyHolder.overrideForTest("a-completely-different-secret");
    String decrypted = converter.convertToEntityAttribute(encrypted);
    CryptoKeyHolder.overrideForTest("test-only-secret-placeholder");

    assertEquals("[无法解密]", decrypted, "解不开时不应把密文透给调用方");
    assertFalse(decrypted.contains("v2:"), "不应返回密文原文");
  }

  @Test
  @DisplayName("派生密钥始终是 32 字节")
  void normalizeKeyProduces256Bit() {
    assertEquals(32, CryptoConverter.normalizeKey("x").length);
    assertEquals(32, CryptoConverter.normalizeKey("").length);
    assertEquals(32, CryptoConverter.normalizeKey("x".repeat(1000)).length);
    // 相同输入必须稳定
    assertArrayEquals(CryptoConverter.normalizeKey("abc"), CryptoConverter.normalizeKey("abc"));
    // 不同输入必须不同
    assertFalse(java.util.Arrays.equals(CryptoConverter.normalizeKey("abc"), CryptoConverter.normalizeKey("abd")));
  }
}
