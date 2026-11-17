package com.house.dto;

import java.util.List;
import java.util.Map;

/**
 * 退租预检结果：把办理退租前需要知道的信息一次性给前端。
 *
 * @param contractId      合同ID
 * @param contractNo      合同编号
 * @param tenantId        租户ID
 * @param tenantName      租户姓名
 * @param roomId          房间ID
 * @param roomNumber      房间号
 * @param buildingName    楼栋名
 * @param depositAmount   合同押金金额
 * @param receivedDeposit 已收取的押金合计
 * @param refundedDeposit 已退还的押金合计
 * @param depositBalance  当前可退押金（已收 - 已退）
 * @param unpaidAmount    未结清账单金额合计
 * @param unpaidCount     未结清账单数量
 * @param unpaidBills     未结清账单明细（id/billMonth/totalAmount/paidAmount）
 * @param unpaidItems     未结清账单对应的房间/租户文字摘要
 * @param hasMeterMissing 本月是否缺水电读数
 * @param blocked         是否禁止退租（有未结清账单时为 true，需前端二次确认）
 * @param warnings        提示信息列表（非阻断，仅提醒）
 */
public record MoveOutCheckResult(
    Long contractId,
    String contractNo,
    Long tenantId,
    String tenantName,
    Long roomId,
    String roomNumber,
    String buildingName,
    Double depositAmount,
    Double receivedDeposit,
    Double refundedDeposit,
    Double depositBalance,
    Double unpaidAmount,
    int unpaidCount,
    List<Map<String, Object>> unpaidBills,
    String unpaidItems,
    boolean hasMeterMissing,
    boolean blocked,
    List<String> warnings) {
}
