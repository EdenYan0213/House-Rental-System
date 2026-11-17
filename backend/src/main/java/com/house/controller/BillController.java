package com.house.controller;

import com.house.model.Bill;
import com.house.model.BillDetail;
import com.house.model.BillStatus;
import com.house.model.Contract;
import com.house.model.PaymentRecord;
import com.house.repo.BillDetailRepository;
import com.house.repo.ContractRepository;
import com.house.repo.PaymentRecordRepository;
import com.house.repo.BillRepository;
import com.house.service.BillService;
import com.house.repo.FeeItemRepository;
import com.house.model.FeeItem;
import com.house.service.ContractService;
import com.house.service.PaymentService;
import com.house.service.RoomService;
import com.house.model.Room;
import com.house.model.Building;
import com.house.model.OperationSnapshot;
import com.house.repo.BuildingRepository;
import com.house.dto.PaymentRequest;
import com.house.service.OperationSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bills")
public class BillController {

  private final BillService billService;
  private final BillRepository billRepository;
  private final BillDetailRepository billDetailRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final ContractRepository contractRepository;
  private final PaymentService paymentService;
  private final FeeItemRepository feeItemRepository;
  private final RoomService roomService;
  private final BuildingRepository buildingRepository;
  private final OperationSnapshotService operationSnapshotService;

  public BillController(BillService billService, BillRepository billRepository,
      BillDetailRepository billDetailRepository, PaymentRecordRepository paymentRecordRepository,
      ContractRepository contractRepository,
      PaymentService paymentService, FeeItemRepository feeItemRepository, RoomService roomService,
      BuildingRepository buildingRepository,
      OperationSnapshotService operationSnapshotService) {
    this.billService = billService;
    this.billRepository = billRepository;
    this.billDetailRepository = billDetailRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.contractRepository = contractRepository;
    this.paymentService = paymentService;
    this.feeItemRepository = feeItemRepository;
    this.roomService = roomService;
    this.buildingRepository = buildingRepository;
    this.operationSnapshotService = operationSnapshotService;
  }

  // 手动生成账单，参数 month 可选，格式 yyyy-MM；若不传，生成上月账单
  @PostMapping("/generate")
  public ResponseEntity<?> generate(@RequestParam(required = false) String month) {
    try {
      String targetMonth = month;
      if (targetMonth == null || targetMonth.isBlank()) {
        // 上月
        java.time.YearMonth ym = java.time.YearMonth.now().minusMonths(1);
        targetMonth = ym.toString();
      }
      int created = billService.generateBillsForMonth(targetMonth);
      return ResponseEntity.ok(Collections.singletonMap("created", created));
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping
  public List<Bill> list(@RequestParam(required = false) String month,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long contractId,
      @RequestParam(required = false) Long roomId) {
    List<Bill> list;
    if (contractId != null) {
      list = billRepository.findByContractId(contractId);
    } else if (month != null && !month.isBlank()) {
      list = billRepository.findByBillMonth(month);
    } else {
      list = billRepository.findAll();
    }
    if (status != null && !status.isBlank()) {
      String wanted = BillStatus.normalize(status);
      list = list.stream()
          .filter(b -> wanted.equals(BillStatus.normalize(b.getStatus())))
          .collect(Collectors.toList());
    }
    if (roomId != null) {
      list = list.stream().filter(b -> roomId.equals(b.getRoomId())).collect(Collectors.toList());
    }
    // 为每个账单填充水电用量（来自 BillDetail）
    if (list != null) {
      for (Bill b : list) {
        // 归一化历史数据的中文状态，保证前端映射表能命中
        b.setStatus(BillStatus.normalize(b.getStatus()));
        List<BillDetail> details = billDetailRepository.findByBillId(b.getId());
        double waterUsage = 0.0;
        double electricUsage = 0.0;
        if (details != null) {
          for (BillDetail d : details) {
            if (d.getName() != null) {
              if (d.getName().contains("水")) {
                waterUsage = d.getUsageAmount() == null ? 0.0 : d.getUsageAmount();
              } else if (d.getName().contains("电")) {
                electricUsage = d.getUsageAmount() == null ? 0.0 : d.getUsageAmount();
              }
            }
          }
        }
        b.setWaterUsage(waterUsage);
        b.setElectricUsage(electricUsage);
      }
    }
    return list;
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable Long id) {
    return billRepository.findById(id).map(bill -> {
      bill.setStatus(BillStatus.normalize(bill.getStatus()));
      List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
      List<PaymentRecord> payments = paymentRecordRepository.findByBillId(bill.getId());

      // 计算已付金额
      double paid = 0.0;
      if (payments != null) {
        for (PaymentRecord p : payments) {
          if (p.getPaidAmount() != null)
            paid += p.getPaidAmount();
        }
      }
      bill.setPaidAmount(paid);

      // 设置展示用的创建时间（兼容前端字段名 createTime）
      if (bill.getGeneratedDate() != null) {
        bill.setCreateTime(bill.getGeneratedDate().toString());
      }

      // 查询合同关联信息：仅在账单对应字段为空时才从合同填充，避免覆盖已持久化的数据
      Contract contract = contractRepository.findById(bill.getContractId()).orElse(null);
      if (contract != null) {
        if (bill.getRoomNumber() == null && contract.getRoomNumber() != null) {
          bill.setRoomNumber(contract.getRoomNumber());
        }
        if (bill.getBuildingName() == null && contract.getBuildingName() != null) {
          bill.setBuildingName(contract.getBuildingName());
        }
        if (bill.getTenantName() == null && contract.getTenantName() != null) {
          bill.setTenantName(contract.getTenantName());
        }
        if (bill.getTenantPhone() == null && contract.getTenantPhone() != null) {
          bill.setTenantPhone(contract.getTenantPhone());
        }
      }

      // 将 BillDetail 转换为前端期望的字段名（显式使用 HashMap 以避免类型推断问题）
      List<Map<String, Object>> detailRows = details.stream().map(d -> {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("feeType", d.getName());
        m.put("description", d.getUsageAmount());
        m.put("amount", d.getAmount());
        // 尝试填充单价：优先使用 feeItemId，其次根据名称匹配水/电
        Double unitPrice = null;
        if (d.getFeeItemId() != null) {
          unitPrice = feeItemRepository.findById(d.getFeeItemId()).map(FeeItem::getUnitPrice).orElse(null);
        }
        if (unitPrice == null && d.getName() != null) {
          if (d.getName().contains("水")) {
            unitPrice = feeItemRepository.findByName("水费").map(FeeItem::getUnitPrice).orElse(null);
          } else if (d.getName().contains("电")) {
            unitPrice = feeItemRepository.findByName("电费").map(FeeItem::getUnitPrice).orElse(null);
          }
        }
        // 如果账单关联房间和楼栋，优先使用楼栋配置单价
        if (unitPrice == null && bill.getRoomId() != null) {
          try {
            Room room = roomService.findById(bill.getRoomId()).orElse(null);
            if (room != null && room.getBuildingId() != null) {
              Building b = buildingRepository.findById(room.getBuildingId()).orElse(null);
              if (b != null) {
                if (d.getName() != null && d.getName().contains("水") && b.getWaterUnitPrice() != null)
                  unitPrice = b.getWaterUnitPrice();
                if (d.getName() != null && d.getName().contains("电") && b.getElectricUnitPrice() != null)
                  unitPrice = b.getElectricUnitPrice();
              }
            }
          } catch (Exception ignore) {
          }
        }
        m.put("unitPrice", unitPrice);
        m.put("prevReading", d.getPrevReading());
        m.put("currReading", d.getCurrReading());
        return m;
      }).collect(Collectors.toList());

      // 从明细提取水电用量（本月 - 上月 已在生成账单时计算并保存在 usageAmount）
      double waterUsage = 0.0;
      double electricUsage = 0.0;
      if (details != null) {
        for (BillDetail d : details) {
          if (d.getName() != null) {
            if (d.getName().contains("水")) {
              waterUsage = d.getUsageAmount() == null ? 0.0 : d.getUsageAmount();
            } else if (d.getName().contains("电")) {
              electricUsage = d.getUsageAmount() == null ? 0.0 : d.getUsageAmount();
            }
          }
        }
      }
      bill.setWaterUsage(waterUsage);
      bill.setElectricUsage(electricUsage);

      Map<String, Object> payload = new java.util.HashMap<>();
      payload.put("bill", bill);
      payload.put("details", detailRows);
      payload.put("payments", payments);

      var result = Collections.unmodifiableMap(Collections.singletonMap("data", payload));
      return ResponseEntity.ok(result);
    }).orElse(ResponseEntity.notFound().build());
  }

  // 重新生成指定账单（未付款时）
  @PostMapping("/regenerate/{id}")
  public ResponseEntity<?> regenerate(@PathVariable Long id) {
    try {
      Optional<Bill> billOpt = billRepository.findById(id);
      if (billOpt.isEmpty()) {
        return ResponseEntity.notFound().build();
      }

      Bill bill = billOpt.get();
      if (BillStatus.isPaid(bill.getStatus())) {
        return ResponseEntity.badRequest().body("已付款的账单不能重新生成");
      }

      String month = bill.getBillMonth();
      int created = billService.generateBillsForMonth(month);
      return ResponseEntity.ok(Collections.singletonMap("message", "账单重新生成成功"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // 按合同ID重新生成账单
  @PostMapping("/regenerate/contract/{contractId}")
  public ResponseEntity<?> regenerateByContract(@PathVariable Long contractId) {
    try {
      int created = billService.generateBillsForContract(contractId);
      return ResponseEntity.ok(Collections.singletonMap("message", "账单重新生成成功"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    Bill before = billRepository.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    String targetName = (before.getBillMonth() == null ? "账单" : before.getBillMonth()) + " / #" + before.getId();
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "账单管理", "delete", "bill", id, targetName, before);
    billRepository.deleteById(id);
    return ResponseEntity.ok(Map.of(
        "undoSnapshotId", snapshot.getId(),
        "undoExpireAt", snapshot.getExpiredAt(),
        "undoTip", "操作成功，可在30分钟内撤销"));
  }

  /**
   * 登记收款。前端 `recordPayment` 调用此路径；此前缺少该映射导致收款静默失败。
   * 支持多次部分收款，状态由 PaymentService 依据累计已付金额更新。
   */
  @PostMapping("/{id}/payments")
  public ResponseEntity<?> recordPayment(@PathVariable Long id, @RequestBody PaymentRequest req) {
    try {
      PaymentRecord saved = paymentService.recordPayment(id, req);
      Bill bill = billRepository.findById(id).orElse(null);
      return ResponseEntity.ok(Map.of(
          "data", saved,
          "billStatus", bill != null ? BillStatus.normalize(bill.getStatus()) : BillStatus.PENDING));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PutMapping("/{id}/void")
  public ResponseEntity<?> voidBill(@PathVariable Long id) {
    Bill before = billRepository.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    if (BillStatus.isPaid(before.getStatus())) {
      return ResponseEntity.badRequest().body("已支付账单不可作废");
    }
    if (BillStatus.isVoid(before.getStatus())) {
      return ResponseEntity.badRequest().body("账单已作废");
    }

    String targetName = (before.getBillMonth() == null ? "账单" : before.getBillMonth()) + " / #" + before.getId();
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "账单管理", "void", "bill", id, targetName, before);

    before.setStatus(BillStatus.VOID);
    Bill after = billRepository.save(before);
    operationSnapshotService.complete(snapshot.getId(), after);

    return ResponseEntity.ok(Map.of(
        "data", after,
        "undoSnapshotId", snapshot.getId(),
        "undoExpireAt", snapshot.getExpiredAt(),
        "undoTip", "账单已作废，可在30分钟内撤销"));
  }
}
