package com.house.service;

import com.house.model.*;
import com.house.repo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RepairOrderService {
  private final RepairOrderRepository repairOrderRepository;
  private final RepairWorkerRepository repairWorkerRepository;
  private final RoomRepository roomRepository;
  private final TenantRepository tenantRepository;
  private final RoomApplianceRepository roomApplianceRepository;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final BillDetailRepository billDetailRepository;
  private final OperationLogService operationLogService;
  private final OperationSnapshotService operationSnapshotService;

  public RepairOrderService(RepairOrderRepository repairOrderRepository,
      RepairWorkerRepository repairWorkerRepository,
      RoomRepository roomRepository,
      TenantRepository tenantRepository,
      RoomApplianceRepository roomApplianceRepository,
      ContractRepository contractRepository,
      BillRepository billRepository,
      BillDetailRepository billDetailRepository,
      OperationLogService operationLogService,
      OperationSnapshotService operationSnapshotService) {
    this.repairOrderRepository = repairOrderRepository;
    this.repairWorkerRepository = repairWorkerRepository;
    this.roomRepository = roomRepository;
    this.tenantRepository = tenantRepository;
    this.roomApplianceRepository = roomApplianceRepository;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.billDetailRepository = billDetailRepository;
    this.operationLogService = operationLogService;
    this.operationSnapshotService = operationSnapshotService;
  }

  public Page<RepairOrder> search(String status, String startDate, String endDate, Long buildingId, Long workerId,
      int page, int size) {
    Specification<RepairOrder> spec = (root, query, cb) -> cb.conjunction();
    if (status != null && !status.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (workerId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("workerId"), workerId));
    }
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
    }
    if (buildingId != null) {
      List<Long> roomIds = roomRepository.findByBuildingId(buildingId).stream().map(Room::getId).toList();
      if (roomIds.isEmpty()) {
        return Page.empty();
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(roomIds));
    }

    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<RepairOrder> result = repairOrderRepository.findAll(spec, pageable);
    result.getContent().forEach(this::fillDisplayFields);
    return result;
  }

  public List<RepairOrder> listByRoom(Long roomId) {
    List<RepairOrder> list = repairOrderRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    list.forEach(this::fillDisplayFields);
    return list;
  }

  public List<RepairOrder> listByTenant(Long tenantId) {
    List<RepairOrder> list = repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    list.forEach(this::fillDisplayFields);
    return list;
  }

  public Optional<RepairOrder> findById(Long id) {
    Optional<RepairOrder> order = repairOrderRepository.findById(id);
    order.ifPresent(this::fillDisplayFields);
    return order;
  }

  @Transactional
  public RepairOrder create(RepairOrder order, HttpServletRequest request) {
    if (order.getRoomId() == null) {
      throw new IllegalArgumentException("房间不能为空");
    }
    if (order.getFaultType() == null || order.getFaultType().isBlank()) {
      throw new IllegalArgumentException("故障类型不能为空");
    }
    order.setOrderNo("RE" + System.currentTimeMillis());
    if (order.getReporterType() == null || order.getReporterType().isBlank()) {
      order.setReporterType("admin");
    }
    if (order.getStatus() == null || order.getStatus().isBlank()) {
      order.setStatus(WorkflowStatus.PENDING);
    }
    if (order.getTenantId() == null) {
      Contract active = contractRepository.findByRoomIdAndStatusIn(order.getRoomId(), ContractStatus.ACTIVE_RAW_VALUES).stream().findFirst()
          .orElse(null);
      if (active != null) {
        order.setTenantId(active.getTenantId());
      }
    }
    if (order.getMaterialFee() == null) {
      order.setMaterialFee(0D);
    }
    if (order.getLaborFee() == null) {
      order.setLaborFee(0D);
    }
    order.setTotalFee((order.getMaterialFee() == null ? 0D : order.getMaterialFee())
        + (order.getLaborFee() == null ? 0D : order.getLaborFee()));
    if (order.getIsTenantCharge() == null) {
      order.setIsTenantCharge(0);
    }
    LocalDateTime now = LocalDateTime.now();
    order.setCreatedAt(now);
    order.setUpdatedAt(now);
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "创建工单: " + saved.getOrderNo(), request);
    return saved;
  }

  @Transactional
  public RepairOrder assignWorker(Long id, Long workerId, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    RepairWorker worker = repairWorkerRepository.findById(workerId)
        .orElseThrow(() -> new IllegalArgumentException("维修工不存在"));
    if (!Objects.equals(worker.getStatus(), 1)) {
      throw new IllegalStateException("维修工已禁用，无法分配");
    }
    order.setWorkerId(workerId);
    order.setAssignedAt(LocalDateTime.now());
    order.setStatus(WorkflowStatus.PROCESSING);
    if (order.getStartTime() == null) {
      order.setStartTime(LocalDateTime.now());
    }
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "分配维修工: " + saved.getOrderNo() + " -> " + worker.getName(), request);
    return saved;
  }

  @Transactional
  public RepairOrder startRepair(Long id, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    if (order.getWorkerId() == null) {
      throw new IllegalStateException("请先分配维修工");
    }
    order.setStatus(WorkflowStatus.PROCESSING);
    order.setStartTime(LocalDateTime.now());
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "开始维修: " + saved.getOrderNo(), request);
    return saved;
  }

  @Transactional
  public RepairOrder completeRepair(Long id, String result, Double materialFee, Double laborFee,
      Integer isTenantCharge, String feeRemark, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    if (!WorkflowStatus.PROCESSING.equals(order.getStatus())) {
      throw new IllegalStateException("当前状态不可完成维修");
    }
    order.setRepairResult(result);
    order.setMaterialFee(materialFee == null ? 0D : materialFee);
    order.setLaborFee(laborFee == null ? 0D : laborFee);
    order.setTotalFee(order.getMaterialFee() + order.getLaborFee());
    order.setIsTenantCharge(isTenantCharge == null ? 0 : isTenantCharge);
    order.setFeeRemark(feeRemark);
    order.setFinishTime(LocalDateTime.now());
    order.setStatus(WorkflowStatus.PENDING_ACCEPT);
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "完成维修待验收: " + saved.getOrderNo(), request);
    return saved;
  }

  @Transactional
  public RepairOrder accept(Long id, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    if (!WorkflowStatus.PENDING_ACCEPT.equals(order.getStatus())) {
      throw new IllegalStateException("当前状态不可验收");
    }
    order.setStatus(WorkflowStatus.COMPLETED);
    order.setAcceptTime(LocalDateTime.now());
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "验收通过: " + saved.getOrderNo(), request);
    return saved;
  }

  @Transactional
  public RepairOrder reject(Long id, String reason, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    if (!WorkflowStatus.PENDING_ACCEPT.equals(order.getStatus())) {
      throw new IllegalStateException("当前状态不可驳回");
    }
    order.setStatus(WorkflowStatus.PROCESSING);
    order.setFeeRemark(appendRemark(order.getFeeRemark(), "验收驳回原因: " + (reason == null ? "" : reason)));
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "驳回验收: " + saved.getOrderNo(), request);
    return saved;
  }

  @Transactional
  public RepairOrder cancel(Long id, String cancelReason, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    var snapshot = operationSnapshotService.capture(
        "维修管理", "cancel", "repair_order", order.getId(), order.getOrderNo(), order);
    order.setStatus("cancelled");
    order.setCancelReason(cancelReason);
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    operationSnapshotService.complete(snapshot.getId(), saved);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "取消工单: " + saved.getOrderNo(), request);
    return saved;
  }

  @Transactional
  public RepairOrder generateBill(Long id, HttpServletRequest request) {
    RepairOrder order = repairOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    if (!"completed".equals(order.getStatus())) {
      throw new IllegalStateException("仅已完成工单可生成账单");
    }
    if (!Objects.equals(order.getIsTenantCharge(), 1)) {
      throw new IllegalStateException("该工单未勾选租户承担费用");
    }
    if (order.getBillItemId() != null) {
      throw new IllegalStateException("已生成账单");
    }

    Contract active = contractRepository.findByRoomIdAndStatusIn(order.getRoomId(), ContractStatus.ACTIVE_RAW_VALUES).stream().findFirst()
        .orElse(null);
    if (active == null) {
      throw new IllegalStateException("该房间无进行中合同，无法生成账单");
    }

    String billMonth = YearMonth.now().plusMonths(1).toString();
    Bill bill = billRepository.findByContractIdAndBillMonth(active.getId(), billMonth).orElse(null);
    if (bill == null) {
      bill = new Bill();
      bill.setContractId(active.getId());
      bill.setBillMonth(billMonth);
      bill.setStatus("PENDING");
      bill.setGeneratedDate(LocalDateTime.now());
      bill.setTotalAmount(0D);
      bill = billRepository.save(bill);
    }

    BillDetail detail = new BillDetail();
    detail.setBillId(bill.getId());
    detail.setFeeItemId(null);
    detail.setName("维修费");
    detail.setUsageAmount(1D);
    detail.setAmount(order.getTotalFee() == null ? 0D : order.getTotalFee());
    BillDetail savedDetail = billDetailRepository.save(detail);

    List<BillDetail> allDetails = billDetailRepository.findByBillId(bill.getId());
    double total = allDetails.stream().map(BillDetail::getAmount).filter(Objects::nonNull).reduce(0D, Double::sum);
    bill.setTotalAmount(total);
    billRepository.save(bill);

    order.setBillItemId(savedDetail.getId());
    order.setUpdatedAt(LocalDateTime.now());
    RepairOrder saved = repairOrderRepository.save(order);
    fillDisplayFields(saved);
    operationLogService.log("维修管理", "生成维修账单: " + saved.getOrderNo() + " -> " + billMonth, request);
    return saved;
  }

  public Map<String, Object> workerStats(Long workerId) {
    List<RepairOrder> orders = repairOrderRepository.findAll().stream()
        .filter(o -> Objects.equals(o.getWorkerId(), workerId))
        .collect(Collectors.toList());
    long total = orders.size();
    long completed = orders.stream().filter(o -> "completed".equals(o.getStatus())).count();

    List<Long> hours = orders.stream()
        .filter(o -> o.getStartTime() != null && o.getFinishTime() != null)
        .map(o -> Duration.between(o.getStartTime(), o.getFinishTime()).toHours())
        .filter(h -> h >= 0)
        .toList();
    double avgHours = hours.isEmpty() ? 0D : hours.stream().mapToLong(v -> v).average().orElse(0D);

    return Map.of(
        "totalOrders", total,
        "completedOrders", completed,
        "avgCompleteHours", Math.round(avgHours * 10.0) / 10.0);
  }

  private void fillDisplayFields(RepairOrder order) {
    Room room = order.getRoomId() == null ? null : roomRepository.findById(order.getRoomId()).orElse(null);
    if (room != null) {
      order.setRoomNumber(room.getRoomNumber());
    }
    Tenant tenant = order.getTenantId() == null ? null : tenantRepository.findById(order.getTenantId()).orElse(null);
    if (tenant != null) {
      order.setTenantName(tenant.getName());
    }
    RepairWorker worker = order.getWorkerId() == null ? null
        : repairWorkerRepository.findById(order.getWorkerId()).orElse(null);
    if (worker != null) {
      order.setWorkerName(worker.getName());
    }
    RoomAppliance appliance = order.getApplianceId() == null ? null
        : roomApplianceRepository.findById(order.getApplianceId()).orElse(null);
    if (appliance != null) {
      order.setApplianceName(appliance.getApplianceName());
    }
  }

  private String appendRemark(String oldText, String append) {
    if (oldText == null || oldText.isBlank()) {
      return append;
    }
    return oldText + " | " + append;
  }
}
