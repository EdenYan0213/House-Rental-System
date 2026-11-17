package com.house.dto;

/**
 * 退租结算请求。
 *
 * @param terminateDate     退租日期（yyyy-MM-dd），空则用今天
 * @param reason            退租原因
 * @param refundDeposit     是否退还押金
 * @param refundAmount      实退金额；为空时默认退「已收押金 - 已退押金」
 * @param deductAmount      扣款金额（如房屋损坏、清洁费），仅用于生成备注
 * @param deductReason      扣款原因
 * @param addToBlacklist    是否加入黑名单
 * @param blacklistReason   黑名单原因
 * @param force             存在未结清账单时是否强制退租
 * @param keepUnpaid        未结清账单是否保留（true 则仅告警不阻断）
 */
public record MoveOutRequest(
    String terminateDate,
    String reason,
    Boolean refundDeposit,
    Double refundAmount,
    Double deductAmount,
    String deductReason,
    Boolean addToBlacklist,
    String blacklistReason,
    Boolean force) {
}
