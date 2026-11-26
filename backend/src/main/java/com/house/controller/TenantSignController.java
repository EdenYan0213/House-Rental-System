package com.house.controller;

import com.house.model.Contract;
import com.house.model.ContractStatus;
import com.house.model.SignAgreement;
import com.house.model.Tenant;
import com.house.model.TenantMessage;
import com.house.repo.ContractRepository;
import com.house.repo.SignAgreementRepository;
import com.house.repo.TenantMessageRepository;
import com.house.service.TenantTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 租客端电子签署：预览、实名认证、提交签署、重签、历史记录。
 *
 * <p>从原 {@code TenantPortalController} 拆出。当前 {@code signProvider} 固定为
 * {@code MOCK_ESIGN}——接入真实服务商（法大大 / e签宝）时只改这里。
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantSignController {

  private static final String PROVIDER_MOCK = "MOCK_ESIGN";

  private final TenantTokenService tenantTokenService;
  private final ContractRepository contractRepository;
  private final SignAgreementRepository signAgreementRepository;
  private final TenantMessageRepository tenantMessageRepository;
  private final TenantPortalSupport support;
  private final TenantMessageText text;

  public TenantSignController(TenantTokenService tenantTokenService,
      ContractRepository contractRepository,
      SignAgreementRepository signAgreementRepository,
      TenantMessageRepository tenantMessageRepository,
      TenantPortalSupport support,
      TenantMessageText text) {
    this.tenantTokenService = tenantTokenService;
    this.contractRepository = contractRepository;
    this.signAgreementRepository = signAgreementRepository;
    this.tenantMessageRepository = tenantMessageRepository;
    this.support = support;
    this.text = text;
  }

  @GetMapping("/contract/sign/preview/{contractId}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> signPreview(@RequestHeader("Authorization") String authHeader,
      @PathVariable Long contractId) {
    return signPreviewInternal(authHeader, contractId);
  }

  @PostMapping("/contract/sign/preview")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> signPreviewPost(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Long contractId = support.parseLong(body.get("contractId"));
    if (contractId == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "contractId不能为空"));
    }
    return signPreviewInternal(authHeader, contractId);
  }

  @PostMapping("/contract/sign/verify")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> verifyIdentity(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Long contractId = support.parseLong(body.get("contractId"));
    String name = support.text(body.get("name"));
    String idCard = support.text(body.get("idCard"));
    if (contractId == null || name.isBlank() || idCard.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "实名认证参数不完整"));
    }

    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null || !tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权操作该合同"));
    }

    SignAgreement agreement = getOrCreateAgreement(contractId, tenantId);
    agreement.setVerifyName(name);
    agreement.setVerifyIdCard(support.maskIdCard(idCard));

    if (idCard.length() < 6) {
      agreement.setVerifyStatus("FAILED");
      agreement.setLastError("实名认证失败: 身份证号格式不正确");
      agreement.setUpdatedAt(LocalDateTime.now());
      signAgreementRepository.save(agreement);
      return ResponseEntity.badRequest().body(Map.of("message", "实名认证失败，请核对身份证号"));
    }

    agreement.setVerifyStatus("PASSED");
    agreement.setUpdatedAt(LocalDateTime.now());
    signAgreementRepository.save(agreement);

    Tenant tenant = (Tenant) context.get("tenant");
    createMessageIfAbsent(
        tenantId,
        (Long) context.get("roomId"),
        "NOTICE",
        "SIGN_VERIFY",
        "CONTRACT",
        contractId,
        "/contract",
        text.of(tenant, "sign_verify_title"),
        text.of(tenant, "sign_verify_content", contract.getContractNo()),
        0);

    return ResponseEntity.ok(Map.of("success", true, "verifyStatus", agreement.getVerifyStatus()));
  }

  @PostMapping("/contract/sign/submit")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> submitSign(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Long contractId = support.parseLong(body.get("contractId"));
    if (contractId == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "contractId不能为空"));
    }

    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null || !tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权操作该合同"));
    }

    SignAgreement agreement = getOrCreateAgreement(contractId, tenantId);
    if (!"PASSED".equals(agreement.getVerifyStatus())) {
      return ResponseEntity.badRequest().body(Map.of("message", "请先完成实名认证"));
    }

    agreement.setSignStatus("SIGNED");
    agreement.setSignFlowNo("FLOW" + System.currentTimeMillis());
    agreement.setSignProvider(PROVIDER_MOCK);
    agreement.setSignedAt(LocalDateTime.now());
    agreement.setExpireAt(LocalDateTime.now().plusHours(24));
    agreement.setLastError(null);
    agreement.setUpdatedAt(LocalDateTime.now());
    signAgreementRepository.save(agreement);

    contract.setStatus(ContractStatus.ACTIVE);
    contractRepository.save(contract);

    Tenant tenant = (Tenant) context.get("tenant");
    createMessageIfAbsent(
        tenantId,
        (Long) context.get("roomId"),
        "NOTICE",
        "SIGN_SUCCESS",
        "CONTRACT",
        contractId,
        "/contract",
        text.of(tenant, "sign_success_title"),
        text.of(tenant, "sign_success_content", contract.getContractNo()),
        0);

    // signedAt / flowNo 在异常路径下可能未被赋值，避免 Map.of 抛 NPE
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("signStatus", agreement.getSignStatus());
    result.put("signedAt", agreement.getSignedAt());
    result.put("flowNo", agreement.getSignFlowNo());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/contract/sign/retry/{contractId}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> retrySign(@RequestHeader("Authorization") String authHeader,
      @PathVariable Long contractId) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    SignAgreement agreement =
        signAgreementRepository.findByContractIdAndTenantId(contractId, tenantId).orElse(null);
    if (agreement == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "签署记录不存在"));
    }

    agreement.setSignStatus("UNSIGNED");
    agreement.setRetryCount((agreement.getRetryCount() == null ? 0 : agreement.getRetryCount()) + 1);
    agreement.setLastError(null);
    agreement.setUpdatedAt(LocalDateTime.now());
    signAgreementRepository.save(agreement);

    return ResponseEntity.ok(Map.of("success", true, "retryCount", agreement.getRetryCount()));
  }

  @GetMapping("/contract/sign/history")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> signHistory(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<SignAgreement> list = signAgreementRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);

    // 一次取出相关合同，避免 N+1
    List<Long> contractIds = list.stream()
        .map(SignAgreement::getContractId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<Long, Contract> contractMap = new HashMap<>();
    if (!contractIds.isEmpty()) {
      for (Contract c : contractRepository.findAllById(contractIds)) {
        contractMap.put(c.getId(), c);
      }
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (SignAgreement item : list) {
      Contract c = contractMap.get(item.getContractId());
      Map<String, Object> row = new HashMap<>();
      row.put("id", item.getId());
      row.put("contractId", item.getContractId());
      row.put("contractNo", c == null || c.getContractNo() == null ? "-" : c.getContractNo());
      row.put("verifyStatus", item.getVerifyStatus());
      row.put("signStatus", item.getSignStatus());
      row.put("signedAt", item.getSignedAt());
      row.put("retryCount", item.getRetryCount());
      row.put("updatedAt", item.getUpdatedAt());
      rows.add(row);
    }
    return ResponseEntity.ok(rows);
  }

  private ResponseEntity<?> signPreviewInternal(String authHeader, Long contractId) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null || !tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权预览该合同"));
    }

    SignAgreement agreement = getOrCreateAgreement(contractId, tenantId);

    // Map.of 不允许 null，expireAt 在签署前可能是 null
    Map<String, Object> payload = new HashMap<>();
    payload.put("contractId", contract.getId());
    payload.put("contractNo", contract.getContractNo());
    payload.put("attachmentUrl", contract.getAttachmentUrl());
    payload.put("verifyStatus", agreement.getVerifyStatus());
    payload.put("signStatus", agreement.getSignStatus());
    payload.put("expireAt", agreement.getExpireAt());
    payload.put("retryCount", agreement.getRetryCount());
    return ResponseEntity.ok(payload);
  }

  private SignAgreement getOrCreateAgreement(Long contractId, Long tenantId) {
    return signAgreementRepository.findByContractIdAndTenantId(contractId, tenantId)
        .orElseGet(() -> signAgreementRepository.save(SignAgreement.builder()
            .contractId(contractId)
            .tenantId(tenantId)
            .verifyStatus("PENDING")
            .signStatus("UNSIGNED")
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build()));
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
