package com.house.controller;

import com.house.dto.BillGenerationResult;
import com.house.dto.PaymentRequest;
import com.house.model.Bill;
import com.house.model.BillDetail;
import com.house.model.BillStatus;
import com.house.model.Contract;
import com.house.model.PaymentRecord;
import com.house.model.RemindLog;
import com.house.model.SystemMessage;
import com.house.repo.BillDetailRepository;
import com.house.repo.ContractRepository;
import com.house.repo.PaymentRecordRepository;
import com.house.repo.BillRepository;
import com.house.repo.SystemMessageRepository;
import com.house.service.BillService;
import com.house.service.PaymentService;
import com.house.repo.FeeItemRepository;
import com.house.model.FeeItem;
import com.house.service.RoomService;
import com.house.service.SmsService;
import com.house.model.Room;
import com.house.model.Building;
import com.house.model.OperationSnapshot;
import com.house.repo.BuildingRepository;
import com.house.service.OperationSnapshotService;
import com.house.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bills")
public class BillController {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BillController.class);

  private final BillService billService;
  private final PaymentService paymentService;
  private final BillRepository billRepository;
  private final BillDetailRepository billDetailRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final ContractRepository contractRepository;
  private final FeeItemRepository feeItemRepository;
  private final RoomService roomService;
  private final BuildingRepository buildingRepository;
  private final OperationSnapshotService operationSnapshotService;
  private final OperationLogService operationLogService;
  private final SmsService smsService;
  private final SystemMessageRepository systemMessageRepository;

  public BillController(BillService billService, BillRepository billRepository,
      BillDetailRepository billDetailRepository, PaymentRecordRepository paymentRecordRepository,
      ContractRepository contractRepository,
      FeeItemRepository feeItemRepository, RoomService roomService,
      BuildingRepository buildingRepository,
      OperationSnapshotService operationSnapshotService,
      OperationLogService operationLogService,
      SmsService smsService,
      SystemMessageRepository systemMessageRepository,
      PaymentService paymentService) {
    this.billService = billService;
    this.paymentService = paymentService;
    this.billRepository = billRepository;
    this.billDetailRepository = billDetailRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.contractRepository = contractRepository;
    this.feeItemRepository = feeItemRepository;
    this.roomService = roomService;
    this.buildingRepository = buildingRepository;
    this.operationSnapshotService = operationSnapshotService;
    this.operationLogService = operationLogService;
    this.smsService = smsService;
    this.systemMessageRepository = systemMessageRepository;
  }

  // 手动生成账单，参数 month 可选，格式 yyyy-MM；若不传，生成上月账单
  @PostMapping("/generate")
  public ResponseEntity<?> generate(@RequestParam(required = false) String month) {
    String targetMonth = month;
    try {
      if (targetMonth == null || targetMonth.isBlank()) {
        // 上月
        java.time.YearMonth ym = java.time.YearMonth.now().minusMonths(1);
        targetMonth = ym.toString();
      }
      // 返回结构化结果：成功数 + 失败明细，前端可展示失败清单并支持单条重试
      BillGenerationResult result = billService.generateBillsForMonthDetailed(targetMonth);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("批量生成账单失败 month={}", targetMonth, e);
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  // 按房间生成账单，month 可选，格式 yyyy-MM；若不传，默认上月
  @PostMapping("/generate/room/{roomId}")
  public ResponseEntity<?> generateByRoom(@PathVariable Long roomId, @RequestParam(required = false) String month) {
    try {
      int created = billService.generateBillsForRoomAndMonth(roomId, month);
      return ResponseEntity.ok(Map.of("created", created, "roomId", roomId));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping
  public List<Bill> list(@RequestParam(required = false) String month,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long contractId,
      @RequestParam(required = false) Long roomId) {
    return queryBills(month, status, contractId, roomId);
  }

  @GetMapping("/export-csv")
  public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String month,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long contractId,
      @RequestParam(required = false) Long roomId) {
    List<Bill> list = queryBills(month, status, contractId, roomId);

    StringBuilder csv = new StringBuilder();
    csv.append("账单ID,账单月份,楼栋,房间号,租户,状态,总金额,已付金额,水量,电量,生成时间\n");
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    for (Bill b : list) {
      csv.append(csvCell(b.getId())).append(',')
          .append(csvCell(b.getBillMonth())).append(',')
          .append(csvCell(b.getBuildingName())).append(',')
          .append(csvCell(b.getRoomNumber())).append(',')
          .append(csvCell(b.getTenantName())).append(',')
          .append(csvCell(b.getStatus())).append(',')
          .append(csvCell(b.getTotalAmount())).append(',')
          .append(csvCell(b.getPaidAmount())).append(',')
          .append(csvCell(b.getWaterUsage())).append(',')
          .append(csvCell(b.getElectricUsage())).append(',')
          .append(csvCell(formatDateTime(b.getGeneratedDate(), dtf)))
          .append('\n');
    }

    byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bills-export.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(bytes);
  }

  private List<Bill> queryBills(String month, String status, Long contractId, Long roomId) {
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
        // 填充已付金额：列表页的「已付」「待收」「批量收款」都依赖该字段
        double paidSum = paymentRecordRepository.findByBillId(b.getId()).stream()
            .mapToDouble(pr -> pr.getPaidAmount() == null ? 0 : pr.getPaidAmount())
            .sum();
        b.setPaidAmount(paidSum);
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

  private String csvCell(Object value) {
    if (value == null) {
      return "";
    }
    String text = String.valueOf(value);
    if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
      return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    return text;
  }

  private String formatDateTime(LocalDateTime value, DateTimeFormatter formatter) {
    if (value == null) {
      return "";
    }
    return value.format(formatter);
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
      billService.generateBillsForMonth(month);
      return ResponseEntity.ok(Collections.singletonMap("message", "账单重新生成成功"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // 按合同ID重新生成账单
  @PostMapping("/regenerate/contract/{contractId}")
  public ResponseEntity<?> regenerateByContract(@PathVariable Long contractId) {
    try {
      billService.generateBillsForContract(contractId);
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

  /**
   * 批量收款：对所选账单按剩余应收金额全额收款。
   */
  @PostMapping("/batch-payments")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> batchPayments(@RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    try {
      List<?> raw = (List<?>) body.get("billIds");
      if (raw == null || raw.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("message", "请选择要收款的账单"));
      }
      List<Long> billIds = raw.stream().map(v -> ((Number) v).longValue()).toList();
      String method = body.get("paymentMethod") == null ? "CASH" : body.get("paymentMethod").toString();
      String remark = body.get("remark") == null ? "批量收款" : body.get("remark").toString();

      var result = paymentService.recordBatchPayment(billIds, method, null, remark);
      operationLogService.log("账单管理",
          "批量收款: " + result.get("successCount") + " 张, 合计 " + result.get("totalPaid"), request);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PutMapping("/{id}/void")
  public ResponseEntity<?> voidBill(@PathVariable Long id, HttpServletRequest request) {
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
    operationLogService.log(
        "bill", "void", String.valueOf(id), targetName, snapshot.getBeforeData(), after,
        "作废账单: " + targetName, request, true, null, null, Map.of("billId", id));

    return ResponseEntity.ok(Map.of(
        "data", after,
        "undoSnapshotId", snapshot.getId(),
        "undoExpireAt", snapshot.getExpiredAt(),
        "undoTip", "账单已作废，可在30分钟内撤销"));
  }

  @PostMapping("/{id}/send-sms")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> sendBillSms(@PathVariable Long id) {
    Optional<Bill> billOpt = billRepository.findById(id);
    if (billOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Bill bill = billOpt.get();
    if (bill.getTenantPhone() == null || bill.getTenantPhone().isBlank()) {
      return ResponseEntity.badRequest().body("该账单未绑定租户手机号，无法发送短信");
    }

    RemindLog log = new RemindLog();
    log.setTenantName(bill.getTenantName());
    log.setPhone(bill.getTenantPhone());
    log.setRoomNumber(bill.getRoomNumber());
    log.setOverdueDays(0);
    log.setBillIds("[" + bill.getId() + "]");
    log.setTotalAmount(bill.getTotalAmount());
    log.setContent(buildBillSmsContent(bill));

    RemindLog sendResult = smsService.sendSms(log);
    SystemMessage latest = systemMessageRepository.findTopByOrderByCreatedAtDesc().orElse(null);

    Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("success", "success".equalsIgnoreCase(sendResult.getStatus()));
    payload.put("isDebug", sendResult.getIsDebug() != null && sendResult.getIsDebug() == 1);
    payload.put("status", sendResult.getStatus());
    payload.put("message", "success".equalsIgnoreCase(sendResult.getStatus()) ? "账单短信发送成功" : "账单短信发送失败");
    payload.put("latestMessageTitle", latest == null ? "" : latest.getTitle());
    payload.put("latestMessageContent", latest == null ? "" : latest.getContent());
    payload.put("latestMessageTime",
        latest == null || latest.getCreatedAt() == null ? "" : latest.getCreatedAt().toString());

    if (!"success".equalsIgnoreCase(sendResult.getStatus())) {
      payload.put("failReason", sendResult.getFailReason());
      return ResponseEntity.badRequest().body(payload);
    }

    return ResponseEntity.ok(payload);
  }

  @GetMapping("/{id}/send-sms")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> sendBillSmsByGet(@PathVariable Long id) {
    return sendBillSms(id);
  }

  private String buildBillSmsContent(Bill bill) {
    String billMonth = bill.getBillMonth() == null ? "当期" : bill.getBillMonth();
    String room = bill.getRoomNumber() == null ? "-" : bill.getRoomNumber();
    double amount = bill.getTotalAmount() == null ? 0.0 : bill.getTotalAmount();
    return String.format("%s 房间%s账单待处理，金额%.2f元，请及时查看并缴费。", billMonth, room, amount);
  }
}
