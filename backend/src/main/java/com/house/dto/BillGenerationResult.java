package com.house.dto;

import java.util.List;
import java.util.Map;

/**
 * 批量生成账单的结果明细。
 *
 * @param billMonth    本次生成的账单月份（yyyy-MM）
 * @param successCount 成功生成的账单数
 * @param totalCount   参与生成的进行中合同总数
 * @param failures     失败明细，每项包含 contractId/contractNo/roomId/roomNumber/reason
 */
public record BillGenerationResult(
    String billMonth,
    int successCount,
    int totalCount,
    List<Map<String, Object>> failures) {

  /** 是否存在失败项，前端据此决定是否弹出失败清单。 */
  public boolean hasFailures() {
    return failures != null && !failures.isEmpty();
  }
}
