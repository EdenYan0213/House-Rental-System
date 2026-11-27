package com.house.service;

import com.house.dto.ContractCreateRequest;
import com.house.model.Contract;
import com.house.model.BillStatus;
import com.house.model.ContractStatus;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.model.Tenant;
import com.house.model.Building;
import com.house.model.Bill;
import com.house.repo.ContractRepository;
import com.house.repo.BillRepository;
import com.house.repo.RoomRepository;
import com.house.repo.BuildingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ContractService {
  private final ContractRepository contractRepository;
  private final RoomService roomService;
  private final TenantService tenantService;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final BillRepository billRepository;
  private final com.house.repo.ContractAttachmentRepository attachmentRepository;

  public ContractService(ContractRepository contractRepository, RoomService roomService,
      TenantService tenantService, RoomRepository roomRepository, BuildingRepository buildingRepository,
      BillRepository billRepository,
      com.house.repo.ContractAttachmentRepository attachmentRepository) {
    this.contractRepository = contractRepository;
    this.roomService = roomService;
    this.tenantService = tenantService;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.billRepository = billRepository;
    this.attachmentRepository = attachmentRepository;
  }

  @Transactional
  public Contract createContract(ContractCreateRequest req) {
    // roomId 为空时直接给可读提示：findById(null) 会抛
    // InvalidDataAccessApiUsageException（不是 IllegalArgumentException），
    // 会被 controller 的 catch 漏掉，最终变成 500。
    if (req.getRoomId() == null) {
      throw new IllegalArgumentException("请选择房间");
    }
    Optional<Room> or = roomService.findById(req.getRoomId());
    if (or.isEmpty())
      throw new IllegalArgumentException("房间不存在");
    Room room = or.get();
    if (!RoomStatus.isIdle(room.getStatus())) {
      throw new IllegalStateException("房间当前不可租（当前状态：" + RoomStatus.normalize(room.getStatus()) + "）");
    }

    Tenant tenant = null;
    if (req.getTenantId() != null) {
      tenant = tenantService.findById(req.getTenantId()).orElseThrow(() -> new IllegalArgumentException("租户不存在"));
    } else {
      Tenant t = Tenant.builder()
          .name(req.getTenantName())
          .phone(req.getTenantPhone())
          .idCard(req.getTenantIdCard())
          .emergencyContact(req.getTenantEmergencyContact())
          .emergencyPhone(req.getTenantEmergencyPhone())
          .remark(req.getTenantRemark())
          .build();
      tenant = tenantService.create(t);
    }

    String contractNo = "CT" + System.currentTimeMillis();
    LocalDate startDate = req.getStartDate();
    LocalDate endDate = req.getEndDate();

    // 冗余存房间号/楼栋名/租户名，供列表直接展示，避免逐条关联查询
    String buildingName = buildingRepository.findById(room.getBuildingId())
        .map(Building::getName)
        .orElse(null);

    Contract c = Contract.builder()
        .contractNo(contractNo)
        .roomId(room.getId())
        .roomNumber(room.getRoomNumber())
        .buildingName(buildingName)
        .tenantId(tenant.getId())
        .tenantName(tenant.getName())
        .tenantPhone(tenant.getPhone())
        .startDate(startDate)
        .endDate(endDate)
        .status(ContractStatus.ACTIVE)
        .deposit(req.getDeposit())
        .monthlyRent(req.getMonthlyRent())
        .paymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "MONTHLY")
        .remark(req.getRemark())
        .attachmentUrl(req.getAttachmentUrl())
        .build();

    Contract saved = contractRepository.save(c);

    room.setStatus(RoomStatus.RENTED);
    roomService.update(room);

    return saved;
  }

  public Optional<Contract> findById(Long id) {
    return contractRepository.findById(id);
  }

  public List<Contract> search(String status, String q, int page, int size, String expiring) {
    List<Contract> all = contractRepository.findAll();

    if (status != null && !status.isBlank() && !status.equals("expiring")) {
      all = all.stream().filter(c -> status.equals(c.getStatus())).collect(Collectors.toList());
    }

    if (expiring != null && !expiring.isBlank()) {
      int days = 3;
      try {
        days = Integer.parseInt(expiring);
      } catch (NumberFormatException e) {
      }
      LocalDate threshold = LocalDate.now().plusDays(days);
      all = all.stream()
          .filter(c -> ContractStatus.isActive(c.getStatus()))
          .filter(c -> c.getEndDate() != null && !c.getEndDate().isAfter(threshold))
          .filter(c -> {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
            return daysLeft >= 0;
          })
          .collect(Collectors.toList());
    } else if (status != null && status.equals("expiring")) {
      LocalDate thirtyDaysLater = LocalDate.now().plusDays(30);
      all = all.stream()
          .filter(c -> ContractStatus.isActive(c.getStatus()))
          .filter(c -> c.getEndDate() != null && c.getEndDate().isBefore(thirtyDaysLater))
          .collect(Collectors.toList());
    }

    if (q != null && !q.isBlank()) {
      final String query = q.toLowerCase();
      all = all.stream()
          .filter(c -> {
            if (c.getContractNo() != null && c.getContractNo().toLowerCase().contains(query))
              return true;
            return false;
          })
          .collect(Collectors.toList());
    }

    fillDisplayFields(all);

    int start = page * size;
    int end = Math.min(start + size, all.size());
    if (start >= all.size())
      return new ArrayList<>();
    return all.subList(start, end);
  }

  /**
   * 分页查询，额外返回筛选后的总条数。
   *
   * <p>前端要靠 totalElements 算总页数；原 {@link #search} 只返回当前页，
   * 调用方无法翻页。
   */
  public Map<String, Object> searchPaged(String status, String q, int page, int size, String expiring) {
    // 先取全量完成筛选与展示字段填充，再切片（与 search 共用同一套规则）
    List<Contract> all = search(status, q, 0, Integer.MAX_VALUE, expiring);
    int total = all.size();
    int safeSize = Math.max(size, 1);
    int safePage = Math.max(page, 0);
    int from = Math.min(safePage * safeSize, total);
    int to = Math.min(from + safeSize, total);

    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("content", all.subList(from, to));
    body.put("totalElements", total);
    body.put("totalPages", safeSize == 0 ? 0 : (int) Math.ceil((double) total / safeSize));
    body.put("page", safePage);
    body.put("size", safeSize);
    return body;
  }

  /**
   * 批量填充合同的展示字段（房间号、楼栋名、租户姓名/电话）。
   *
   * <p>原实现逐条查 room / building / tenant，100 条合同就是 300 次查询。
   * 这里改为各查一次后回填。
   */
  private void fillDisplayFields(List<Contract> contracts) {
    if (contracts == null || contracts.isEmpty()) {
      return;
    }
    List<Long> roomIds = contracts.stream()
        .map(Contract::getRoomId).filter(java.util.Objects::nonNull).distinct().toList();
    List<Long> tenantIds = contracts.stream()
        .map(Contract::getTenantId).filter(java.util.Objects::nonNull).distinct().toList();

    Map<Long, Room> roomMap = new java.util.HashMap<>();
    if (!roomIds.isEmpty()) {
      for (Room r : roomRepository.findAllById(roomIds)) {
        roomMap.put(r.getId(), r);
      }
    }
    List<Long> buildingIds = roomMap.values().stream()
        .map(Room::getBuildingId).filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, Building> buildingMap = new java.util.HashMap<>();
    if (!buildingIds.isEmpty()) {
      for (Building b : buildingRepository.findAllById(buildingIds)) {
        buildingMap.put(b.getId(), b);
      }
    }
    Map<Long, Tenant> tenantMap = new java.util.HashMap<>();
    if (!tenantIds.isEmpty()) {
      for (Tenant t : tenantService.findAllByIds(tenantIds)) {
        tenantMap.put(t.getId(), t);
      }
    }

    for (Contract c : contracts) {
      Room room = roomMap.get(c.getRoomId());
      if (room != null) {
        c.setRoomNumber(room.getRoomNumber());
        Building b = buildingMap.get(room.getBuildingId());
        if (b != null) {
          c.setBuildingName(b.getName());
        }
      }
      Tenant tenant = tenantMap.get(c.getTenantId());
      if (tenant != null) {
        c.setTenantName(tenant.getName());
        c.setTenantPhone(tenant.getPhone());
      }
    }
  }

  @Transactional
  public Contract update(Contract contract) {
    return contractRepository.save(contract);
  }

  @Transactional
  public void delete(Long id) {
    contractRepository.deleteById(id);
  }

  @Transactional
  public void terminate(Long id, String terminateDate, String reason) {
    Optional<Contract> oc = contractRepository.findById(id);
    if (oc.isPresent()) {
      Contract c = oc.get();
      c.setStatus(ContractStatus.TERMINATED);
      if (terminateDate != null) {
        c.setEndDate(LocalDate.parse(terminateDate));
      }
      contractRepository.save(c);

      Optional<Room> or = roomRepository.findById(c.getRoomId());
      if (or.isPresent()) {
        Room room = or.get();
        room.setStatus(RoomStatus.IDLE);
        roomRepository.save(room);
      }
    }
  }

  @Transactional
  public Contract renew(Long id, String newEndDate) {
    Optional<Contract> oc = contractRepository.findById(id);
    if (oc.isEmpty()) {
      throw new IllegalArgumentException("合同不存在");
    }
    Contract contract = oc.get();
    if (!ContractStatus.isActive(contract.getStatus())) {
      throw new IllegalStateException("仅进行中合同可续租");
    }
    if (newEndDate == null || newEndDate.isBlank()) {
      throw new IllegalArgumentException("请填写新的结束日期");
    }

    LocalDate newEnd = LocalDate.parse(newEndDate);
    if (contract.getEndDate() != null && !newEnd.isAfter(contract.getEndDate())) {
      throw new IllegalArgumentException("新的结束日期必须晚于当前结束日期");
    }

    contract.setEndDate(newEnd);
    return contractRepository.save(contract);
  }

  @Transactional
  public Map<String, Object> batchRenew(List<Long> contractIds,
      String startDate,
      String endDate,
      Integer durationMonths,
      String rentMode,
      Double fixedAmount,
      Double percent,
      Boolean inheritDeposit,
      Boolean inheritPaymentMethod) {
    if (contractIds == null || contractIds.isEmpty()) {
      throw new IllegalArgumentException("请选择要续租的合同");
    }

    List<Contract> contracts = contractRepository.findAllById(contractIds);
    List<Contract> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    for (Contract oldContract : contracts) {
      if (!ContractStatus.isActive(oldContract.getStatus())) {
        skipped.add("合同" + oldContract.getContractNo() + "不是进行中状态");
        continue;
      }
      if (oldContract.getEndDate() == null || oldContract.getEndDate().isBefore(LocalDate.now())) {
        skipped.add("合同" + oldContract.getContractNo() + "已过期，不能续租");
        continue;
      }

      List<Bill> bills = billRepository.findByContractId(oldContract.getId());
      boolean hasUnpaid = bills.stream()
          .anyMatch(b -> !BillStatus.isPaid(b.getStatus()));
      if (hasUnpaid) {
        skipped.add("合同" + oldContract.getContractNo() + "存在未结清账单");
        continue;
      }

      LocalDate newStart = (startDate == null || startDate.isBlank()) ? oldContract.getEndDate().plusDays(1)
          : LocalDate.parse(startDate);
      LocalDate newEnd;
      if (endDate != null && !endDate.isBlank()) {
        newEnd = LocalDate.parse(endDate);
      } else if (durationMonths != null && durationMonths > 0) {
        newEnd = newStart.plusMonths(durationMonths).minusDays(1);
      } else {
        newEnd = newStart.plusYears(1).minusDays(1);
      }

      Double oldRent = oldContract.getMonthlyRent() == null ? 0D : oldContract.getMonthlyRent();
      Double newRent = oldRent;
      if ("PERCENT".equalsIgnoreCase(rentMode) && percent != null) {
        newRent = oldRent * (1 + percent / 100D);
      } else if (fixedAmount != null) {
        newRent = oldRent + fixedAmount;
      }
      if (newRent < 0) {
        newRent = 0D;
      }

      Contract newContract = Contract.builder()
          .contractNo("CT" + System.currentTimeMillis() + oldContract.getId())
          .roomId(oldContract.getRoomId())
          .tenantId(oldContract.getTenantId())
          .startDate(newStart)
          .endDate(newEnd)
          .status(ContractStatus.PENDING)
          .deposit(Boolean.FALSE.equals(inheritDeposit) ? 0D : oldContract.getDeposit())
          .monthlyRent(newRent)
          .paymentMethod(Boolean.FALSE.equals(inheritPaymentMethod) ? "MONTHLY" : oldContract.getPaymentMethod())
          .remark(oldContract.getRemark())
          .attachmentUrl(oldContract.getAttachmentUrl())
          .build();
      Contract savedNew = contractRepository.save(newContract);
      created.add(savedNew);

      // 续租生成的合同继承旧合同的附件版本，保证「当时签的是哪版」可追溯
      if (oldContract.getAttachmentUrl() != null && !oldContract.getAttachmentUrl().isBlank()) {
        attachmentRepository.save(com.house.model.ContractAttachment.builder()
            .contractId(savedNew.getId())
            .version(1)
            .fileUrl(oldContract.getAttachmentUrl())
            .category("CONTRACT")
            .remark("续租继承自 " + oldContract.getContractNo())
            .operator("系统")
            .build());
      }
    }

    return Map.of(
        "createdCount", created.size(),
        "skippedCount", skipped.size(),
        "skipped", skipped,
        "created", created);
  }

  @Transactional
  public Map<String, Object> batchTerminate(List<Long> contractIds, String terminateDate, String reason,
      boolean allowForce) {
    if (contractIds == null || contractIds.isEmpty()) {
      throw new IllegalArgumentException("请选择要退租的合同");
    }
    LocalDate tDate = (terminateDate == null || terminateDate.isBlank()) ? LocalDate.now()
        : LocalDate.parse(terminateDate);

    List<Contract> contracts = contractRepository.findAllById(contractIds);
    List<String> blocked = new ArrayList<>();
    List<Contract> terminated = new ArrayList<>();

    // 一次性把涉及合同的账单捞出来，避免循环里逐条查库（批量退租时是 N+1）
    Map<Long, Boolean> hasUnpaidByContract = new java.util.HashMap<>();
    if (!contracts.isEmpty()) {
      List<Long> ids = contracts.stream().map(Contract::getId).filter(java.util.Objects::nonNull).toList();
      if (!ids.isEmpty()) {
        for (Bill b : billRepository.findByContractIdIn(ids)) {
          if (!BillStatus.isPaid(b.getStatus())) {
            hasUnpaidByContract.put(b.getContractId(), true);
          } else {
            hasUnpaidByContract.putIfAbsent(b.getContractId(), false);
          }
        }
      }
    }

    // 房间同样批量预取，避免循环内 findById
    Map<Long, Room> roomById = new java.util.HashMap<>();
    List<Long> roomIds = contracts.stream().map(Contract::getRoomId).filter(java.util.Objects::nonNull).distinct().toList();
    if (!roomIds.isEmpty()) {
      for (Room r : roomRepository.findAllById(roomIds)) {
        roomById.put(r.getId(), r);
      }
    }

    for (Contract c : contracts) {
      if (!ContractStatus.isActive(c.getStatus())) {
        blocked.add("合同" + c.getContractNo() + "不是进行中状态");
        continue;
      }
      boolean hasUnpaid = hasUnpaidByContract.getOrDefault(c.getId(), false);
      if (hasUnpaid && !allowForce) {
        blocked.add("合同" + c.getContractNo() + "存在未结清账单");
        continue;
      }
      c.setStatus(ContractStatus.TERMINATED);
      c.setEndDate(tDate);
      if (reason != null && !reason.isBlank()) {
        c.setRemark((c.getRemark() == null ? "" : c.getRemark() + " | ") + "批量退租原因: " + reason);
      }
      contractRepository.save(c);

      Room room = roomById.get(c.getRoomId());
      if (room != null) {
        room.setStatus(RoomStatus.IDLE);
        roomRepository.save(room);
      }
      terminated.add(c);
    }

    if (!blocked.isEmpty() && terminated.isEmpty()) {
      throw new IllegalStateException("存在未结清事项，无法退租: " + String.join("; ", blocked));
    }

    return Map.of(
        "terminatedCount", terminated.size(),
        "blockedCount", blocked.size(),
        "blocked", blocked,
        "terminated", terminated.stream().map(Contract::getContractNo).toList());
  }
}
