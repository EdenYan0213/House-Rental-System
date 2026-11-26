package com.house.controller;

import com.house.model.Contract;
import com.house.model.DepositRecord;
import com.house.model.Tenant;
import com.house.repo.ContractRepository;
import com.house.repo.DepositRecordRepository;
import com.house.repo.RoomRepository;
import com.house.service.TenantTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租客押金接口的隔离性测试。
 *
 * <p>守护一个真实存在过的数据泄漏：`/api/deposits` 只要求登录，
 * 于是租客 token 可以翻出全部租客的押金记录（姓名、合同号、金额），
 * 甚至能 POST 伪造记录。现在管理端加了角色门禁，
 * 租客只能通过本接口看到自己名下合同的记录。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("租客押金查询")
class TenantDepositScopeTest {

  @Mock
  private TenantTokenService tenantTokenService;
  @Mock
  private ContractRepository contractRepository;
  @Mock
  private DepositRecordRepository depositRecordRepository;
  @Mock
  private RoomRepository roomRepository;

  private TenantContractController controller;

  private TenantContractController build() {
    return new TenantContractController(
        tenantTokenService,
        contractRepository,
        null, // renewApplicationRepository：本测试不涉及
        roomRepository,
        null, // fileStorageConfig
        null, // feedbackRepository
        depositRecordRepository,
        null); // support
  }

  private Map<String, Object> tenantContext(long tenantId) {
    Map<String, Object> context = new HashMap<>();
    context.put("tenantId", tenantId);
    context.put("tenant", new Tenant());
    return context;
  }

  @Test
  @DisplayName("只查询该租客名下合同的押金记录")
  void queriesOnlyOwnContracts() {
    controller = build();
    when(tenantTokenService.parseAndValidateTenant(any())).thenReturn(tenantContext(225L));

    Contract mine = new Contract();
    mine.setId(221L);
    when(contractRepository.findByTenantId(225L)).thenReturn(List.of(mine));

    DepositRecord receive = DepositRecord.builder()
        .id(1L).contractId(221L).type("RECEIVE").amount(2400D)
        .date(LocalDateTime.now().minusDays(10)).build();
    DepositRecord refund = DepositRecord.builder()
        .id(2L).contractId(221L).type("REFUND").amount(400D)
        .date(LocalDateTime.now()).build();
    when(depositRecordRepository.findByContractIdIn(List.of(221L))).thenReturn(List.of(receive, refund));

    ResponseEntity<?> response = controller.myDeposits("Bearer token");
    assertEquals(200, response.getStatusCode().value());

    Map<?, ?> body = (Map<?, ?>) response.getBody();
    assertNotNull(body);
    // 只按自己的合同 ID 去查，不会退化成 findAll
    verify(depositRecordRepository).findByContractIdIn(List.of(221L));
    verify(depositRecordRepository, never()).findAll();

    Map<?, ?> summary = (Map<?, ?>) body.get("summary");
    assertEquals(2400D, (Double) summary.get("received"));
    assertEquals(400D, (Double) summary.get("refunded"));
    assertEquals(2000D, (Double) summary.get("balance"));
  }

  @Test
  @DisplayName("兼容中文“退还”写法")
  void treatsChineseRefundAsRefund() {
    controller = build();
    when(tenantTokenService.parseAndValidateTenant(any())).thenReturn(tenantContext(225L));

    Contract mine = new Contract();
    mine.setId(221L);
    when(contractRepository.findByTenantId(225L)).thenReturn(List.of(mine));

    DepositRecord refund = DepositRecord.builder()
        .id(3L).contractId(221L).type("退还").amount(500D)
        .date(LocalDateTime.now()).build();
    when(depositRecordRepository.findByContractIdIn(List.of(221L))).thenReturn(List.of(refund));

    Map<?, ?> body = (Map<?, ?>) controller.myDeposits("Bearer token").getBody();
    assertNotNull(body);
    Map<?, ?> summary = (Map<?, ?>) body.get("summary");
    assertEquals(0D, (Double) summary.get("received"));
    assertEquals(500D, (Double) summary.get("refunded"));
  }

  @Test
  @DisplayName("没有合同时返回空结果，不查押金表")
  void emptyWhenNoContracts() {
    controller = build();
    when(tenantTokenService.parseAndValidateTenant(any())).thenReturn(tenantContext(999L));
    when(contractRepository.findByTenantId(999L)).thenReturn(List.of());

    Map<?, ?> body = (Map<?, ?>) controller.myDeposits("Bearer token").getBody();
    assertNotNull(body);
    assertTrue(((List<?>) body.get("content")).isEmpty());
    verify(depositRecordRepository, never()).findByContractIdIn(anyList());
  }
}
