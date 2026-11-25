package com.house.controller;

import com.house.dto.PaymentCreateRequest;
import com.house.dto.PaymentRequest;
import com.house.model.Bill;
import com.house.model.BillDetail;
import com.house.model.Contract;
import com.house.model.Notice;
import com.house.model.PaymentOrder;
import com.house.model.PaymentRecord;
import com.house.model.Tenant;
import com.house.repo.BillDetailRepository;
import com.house.repo.BillRepository;
import com.house.repo.ContractRepository;
import com.house.repo.NoticeRepository;
import com.house.repo.PaymentOrderRepository;
import com.house.repo.PaymentRecordRepository;
import com.house.service.PaymentService;
import com.house.service.TenantTokenService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TenantPhase1Controller {
  private final TenantTokenService tenantTokenService;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final BillDetailRepository billDetailRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final PaymentOrderRepository paymentOrderRepository;
  private final PaymentService paymentService;
  private final NoticeRepository noticeRepository;

  public TenantPhase1Controller(TenantTokenService tenantTokenService,
      ContractRepository contractRepository,
      BillRepository billRepository,
      BillDetailRepository billDetailRepository,
      PaymentRecordRepository paymentRecordRepository,
      PaymentOrderRepository paymentOrderRepository,
      PaymentService paymentService,
      NoticeRepository noticeRepository) {
    this.tenantTokenService = tenantTokenService;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.billDetailRepository = billDetailRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.paymentOrderRepository = paymentOrderRepository;
    this.paymentService = paymentService;
    this.noticeRepository = noticeRepository;
  }

  // Phase 1: Tenant bill query and bill detail.

  @GetMapping("/bill/list")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> listBills(@RequestHeader("Authorization") String authHeader,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");

    List<Contract> contracts = contractRepository.findByTenantIdAndRoomId(tenant.getId(), roomId);
    List<Long> contractIds = contracts.stream().map(Contract::getId).toList();

    List<Bill> source = contractIds.isEmpty()
        ? List.of()
        : billRepository.findByContractIdInOrderByBillMonthDescIdDesc(contractIds);

    List<Map<String, Object>> allRows = new ArrayList<>();
    for (Bill bill : source) {
      double paid = paymentRecordRepository.findByBillId(bill.getId())
          .stream()
          .mapToDouble(item -> item.getPaidAmount() == null ? 0D : item.getPaidAmount())
          .sum();
      double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
      String billStatus = normalizeStatus(bill.getStatus(), total, paid);
      if (status != null && !status.isBlank() && !status.equalsIgnoreCase(billStatus)) {
        continue;
      }

      Map<String, Object> row = new HashMap<>();
      row.put("id", bill.getId());
      row.put("billMonth", bill.getBillMonth());
      row.put("totalAmount", total);
      row.put("paidAmount", paid);
      row.put("unpaidAmount", round(Math.max(0D, total - paid)));
      row.put("status", billStatus);
      row.put("generatedDate", bill.getGeneratedDate());
      allRows.add(row);
    }

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    int from = Math.min(safePage * safeSize, allRows.size());
    int to = Math.min(from + safeSize, allRows.size());

    return ResponseEntity.ok(Map.of(
        "content", allRows.subList(from, to),
        "totalElements", allRows.size(),
        "page", safePage,
        "size", safeSize));
  }

  @GetMapping("/bill/detail/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> billDetail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");

    Bill bill = billRepository.findById(id).orElse(null);
    if (bill == null) {
      return ResponseEntity.notFound().build();
    }

    Contract contract = contractRepository.findById(bill.getContractId()).orElse(null);
    if (contract == null || !tenant.getId().equals(contract.getTenantId()) || !roomId.equals(contract.getRoomId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权限访问该账单"));
    }

    List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
    List<PaymentRecord> payments = paymentRecordRepository.findByBillId(bill.getId());

    double paid = payments.stream().mapToDouble(item -> item.getPaidAmount() == null ? 0D : item.getPaidAmount()).sum();
    double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
    String normalized = normalizeStatus(bill.getStatus(), total, paid);

    List<Map<String, Object>> detailRows = new ArrayList<>();
    for (BillDetail detail : details) {
      Map<String, Object> row = new HashMap<>();
      row.put("name", detail.getName());
      row.put("usage", detail.getUsageAmount());
      row.put("amount", detail.getAmount());
      detailRows.add(row);
    }

    List<Map<String, Object>> paymentRows = new ArrayList<>();
    for (PaymentRecord payment : payments) {
      Map<String, Object> row = new HashMap<>();
      row.put("paidAmount", payment.getPaidAmount());
      row.put("paidDate", payment.getPaidDate());
      row.put("paymentMethod", payment.getPaymentMethod());
      row.put("remark", payment.getRemark());
      paymentRows.add(row);
    }

    Map<String, Object> billData = new HashMap<>();
    billData.put("id", bill.getId());
    billData.put("billMonth", bill.getBillMonth());
    billData.put("generatedDate", bill.getGeneratedDate());
    billData.put("status", normalized);
    billData.put("totalAmount", total);
    billData.put("paidAmount", paid);
    billData.put("unpaidAmount", round(Math.max(0D, total - paid)));

    return ResponseEntity.ok(Map.of(
        "bill", billData,
        "details", detailRows,
        "payments", paymentRows));
  }

  // Phase 1: Tenant online payment flow.

  @PostMapping("/payment/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> createPayment(@RequestHeader("Authorization") String authHeader,
      @RequestBody PaymentCreateRequest request) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");

    if (request.getBillId() == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "billId不能为空"));
    }

    String payType = request.getPayType() == null ? "wx" : request.getPayType().trim().toLowerCase();
    if (!"wx".equals(payType) && !"alipay".equals(payType)) {
      return ResponseEntity.badRequest().body(Map.of("message", "仅支持wx或alipay"));
    }

    Bill bill = billRepository.findById(request.getBillId()).orElse(null);
    if (bill == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "账单不存在"));
    }

    Contract contract = contractRepository.findById(bill.getContractId()).orElse(null);
    if (contract == null || !tenant.getId().equals(contract.getTenantId()) || !roomId.equals(contract.getRoomId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权支付该账单"));
    }

    double paid = paymentRecordRepository.findByBillId(bill.getId())
        .stream()
        .mapToDouble(item -> item.getPaidAmount() == null ? 0D : item.getPaidAmount())
        .sum();
    double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
    double unpaidAmount = Math.max(0D, total - paid);
    if (unpaidAmount <= 0.0001) {
      return ResponseEntity.badRequest().body(Map.of("message", "账单已支付"));
    }

    String outTradeNo = "PO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    LocalDateTime now = LocalDateTime.now();

    PaymentOrder order = PaymentOrder.builder()
        .billId(bill.getId())
        .amount(unpaidAmount)
        .payType(payType)
        .outTradeNo(outTradeNo)
        .status(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
    paymentOrderRepository.save(order);

    return ResponseEntity.ok(Map.of(
        "orderId", order.getId(),
        "outTradeNo", outTradeNo,
        "amount", round(unpaidAmount),
        "payType", payType,
        "payParams", Map.of(
            "mock", true,
            "provider", payType,
            "orderStr", "mock-order-" + outTradeNo,
            "prepayId", "mock-prepay-" + outTradeNo)));
  }

  @PostMapping("/payment/callback")
  public ResponseEntity<?> paymentCallback(@RequestParam String outTradeNo,
      @RequestParam(defaultValue = "success") String result,
      @RequestParam(required = false) String transactionId) {
    PaymentOrder order = paymentOrderRepository.findByOutTradeNo(outTradeNo).orElse(null);
    if (order == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "订单不存在"));
    }

    if (order.getStatus() != null && order.getStatus() == 1) {
      return ResponseEntity.ok(Map.of("success", true, "message", "订单已处理"));
    }

    boolean success = "success".equalsIgnoreCase(result) || "paid".equalsIgnoreCase(result);
    order.setUpdatedAt(LocalDateTime.now());
    order.setTransactionId(transactionId == null ? order.getTransactionId() : transactionId);

    if (!success) {
      order.setStatus(2);
      paymentOrderRepository.save(order);
      return ResponseEntity.ok(Map.of("success", false, "message", "支付失败"));
    }

    order.setStatus(1);
    paymentOrderRepository.save(order);

    PaymentRequest paymentRequest = new PaymentRequest();
    paymentRequest.setPaidAmount(order.getAmount());
    paymentRequest.setPaymentMethod("wx".equalsIgnoreCase(order.getPayType()) ? "WECHAT" : "ALIPAY");
    paymentRequest.setRemark("租客端在线支付");
    paymentService.recordPayment(order.getBillId(), paymentRequest);

    return ResponseEntity.ok(Map.of("success", true, "message", "支付成功"));
  }

  // Phase 1: Tenant notice flow.

  @GetMapping("/notice/list")
  public ResponseEntity<?> noticeList(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    var result = noticeRepository.findAllByOrderBySortDescCreatedAtDesc(PageRequest.of(safePage, safeSize));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Notice notice : result.getContent()) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", notice.getId());
      row.put("title", notice.getTitle());
      row.put("summary", toSummary(notice.getContent()));
      row.put("image", notice.getImage());
      row.put("createdAt", notice.getCreatedAt());
      rows.add(row);
    }

    return ResponseEntity.ok(Map.of(
        "content", rows,
        "totalElements", result.getTotalElements(),
        "page", safePage,
        "size", safeSize));
  }

  @GetMapping("/notice/detail/{id}")
  public ResponseEntity<?> noticeDetail(@PathVariable Long id) {
    Notice notice = noticeRepository.findById(id).orElse(null);
    if (notice == null) {
      return ResponseEntity.notFound().build();
    }

    Map<String, Object> result = new HashMap<>();
    result.put("id", notice.getId());
    result.put("title", notice.getTitle());
    result.put("content", notice.getContent());
    result.put("image", notice.getImage());
    result.put("createdAt", notice.getCreatedAt());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/notice/latest")
  public ResponseEntity<?> latestNotice() {
    Notice notice = noticeRepository.findTopByOrderBySortDescCreatedAtDesc().orElse(null);
    if (notice == null) {
      return ResponseEntity.ok(Collections.emptyMap());
    }

    // 公告字段历史数据可能为空，Map.of 不接受 null
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", notice.getId());
    body.put("title", notice.getTitle());
    body.put("summary", toSummary(notice.getContent()));
    body.put("createdAt", notice.getCreatedAt());
    return ResponseEntity.ok(body);
  }

  @PostMapping("/notice")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> createNotice(@RequestBody Notice notice) {
    if (notice.getTitle() == null || notice.getTitle().isBlank() || notice.getContent() == null
        || notice.getContent().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "标题和内容不能为空"));
    }

    Notice saved = Notice.builder()
        .title(notice.getTitle().trim())
        .content(notice.getContent())
        .image(notice.getImage())
        .sort(notice.getSort() == null ? 0 : notice.getSort())
        .createdAt(LocalDateTime.now())
        .build();

    return ResponseEntity.ok(noticeRepository.save(saved));
  }

  private String normalizeStatus(String status, double total, double paid) {
    String raw = status == null ? "" : status.trim().toUpperCase();
    if ("VOID".equals(raw) || "OBSOLETE".equals(raw) || "作废".equals(status) || "已作废".equals(status)) {
      return "VOID";
    }
    if (paid >= total - 0.0001 && total > 0) {
      return "PAID";
    }
    if (paid > 0) {
      return "PARTIAL";
    }
    return "PENDING";
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private String toSummary(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String plain = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    return plain.length() > 70 ? plain.substring(0, 70) + "..." : plain;
  }
}
