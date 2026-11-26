package com.house.model;

import java.util.List;

/**
 * 工单类流程状态（维修工单 / 投诉建议）常量。
 *
 * <p>维修与投诉共用同一套生命周期，此前字面量散落在 6 个文件、共 33 处
 * （{@code TenantRepairController}、{@code RepairOrderService}、{@code ComplaintService}、
 * {@code TenantMessageController}、{@code TenantPhase2Support} 等），改一个状态要全局搜。
 * 统一到这里后：新增状态改一处，编译期就能发现漏改的 switch。
 *
 * <p>与房间/账单/合同状态不同，这里的值是<b>小写</b>且带下划线，属于历史约定，
 * 前端 {@code TenantPhase2Support.statusGroup} 的分组键也依赖它，保持不变。
 */
public final class WorkflowStatus {

  /** 待受理。 */
  public static final String PENDING = "pending";
  /** 已受理 / 处理中。 */
  public static final String PROCESSING = "processing";
  /** 已派单（维修专用）：处理中且已指定维修工。 */
  public static final String PENDING_ACCEPT = "pending_accept";
  /** 待租客确认（投诉专用）：处理完成，等待租客确认结单。 */
  public static final String PENDING_CONFIRM = "pending_confirm";
  /** 已完成。 */
  public static final String COMPLETED = "completed";
  /** 已关闭（投诉专用）：双方不再跟进。 */
  public static final String CLOSED = "closed";
  /** 已取消。 */
  public static final String CANCELLED = "cancelled";
  /** 已分配。 */
  public static final String ASSIGNED = "assigned";

  /** 进行中（广义）：尚未进入终态的全部状态。 */
  public static final List<String> IN_PROGRESS = List.of(PENDING, ASSIGNED, PROCESSING, PENDING_ACCEPT, PENDING_CONFIRM);

  private WorkflowStatus() {
  }

  public static boolean isCompleted(String raw) {
    return raw != null && COMPLETED.equalsIgnoreCase(raw.trim());
  }

  /** 终态：已完成、已关闭、已取消。 */
  public static boolean isFinal(String raw) {
    if (raw == null) {
      return false;
    }
    String value = raw.trim();
    return COMPLETED.equalsIgnoreCase(value)
        || CLOSED.equalsIgnoreCase(value)
        || CANCELLED.equalsIgnoreCase(value);
  }
}
