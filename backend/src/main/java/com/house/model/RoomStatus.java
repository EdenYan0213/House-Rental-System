package com.house.model;

import java.util.Locale;

/**
 * 房间状态常量与归一化工具。
 *
 * <p>规范：<b>代码与数据库一律使用英文大写下划线值</b>，中文只在前端展示层出现。
 * 读取时通过 {@link #normalize(String)} 兼容历史数据里的中文旧值，写入时产出英文值。
 */
public final class RoomStatus {

  public static final String IDLE = "IDLE";
  public static final String RENTED = "RENTED";
  public static final String MAINTENANCE = "MAINTENANCE";

  private RoomStatus() {
  }

  /** 把任意已知写法归一化为规范英文值；未知值原样返回（不丢弃用户自定义状态）。 */
  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return IDLE;
    }
    String value = raw.trim();
    switch (value.toUpperCase(Locale.ROOT)) {
      case "IDLE":
      case "空闲":
      case "空置":
      case "待租":
        return IDLE;
      case "RENTED":
      case "已租":
      case "在租":
      case "已出租":
        return RENTED;
      case "MAINTENANCE":
      case "维修":
      case "维修中":
        return MAINTENANCE;
      default:
        return value;
    }
  }

  public static boolean isIdle(String raw) {
    return IDLE.equals(normalize(raw));
  }

  public static boolean isRented(String raw) {
    return RENTED.equals(normalize(raw));
  }

  public static boolean isMaintenance(String raw) {
    return MAINTENANCE.equals(normalize(raw));
  }

  /** 判断房间状态是否满足筛选条件；筛选值为空时视为全部匹配。 */
  public static boolean matches(String raw, String filter) {
    if (filter == null || filter.isBlank()) {
      return true;
    }
    return normalize(raw).equals(normalize(filter));
  }
}
