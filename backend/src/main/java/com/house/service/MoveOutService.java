package com.house.service;

import com.house.dto.MoveOutCheckResult;
import com.house.dto.MoveOutRequest;
import com.house.model.Bill;
import com.house.model.ContractStatus;
import com.house.model.BillStatus;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.DepositRecord;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.model.Tenant;
import com.house.repo.BillRepository;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.DepositRecordRepository;
import com.house.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退租一条龙：预检 + 结算。
 *
 * <p>把原先分散的「解约 / 退押金 / 加黑名单 / 房间置空闲」收敛为一次事务，
 * 避免管理员漏做其中某一步，导致房间状态与合同状态不一致。
 */
@Service
@RequiredArgsConstructor
public class MoveOutService {

  private static final Logger log = LoggerFactory.getLogger(MoveOutService.class);

  private final ContractRepository contractRepository;
  private final ContractService contractService;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final BillRepository billRepository;
  private final DepositRecordRepository depositRecordRepository;
  private final TenantService tenantService;

  /** 退租前预检：返回押金余额、未结清账单、缺读数等关键信息。 */
  public MoveOutCheckResult check(Long contractId) {
    Contract contract = contractRepository.findById(contractId)
        .orElseThrow(() -> new IllegalArgumentException("合同不存在"));

    Room room = contract.getRoomId() == null ? null
        : roomRepository.findById(contract.getRoomId()).orElse(null);
    String buildingName = room == null || room.getBuildingId() == null ? null
        : buildingRepository.findById(room.getBuildingId()).map(Building::getName).orElse(null);
    Tenant tenant = contract.getTenantId() == null ? null
        : tenantService.findById(contract.getTenantId()).orElse(null);

    double depositAmount = nz(contract.getDeposit());
    double received = 0;
    double refunded = 0;
    for (DepositRecord r : depositRecordRepository.findByContractIdIn(List.of(contractId))) {
      double amt = nz(r.getAmount());
      if ("RECEIVE".equalsIgnoreCase(r.getType())) {
        received += amt;
      } else if ("REFUND".equalsIgnoreCase(r.getType())) {
        refunded += amt;
      }
    }
    double balance = Math.max(received - refunded, 0);

    // 未结清账单（非 PAID / 非 VOID）
    List<Bill> bills = billRepository.findByContractId(contractId);
    List<Map<String, Object>> unpaidBills = new ArrayList<>();
    double unpaidAmount = 0;
    for (Bill b : bills) {
      if (!BillStatus.isOutstanding(b.getStatus())) {
        continue;
      }
      double remain = Math.max(nz(b.getTotalAmount()) - nz(b.getPaidAmount()), 0);
      unpaidAmount += remain;
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", b.getId());
      item.put("billMonth", b.getBillMonth());
      item.put("totalAmount", b.getTotalAmount());
      item.put("paidAmount", b.getPaidAmount());
      item.put("remainAmount", remain);
      unpaidBills.add(item);
    }

    List<String> warnings = new ArrayList<>();
    if (!unpaidBills.isEmpty()) {
      warnings.add("存在 " + unpaidBills.size() + " 张未结清账单，合计 ¥" + fmt(unpaidAmount));
    }
    if (received <= 0 && depositAmount > 0) {
      warnings.add("该合同尚未登记押金收取记录，可能无法退还押金");
    }
    if (room != null && !RoomStatus.isRented(room.getStatus())) {
      warnings.add("房间当前状态为「" + RoomStatus.normalize(room.getStatus()) + "」，退租后仍会置为空闲");
    }

    String unpaidItems = unpaidBills.isEmpty() ? null
        : unpaidBills.stream()
            .map(b -> b.get("billMonth") + " 待收 ¥" + fmt((Double) b.get("remainAmount")))
            .reduce((a, b) -> a + "；" + b)
            .orElse(null);

    return new MoveOutCheckResult(
        contract.getId(),
        contract.getContractNo(),
        contract.getTenantId(),
        tenant == null ? contract.getTenantName() : tenant.getName(),
        contract.getRoomId(),
        room == null ? contract.getRoomNumber() : room.getRoomNumber(),
        buildingName,
        depositAmount,
        received,
        refunded,
        balance,
        unpaidAmount,
        unpaidBills.size(),
        unpaidBills,
        unpaidItems,
        false,
        !unpaidBills.isEmpty(),
        warnings);
  }

  /**
   * 执行退租结算。
   *
   * <p>步骤：校验 → 退还押金（写押金记录）→ 解约合同 → 房间置空闲 → 可选加入黑名单。
   * 全部在同一事务内，任一步失败整体回滚。
   *
   * @return 执行结果摘要，含实际退押金额、解约后房间状态等
   */
  @Transactional
  public Map<String, Object> settle(Long contractId, MoveOutRequest req) {
    Contract contract = contractRepository.findById(contractId)
        .orElseThrow(() -> new IllegalArgumentException("合同不存在"));
    if (!ContractStatus.isActive(contract.getStatus())) {
      throw new IllegalStateException("仅进行中的合同可办理退租，当前状态：" + contract.getStatus());
    }

    MoveOutCheckResult check = check(contractId);
    boolean force = Boolean.TRUE.equals(req.force());
    if (check.blocked() && !force) {
      throw new IllegalStateException(
          "存在 " + check.unpaidCount() + " 张未结清账单（合计 ¥" + fmt(check.unpaidAmount())
              + "），请先收款或勾选「强制退租」");
    }

    List<String> done = new ArrayList<>();
    double refunded = 0;

    // 1. 退还押金
    boolean doRefund = Boolean.TRUE.equals(req.refundDeposit());
    if (doRefund && check.depositBalance() > 0) {
      double deduct = Math.max(nz(req.deductAmount()), 0);
      // 未显式指定实退金额时，按「可退余额 - 扣款」计算，避免重复退全额
      double amount = req.refundAmount() == null
          ? Math.max(check.depositBalance() - deduct, 0)
          : req.refundAmount();
      amount = Math.max(amount, 0);
      // 上限保护：实退不得超过「可退余额 - 扣款」
      double maxRefund = Math.max(check.depositBalance() - deduct, 0);
      if (amount > maxRefund) {
        log.warn("退押金金额 {} 超过上限 {}，已按上限处理 contractId={}", amount, maxRefund, contractId);
        amount = maxRefund;
      }
      if (amount > 0) {
        String remark = "退租退还押金";
        if (deduct > 0) {
          remark += "（扣款 ¥" + fmt(deduct)
              + (req.deductReason() == null || req.deductReason().isBlank()
                  ? "" : "：" + req.deductReason())
              + "）";
        }
        DepositRecord record = DepositRecord.builder()
            .contractId(contractId)
            .type("REFUND")
            .amount(amount)
            .date(LocalDateTime.now())
            .remark(remark)
            .build();
        depositRecordRepository.save(record);
        refunded = amount;
        done.add("已退还押金 ¥" + fmt(amount));
        log.info("退租退押金 contractId={} amount={}", contractId, amount);
      }
    }

    // 2. 解约合同（同时会把房间置为空闲）
    String terminateDate = req.terminateDate();
    if (terminateDate == null || terminateDate.isBlank()) {
      terminateDate = LocalDate.now().toString();
    }
    contractService.terminate(contractId, terminateDate, req.reason());
    done.add("合同已解约（退租日 " + terminateDate + "）");

    // 3. 确保房间为空闲（terminate 已处理，这里兜底并记录结果）
    String roomStatusAfter = null;
    if (contract.getRoomId() != null) {
      Room room = roomRepository.findById(contract.getRoomId()).orElse(null);
      if (room != null) {
        if (!RoomStatus.isIdle(room.getStatus())) {
          room.setStatus(RoomStatus.IDLE);
          roomRepository.save(room);
        }
        roomStatusAfter = RoomStatus.normalize(room.getStatus());
        done.add("房间 " + room.getRoomNumber() + " 已置为空闲");
      }
    }

    // 4. 可选加入黑名单
    if (Boolean.TRUE.equals(req.addToBlacklist()) && contract.getTenantId() != null) {
      String reason = req.blacklistReason();
      if (reason == null || reason.isBlank()) {
        reason = req.reason() == null ? "退租" : req.reason();
      }
      try {
        tenantService.addToBlacklist(contract.getTenantId(), reason, null);
        done.add("租户已加入黑名单");
      } catch (Exception e) {
        // 已在黑名单等情况不阻断退租
        log.warn("退租时加入黑名单失败 contractId={} reason={}", contractId, e.getMessage());
        done.add("加入黑名单跳过：" + e.getMessage());
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("contractId", contractId);
    result.put("contractNo", contract.getContractNo());
    result.put("refundedDeposit", refunded);
    result.put("unpaidAmount", check.unpaidAmount());
    result.put("unpaidCount", check.unpaidCount());
    result.put("roomStatus", roomStatusAfter);
    result.put("steps", done);
    return result;
  }

  private static double nz(Double v) {
    return v == null ? 0 : v;
  }

  private static String fmt(double v) {
    return String.format("%,.2f", v);
  }
}
