package com.house.service;

import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.Contract;
import com.house.model.ContractStatus;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.BillRepository;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 合同到期自动处理。
 *
 * <p>此前系统没有这个机制：合同过了 {@code end_date} 仍显示「生效中」，
 * 房间一直挂着「已租」，只能靠管理员手工去改。时间一长就会出现
 * 「明明没人住了，系统还说在租」——直接影响出租率统计与账单生成。
 *
 * <p>规则：
 * <ul>
 *   <li>合同到期 → 状态置为 {@link ContractStatus#EXPIRED}</li>
 *   <li>房间释放为 {@link RoomStatus#IDLE}，<b>但仅当该合同没有未结清账单</b></li>
 * </ul>
 * 有欠款时不释放房间：房间是「这笔欠款属于谁」的线索，先释放会让催缴失去抓手。
 * 这类合同会在结果里单列出来，由管理员处理完欠款后手工释放。
 */
@Service
public class ContractExpiryService {

  private static final Logger log = LoggerFactory.getLogger(ContractExpiryService.class);

  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;
  private final BillRepository billRepository;

  /** 是否启用到期自动处理。默认开启；如需完全人工管理可设为 false。 */
  @Value("${app.contract.auto-expire-enabled:true}")
  private boolean autoExpireEnabled;

  public ContractExpiryService(ContractRepository contractRepository,
      RoomRepository roomRepository,
      BillRepository billRepository) {
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
    this.billRepository = billRepository;
  }

  /**
   * 每日扫描：把已过到期日、仍标记为生效中的合同置为已到期。
   *
   * <p>cron 默认凌晨 0:15，避开账单生成（1:00）与提醒任务（0:05）。
   */
  @Scheduled(cron = "${app.contract.expire-cron:0 15 0 * * ?}")
  @Transactional
  public void expireContractsScheduled() {
    if (!autoExpireEnabled) {
      return;
    }
    Map<String, Object> result = expireContracts(LocalDate.now());
    int expired = (int) result.get("expiredCount");
    if (expired > 0) {
      log.info("合同到期自动处理完成：过期 {} 份，释放房间 {} 间，因欠款保留房间 {} 间",
          expired, result.get("releasedRoomCount"), result.get("keptRoomCount"));
    }
  }

  /**
   * 执行到期处理。抽成独立方法便于测试与手动触发。
   *
   * @param today 以哪一天为基准判断到期（便于测试传入固定日期）
   * @return 处理结果：过期合同数、释放房间数、因欠款保留的房间数、明细
   */
  @Transactional
  public Map<String, Object> expireContracts(LocalDate today) {
    List<Contract> activeContracts = contractRepository.findByStatusIn(ContractStatus.ACTIVE_RAW_VALUES);

    List<Map<String, Object>> expiredItems = new ArrayList<>();
    List<Map<String, Object>> keptItems = new ArrayList<>();
    int releasedRoomCount = 0;

    // 批量预取这些合同下的账单与房间，避免循环查库
    List<Long> contractIds = activeContracts.stream()
        .map(Contract::getId).filter(Objects::nonNull).toList();

    Map<Long, Boolean> hasUnpaidByContract = new HashMap<>();
    if (!contractIds.isEmpty()) {
      for (Bill b : billRepository.findByContractIdIn(contractIds)) {
        if (!BillStatus.isPaid(b.getStatus())) {
          hasUnpaidByContract.put(b.getContractId(), true);
        } else {
          hasUnpaidByContract.putIfAbsent(b.getContractId(), false);
        }
      }
    }

    List<Long> roomIds = activeContracts.stream()
        .map(Contract::getRoomId).filter(Objects::nonNull).distinct().toList();
    Map<Long, Room> roomById = new HashMap<>();
    if (!roomIds.isEmpty()) {
      for (Room r : roomRepository.findAllById(roomIds)) {
        roomById.put(r.getId(), r);
      }
    }

    // 这些合同即将过期，但房间可能已被另一份合同接续（换租/续租）。
    // 记录每个房间「除本批待过期合同之外」的其他生效合同最晚到期日：
    // 若存在且不早于本合同到期日，说明房间仍被占用，不能释放。
    Map<Long, LocalDate> latestOtherEndByRoom = new HashMap<>();
    if (!roomIds.isEmpty()) {
      java.util.Set<Long> expiringIds = activeContracts.stream()
          .filter(c -> c.getEndDate() != null && c.getEndDate().isBefore(today))
          .map(Contract::getId)
          .filter(Objects::nonNull)
          .collect(java.util.stream.Collectors.toSet());

      for (Contract c : contractRepository.findByRoomIdInAndStatus(roomIds, ContractStatus.ACTIVE)) {
        // 必须排除本批将过期的合同自身，否则「自己和自己比」永远判定为有接续
        if (c.getId() != null && expiringIds.contains(c.getId())) {
          continue;
        }
        if (c.getEndDate() == null) {
          continue;
        }
        latestOtherEndByRoom.merge(c.getRoomId(), c.getEndDate(),
            (a, b) -> a.isAfter(b) ? a : b);
      }
    }

    for (Contract contract : activeContracts) {
      LocalDate endDate = contract.getEndDate();
      // 无到期日视为长期合同，不处理
      if (endDate == null || !endDate.isBefore(today)) {
        continue;
      }

      contract.setStatus(ContractStatus.EXPIRED);
      contractRepository.save(contract);

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("contractId", contract.getId());
      item.put("contractNo", contract.getContractNo());
      item.put("endDate", endDate);
      expiredItems.add(item);

      Room room = contract.getRoomId() == null ? null : roomById.get(contract.getRoomId());
      boolean hasUnpaid = hasUnpaidByContract.getOrDefault(contract.getId(), false);

      if (room == null) {
        continue;
      }
      if (hasUnpaid) {
        // 有欠款：保留房间状态，作为催缴线索
        item.put("keptRoomReason", "存在未结清账单，未释放房间");
        keptItems.add(item);
        continue;
      }

      // 房间还有别的生效合同，说明已换租或续租，不能置为空闲
      LocalDate latestOther = latestOtherEndByRoom.get(room.getId());
      if (latestOther != null && !latestOther.isBefore(endDate)) {
        item.put("keptRoomReason", "该房间存在其他生效合同，未释放房间");
        keptItems.add(item);
        continue;
      }

      room.setStatus(RoomStatus.IDLE);
      roomRepository.save(room);
      releasedRoomCount++;
      item.put("releasedRoomId", room.getId());
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("expiredCount", expiredItems.size());
    result.put("releasedRoomCount", releasedRoomCount);
    result.put("keptRoomCount", keptItems.size());
    result.put("expired", expiredItems);
    return result;
  }
}
