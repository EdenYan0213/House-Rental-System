package com.house.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

  // NOTE: demo 用，生产请使用更安全的密钥管理
  private static final String SECRET = "ChangeThisSecret!!"; // 16 chars

  private SecretKeySpec getKey() {
    return new SecretKeySpec(SECRET.getBytes(), ALGORITHM);
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null)
      return null;
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, getKey());
      byte[] encrypted = cipher.doFinal(attribute.getBytes());
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
      return attribute;
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null)
      return null;
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, getKey());
      byte[] decoded = Base64.getDecoder().decode(dbData);
      return new String(cipher.doFinal(decoded));
    } catch (Exception e) {
      return dbData;
    }
  }
}
