package com.house.util;

/**
 * 请求体（{@code Map<String,Object>}）取值工具。
 *
 * <p>项目里大量接口用弱类型 body 接收参数，各处都写了 `parseLong` / `text`
 * 之类的私有副本（`ComplaintController`、`ComplaintService`、`TenantPortalSupport`…），
 * 行为还不完全一致（有的 trim 后判 blank，有的直接 parse）。
 * 新增代码用这里的方法，避免再产生第五份实现。
 */
public final class RequestValues {

  private RequestValues() {
  }

  /** 取 Long；null、空白、非法值都返回 null，不抛异常。 */
  public static Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    String text = value.toString().trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 取 Integer；无法解析时返回 {@code defaultValue}。 */
  public static int parseInt(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** 取字符串并 trim；null 返回空串。 */
  public static String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  /** 取字符串并 trim；null 或空白返回 {@code defaultValue}。 */
  public static String textOrDefault(Object value, String defaultValue) {
    String text = text(value);
    return text.isEmpty() ? defaultValue : text;
  }
}
