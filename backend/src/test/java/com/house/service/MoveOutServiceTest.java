package com.house.service;

import com.house.dto.MoveOutCheckResult;
import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.DepositRecord;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.BillRepository;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.DepositRecordRepository;
import com.house.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 退租预检测试：押金余额、未结清账单、拦截判定。
 *
 * <p>这些计算直接决定「能不能退租」和「该退多少钱」，
 * 出错会造成资金差错，因此覆盖边界。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoveOutServiceTest {

  @Mock private ContractRepository contractRepository;
  @Mock private ContractService contractService;
  @Mock private RoomRepository roomRepository;
  @Mock private BuildingRepository buildingRepository;
  @Mock private BillRepository billRepository;
  @Mock private DepositRecordRepository depositRecordRepository;
  @Mock private TenantService tenantService;

  @InjectMocks private MoveOutService moveOutService;

  private Contract contract;
  private Room room;

  @BeforeEach
  void setUp() {
    contract = Contract.builder()
        .id(1L)
        .contractNo("CT001")
        .tenantId(10L)
        .roomId(100L)
        .status("ACTIVE")
        .deposit(2800.0)
        .build();
    room = Room.builder()
        .id(100L)
        .buildingId(5L)
        .roomNumber("101")
        .status(RoomStatus.RENTED)
        .build();

    when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
    when(roomRepository.findById(100L)).thenReturn(Optional.of(room));
    when(buildingRepository.findById(5L))
        .thenReturn(Optional.of(Building.builder().id(5L).name("A栋").build()));
    when(tenantService.findById(10L)).thenReturn(Optional.empty());
    when(billRepository.findByContractId(1L)).thenReturn(List.of());
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of());
  }

  @Test
  @DisplayName("无收款记录时押金余额为 0，并提示尚未登记押金")
  void depositBalanceZeroWithoutRecords() {
    MoveOutCheckResult r = moveOutService.check(1L);

    assertEquals(0.0, r.depositBalance());
    assertEquals(2800.0, r.depositAmount());
    assertTrue(r.warnings().stream().anyMatch(w -> w.contains("尚未登记押金")),
        "应提示未登记押金");
  }

  @Test
  @DisplayName("押金余额 = 已收 - 已退")
  void depositBalanceIsReceivedMinusRefunded() {
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of(
        DepositRecord.builder().contractId(1L).type("RECEIVE").amount(2800.0).build(),
        DepositRecord.builder().contractId(1L).type("REFUND").amount(300.0).build()
    ));

    MoveOutCheckResult r = moveOutService.check(1L);

    assertEquals(2800.0, r.receivedDeposit());
    assertEquals(300.0, r.refundedDeposit());
    assertEquals(2500.0, r.depositBalance());
  }

  @Test
  @DisplayName("已退超过已收时余额归零，不出现负数")
  void depositBalanceNeverNegative() {
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of(
        DepositRecord.builder().contractId(1L).type("RECEIVE").amount(1000.0).build(),
        DepositRecord.builder().contractId(1L).type("REFUND").amount(1500.0).build()
    ));

    assertEquals(0.0, moveOutService.check(1L).depositBalance());
  }

  @Test
  @DisplayName("其它合同的押金记录不计入本单")
  void depositRecordsAreScopedToContract() {
    // 现在按合同直查，不再把全表读进内存过滤：
    // 断言只请求了本合同的记录，不会退化成 findAll
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of(
        DepositRecord.builder().contractId(1L).type("RECEIVE").amount(1000.0).build()
    ));

    assertEquals(1000.0, moveOutService.check(1L).receivedDeposit());
    verify(depositRecordRepository).findByContractIdIn(List.of(1L));
    verify(depositRecordRepository, never()).findAll();
  }

  @Test
  @DisplayName("未结清账单计入未收总额并触发拦截")
  void unpaidBillsBlockMoveOut() {
    when(billRepository.findByContractId(1L)).thenReturn(List.of(
        Bill.builder().id(11L).billMonth("2026-10").totalAmount(2800.0)
            .paidAmount(0.0).status(BillStatus.PENDING).build(),
        Bill.builder().id(12L).billMonth("2026-11").totalAmount(3000.0)
            .paidAmount(1000.0).status(BillStatus.PARTIAL).build()
    ));

    MoveOutCheckResult r = moveOutService.check(1L);

    assertEquals(2, r.unpaidCount());
    // 2800 + (3000-1000) = 4800
    assertEquals(4800.0, r.unpaidAmount());
    assertTrue(r.blocked(), "有未结清账单时应拦截");
  }

  @Test
  @DisplayName("已付清与已作废账单不计入未收")
  void finalBillsAreExcluded() {
    when(billRepository.findByContractId(1L)).thenReturn(List.of(
        Bill.builder().id(11L).totalAmount(2800.0).paidAmount(2800.0)
            .status(BillStatus.PAID).build(),
        Bill.builder().id(12L).totalAmount(3000.0).paidAmount(0.0)
            .status(BillStatus.VOID).build()
    ));

    MoveOutCheckResult r = moveOutService.check(1L);

    assertEquals(0, r.unpaidCount());
    assertEquals(0.0, r.unpaidAmount());
    assertFalse(r.blocked(), "无未结清账单时不应拦截");
  }

  @Test
  @DisplayName("中文状态的历史账单同样被正确识别")
  void chineseStatusBillsHandled() {
    when(billRepository.findByContractId(1L)).thenReturn(List.of(
        Bill.builder().id(11L).totalAmount(2000.0).paidAmount(0.0).status("未支付").build(),
        Bill.builder().id(12L).totalAmount(2000.0).paidAmount(2000.0).status("已支付").build()
    ));

    MoveOutCheckResult r = moveOutService.check(1L);

    assertEquals(1, r.unpaidCount());
    assertEquals(2000.0, r.unpaidAmount());
  }

  @Test
  @DisplayName("合同不存在时抛出可读异常")
  void missingContractThrows() {
    when(contractRepository.findById(99L)).thenReturn(Optional.empty());

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> moveOutService.check(99L));
    assertEquals("合同不存在", ex.getMessage());
  }

  @Test
  @DisplayName("检查结果包含房间与楼栋信息，供界面直接展示")
  void checkIncludesRoomAndBuilding() {
    MoveOutCheckResult r = moveOutService.check(1L);

    assertEquals("101", r.roomNumber());
    assertEquals("A栋", r.buildingName());
    assertEquals("CT001", r.contractNo());
  }

  // ---------- 结算 ----------

  @Test
  @DisplayName("存在未结清账单且未强制时拒绝退租，且不改动任何数据")
  void settleRejectsWhenUnpaidAndNotForced() {
    when(billRepository.findByContractId(1L)).thenReturn(List.of(
        Bill.builder().id(11L).totalAmount(1000.0).paidAmount(0.0)
            .status(BillStatus.PENDING).build()
    ));

    var req = new com.house.dto.MoveOutRequest(
        "2026-11-30", "退租", true, null, 0.0, null, false, null, false);

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> moveOutService.settle(1L, req));
    assertTrue(ex.getMessage().contains("未结清账单"));

    verify(contractService, never()).terminate(any(), any(), any());
    verify(depositRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("强制退租可越过欠费拦截")
  void settleAllowsForce() {
    when(billRepository.findByContractId(1L)).thenReturn(List.of(
        Bill.builder().id(11L).totalAmount(1000.0).paidAmount(0.0)
            .status(BillStatus.PENDING).build()
    ));
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of(
        DepositRecord.builder().contractId(1L).type("RECEIVE").amount(2000.0).build()
    ));

    var req = new com.house.dto.MoveOutRequest(
        "2026-11-30", "退租", true, null, 0.0, null, false, null, true);

    var result = moveOutService.settle(1L, req);

    assertEquals(2000.0, result.get("refundedDeposit"));
    verify(contractService).terminate(eq(1L), eq("2026-11-30"), any());
    verify(depositRecordRepository).save(any(DepositRecord.class));
  }

  @Test
  @DisplayName("退押金时扣款生效：实退 = 余额 - 扣款")
  void settleAppliesDeduction() {
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of(
        DepositRecord.builder().contractId(1L).type("RECEIVE").amount(3000.0).build()
    ));

    var req = new com.house.dto.MoveOutRequest(
        "2026-11-30", null, true, null, 500.0, "墙面损坏", false, null, false);

    var result = moveOutService.settle(1L, req);

    assertEquals(2500.0, result.get("refundedDeposit"));
  }

  @Test
  @DisplayName("不勾选退押金时不产生押金记录")
  void settleWithoutRefundSkipsDepositRecord() {
    when(depositRecordRepository.findByContractIdIn(anyList())).thenReturn(List.of(
        DepositRecord.builder().contractId(1L).type("RECEIVE").amount(3000.0).build()
    ));

    var req = new com.house.dto.MoveOutRequest(
        "2026-11-30", null, false, null, 0.0, null, false, null, false);

    var result = moveOutService.settle(1L, req);

    assertEquals(0.0, result.get("refundedDeposit"));
    verify(depositRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("非进行中合同不能退租")
  void settleRejectsNonActiveContract() {
    contract.setStatus("TERMINATED");

    var req = new com.house.dto.MoveOutRequest(
        "2026-11-30", null, false, null, 0.0, null, false, null, false);

    assertThrows(IllegalStateException.class, () -> moveOutService.settle(1L, req));
  }
}
