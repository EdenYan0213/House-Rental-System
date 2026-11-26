package com.house.model;

import java.util.List;
import java.util.Locale;

/**
 * 合同状态常量与归一化工具。
 *
 * <p>与 {@link RoomStatus} / {@link BillStatus} 同一套约定：<b>持久化与代码判断一律使用英文大写</b>，
 * 中文旧值只在读取时通过 {@link #normalize(String)} 兼容。历史数据里确实存在
 * {@code ACTIVE} 与 {@code 进行中} 混写（{@link BillService} 一度写入中文），
 * 所以凡是要判断“合同是否生效”的地方都走这里，不要再写字面量。
 */
public final class ContractStatus {

  /** 生效中：可收租、可生成账单、可绑定房间。 */
  public static final String ACTIVE = "ACTIVE";
  /** 待生效：已创建但未到起租日 / 未签署。 */
  public static final String PENDING = "PENDING";
  /** 已终止：提前退租，不可再操作。 */
  public static final String TERMINATED = "TERMINATED";
  /** 已到期：自然到期。 */
  public static final String EXPIRED = "EXPIRED";

  /** 视为“生效中”的全部原始写法，供需要直接查库的场景使用（JPA 派生查询无法调用 normalize）。 */
  public static final List<String> ACTIVE_RAW_VALUES = List.of(ACTIVE, "进行中");

  private ContractStatus() {
  }

  /** 把任意已知写法归一化为规范英文值；未知值原样返回。 */
  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return PENDING;
    }
    String value = raw.trim();
    switch (value.toUpperCase(Locale.ROOT)) {
      case "ACTIVE":
      case "进行中":
      case "生效中":
        return ACTIVE;
      case "PENDING":
      case "待生效":
      case "待签署":
        return PENDING;
      case "TERMINATED":
      case "已终止":
      case "已退租":
        return TERMINATED;
      case "EXPIRED":
      case "已到期":
      case "已过期":
        return EXPIRED;
      default:
        return value.toUpperCase(Locale.ROOT);
    }
  }

  public static boolean isActive(String raw) {
    return ACTIVE.equals(normalize(raw));
  }

  public static boolean isPending(String raw) {
    return PENDING.equals(normalize(raw));
  }

  public static boolean isTerminated(String raw) {
    return TERMINATED.equals(normalize(raw));
  }

  public static boolean isExpired(String raw) {
    return EXPIRED.equals(normalize(raw));
  }

  /** 终态：已终止或已到期，不可再收款 / 续租 / 退租。 */
  public static boolean isFinal(String raw) {
    return isTerminated(raw) || isExpired(raw);
  }
}
