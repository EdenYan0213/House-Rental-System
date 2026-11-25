package com.house.controller;

import com.house.model.Bill;
import com.house.model.ComplaintSuggestion;
import com.house.model.Contract;
import com.house.model.Notice;
import com.house.model.PaymentRecord;
import com.house.model.RemindLog;
import com.house.model.RepairOrder;
import com.house.model.SignAgreement;
import com.house.model.Tenant;
import com.house.model.TenantMessage;
import com.house.repo.BillRepository;
import com.house.repo.ComplaintSuggestionRepository;
import com.house.repo.ContractRepository;
import com.house.repo.NoticeRepository;
import com.house.repo.PaymentRecordRepository;
import com.house.repo.RemindLogRepository;
import com.house.repo.RenewApplicationRepository;
import com.house.repo.RepairOrderRepository;
import com.house.repo.SignAgreementRepository;
import com.house.repo.TenantMessageRepository;
import com.house.repo.TenantRepository;
import com.house.service.TenantTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 租客端：通知偏好、站内信、首页数据看板。
 *
 * <p>从原 {@code TenantPortalController} 拆出。包含一个副作用：
 * 读消息列表时会按账单/合同/报修状态**自动生成**待办与通知，
 * 该逻辑在 {@link #generateAutoMessages} 内，已按偏好开关逐段控制。
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantMessageController {

  private static final Set<String> REPAIR_PENDING_STATUS =
      Set.of("pending", "assigned", "processing", "pending_accept");
  private static final Set<String> COMPLAINT_PENDING_STATUS =
      Set.of("pending", "assigned", "processing", "pending_confirm");

  private final TenantTokenService tenantTokenService;
  private final TenantRepository tenantRepository;
  private final TenantMessageRepository tenantMessageRepository;
  private final SignAgreementRepository signAgreementRepository;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final RepairOrderRepository repairOrderRepository;
  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final RenewApplicationRepository renewApplicationRepository;
  private final NoticeRepository noticeRepository;
  private final TenantPortalSupport support;
  private final TenantMessageText text;

  public TenantMessageController(TenantTokenService tenantTokenService,
      TenantRepository tenantRepository,
      TenantMessageRepository tenantMessageRepository,
      SignAgreementRepository signAgreementRepository,
      ContractRepository contractRepository,
      BillRepository billRepository,
      PaymentRecordRepository paymentRecordRepository,
      RepairOrderRepository repairOrderRepository,
      ComplaintSuggestionRepository complaintSuggestionRepository,
      RenewApplicationRepository renewApplicationRepository,
      NoticeRepository noticeRepository,
      TenantPortalSupport support,
      TenantMessageText text) {
    this.tenantTokenService = tenantTokenService;
    this.tenantRepository = tenantRepository;
    this.tenantMessageRepository = tenantMessageRepository;
    this.signAgreementRepository = signAgreementRepository;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.repairOrderRepository = repairOrderRepository;
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.renewApplicationRepository = renewApplicationRepository;
    this.noticeRepository = noticeRepository;
    this.support = support;
    this.text = text;
  }

  // ---------------- 通知偏好 ----------------

  @GetMapping("/preferences")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> getPreferences(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    support.normalizeTenantPreference(tenant);
    tenantRepository.save(tenant);

    return ResponseEntity.ok(Map.of(
        "language", tenant.getLanguagePreference(),
        "notifyOverdue", support.bool(tenant.getNotifyOverdue()),
        "notifyRepair", support.bool(tenant.getNotifyRepair()),
        "notifyContract", support.bool(tenant.getNotifyContract()),
        "notifyNotice", support.bool(tenant.getNotifyNotice())));
  }

  @PutMapping("/preferences")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> updatePreferences(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    support.normalizeTenantPreference(tenant);

    String language = support.text(body.get("language"));
    if (!language.isBlank()) {
      tenant.setLanguagePreference(language.startsWith("en") ? "en-US" : "zh-CN");
    }
    if (body.containsKey("notifyOverdue")) {
      tenant.setNotifyOverdue(support.boolValue(body.get("notifyOverdue")) ? 1 : 0);
    }
    if (body.containsKey("notifyRepair")) {
      tenant.setNotifyRepair(support.boolValue(body.get("notifyRepair")) ? 1 : 0);
    }
    if (body.containsKey("notifyContract")) {
      tenant.setNotifyContract(support.boolValue(body.get("notifyContract")) ? 1 : 0);
    }
    if (body.containsKey("notifyNotice")) {
      tenant.setNotifyNotice(support.boolValue(body.get("notifyNotice")) ? 1 : 0);
    }

    tenantRepository.save(tenant);
    return getPreferences(authHeader);
  }

  // ---------------- 站内信 ----------------

  @GetMapping("/messages")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> listMessages(@RequestHeader("Authorization") String authHeader,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long tenantId = tenant.getId();

    generateAutoMessages(tenant, context);

    List<TenantMessage> source;
    if (category == null || category.isBlank() || "all".equalsIgnoreCase(category)) {
      source = tenantMessageRepository.findByTenantIdAndIsDeletedOrderByCreatedAtDesc(tenantId, 0);
    } else {
      source = tenantMessageRepository.findByTenantIdAndCategoryAndIsDeletedOrderByCreatedAtDesc(
          tenantId,
          category.toUpperCase(),
          0);
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (TenantMessage item : source) {
      rows.add(toMessageRow(item, false));
    }

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    int from = Math.min(safePage * safeSize, rows.size());
    int to = Math.min(from + safeSize, rows.size());

    return ResponseEntity.ok(Map.of(
        "content", rows.subList(from, to),
        "totalElements", rows.size(),
        "page", safePage,
        "size", safeSize,
        "unreadCount", tenantMessageRepository.countByTenantIdAndIsReadAndIsDeleted(tenantId, 0, 0)));
  }

  @GetMapping("/messages/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> messageDetail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    TenantMessage msg = findOwnedMessage(id, tenantId);
    if (msg == null) {
      return ResponseEntity.notFound().build();
    }

    if (Objects.equals(msg.getIsRead(), 0)) {
      msg.setIsRead(1);
      msg.setReadAt(LocalDateTime.now());
      tenantMessageRepository.save(msg);
    }

    return ResponseEntity.ok(toMessageRow(msg, true));
  }

  @PostMapping("/messages/{id}/read")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> readMessage(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    TenantMessage msg = findOwnedMessage(id, tenantId);
    if (msg == null) {
      return ResponseEntity.notFound().build();
    }

    msg.setIsRead(1);
    msg.setReadAt(LocalDateTime.now());
    tenantMessageRepository.save(msg);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @PostMapping("/messages/{id}/delete")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> deleteMessage(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    TenantMessage msg = findOwnedMessage(id, tenantId);
    if (msg == null) {
      return ResponseEntity.notFound().build();
    }

    msg.setIsDeleted(1);
    msg.setDeletedAt(LocalDateTime.now());
    tenantMessageRepository.save(msg);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @PostMapping("/messages/{id}/process")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> processTodo(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    TenantMessage msg = findOwnedMessage(id, tenantId);
    if (msg == null) {
      return ResponseEntity.notFound().build();
    }

    msg.setActionStatus("DONE");
    msg.setNeedAction(0);
    msg.setIsRead(1);
    msg.setReadAt(LocalDateTime.now());
    tenantMessageRepository.save(msg);
    return ResponseEntity.ok(Map.of("success", true, "message", "已处理"));
  }

  // ---------------- 数据看板 ----------------

  @GetMapping("/dashboard")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> dashboard(@RequestHeader("Authorization") String authHeader,
      @RequestParam(defaultValue = "12") int months) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");
    Long roomId = (Long) context.get("roomId");

    int monthCount = Math.min(Math.max(months, 3), 24);

    List<Contract> contracts = contractRepository.findByTenantIdAndRoomId(tenantId, roomId);
    List<Long> contractIds = contracts.stream().map(Contract::getId).toList();

    List<Bill> bills = contractIds.isEmpty() ? List.of() : billRepository.findByContractIdIn(contractIds);
    Map<String, Double> amountByMonth = new LinkedHashMap<>();
    YearMonth now = YearMonth.now();
    for (int i = monthCount - 1; i >= 0; i--) {
      amountByMonth.put(now.minusMonths(i).toString(), 0D);
    }
    for (Bill bill : bills) {
      if (bill.getBillMonth() == null || !amountByMonth.containsKey(bill.getBillMonth())) {
        continue;
      }
      double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
      amountByMonth.put(bill.getBillMonth(),
          support.round(amountByMonth.get(bill.getBillMonth()) + total));
    }

    List<Map<String, Object>> billTrend = new ArrayList<>();
    for (Map.Entry<String, Double> e : amountByMonth.entrySet()) {
      billTrend.add(Map.of("month", e.getKey(), "amount", e.getValue()));
    }

    List<RepairOrder> repairs = repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    long repairPending = repairs.stream()
        .filter(r -> REPAIR_PENDING_STATUS.contains(support.text(r.getStatus())))
        .count();
    long repairCompleted = repairs.stream()
        .filter(r -> "completed".equalsIgnoreCase(support.text(r.getStatus())))
        .count();

    List<ComplaintSuggestion> complaints =
        complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    long complaintPending = complaints.stream()
        .filter(c -> COMPLAINT_PENDING_STATUS.contains(support.text(c.getStatus())))
        .count();

    Contract active = contracts.stream()
        .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || "进行中".equals(c.getStatus()))
        .max(Comparator.comparing(Contract::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
        .orElse(null);

    Map<String, Object> renewHint = new HashMap<>();
    if (active != null && active.getEndDate() != null) {
      long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), active.getEndDate());
      renewHint.put("daysLeft", daysLeft);
      renewHint.put("canApplyRenew", daysLeft <= 90);
      renewHint.put("contractId", active.getId());
    } else {
      renewHint.put("daysLeft", null);
      renewHint.put("canApplyRenew", false);
      renewHint.put("contractId", null);
    }

    long stayDays = 0;
    LocalDate start = contracts.stream()
        .map(Contract::getStartDate)
        .filter(Objects::nonNull)
        .min(LocalDate::compareTo)
        .orElse(null);
    if (start != null) {
      stayDays = ChronoUnit.DAYS.between(start, LocalDate.now());
    }

    long renewPending = renewApplicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
        .stream()
        .filter(r -> Objects.equals(r.getStatus(), 0))
        .count();

    return ResponseEntity.ok(Map.of(
        "billTrend", billTrend,
        "repair", Map.of("pending", repairPending, "completed", repairCompleted),
        "complaint", Map.of("pending", complaintPending, "total", complaints.size()),
        "renew", Map.of("pendingCount", renewPending, "hint", renewHint),
        "stay", Map.of("days", stayDays, "months", Math.max(0, stayDays / 30))));
  }

  // ---------------- 内部工具 ----------------

  /** 取属于当前租客且未删除的消息；不属于或已删除返回 null（调用方回 404）。 */
  private TenantMessage findOwnedMessage(Long id, Long tenantId) {
    TenantMessage msg = tenantMessageRepository.findById(id).orElse(null);
    if (msg == null || !tenantId.equals(msg.getTenantId()) || !Objects.equals(msg.getIsDeleted(), 0)) {
      return null;
    }
    return msg;
  }

  private Map<String, Object> toMessageRow(TenantMessage item, boolean withContent) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", item.getId());
    row.put("category", item.getCategory());
    row.put("type", item.getType());
    row.put("title", item.getTitle());
    row.put("content", withContent ? item.getContent() : null);
    row.put("targetUrl", item.getTargetUrl());
    row.put("isRead", item.getIsRead());
    row.put("needAction", item.getNeedAction());
    row.put("actionStatus", item.getActionStatus());
    row.put("createdAt", item.getCreatedAt());
    return row;
  }

  /**
   * 按当前业务状态补生成站内信。
   *
   * <p>每条都走 {@link #createMessageIfAbsent}，同一 (type, refType, refId) 只生成一次，
   * 因此可以在每次读列表时安全调用。
   */
  private void generateAutoMessages(Tenant tenant, Map<String, Object> context) {
    support.normalizeTenantPreference(tenant);

    Long tenantId = tenant.getId();
    Long roomId = (Long) context.get("roomId");
    String lang = tenant.getLanguagePreference();

    List<Contract> contracts = contractRepository.findByTenantId(tenantId);
    List<Long> contractIds = contracts.stream().map(Contract::getId).toList();

    if (support.bool(tenant.getNotifyOverdue()) && !contractIds.isEmpty()) {
      for (Bill bill : billRepository.findByContractIdIn(contractIds)) {
        double paid = paymentRecordRepository.findByBillId(bill.getId()).stream()
            .mapToDouble((PaymentRecord item) -> item.getPaidAmount() == null ? 0D : item.getPaidAmount())
            .sum();
        double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
        if (total - paid > 0.01) {
          createMessageIfAbsent(tenantId, roomId, "TODO", "OVERDUE", "BILL", bill.getId(), "/bills",
              text.ofLang(lang, "overdue_title"),
              text.ofLang(lang, "overdue_content", bill.getBillMonth(), support.round(total - paid)),
              1);
        }
      }
    }

    if (support.bool(tenant.getNotifyContract())) {
      for (Contract c : contracts) {
        if (c.getEndDate() == null) {
          continue;
        }
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
        if (daysLeft >= 0 && daysLeft <= 30) {
          createMessageIfAbsent(tenantId, roomId, "TODO", "CONTRACT_EXPIRE", "CONTRACT", c.getId(), "/contract",
              text.ofLang(lang, "contract_expire_title"),
              text.ofLang(lang, "contract_expire_content", c.getContractNo(), daysLeft),
              1);
        }
      }
    }

    if (support.bool(tenant.getNotifyRepair())) {
      for (RepairOrder r : repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
          .stream().limit(5).toList()) {
        if (Set.of("processing", "pending_accept", "completed").contains(support.text(r.getStatus()))) {
          createMessageIfAbsent(tenantId, roomId, "NOTICE", "REPAIR_PROGRESS", "REPAIR", r.getId(),
              "/repairs/" + r.getId(),
              text.ofLang(lang, "repair_title"),
              text.ofLang(lang, "repair_content", r.getOrderNo(), r.getStatus()),
              0);
        }
      }

      for (ComplaintSuggestion c : complaintSuggestionRepository
          .findByTenantIdOrderByCreatedAtDesc(tenantId).stream().limit(5).toList()) {
        if (Set.of("processing", "pending_confirm", "completed").contains(support.text(c.getStatus()))) {
          createMessageIfAbsent(tenantId, roomId, "NOTICE", "COMPLAINT_PROGRESS", "COMPLAINT", c.getId(),
              "/complaints/" + c.getId(),
              text.ofLang(lang, "complaint_title"),
              text.ofLang(lang, "complaint_content", c.getComplaintNo(), c.getStatus()),
              0);
        }
      }
    }

    if (support.bool(tenant.getNotifyNotice())) {
      Notice latest = noticeRepository.findTopByOrderBySortDescCreatedAtDesc().orElse(null);
      if (latest != null) {
        createMessageIfAbsent(tenantId, roomId, "NOTICE", "NOTICE_NEW", "NOTICE", latest.getId(),
            "/notices/" + latest.getId(),
            text.ofLang(lang, "notice_title"),
            text.ofLang(lang, "notice_content", latest.getTitle()),
            0);
      }
    }

    for (Contract c : contracts) {
      SignAgreement agreement =
          signAgreementRepository.findByContractIdAndTenantId(c.getId(), tenantId).orElse(null);
      if (agreement == null) {
        continue;
      }
      if ("FAILED".equals(agreement.getSignStatus()) || "TIMEOUT".equals(agreement.getSignStatus())) {
        createMessageIfAbsent(tenantId, roomId, "TODO", "SIGN_RETRY", "CONTRACT", c.getId(), "/contract",
            text.ofLang(lang, "sign_retry_title"),
            text.ofLang(lang, "sign_retry_content", c.getContractNo()),
            1);
      }
      // 签署窗口过期后自动置为 TIMEOUT，保证下一次读取能提示重签
      if (agreement.getExpireAt() != null && LocalDateTime.now().isAfter(agreement.getExpireAt())
          && !"SIGNED".equals(agreement.getSignStatus())) {
        agreement.setSignStatus("TIMEOUT");
        agreement.setLastError("签署已超时，请重新发起");
        agreement.setUpdatedAt(LocalDateTime.now());
        signAgreementRepository.save(agreement);
      }
    }
  }

  private void createMessageIfAbsent(Long tenantId,
      Long roomId,
      String category,
      String type,
      String refType,
      Long refId,
      String targetUrl,
      String title,
      String content,
      int needAction) {
    boolean exists = tenantMessageRepository
        .findTopByTenantIdAndTypeAndRefTypeAndRefIdAndIsDeletedOrderByCreatedAtDesc(
            tenantId, type, refType, refId, 0)
        .isPresent();
    if (exists) {
      return;
    }

    tenantMessageRepository.save(TenantMessage.builder()
        .tenantId(tenantId)
        .roomId(roomId)
        .category(category)
        .type(type)
        .title(title)
        .content(content)
        .targetUrl(targetUrl)
        .refType(refType)
        .refId(refId)
        .isRead(0)
        .isDeleted(0)
        .needAction(needAction)
        .actionStatus(needAction == 1 ? "PENDING" : "DONE")
        .createdAt(LocalDateTime.now())
        .build());
  }
}
