package com.house.dto;

import java.util.List;
import java.util.Map;

/**
 * 财务报表结果。
 *
 * <p>统计口径说明：
 * <ul>
 *   <li>应收 / 已收 / 未收 按「账单」统计（已作废账单不计入应收）</li>
 *   <li>收入（实收）按「收款记录」统计，以收款日期归属月份，能反映真实现金流</li>
 *   <li>支出目前只有维修费用（repair_order.total_fee）</li>
 * </ul>
 *
 * @param period        统计区间 { startMonth, endMonth }
 * @param summary       总览：应收/已收/未收/收租率/支出/净收入
 * @param monthlyTrend  逐月收支 { months[], receivable[], received[], expense[] }
 * @param byBuilding    按楼栋汇总 [{ buildingName, receivable, received, unpaid, roomCount, occupancyRate }]
 * @param byRoom        按房间汇总 [{ roomNumber, buildingName, tenantName, receivable, received, unpaid, bills }]
 * @param costBreakdown 费用构成 [{ name, amount }] —— 房租/水费/电费等
 * @param paymentMethods 收款方式分布 [{ method, amount, count }]
 */
public record FinanceReportResult(
    Map<String, Object> period,
    Map<String, Object> summary,
    Map<String, Object> monthlyTrend,
    List<Map<String, Object>> byBuilding,
    List<Map<String, Object>> byRoom,
    List<Map<String, Object>> costBreakdown,
    List<Map<String, Object>> paymentMethods) {
}
