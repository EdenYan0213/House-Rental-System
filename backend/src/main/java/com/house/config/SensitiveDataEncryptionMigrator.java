package com.house.config;

import com.house.util.CryptoConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 一次性把历史明文敏感字段加密回填。
 *
 * <p>背景：{@link CryptoConverter} 旧实现的密钥长度非法（18 字节），
 * AES 初始化必然失败，异常被吞后原样返回明文，导致 {@code tenant.id_card}
 * 和 {@code sms_config.access_secret} 一直以明文存储。
 *
 * <p>修好转换器之后，需要把已存在的明文转成密文——否则这些行会一直是明文。
 * 这里走 JdbcTemplate 直接读写原始列值，绕开 JPA 的转换器：
 * <ul>
 *   <li>读出来的是数据库里的原始字符串（明文）；</li>
 *   <li>用转换器加密后写回。</li>
 * </ul>
 *
 * <p>幂等：已是 {@code v2:} 前缀的行会被跳过，可以安全地重复执行。
 */
@Component
@Order(100)
public class SensitiveDataEncryptionMigrator implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(SensitiveDataEncryptionMigrator.class);

  private final JdbcTemplate jdbcTemplate;
  private final CryptoConverter converter = new CryptoConverter();

  public SensitiveDataEncryptionMigrator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    migrateTenantIdCards();
    migrateSmsAccessSecrets();
  }

  private void migrateTenantIdCards() {
    migrate(
        "tenant",
        "id_card",
        "SELECT id, id_card FROM tenant WHERE id_card IS NOT NULL AND id_card <> ''",
        "UPDATE tenant SET id_card = ? WHERE id = ?");
  }

  private void migrateSmsAccessSecrets() {
    migrate(
        "sms_config",
        "access_secret",
        "SELECT id, access_secret FROM sms_config WHERE access_secret IS NOT NULL AND access_secret <> ''",
        "UPDATE sms_config SET access_secret = ? WHERE id = ?");
  }

  /**
   * 通用的「明文 → 密文」回填。
   *
   * <p>逐行失败不影响其它行：某一行加解密异常时记日志跳过，
   * 而不是让整个启动过程失败（否则修复一个脏值就把服务拖死）。
   */
  private void migrate(String table, String column, String selectSql, String updateSql) {
    List<Map<String, Object>> rows;
    try {
      rows = jdbcTemplate.queryForList(selectSql);
    } catch (Exception e) {
      // 表或列不存在（老库未迁移）时静默跳过，不阻塞启动
      log.debug("跳过 {} 的 {} 加密回填：{}", table, column, e.getMessage());
      return;
    }

    int migrated = 0;
    int skipped = 0;
    int failed = 0;

    for (Map<String, Object> row : rows) {
      Object id = row.get("id");
      Object value = row.get(column);
      if (id == null || value == null) {
        continue;
      }
      String raw = String.valueOf(value);
      if (raw.isBlank()) {
        continue;
      }
      if (CryptoConverter.isEncrypted(raw)) {
        skipped++;
        continue;
      }
      try {
        jdbcTemplate.update(updateSql, converter.convertToDatabaseColumn(raw), id);
        migrated++;
      } catch (Exception e) {
        failed++;
        log.warn("加密 {}.{} 失败（id={}）：{}", table, column, id, e.getMessage());
      }
    }

    if (migrated > 0 || failed > 0) {
      log.info("敏感字段加密回填 {}.{}：已加密 {} 行，跳过（已加密）{} 行，失败 {} 行",
          table, column, migrated, skipped, failed);
    } else if (skipped > 0) {
      log.debug("{}.{} 无需回填（{} 行已是密文）", table, column, skipped);
    }
  }
}
