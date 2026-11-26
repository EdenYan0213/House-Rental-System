package com.house.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 房间/账单状态归一化测试。
 *
 * <p>这两套状态历史上中英混写，是筛选失效和按钮门禁错误的根源，
 * 因此对兼容性做穷举验证。
 */
class StatusNormalizeTest {

  // ---------- RoomStatus ----------

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("房间状态为空时归一化为空闲")
  void roomStatusBlankDefaultsToIdle(String raw) {
    assertEquals(RoomStatus.IDLE, RoomStatus.normalize(raw));
  }

  @ParameterizedTest
  @CsvSource({
      "IDLE, IDLE",
      "idle, IDLE",
      "空闲, IDLE",
      "空置, IDLE",
      "RENTED, RENTED",
      "rented, RENTED",
      "已租, RENTED",
      "在租, RENTED",
      "MAINTENANCE, MAINTENANCE",
      "维修, MAINTENANCE",
      "维修中, MAINTENANCE"
  })
  @DisplayName("房间状态兼容中英文与历史别名")
  void roomStatusNormalizesAliases(String raw, String expected) {
    assertEquals(expected, RoomStatus.normalize(raw));
  }

  @Test
  @DisplayName("未知房间状态原样保留，不吞掉用户自定义值")
  void roomStatusKeepsUnknown() {
    assertEquals("装修中", RoomStatus.normalize("装修中"));
  }

  @Test
  @DisplayName("房间状态判定辅助方法")
  void roomStatusHelpers() {
    assertTrue(RoomStatus.isIdle("IDLE"));
    assertTrue(RoomStatus.isIdle("空闲"));
    assertTrue(RoomStatus.isRented("RENTED"));
    assertTrue(RoomStatus.isRented("已租"));
    assertTrue(RoomStatus.isMaintenance("维修中"));
    assertFalse(RoomStatus.isIdle("RENTED"));
  }

  @Test
  @DisplayName("房间状态筛选：空筛选视为全部匹配")
  void roomStatusMatches() {
    assertTrue(RoomStatus.matches("IDLE", null));
    assertTrue(RoomStatus.matches("IDLE", ""));
    assertTrue(RoomStatus.matches("IDLE", "空闲"));
    assertTrue(RoomStatus.matches("空闲", "IDLE"));
    assertFalse(RoomStatus.matches("IDLE", "RENTED"));
  }

  // ---------- BillStatus ----------

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("账单状态为空时归一化为待支付")
  void billStatusBlankDefaultsToPending(String raw) {
    assertEquals(BillStatus.PENDING, BillStatus.normalize(raw));
  }

  @ParameterizedTest
  @CsvSource({
      "PENDING, PENDING",
      "pending, PENDING",
      "待支付, PENDING",
      "未支付, PENDING",
      "待付款, PENDING",
      "UNPAID, PENDING",
      "PARTIAL, PARTIAL",
      "部分支付, PARTIAL",
      "部分付款, PARTIAL",
      "PAID, PAID",
      "已支付, PAID",
      "已付清, PAID",
      "VOID, VOID",
      "作废, VOID",
      "已作废, VOID",
      "OBSOLETE, VOID"
  })
  @DisplayName("账单状态兼容中英文与历史别名")
  void billStatusNormalizesAliases(String raw, String expected) {
    assertEquals(expected, BillStatus.normalize(raw));
  }

  @Test
  @DisplayName("账单终态判定：已付清与已作废都不可再操作")
  void billFinalStates() {
    assertTrue(BillStatus.isFinal("PAID"));
    assertTrue(BillStatus.isFinal("已付清"));
    assertTrue(BillStatus.isFinal("VOID"));
    assertTrue(BillStatus.isFinal("作废"));
    assertTrue(BillStatus.isFinal("OBSOLETE"));

    assertFalse(BillStatus.isFinal("PENDING"));
    assertFalse(BillStatus.isFinal("PARTIAL"));
    assertFalse(BillStatus.isFinal("部分付款"));
  }

  @Test
  @DisplayName("未结清判定与终态互斥")
  void billOutstandingIsComplementOfFinal() {
    for (String s : new String[]{"PENDING", "PARTIAL", "待支付", "部分支付", null, ""}) {
      assertTrue(BillStatus.isOutstanding(s), "应视为未结清: " + s);
      assertFalse(BillStatus.isFinal(s), "不应视为终态: " + s);
    }
    for (String s : new String[]{"PAID", "VOID", "已付清", "作废"}) {
      assertFalse(BillStatus.isOutstanding(s), "不应视为未结清: " + s);
    }
  }

  @Test
  @DisplayName("已付清与部分支付不应混淆")
  void billPaidVsPartial() {
    assertTrue(BillStatus.isPaid("PAID"));
    assertFalse(BillStatus.isPaid("PARTIAL"));
    assertTrue(BillStatus.isPartial("PARTIAL"));
    assertFalse(BillStatus.isPartial("PAID"));
  }

  // ---------- ContractStatus ----------

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("合同状态为空时归一化为待生效")
  void contractStatusBlankDefaultsToPending(String raw) {
    assertEquals(ContractStatus.PENDING, ContractStatus.normalize(raw));
  }

  @ParameterizedTest
  @CsvSource({
      "ACTIVE, ACTIVE",
      "active, ACTIVE",
      "进行中, ACTIVE",
      "生效中, ACTIVE",
      "PENDING, PENDING",
      "待生效, PENDING",
      "待签署, PENDING",
      "TERMINATED, TERMINATED",
      "已终止, TERMINATED",
      "已退租, TERMINATED",
      "EXPIRED, EXPIRED",
      "已到期, EXPIRED",
      "已过期, EXPIRED"
  })
  @DisplayName("合同状态兼容中英文与历史别名")
  void contractStatusNormalizesAliases(String raw, String expected) {
    assertEquals(expected, ContractStatus.normalize(raw));
  }

  @Test
  @DisplayName("合同生效判定兼容历史中文写法")
  void contractActiveHelpers() {
    assertTrue(ContractStatus.isActive("ACTIVE"));
    assertTrue(ContractStatus.isActive("进行中"));
    assertFalse(ContractStatus.isActive("TERMINATED"));
    assertTrue(ContractStatus.isFinal("TERMINATED"));
    assertTrue(ContractStatus.isFinal("已到期"));
    assertFalse(ContractStatus.isFinal("ACTIVE"));
  }

  @Test
  @DisplayName("ACTIVE_RAW_VALUES 覆盖英文与中文，供 JPA 派生查询使用")
  void contractActiveRawValuesCoversBothSpellings() {
    assertTrue(ContractStatus.ACTIVE_RAW_VALUES.contains("ACTIVE"));
    assertTrue(ContractStatus.ACTIVE_RAW_VALUES.contains("进行中"));
    // 每个原始值归一化后必须都是 ACTIVE，否则查询会漏数据
    ContractStatus.ACTIVE_RAW_VALUES.forEach(raw ->
        assertTrue(ContractStatus.isActive(raw), "应归一化为 ACTIVE: " + raw));
  }

  // ---------- WorkflowStatus ----------

  @Test
  @DisplayName("工单终态判定")
  void workflowFinalStates() {
    assertTrue(WorkflowStatus.isFinal("completed"));
    assertTrue(WorkflowStatus.isFinal("CLOSED"));
    assertTrue(WorkflowStatus.isFinal("cancelled"));
    assertFalse(WorkflowStatus.isFinal("pending"));
    assertFalse(WorkflowStatus.isFinal("pending_confirm"));
    assertFalse(WorkflowStatus.isFinal(null));
  }

  @Test
  @DisplayName("工单完成判定忽略大小写与空白")
  void workflowIsCompleted() {
    assertTrue(WorkflowStatus.isCompleted("completed"));
    assertTrue(WorkflowStatus.isCompleted(" COMPLETED "));
    assertFalse(WorkflowStatus.isCompleted("closed"));
  }

  @Test
  @DisplayName("IN_PROGRESS 不含任何终态")
  void workflowInProgressExcludesFinal() {
    WorkflowStatus.IN_PROGRESS.forEach(s ->
        assertFalse(WorkflowStatus.isFinal(s), "进行中列表不应包含终态: " + s));
  }
}
