package com.house.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租客端第二期（报修 / 投诉 / 合同 / 反馈）共享工具。
 *
 * <p>从 809 行的 {@code TenantPhase2Controller} 抽出。这里只放纯转换逻辑：
 * 请求体解析、图片 JSON 序列化、时间线节点构造、状态分组映射。
 * 不访问数据库，便于单测。
 */
@Component
public class TenantPhase2Support {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 请求体取值：null 安全 + 去首尾空白。 */
  public String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  public Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  public Integer parseInt(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  /** 图片字段（库中存 JSON 字符串）→ List；解析失败返回空列表。 */
  public List<String> parseImages(String imagesJson) {
    try {
      if (imagesJson == null || imagesJson.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
      });
    } catch (Exception e) {
      return List.of();
    }
  }

  /** List → JSON 字符串；失败返回 "[]" 而不是抛异常打断业务。 */
  public String toJson(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values == null ? List.of() : values);
    } catch (Exception e) {
      return "[]";
    }
  }

  /** 请求体里的数组 → 去空白的字符串列表。 */
  public List<String> toStringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      if (item != null && !item.toString().isBlank()) {
        result.add(item.toString());
      }
    }
    return result;
  }

  /** 构造时间线节点。 */
  public Map<String, Object> timelineNode(String content, String operator, Object time) {
    Map<String, Object> item = new HashMap<>();
    item.put("content", content);
    item.put("operator", operator);
    item.put("time", time);
    return item;
  }

  /** 分页切片，统一 0 起始。 */
  public <T> Map<String, Object> paginate(List<T> rows, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    int from = Math.min(safePage * safeSize, rows.size());
    int to = Math.min(from + safeSize, rows.size());

    Map<String, Object> body = new HashMap<>();
    body.put("content", rows.subList(from, to));
    body.put("totalElements", rows.size());
    body.put("page", safePage);
    body.put("size", safeSize);
    return body;
  }

  /**
   * 前端筛选值 → 数据库状态集合。
   *
   * <p>前端只暴露三档（all / processing / completed），
   * 后台状态值更多，这里做映射；未知值原样返回，避免静默丢数据。
   */
  public List<String> repairStatusGroup(String status) {
    String key = status.trim().toLowerCase();
    if ("processing".equals(key)) {
      return List.of("pending", "processing", "pending_accept");
    }
    if ("completed".equals(key)) {
      return List.of("completed", "cancelled");
    }
    return List.of(status);
  }

  public List<String> complaintStatusGroup(String status) {
    String key = status.trim().toLowerCase();
    if ("processing".equals(key)) {
      return List.of("pending", "processing", "pending_confirm");
    }
    if ("completed".equals(key)) {
      return List.of("completed", "closed");
    }
    return List.of(status);
  }
}
