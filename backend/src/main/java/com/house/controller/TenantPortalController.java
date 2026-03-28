package com.house.controller;

import com.house.dto.TenantSmsLoginRequest;
import com.house.dto.TenantSmsSendRequest;
import com.house.dto.TenantSwitchRoomRequest;
import com.house.model.*;
import com.house.repo.*;
import com.house.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.house.service.TenantTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/tenant")
public class TenantPortalController {
  private static final List<String> ACTIVE_CONTRACT_STATUS = List.of("ACTIVE", "进行中");
  private static final Logger log = LoggerFactory.getLogger(TenantPortalController.class);

  private final TenantTokenService tenantTokenService;
  private final TenantRepository tenantRepository;
  private final TenantLoginCodeRepository tenantLoginCodeRepository;
  private final TenantMessageRepository tenantMessageRepository;
  private final SignAgreementRepository signAgreementRepository;
  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final BillRepository billRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final RepairOrderRepository repairOrderRepository;
  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final RenewApplicationRepository renewApplicationRepository;
  private final NoticeRepository noticeRepository;
  private final SmsService smsService;
  private final SystemMessageRepository systemMessageRepository;
  private final PasswordEncoder passwordEncoder;

  public TenantPortalController(TenantTokenService tenantTokenService,
      TenantRepository tenantRepository,
      TenantLoginCodeRepository tenantLoginCodeRepository,
      TenantMessageRepository tenantMessageRepository,
      SignAgreementRepository signAgreementRepository,
      ContractRepository contractRepository,
      RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      BillRepository billRepository,
      PaymentRecordRepository paymentRecordRepository,
      RepairOrderRepository repairOrderRepository,
      ComplaintSuggestionRepository complaintSuggestionRepository,
      RenewApplicationRepository renewApplicationRepository,
      NoticeRepository noticeRepository,
      SmsService smsService,
      SystemMessageRepository systemMessageRepository,
      PasswordEncoder passwordEncoder) {
    this.tenantTokenService = tenantTokenService;
    this.tenantRepository = tenantRepository;
    this.tenantLoginCodeRepository = tenantLoginCodeRepository;
    this.tenantMessageRepository = tenantMessageRepository;
    this.signAgreementRepository = signAgreementRepository;
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.billRepository = billRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.repairOrderRepository = repairOrderRepository;
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.renewApplicationRepository = renewApplicationRepository;
    this.noticeRepository = noticeRepository;
    this.smsService = smsService;
    this.systemMessageRepository = systemMessageRepository;
    this.passwordEncoder = passwordEncoder;
  }

  // Phase 1: Tenant register and login.

  @PostMapping("/register/sms")
  public ResponseEntity<?> sendRegisterCode(@RequestBody TenantSmsSendRequest request) {
    String phone = normalizePhone(request.getPhone());
    if (phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号不能为空"));
    }

    Tenant existing = tenantRepository.findByPhone(phone).orElse(null);
    if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号已注册，请直接登录"));
    }

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

    if ((record.getSentCount() == null ? 0 : record.getSentCount()) >= 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号今日验证码发送次数已达上限"));
    }

    String code = String.format("%06d", new Random().nextInt(1_000_000));
    LocalDateTime now = LocalDateTime.now();
    record.setCode(code);
    record.setExpiresAt(now.plusMinutes(5));
    record.setLastSentAt(now);
    record.setSentCount((record.getSentCount() == null ? 0 : record.getSentCount()) + 1);
    tenantLoginCodeRepository.save(record);

    // 当前阶段短信验证码以日志输出为主，便于联调与测试。
    log.info("[TENANT_SMS_CODE] scene=register phone={} code={} expireAt={}", phone, code, record.getExpiresAt());

    return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
    String name = text(body.get("name"));
    String phone = normalizePhone(text(body.get("phone")));
    String code = text(body.get("code"));
    String password = text(body.get("password"));

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

    TenantLoginCode loginCode = tenantLoginCodeRepository.findByPhone(phone).orElse(null);
    if (loginCode == null || loginCode.getCode() == null || !code.equals(loginCode.getCode())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码错误"));
    }
    if (loginCode.getExpiresAt() == null || LocalDateTime.now().isAfter(loginCode.getExpiresAt())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码已过期，请重新获取"));
    }

    Tenant tenant = existing == null ? new Tenant() : existing;
    tenant.setName(name);
    tenant.setPhone(phone);
    tenant.setPassword(passwordEncoder.encode(password));
    tenant.setDeleted(0);
    tenant.setRegisterSource("APP");
    normalizeTenantPreference(tenant);

    Tenant saved = tenantRepository.save(tenant);
    // 注册成功后作废验证码，避免重复使用。
    loginCode.setCode("000000");
    loginCode.setExpiresAt(LocalDateTime.now().minusSeconds(1));
    tenantLoginCodeRepository.save(loginCode);

    createAdminMessage(
        "TENANT_REGISTER",
        "新租户注册",
        "租户 " + saved.getName() + "（" + maskPhone(saved.getPhone()) + "）已在租客端注册，请及时跟进租房需求。",
        saved.getId());

    String token = tenantTokenService.buildTenantToken(saved, null, true);
    return ResponseEntity.ok(Map.of(
        "message", "注册成功",
        "token", token,
        "isProspect", true,
        "tenantInfo", Map.of("id", saved.getId(), "name", saved.getName(), "phone", maskPhone(saved.getPhone())),
        "roomList", List.of(),
        "currentRoomId", ""));
  }

  @PostMapping("/login/password")
  public ResponseEntity<?> passwordLogin(@RequestBody Map<String, Object> body) {
    String phone = normalizePhone(text(body.get("phone")));
    String password = text(body.get("password"));
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

  // SMS login entry.

  @PostMapping("/login/sms")
  public ResponseEntity<?> sendLoginCode(@RequestBody TenantSmsSendRequest request) {
    String phone = normalizePhone(request.getPhone());
    if (phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号不能为空"));
    }

    Tenant tenant = tenantRepository.findByPhone(phone).orElse(null);
    if (tenant == null || (tenant.getDeleted() != null && tenant.getDeleted() == 1)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号未注册"));
    }

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

    if ((record.getSentCount() == null ? 0 : record.getSentCount()) >= 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号今日验证码发送次数已达上限"));
    }

    String code = String.format("%06d", new Random().nextInt(1_000_000));
    LocalDateTime now = LocalDateTime.now();
    record.setCode(code);
    record.setExpiresAt(now.plusMinutes(5));
    record.setLastSentAt(now);
    record.setSentCount((record.getSentCount() == null ? 0 : record.getSentCount()) + 1);
    tenantLoginCodeRepository.save(record);
    log.info("[TENANT_SMS_CODE] scene=login phone={} code={} expireAt={}", phone, code, record.getExpiresAt());

    RemindLog smsLog = RemindLog.builder()
        .tenantId(tenant.getId())
        .tenantName(tenant.getName())
        .phone(phone)
        .roomNumber("租客登录")
        .content("您正在登录房屋租赁系统，验证码：" + code + "，5分钟内有效。")
        .build();
    smsService.sendSms(smsLog);

    return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
  }

  @PostMapping("/login")
  public ResponseEntity<?> tenantLogin(@RequestBody TenantSmsLoginRequest request) {
    String phone = normalizePhone(request.getPhone());
    String code = text(request.getCode());

    if (phone.isBlank() || code.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号或验证码不能为空"));
    }

    Tenant tenant = tenantRepository.findByPhone(phone).orElse(null);
    if (tenant == null || (tenant.getDeleted() != null && tenant.getDeleted() == 1)) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号未注册"));
    }

    TenantLoginCode loginCode = tenantLoginCodeRepository.findByPhone(phone).orElse(null);
    if (loginCode == null || loginCode.getCode() == null || !loginCode.getCode().equals(code)) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码错误"));
    }
    if (loginCode.getExpiresAt() == null || LocalDateTime.now().isAfter(loginCode.getExpiresAt())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码已过期，请重新获取"));
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
        List.of("ACTIVE", "进行中"));
    List<Map<String, Object>> rooms = tenantTokenService.buildRoomList(activeContracts);

    Map<String, Object> tenantInfo = new HashMap<>();
    tenantInfo.put("id", tenant.getId());
    tenantInfo.put("name", tenant.getName());
    tenantInfo.put("phone", maskPhone(tenant.getPhone()));
    tenantInfo.put("idCard", maskIdCard(tenant.getIdCard()));

    Map<String, Object> payload = new HashMap<>();
    payload.put("tenant", tenantInfo);
    payload.put("rooms", rooms);
    payload.put("currentRoom", roomId == null ? Map.of() : buildRoomInfo(roomId));
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
        "currentRoom", buildRoomInfo(roomId),
        "currentRoomId", roomId));
  }

  @GetMapping("/home")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> home(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");
    if (Boolean.TRUE.equals(context.get("isProspect"))) {
      return ResponseEntity.ok(Map.of(
          "tenantName", tenant.getName(),
          "currentRoom", Map.of(),
          "unpaidCount", 0,
          "unpaidAmount", 0,
          "latestNotice", null,
          "isProspect", true));
    }

    List<Contract> roomContracts = contractRepository.findByTenantIdAndRoomId(tenant.getId(), roomId);
    List<Long> contractIds = roomContracts.stream().map(Contract::getId).toList();
    List<Bill> bills = contractIds.isEmpty() ? List.of() : billRepository.findByContractIdIn(contractIds);

    int unpaidCount = 0;
    double unpaidAmount = 0D;
    for (Bill bill : bills) {
      double paid = paymentRecordRepository.findByBillId(bill.getId())
          .stream()
          .mapToDouble(item -> item.getPaidAmount() == null ? 0D : item.getPaidAmount())
          .sum();
      double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
      if (total - paid > 0.0001) {
        unpaidCount++;
        unpaidAmount += (total - paid);
      }
    }

    Notice latest = noticeRepository.findTopByOrderBySortDescCreatedAtDesc().orElse(null);
    Map<String, Object> latestNotice = latest == null
        ? null
        : Map.of(
            "id", latest.getId(),
            "title", latest.getTitle(),
            "createdAt", latest.getCreatedAt());

    return ResponseEntity.ok(Map.of(
        "tenantName", tenant.getName(),
        "currentRoom", buildRoomInfo(roomId),
        "unpaidCount", unpaidCount,
        "unpaidAmount", round(unpaidAmount),
        "latestNotice", latestNotice));
  }

  @GetMapping("/vacant-rooms")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> vacantRooms(@RequestHeader("Authorization") String authHeader) {
    tenantTokenService.parseAndValidateTenant(authHeader);
    List<Room> rooms = roomRepository.findAll();
    List<Map<String, Object>> payload = new ArrayList<>();
    for (Room room : rooms) {
      String status = room.getStatus() == null ? "" : room.getStatus().trim();
      boolean idle = "IDLE".equalsIgnoreCase(status) || "空闲".equals(status);
      if (!idle) {
        continue;
      }
      boolean hasActiveContract = contractRepository.findByRoomIdAndStatus(room.getId(), "ACTIVE").stream().findFirst()
          .isPresent();
      if (hasActiveContract) {
        continue;
      }
      Building building = room.getBuildingId() == null ? null
          : buildingRepository.findById(room.getBuildingId()).orElse(null);
      payload.add(Map.of(
          "roomId", room.getId(),
          "buildingName", building == null ? "-" : building.getName(),
          "roomNumber", room.getRoomNumber() == null ? "-" : room.getRoomNumber(),
          "floor", room.getFloor() == null ? "" : room.getFloor(),
          "layout", room.getLayout() == null ? "" : room.getLayout(),
          "area", room.getArea() == null ? "" : room.getArea(),
          "imageUrl", room.getImageUrl() == null ? "" : room.getImageUrl()));
    }
    return ResponseEntity.ok(payload);
  }

  @PostMapping("/rent-intent")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> rentIntent(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = parseLong(body.get("roomId"));
    String message = text(body.get("message"));
    String contactPhone = normalizePhone(text(body.get("contactPhone")));

    if (roomId == null || message.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "房间和沟通内容不能为空"));
    }
    Room room = roomRepository.findById(roomId).orElse(null);
    if (room == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "房间不存在"));
    }

    SystemMessage msg = new SystemMessage();
    msg.setType("TENANT_RENT_INTENT");
    msg.setTitle("新租房沟通申请");
    msg.setContent("租户: " + (tenant.getName() == null ? "-" : tenant.getName())
        + "\n手机号: " + (contactPhone.isBlank() ? maskPhone(tenant.getPhone()) : contactPhone)
        + "\n意向房间: " + (room.getRoomNumber() == null ? "-" : room.getRoomNumber())
        + "\n留言: " + message);
    msg.setRelatedId(tenant.getId());
    msg.setIsRead(false);
    msg.setCreatedAt(LocalDateTime.now());
    systemMessageRepository.save(msg);

    return ResponseEntity.ok(Map.of("success", true, "message", "已提交沟通申请，管理员会尽快联系您"));
  }

  // Phase 2: Tenant profile update flow.

  @PostMapping("/update-phone/sms")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> sendUpdatePhoneCode(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    tenantTokenService.parseAndValidateTenant(authHeader);
    String phone = text(body.get("phone"));
    if (phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "手机号不能为空"));
    }

    LocalDate today = LocalDate.now();
    TenantLoginCode record = tenantLoginCodeRepository.findByPhone(phone)
        .orElse(TenantLoginCode.builder().phone(phone).sentDate(today).sentCount(0).build());

    if (!today.equals(record.getSentDate())) {
      record.setSentDate(today);
      record.setSentCount(0);
    }
    if ((record.getSentCount() == null ? 0 : record.getSentCount()) >= 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "该手机号今日验证码发送次数已达上限"));
    }

    String code = String.format("%06d", new Random().nextInt(1_000_000));
    LocalDateTime now = LocalDateTime.now();
    record.setCode(code);
    record.setExpiresAt(now.plusMinutes(5));
    record.setLastSentAt(now);
    record.setSentCount((record.getSentCount() == null ? 0 : record.getSentCount()) + 1);
    tenantLoginCodeRepository.save(record);
    log.info("[TENANT_SMS_CODE] scene=update_phone phone={} code={} expireAt={}", phone, code, record.getExpiresAt());

    smsService.sendSms(RemindLog.builder()
        .tenantId(null)
        .tenantName("租客")
        .phone(phone)
        .roomNumber("手机号修改")
        .content("您正在修改手机号，验证码：" + code + "，5分钟内有效。")
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

    String oldPhone = text(body.get("oldPhone"));
    String newPhone = text(body.get("newPhone"));
    String code = text(body.get("code"));

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

    TenantLoginCode loginCode = tenantLoginCodeRepository.findByPhone(newPhone).orElse(null);
    if (loginCode == null || loginCode.getCode() == null || !code.equals(loginCode.getCode())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码错误"));
    }
    if (loginCode.getExpiresAt() == null || LocalDateTime.now().isAfter(loginCode.getExpiresAt())) {
      return ResponseEntity.badRequest().body(Map.of("message", "验证码已过期"));
    }

    tenant.setPhone(newPhone);
    tenantRepository.save(tenant);

    String token = tenantTokenService.buildTenantToken(tenant, roomId);
    return ResponseEntity.ok(Map.of("success", true, "token", token, "message", "手机号更新成功"));
  }

  // Phase 3: Preferences, messages, dashboard, and e-sign workflow.

  @GetMapping("/preferences")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> getPreferences(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    normalizeTenantPreference(tenant);
    tenantRepository.save(tenant);

    return ResponseEntity.ok(Map.of(
        "language", tenant.getLanguagePreference(),
        "notifyOverdue", bool(tenant.getNotifyOverdue()),
        "notifyRepair", bool(tenant.getNotifyRepair()),
        "notifyContract", bool(tenant.getNotifyContract()),
        "notifyNotice", bool(tenant.getNotifyNotice())));
  }

  @PutMapping("/preferences")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> updatePreferences(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    normalizeTenantPreference(tenant);

    String language = text(body.get("language"));
    if (!language.isBlank()) {
      tenant.setLanguagePreference(language.startsWith("en") ? "en-US" : "zh-CN");
    }
    if (body.containsKey("notifyOverdue")) {
      tenant.setNotifyOverdue(boolValue(body.get("notifyOverdue")) ? 1 : 0);
    }
    if (body.containsKey("notifyRepair")) {
      tenant.setNotifyRepair(boolValue(body.get("notifyRepair")) ? 1 : 0);
    }
    if (body.containsKey("notifyContract")) {
      tenant.setNotifyContract(boolValue(body.get("notifyContract")) ? 1 : 0);
    }
    if (body.containsKey("notifyNotice")) {
      tenant.setNotifyNotice(boolValue(body.get("notifyNotice")) ? 1 : 0);
    }

    tenantRepository.save(tenant);
    return getPreferences(authHeader);
  }

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

    TenantMessage msg = tenantMessageRepository.findById(id).orElse(null);
    if (msg == null || !tenantId.equals(msg.getTenantId()) || !Objects.equals(msg.getIsDeleted(), 0)) {
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

    TenantMessage msg = tenantMessageRepository.findById(id).orElse(null);
    if (msg == null || !tenantId.equals(msg.getTenantId()) || !Objects.equals(msg.getIsDeleted(), 0)) {
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

    TenantMessage msg = tenantMessageRepository.findById(id).orElse(null);
    if (msg == null || !tenantId.equals(msg.getTenantId()) || !Objects.equals(msg.getIsDeleted(), 0)) {
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

    TenantMessage msg = tenantMessageRepository.findById(id).orElse(null);
    if (msg == null || !tenantId.equals(msg.getTenantId()) || !Objects.equals(msg.getIsDeleted(), 0)) {
      return ResponseEntity.notFound().build();
    }

    msg.setActionStatus("DONE");
    msg.setNeedAction(0);
    msg.setIsRead(1);
    msg.setReadAt(LocalDateTime.now());
    tenantMessageRepository.save(msg);
    return ResponseEntity.ok(Map.of("success", true, "message", "已处理"));
  }

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
      YearMonth ym = now.minusMonths(i);
      amountByMonth.put(ym.toString(), 0D);
    }
    for (Bill bill : bills) {
      if (bill.getBillMonth() == null || !amountByMonth.containsKey(bill.getBillMonth())) {
        continue;
      }
      double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
      amountByMonth.put(bill.getBillMonth(), round(amountByMonth.get(bill.getBillMonth()) + total));
    }

    List<Map<String, Object>> billTrend = new ArrayList<>();
    for (Map.Entry<String, Double> e : amountByMonth.entrySet()) {
      billTrend.add(Map.of("month", e.getKey(), "amount", e.getValue()));
    }

    List<RepairOrder> repairs = repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    long repairPending = repairs.stream()
        .filter(r -> Set.of("pending", "assigned", "processing", "pending_accept").contains(text(r.getStatus())))
        .count();
    long repairCompleted = repairs.stream().filter(r -> "completed".equalsIgnoreCase(text(r.getStatus()))).count();

    List<ComplaintSuggestion> complaints = complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    long complaintPending = complaints.stream()
        .filter(c -> Set.of("pending", "assigned", "processing", "pending_confirm").contains(text(c.getStatus())))
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

  @GetMapping("/contract/sign/preview/{contractId}")
  public ResponseEntity<?> signPreview(@RequestHeader("Authorization") String authHeader,
      @PathVariable Long contractId) {
    return signPreviewInternal(authHeader, contractId);
  }

  @PostMapping("/contract/sign/preview")
  public ResponseEntity<?> signPreviewPost(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Long contractId = parseLong(body.get("contractId"));
    if (contractId == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "contractId不能为空"));
    }
    return signPreviewInternal(authHeader, contractId);
  }

  private ResponseEntity<?> signPreviewInternal(String authHeader, Long contractId) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null || !tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权预览该合同"));
    }

    SignAgreement agreement = getOrCreateAgreement(contractId, tenantId);

    // Map.of does not allow null values. expireAt can be null before signing.
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

  @PostMapping("/contract/sign/verify")
  public ResponseEntity<?> verifyIdentity(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Long contractId = parseLong(body.get("contractId"));
    String name = text(body.get("name"));
    String idCard = text(body.get("idCard"));
    if (contractId == null || name.isBlank() || idCard.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "实名认证参数不完整"));
    }

    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null || !tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权操作该合同"));
    }

    SignAgreement agreement = getOrCreateAgreement(contractId, tenantId);
    agreement.setVerifyName(name);
    agreement.setVerifyIdCard(maskIdCard(idCard));
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

    createMessageIfAbsent(
        tenantId,
        (Long) context.get("roomId"),
        "NOTICE",
        "SIGN_VERIFY",
        "CONTRACT",
        contractId,
        "/contract",
        msg((Tenant) context.get("tenant"), "sign_verify_title"),
        msg((Tenant) context.get("tenant"), "sign_verify_content", contract.getContractNo()),
        0);

    return ResponseEntity.ok(Map.of("success", true, "verifyStatus", agreement.getVerifyStatus()));
  }

  @PostMapping("/contract/sign/submit")
  public ResponseEntity<?> submitSign(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Long contractId = parseLong(body.get("contractId"));
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
    agreement.setSignProvider("MOCK_ESIGN");
    agreement.setSignedAt(LocalDateTime.now());
    agreement.setExpireAt(LocalDateTime.now().plusHours(24));
    agreement.setLastError(null);
    agreement.setUpdatedAt(LocalDateTime.now());
    signAgreementRepository.save(agreement);

    contract.setStatus("ACTIVE");
    contractRepository.save(contract);

    createMessageIfAbsent(
        tenantId,
        (Long) context.get("roomId"),
        "NOTICE",
        "SIGN_SUCCESS",
        "CONTRACT",
        contractId,
        "/contract",
        msg((Tenant) context.get("tenant"), "sign_success_title"),
        msg((Tenant) context.get("tenant"), "sign_success_content", contract.getContractNo()),
        0);

    return ResponseEntity.ok(Map.of(
        "success", true,
        "signStatus", agreement.getSignStatus(),
        "signedAt", agreement.getSignedAt(),
        "flowNo", agreement.getSignFlowNo()));
  }

  @PostMapping("/contract/sign/retry/{contractId}")
  public ResponseEntity<?> retrySign(@RequestHeader("Authorization") String authHeader, @PathVariable Long contractId) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    SignAgreement agreement = signAgreementRepository.findByContractIdAndTenantId(contractId, tenantId).orElse(null);
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
  public ResponseEntity<?> signHistory(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<SignAgreement> list = signAgreementRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (SignAgreement item : list) {
      Contract c = contractRepository.findById(item.getContractId()).orElse(null);
      Map<String, Object> row = new HashMap<>();
      row.put("id", item.getId());
      row.put("contractId", item.getContractId());
      row.put("contractNo", c == null ? "-" : (c.getContractNo() == null ? "-" : c.getContractNo()));
      row.put("verifyStatus", item.getVerifyStatus());
      row.put("signStatus", item.getSignStatus());
      row.put("signedAt", item.getSignedAt());
      row.put("retryCount", item.getRetryCount());
      row.put("updatedAt", item.getUpdatedAt());
      rows.add(row);
    }
    return ResponseEntity.ok(rows);
  }

  private void generateAutoMessages(Tenant tenant, Map<String, Object> context) {
    normalizeTenantPreference(tenant);

    Long tenantId = tenant.getId();
    Long roomId = (Long) context.get("roomId");
    String lang = tenant.getLanguagePreference();

    List<Contract> contracts = contractRepository.findByTenantId(tenantId);
    List<Long> contractIds = contracts.stream().map(Contract::getId).toList();

    if (bool(tenant.getNotifyOverdue()) && !contractIds.isEmpty()) {
      List<Bill> bills = billRepository.findByContractIdIn(contractIds);
      for (Bill bill : bills) {
        double paid = paymentRecordRepository.findByBillId(bill.getId())
            .stream()
            .mapToDouble(item -> item.getPaidAmount() == null ? 0D : item.getPaidAmount())
            .sum();
        double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
        if (total - paid > 0.01) {
          createMessageIfAbsent(
              tenantId,
              roomId,
              "TODO",
              "OVERDUE",
              "BILL",
              bill.getId(),
              "/bills",
              msgLang(lang, "overdue_title"),
              msgLang(lang, "overdue_content", bill.getBillMonth(), round(total - paid)),
              1);
        }
      }
    }

    if (bool(tenant.getNotifyContract())) {
      for (Contract c : contracts) {
        if (c.getEndDate() == null) {
          continue;
        }
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
        if (daysLeft >= 0 && daysLeft <= 30) {
          createMessageIfAbsent(
              tenantId,
              roomId,
              "TODO",
              "CONTRACT_EXPIRE",
              "CONTRACT",
              c.getId(),
              "/contract",
              msgLang(lang, "contract_expire_title"),
              msgLang(lang, "contract_expire_content", c.getContractNo(), daysLeft),
              1);
        }
      }
    }

    if (bool(tenant.getNotifyRepair())) {
      List<RepairOrder> repairs = repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
      for (RepairOrder r : repairs.stream().limit(5).toList()) {
        if (Set.of("processing", "pending_accept", "completed").contains(text(r.getStatus()))) {
          createMessageIfAbsent(
              tenantId,
              roomId,
              "NOTICE",
              "REPAIR_PROGRESS",
              "REPAIR",
              r.getId(),
              "/repairs/" + r.getId(),
              msgLang(lang, "repair_title"),
              msgLang(lang, "repair_content", r.getOrderNo(), r.getStatus()),
              0);
        }
      }

      List<ComplaintSuggestion> complaints = complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
      for (ComplaintSuggestion c : complaints.stream().limit(5).toList()) {
        if (Set.of("processing", "pending_confirm", "completed").contains(text(c.getStatus()))) {
          createMessageIfAbsent(
              tenantId,
              roomId,
              "NOTICE",
              "COMPLAINT_PROGRESS",
              "COMPLAINT",
              c.getId(),
              "/complaints/" + c.getId(),
              msgLang(lang, "complaint_title"),
              msgLang(lang, "complaint_content", c.getComplaintNo(), c.getStatus()),
              0);
        }
      }
    }

    if (bool(tenant.getNotifyNotice())) {
      Notice latest = noticeRepository.findTopByOrderBySortDescCreatedAtDesc().orElse(null);
      if (latest != null) {
        createMessageIfAbsent(
            tenantId,
            roomId,
            "NOTICE",
            "NOTICE_NEW",
            "NOTICE",
            latest.getId(),
            "/notices/" + latest.getId(),
            msgLang(lang, "notice_title"),
            msgLang(lang, "notice_content", latest.getTitle()),
            0);
      }
    }

    for (Contract c : contracts) {
      SignAgreement agreement = signAgreementRepository.findByContractIdAndTenantId(c.getId(), tenantId).orElse(null);
      if (agreement == null) {
        continue;
      }
      if ("FAILED".equals(agreement.getSignStatus()) || "TIMEOUT".equals(agreement.getSignStatus())) {
        createMessageIfAbsent(
            tenantId,
            roomId,
            "TODO",
            "SIGN_RETRY",
            "CONTRACT",
            c.getId(),
            "/contract",
            msgLang(lang, "sign_retry_title"),
            msgLang(lang, "sign_retry_content", c.getContractNo()),
            1);
      }
      if (agreement.getExpireAt() != null && LocalDateTime.now().isAfter(agreement.getExpireAt())
          && !"SIGNED".equals(agreement.getSignStatus())) {
        agreement.setSignStatus("TIMEOUT");
        agreement.setLastError("签署已超时，请重新发起");
        agreement.setUpdatedAt(LocalDateTime.now());
        signAgreementRepository.save(agreement);
      }
    }
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
        .findTopByTenantIdAndTypeAndRefTypeAndRefIdAndIsDeletedOrderByCreatedAtDesc(tenantId, type, refType, refId, 0)
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

  private void normalizeTenantPreference(Tenant tenant) {
    if (tenant.getLanguagePreference() == null || tenant.getLanguagePreference().isBlank()) {
      tenant.setLanguagePreference("zh-CN");
    }
    if (tenant.getNotifyOverdue() == null) {
      tenant.setNotifyOverdue(1);
    }
    if (tenant.getNotifyRepair() == null) {
      tenant.setNotifyRepair(1);
    }
    if (tenant.getNotifyContract() == null) {
      tenant.setNotifyContract(1);
    }
    if (tenant.getNotifyNotice() == null) {
      tenant.setNotifyNotice(1);
    }
  }

  private boolean bool(Integer v) {
    return v != null && v == 1;
  }

  private boolean boolValue(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    String s = text(value);
    return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
  }

  private String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  private List<Contract> getActiveContracts(Long tenantId) {
    return contractRepository.findByTenantIdAndStatusIn(tenantId, ACTIVE_CONTRACT_STATUS);
  }

  private String normalizePhone(String phone) {
    return phone == null ? "" : phone.trim();
  }

  private ResponseEntity<?> buildTenantLoginResult(Tenant tenant) {
    List<Contract> contracts = getActiveContracts(tenant.getId());
    List<Map<String, Object>> roomList = tenantTokenService.buildRoomList(contracts);
    boolean isProspect = roomList.isEmpty();
    Long defaultRoomId = isProspect ? null : (Long) roomList.get(0).get("roomId");
    String token = tenantTokenService.buildTenantToken(tenant, defaultRoomId, isProspect);

    Map<String, Object> tenantInfo = new HashMap<>();
    tenantInfo.put("id", tenant.getId());
    tenantInfo.put("name", tenant.getName());
    tenantInfo.put("phone", maskPhone(tenant.getPhone()));
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

  private Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  private String msg(Tenant tenant, String key, Object... args) {
    String lang = tenant.getLanguagePreference();
    if (lang == null || lang.isBlank()) {
      lang = "zh-CN";
    }
    return msgLang(lang, key, args);
  }

  private String msgLang(String lang, String key, Object... args) {
    boolean en = lang.startsWith("en");
    String template;
    if (en) {
      template = switch (key) {
        case "overdue_title" -> "Bill overdue reminder";
        case "overdue_content" -> "Your bill %s is overdue, unpaid amount: %.2f.";
        case "contract_expire_title" -> "Contract nearing expiration";
        case "contract_expire_content" -> "Contract %s expires in %s days. You can submit renewal.";
        case "repair_title" -> "Repair progress update";
        case "repair_content" -> "Repair order %s status changed to %s.";
        case "complaint_title" -> "Complaint update";
        case "complaint_content" -> "Complaint %s status changed to %s.";
        case "notice_title" -> "New announcement";
        case "notice_content" -> "A new announcement is published: %s";
        case "sign_verify_title" -> "Identity verification passed";
        case "sign_verify_content" -> "Contract %s is ready for signing.";
        case "sign_success_title" -> "Contract signed successfully";
        case "sign_success_content" -> "Contract %s has been signed.";
        case "sign_retry_title" -> "Signature retry required";
        case "sign_retry_content" -> "Contract %s signing failed or timed out. Please retry.";
        default -> key;
      };
    } else {
      template = switch (key) {
        case "overdue_title" -> "账单逾期提醒";
        case "overdue_content" -> "您的账单%s已逾期，未付金额: %.2f元。";
        case "contract_expire_title" -> "合同即将到期";
        case "contract_expire_content" -> "合同%s将在%s天后到期，可提交续租申请。";
        case "repair_title" -> "报修进度更新";
        case "repair_content" -> "报修单%s状态已更新为%s。";
        case "complaint_title" -> "投诉进度更新";
        case "complaint_content" -> "投诉单%s状态已更新为%s。";
        case "notice_title" -> "新公告通知";
        case "notice_content" -> "您有新的公告: %s";
        case "sign_verify_title" -> "实名认证通过";
        case "sign_verify_content" -> "合同%s已可进入签署流程。";
        case "sign_success_title" -> "电子签署成功";
        case "sign_success_content" -> "合同%s已完成电子签署。";
        case "sign_retry_title" -> "签署需重试";
        case "sign_retry_content" -> "合同%s签署失败或超时，请重新签署。";
        default -> key;
      };
    }
    try {
      return String.format(template, args);
    } catch (Exception e) {
      return template;
    }
  }

  private Map<String, Object> buildRoomInfo(Long roomId) {
    Room room = roomRepository.findById(roomId).orElse(null);
    if (room == null) {
      return Map.of("roomId", roomId, "roomText", "-");
    }
    Building building = room.getBuildingId() == null ? null
        : buildingRepository.findById(room.getBuildingId()).orElse(null);
    String roomText = (building == null ? "" : building.getName())
        + (room.getRoomNumber() == null ? "" : room.getRoomNumber());
    return Map.of(
        "roomId", room.getId(),
        "roomNumber", room.getRoomNumber() == null ? "-" : room.getRoomNumber(),
        "buildingName", building == null ? "-" : building.getName(),
        "roomText", roomText.isBlank() ? "-" : roomText);
  }

  private String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }

  private String maskIdCard(String idCard) {
    if (idCard == null || idCard.length() < 8) {
      return idCard == null ? "" : idCard;
    }
    return idCard.substring(0, 4) + "****" + idCard.substring(idCard.length() - 4);
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
