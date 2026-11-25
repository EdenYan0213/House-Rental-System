package com.house.controller;

import com.house.model.OperationSnapshot;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 撤销快照响应的统一构造。
 *
 * <p>「删除/编辑/作废」类接口成功后都要返回
 * {@code undoSnapshotId} / {@code undoExpireAt} / {@code undoTip}，
 * 供前端 `showUndoToast` 渲染撤销提示。原先这段在 11 个 controller 里各写一遍，
 * 且都用 {@code Map.of(...)} —— 一旦 {@code snapshot.getExpiredAt()} 为 null
 * （例如未来改动 capture 逻辑）就会抛 NPE，整个接口 500。
 *
 * <p>这里改用 {@code LinkedHashMap}，null 值可安全放入，前端拿到 null 时
 * 自行降级（不显示撤销按钮）。
 */
public final class UndoResponse {

  private static final String DEFAULT_TIP = "操作成功，可在30分钟内撤销";

  private UndoResponse() {
  }

  /**
   * 纯撤销响应（删除、作废等不返回实体的接口）。
   */
  public static Map<String, Object> of(OperationSnapshot snapshot) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("undoSnapshotId", snapshot == null ? null : snapshot.getId());
    body.put("undoExpireAt", snapshot == null ? null : snapshot.getExpiredAt());
    body.put("undoTip", DEFAULT_TIP);
    return body;
  }

  /**
   * 带业务数据的撤销响应（编辑类接口需要同时返回保存后的实体）。
   *
   * @param dataKey 业务数据在响应里的字段名，通常是 {@code "data"}
   */
  public static Map<String, Object> withData(String dataKey, Object data, OperationSnapshot snapshot) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put(dataKey, data);
    body.putAll(of(snapshot));
    return body;
  }

  /**
   * 带业务数据 + 自定义提示文案。
   */
  public static Map<String, Object> withData(String dataKey, Object data,
      OperationSnapshot snapshot, String tip) {
    Map<String, Object> body = withData(dataKey, data, snapshot);
    body.put("undoTip", tip == null || tip.isBlank() ? DEFAULT_TIP : tip);
    return body;
  }

  /**
   * 兼容调用方自定义提示文案的场景。
   */
  public static Map<String, Object> of(OperationSnapshot snapshot, String tip) {
    Map<String, Object> body = of(snapshot);
    body.put("undoTip", tip == null || tip.isBlank() ? DEFAULT_TIP : tip);
    return body;
  }

  /** 便于测试断言：撤销窗口截止时间（可能为 null）。 */
  public static LocalDateTime expireAtOf(Map<String, Object> body) {
    Object v = body == null ? null : body.get("undoExpireAt");
    return v instanceof LocalDateTime dt ? dt : null;
  }
}
