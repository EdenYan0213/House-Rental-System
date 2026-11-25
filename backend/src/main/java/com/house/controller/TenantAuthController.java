package com.house.controller;

import com.house.dto.TenantSmsLoginRequest;
import com.house.dto.TenantSmsSendRequest;
import com.house.dto.TenantSwitchRoomRequest;
import com.house.model.Contract;
import com.house.model.RemindLog;
import com.house.model.SystemMessage;
import com.house.model.Tenant;
import com.house.model.TenantLoginCode;
import com.house.repo.ContractRepository;
import com.house.repo.SystemMessageRepository;
import com.house.repo.TenantLoginCodeRepository;
import com.house.repo.TenantRepository;
import com.house.service.SmsService;
import com.house.service.TenantTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 租客端认证：注册、密码/验证码登录、当前会话、切换房间、修改手机号。
 *
 * <p>从原 {@code TenantPortalController} 拆出（该文件曾达 1344 行）。
 * 路径前缀仍是 {@code /api/tenant}，对前端完全透明。
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantAuthController {

  private static final Logger log = LoggerFactory.getLogger(TenantAuthController.class);
  private static final List<String> ACTIVE_CONTRACT_STATUS = List.of("ACTIVE", "进行中");

  private final TenantTokenService tenantTokenService;
  private final TenantRepository tenantRepository;
  private final TenantLoginCodeRepository tenantLoginCodeRepository;
  private final ContractRepository contractRepository;
  private final SystemMessageRepository systemMessageRepository;
  private final SmsService smsService;
  private final PasswordEncoder passwordEncoder;
  private final TenantPortalSupport support;

  public TenantAuthController(TenantTokenService tenantTokenService,
      TenantRepository tenantRepository,
      TenantLoginCodeRepository tenantLoginCodeRepository,
      ContractRepository contractRepository,
      SystemMessageRepository systemMessageRepository,
      SmsService smsService,
      PasswordEncoder passwordEncoder,
      TenantPortalSupport support) {
    this.tenantTokenService = tenantTokenService;
    this.tenantRepository = tenantRepository;
    this.tenantLoginCodeRepository = tenantLoginCodeRepository;
    this.contractRepository = contractRepository;
    this.systemMessageRepository = systemMessageRepository;
    this.smsService = smsService;
    this.passwordEncoder = passwordEncoder;
    this.support = support;
  }

  @PostMapping("/register/sms")
  public ResponseEntity<?> sendRegisterCode(@RequestBody TenantSmsSendRequest request) {
    String phone = support.normalizePhone(request.getPhone());
    if (phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号不能为空"));
    }

    Tenant existing = tenantRepository.findByPhone(phone).orElse(null);
    if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号已注册，请直接登录"));
    }

    CodeIssue issue = issueCode(phone, "register");
    if (issue.failed()) {
      return issue.error();
    }
    return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
    String name = support.text(body.get("name"));
    String phone = support.normalizePhone(support.text(body.get("phone")));
    String code = support.text(body.get("code"));
    String password = support.text(body.get("password"));

    if (name.isBlank() || phone.isBlank() || code.isBlank() || password.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "姓名、手机号、验证码、密码不能为空"));
    }
    if (password.length() < 6) {
      return ResponseEntity.badRequest().body(Map.of("message", "密码长度不能少于6位"));
    }

    Tenant existing = tenantRepository.findByPhone(phone).orElse(null);
    if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号已注册"));
    }

    ResponseEntity<?> codeError = verifyCode(phone, code);
    if (codeError != null) {
      return codeError;
    }

    Tenant tenant = existing == null ? new Tenant() : existing;
    tenant.setName(name);
    tenant.setPhone(phone);
    tenant.setPassword(passwordEncoder.encode(password));
    tenant.setDeleted(0);
    tenant.setRegisterSource("APP");
    support.normalizeTenantPreference(tenant);

    Tenant saved = tenantRepository.save(tenant);
    // 注册成功后作废验证码，避免重复使用。
    consumeCode(phone);

    createAdminMessage(
        "TENANT_REGISTER",
        "新租户注册",
        "租户 " + saved.getName() + "（" + support.maskPhone(saved.getPhone()) + "）已在租客端注册，请及时跟进租房需求。",
        saved.getId());

    String token = tenantTokenService.buildTenantToken(saved, null, true);
    return ResponseEntity.ok(Map.of(
        "message", "注册成功",
        "token", token,
        "isProspect", true,
        "tenantInfo", Map.of("id", saved.getId(), "name", saved.getName(), "phone", support.maskPhone(saved.getPhone())),
        "roomList", List.of(),
        "currentRoomId", ""));
  }

  @PostMapping("/login/password")
  public ResponseEntity<?> passwordLogin(@RequestBody Map<String, Object> body) {
    String phone = support.normalizePhone(support.text(body.get("phone")));
    String password = support.text(body.get("password"));
    if (phone.isBlank() || password.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号或密码不能为空"));
    }

    Tenant tenant = tenantRepository.findByPhone(phone).orElse(null);
    if (tenant == null || (tenant.getDeleted() != null && tenant.getDeleted() == 1)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号未注册"));
    }
    if (tenant.getPassword() == null || tenant.getPassword().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "该账号未设置密码，请使用验证码登录"));
    }
    if (!passwordEncoder.matches(password, tenant.getPassword())) {
      return ResponseEntity.badRequest().body(Map.of("message", "密码错误"));
    }

    return buildTenantLoginResult(tenant);
  }

  @PostMapping("/login/sms")
  public ResponseEntity<?> sendLoginCode(@RequestBody TenantSmsSendRequest request) {
    String phone = support.normalizePhone(request.getPhone());
    if (phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号不能为空"));
    }

    Tenant tenant = tenantRepository.findByPhone(phone).orElse(null);
    if (tenant == null || (tenant.getDeleted() != null && tenant.getDeleted() == 1)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号未注册"));
    }

    CodeIssue issue = issueCode(phone, "login");
    if (issue.failed()) {
      return issue.error();
    }
    String code = issue.code();

    smsService.sendSms(RemindLog.builder()
        .tenantId(tenant.getId())
        .tenantName(tenant.getName())
        .phone(phone)
        .roomNumber(null)
        .content("您正在登录房屋租赁系统，验证码：" + code + "，5分钟内有效。")
        .scene(RemindLog.SCENE_LOGIN_CODE)
        .build());

    return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
  }

  @PostMapping("/login")
  public ResponseEntity<?> tenantLogin(@RequestBody TenantSmsLoginRequest request) {
    String phone = support.normalizePhone(request.getPhone());
    String code = support.text(request.getCode());

    if (phone.isBlank() || code.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号或验证码不能为空"));
    }

    Tenant tenant = tenantRepository.findByPhone(phone).orElse(null);
    if (tenant == null || (tenant.getDeleted() != null && tenant.getDeleted() == 1)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号未注册"));
    }

    ResponseEntity<?> codeError = verifyCode(phone, code);
    if (codeError != null) {
      return codeError;
    }

    return buildTenantLoginResult(tenant);
  }

  @GetMapping("/current")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> current(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");
    boolean isProspect = Boolean.TRUE.equals(context.get("isProspect"));

    List<Contract> activeContracts = contractRepository.findByTenantIdAndStatusIn(tenant.getId(),
        ACTIVE_CONTRACT_STATUS);
    List<Map<String, Object>> rooms = tenantTokenService.buildRoomList(activeContracts);

    Map<String, Object> tenantInfo = new HashMap<>();
    tenantInfo.put("id", tenant.getId());
    tenantInfo.put("name", tenant.getName());
    tenantInfo.put("phone", support.maskPhone(tenant.getPhone()));
    tenantInfo.put("idCard", support.maskIdCard(tenant.getIdCard()));

    Map<String, Object> payload = new HashMap<>();
    payload.put("tenant", tenantInfo);
    payload.put("rooms", rooms);
    payload.put("currentRoom", roomId == null ? Map.of() : support.buildRoomInfo(roomId));
    payload.put("currentRoomId", roomId == null ? "" : roomId);
    payload.put("isProspect", isProspect);
    return ResponseEntity.ok(payload);
  }

  @PostMapping("/switch-room")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> switchRoom(@RequestHeader("Authorization") String authHeader,
      @RequestBody TenantSwitchRoomRequest request) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    if (Boolean.TRUE.equals(context.get("isProspect"))) {
      return ResponseEntity.badRequest().body(Map.of("message", "待租用户暂无可切换房间"));
    }
    Tenant tenant = (Tenant) context.get("tenant");
    @SuppressWarnings("unchecked")
    Set<Long> roomIds = (Set<Long>) context.get("roomIds");

    Long roomId = request.getRoomId();
    if (roomId == null || !roomIds.contains(roomId)) {
      return ResponseEntity.badRequest().body(Map.of("message", "房间无权限或不存在"));
    }

    String token = tenantTokenService.buildTenantToken(tenant, roomId);
    return ResponseEntity.ok(Map.of(
        "token", token,
        "currentRoom", support.buildRoomInfo(roomId),
        "currentRoomId", roomId));
  }

  @PostMapping("/update-phone/sms")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> sendUpdatePhoneCode(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    tenantTokenService.parseAndValidateTenant(authHeader);
    String phone = support.text(body.get("phone"));
    if (phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号不能为空"));
    }

    CodeIssue issue = issueCode(phone, "update_phone");
    if (issue.failed()) {
      return issue.error();
    }
    String code = issue.code();

    smsService.sendSms(RemindLog.builder()
        .tenantId(null)
        .tenantName("租客")
        .phone(phone)
        .roomNumber(null)
        .content("您正在修改手机号，验证码：" + code + "，5分钟内有效。")
        .scene(RemindLog.SCENE_UPDATE_PHONE)
        .build());

    return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
  }

  @PostMapping("/update-phone")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> updatePhone(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");

    String oldPhone = support.text(body.get("oldPhone"));
    String newPhone = support.text(body.get("newPhone"));
    String code = support.text(body.get("code"));

    if (oldPhone.isBlank() || newPhone.isBlank() || code.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "参数不完整"));
    }
    if (!oldPhone.equals(tenant.getPhone())) {
      return ResponseEntity.badRequest().body(Map.of("message", "原手机号校验失败"));
    }
    if (oldPhone.equals(newPhone)) {
      return ResponseEntity.badRequest().body(Map.of("message", "新手机号不能与原手机号相同"));
    }
    if (tenantRepository.existsByPhone(newPhone)) {
      return ResponseEntity.badRequest().body(Map.of("message", "新手机号已被占用"));
    }

    ResponseEntity<?> codeError = verifyCode(newPhone, code);
    if (codeError != null) {
      return codeError;
    }

    tenant.setPhone(newPhone);
    tenantRepository.save(tenant);

    String token = tenantTokenService.buildTenantToken(tenant, roomId);
    return ResponseEntity.ok(Map.of("success", true, "token", token, "message", "手机号更新成功"));
  }

  // ---------------- 验证码：签发与校验 ----------------

  /**
   * 校验验证码；不通过时返回带中文提示的 400 响应，通过返回 null。
   */
  private ResponseEntity<?> verifyCode(String phone, String code) {
    TenantLoginCode loginCode = tenantLoginCodeRepository.findByPhone(phone).orElse(null);
    if (loginCode == null || loginCode.getCode() == null || !code.equals(loginCode.getCode())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码错误"));
    }
    if (loginCode.getExpiresAt() == null || LocalDateTime.now().isAfter(loginCode.getExpiresAt())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码已过期，请重新获取"));
    }
    return null;
  }

  /** 作废验证码，防止重复使用。 */
  private void consumeCode(String phone) {
    tenantLoginCodeRepository.findByPhone(phone).ifPresent(record -> {
      record.setCode("000000");
      record.setExpiresAt(LocalDateTime.now().minusSeconds(1));
      tenantLoginCodeRepository.save(record);
    });
  }

  /** 验证码签发结果：要么被限流（error 非空），要么带明文 code。 */
  private record CodeIssue(ResponseEntity<?> error, String code) {
    boolean failed() {
      return error != null;
    }
  }

  /**
   * 签发 6 位验证码，每日每号上限 5 次。
   *
   * <p>注册场景只需要知道是否被限流，登录/改手机号还需要明文去发短信，
   * 因此统一返回 {@link CodeIssue}，由调用方决定用哪部分。
   */
  private CodeIssue issueCode(String phone, String scene) {
    LocalDate today = LocalDate.now();
    TenantLoginCode record = tenantLoginCodeRepository.findByPhone(phone)
        .orElse(TenantLoginCode.builder()
            .phone(phone)
            .sentDate(today)
            .sentCount(0)
            .build());

    if (!today.equals(record.getSentDate())) {
      record.setSentDate(today);
      record.setSentCount(0);
    }

    int sentCount = record.getSentCount() == null ? 0 : record.getSentCount();
    if (sentCount >= 5) {
      return new CodeIssue(
          ResponseEntity.badRequest().body(Map.of("message", "该手机号今日验证码发送次数已达上限")),
          null);
    }

    String code = String.format("%06d", new Random().nextInt(1_000_000));
    LocalDateTime now = LocalDateTime.now();
    record.setCode(code);
    record.setExpiresAt(now.plusMinutes(5));
    record.setLastSentAt(now);
    record.setSentCount(sentCount + 1);
    tenantLoginCodeRepository.save(record);

    // 当前阶段短信验证码以日志输出为主，便于联调与测试。
    log.info("[TENANT_SMS_CODE] scene={} phone={} code={} expireAt={}", scene, phone, code, record.getExpiresAt());
    return new CodeIssue(null, code);
  }

  // ---------------- 登录结果装配 ----------------

  private ResponseEntity<?> buildTenantLoginResult(Tenant tenant) {
    List<Contract> contracts = contractRepository.findByTenantIdAndStatusIn(
        tenant.getId(), ACTIVE_CONTRACT_STATUS);
    List<Map<String, Object>> roomList = tenantTokenService.buildRoomList(contracts);
    boolean isProspect = roomList.isEmpty();
    Long defaultRoomId = isProspect ? null : (Long) roomList.get(0).get("roomId");
    String token = tenantTokenService.buildTenantToken(tenant, defaultRoomId, isProspect);

    Map<String, Object> tenantInfo = new HashMap<>();
    tenantInfo.put("id", tenant.getId());
    tenantInfo.put("name", tenant.getName());
    tenantInfo.put("phone", support.maskPhone(tenant.getPhone()));
    tenantInfo.put("isProspect", isProspect);

    Map<String, Object> payload = new HashMap<>();
    payload.put("token", token);
    payload.put("tenantInfo", tenantInfo);
    payload.put("roomList", roomList);
    payload.put("currentRoomId", defaultRoomId == null ? "" : defaultRoomId);
    payload.put("isProspect", isProspect);
    return ResponseEntity.ok(payload);
  }

  private void createAdminMessage(String type, String title, String content, Long relatedId) {
    SystemMessage msg = new SystemMessage();
    msg.setType(type);
    msg.setTitle(title);
    msg.setContent(content);
    msg.setRelatedId(relatedId);
    msg.setIsRead(false);
    msg.setCreatedAt(LocalDateTime.now());
    systemMessageRepository.save(msg);
  }
}
