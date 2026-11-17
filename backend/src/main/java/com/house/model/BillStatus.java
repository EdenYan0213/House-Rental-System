package com.house.model;

import java.util.Locale;

/**
 * 账单状态常量与归一化工具。
 *
 * <p>历史数据中同一状态存在中英文两种写法（{@code BillService} 写入 {@code PENDING}，
 * {@code PaymentService} 写入 {@code 已支付}）。统一规范：<b>持久化与对外输出一律使用英文大写</b>，
 * 读取时通过 {@link #normalize(String)} 兼容中文旧数据。前端映射表、筛选下拉与按钮门禁均按英文值判断。
 */
public final class BillStatus {

  public static final String PENDING = "PENDING";
  public static final String PARTIAL = "PARTIAL";
  public static final String PAID = "PAID";
  public static final String VOID = "VOID";

  private BillStatus() {
  }

  /** 把任意已知写法归一化为规范英文值；未知值原样返回。 */
  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return PENDING;
    }
    String value = raw.trim();
    switch (value.toUpperCase(Locale.ROOT)) {
      case "PENDING":
      case "UNPAID":
      case "待支付":
      case "未支付":
      case "待付款":
        return PENDING;
      case "PARTIAL":
      case "部分支付":
      case "部分付款":
        return PARTIAL;
      case "PAID":
      case "已支付":
      case "已付清":
        return PAID;
      case "VOID":
      case "OBSOLETE":
      case "作废":
      case "已作废":
        return VOID;
      default:
        return value.toUpperCase(Locale.ROOT);
    }
  }

  public static boolean isPending(String raw) {
    return PENDING.equals(normalize(raw));
  }

  public static boolean isPartial(String raw) {
    return PARTIAL.equals(normalize(raw));
  }

  public static boolean isPaid(String raw) {
    return PAID.equals(normalize(raw));
  }

  public static boolean isVoid(String raw) {
    return VOID.equals(normalize(raw));
  }

  /** 终态：已付清或已作废，不可再收款 / 作废 / 重新生成。 */
  public static boolean isFinal(String raw) {
    return isPaid(raw) || isVoid(raw);
  }

  /** 仍需收款：待支付或部分支付（未付清且未作废）。 */
  public static boolean isOutstanding(String raw) {
    return !isFinal(raw);
  }
}
