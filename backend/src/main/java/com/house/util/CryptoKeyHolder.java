package com.house.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 把 {@code app.crypto.secret} 交给 {@link CryptoConverter} 使用。
 *
 * <p>JPA 的 {@code AttributeConverter} 由 Hibernate 实例化，不是 Spring Bean，
 * 因此拿不到 {@code @Value} 注入的配置。这里用一个静态持有器桥接：
 * 应用启动时把配置写进去，转换器读静态字段。
 *
 * <p>好处是密钥可以通过 {@code CRYPTO_SECRET} 环境变量轮换，
 * 而不是像旧实现那样硬编码在源码里。
 */
@Component
public class CryptoKeyHolder {

  private static volatile String secret;

  private final String configuredSecret;

  public CryptoKeyHolder(@Value("${app.crypto.secret:}") String configuredSecret) {
    this.configuredSecret = configuredSecret;
  }

  @PostConstruct
  void init() {
    if (configuredSecret == null || configuredSecret.isBlank()) {
      throw new IllegalStateException(
          "app.crypto.secret（环境变量 CRYPTO_SECRET）未配置，敏感字段无法加密");
    }
    secret = configuredSecret;
  }

  /**
   * 供 {@link CryptoConverter} 读取。
   *
   * <p>单元测试里没有 Spring 容器，此时回落到一个仅用于测试的固定值，
   * 避免 NPE；生产路径一定先经过 {@link #init()}。
   */
  public static String getSecret() {
    String s = secret;
    if (s == null || s.isBlank()) {
      return "test-only-secret-placeholder";
    }
    return s;
  }

  /** 仅供测试使用。 */
  static void overrideForTest(String value) {
    secret = value;
  }
}
