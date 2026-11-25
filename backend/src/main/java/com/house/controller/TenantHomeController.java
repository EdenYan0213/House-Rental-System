package com.house.controller;

import com.house.model.Bill;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.Notice;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.model.SystemMessage;
import com.house.model.Tenant;
import com.house.repo.BillRepository;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.NoticeRepository;
import com.house.repo.PaymentRecordRepository;
import com.house.repo.RoomRepository;
import com.house.repo.SystemMessageRepository;
import com.house.service.TenantTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 租客端首页与看房：首页概览、空置房间列表、租房沟通申请。
 *
 * <p>从原 {@code TenantPortalController} 拆出。路径前缀仍为 {@code /api/tenant}。
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantHomeController {

  private final TenantTokenService tenantTokenService;
  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final BillRepository billRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final NoticeRepository noticeRepository;
  private final SystemMessageRepository systemMessageRepository;
  private final TenantPortalSupport support;

  public TenantHomeController(TenantTokenService tenantTokenService,
      ContractRepository contractRepository,
      RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      BillRepository billRepository,
      PaymentRecordRepository paymentRecordRepository,
      NoticeRepository noticeRepository,
      SystemMessageRepository systemMessageRepository,
      TenantPortalSupport support) {
    this.tenantTokenService = tenantTokenService;
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.billRepository = billRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.noticeRepository = noticeRepository;
    this.systemMessageRepository = systemMessageRepository;
    this.support = support;
  }

  @GetMapping("/home")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> home(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");

    if (Boolean.TRUE.equals(context.get("isProspect"))) {
      Map<String, Object> prospect = new LinkedHashMap<>();
      prospect.put("tenantName", tenant.getName());
      prospect.put("currentRoom", Map.of());
      prospect.put("unpaidCount", 0);
      prospect.put("unpaidAmount", 0);
      prospect.put("latestNotice", null);
      prospect.put("isProspect", true);
      return ResponseEntity.ok(prospect);
    }

    List<Contract> roomContracts = contractRepository.findByTenantIdAndRoomId(tenant.getId(), roomId);
    List<Long> contractIds = roomContracts.stream().map(Contract::getId).toList();
    List<Bill> bills = contractIds.isEmpty() ? List.of() : billRepository.findByContractIdIn(contractIds);

    int unpaidCount = 0;
    double unpaidAmount = 0D;
    for (Bill bill : bills) {
      double paid = sumPaid(bill.getId());
      double total = bill.getTotalAmount() == null ? 0D : bill.getTotalAmount();
      if (total - paid > 0.0001) {
        unpaidCount++;
        unpaidAmount += (total - paid);
      }
    }

    Notice latest = noticeRepository.findTopByOrderBySortDescCreatedAtDesc().orElse(null);
    Map<String, Object> latestNotice = null;
    if (latest != null) {
      // 公告的 title / createdAt 历史数据可能为空，Map.of 不接受 null
      latestNotice = new LinkedHashMap<>();
      latestNotice.put("id", latest.getId());
      latestNotice.put("title", latest.getTitle());
      latestNotice.put("createdAt", latest.getCreatedAt());
    }

    // 注意：Map.of 不接受 null 值，无公告时会抛 NPE，这里用 LinkedHashMap
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantName", tenant.getName());
    body.put("currentRoom", support.buildRoomInfo(roomId));
    body.put("unpaidCount", unpaidCount);
    body.put("unpaidAmount", support.round(unpaidAmount));
    body.put("latestNotice", latestNotice);
    return ResponseEntity.ok(body);
  }

  @GetMapping("/vacant-rooms")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> vacantRooms(@RequestHeader("Authorization") String authHeader) {
    tenantTokenService.parseAndValidateTenant(authHeader);

    Map<Long, Building> buildingMap = buildingRepository.findAll().stream()
        .collect(java.util.stream.Collectors.toMap(Building::getId, b -> b, (a, b) -> a));

    // 先筛出空闲房间，再一次性查出这些房间的生效合同，
    // 避免在循环里逐间查库（原实现是 N+1）。
    List<Room> idleRooms = roomRepository.findAll().stream()
        .filter(r -> RoomStatus.isIdle(r.getStatus()))
        .toList();
    Set<Long> roomsWithActiveContract = idleRooms.isEmpty()
        ? Set.of()
        : contractRepository.findByRoomIdInAndStatus(
                idleRooms.stream().map(Room::getId).toList(), "ACTIVE")
            .stream()
            .map(Contract::getRoomId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

    List<Map<String, Object>> payload = new ArrayList<>();
    for (Room room : idleRooms) {
      // 空闲但仍有生效合同的房间不算可租
      if (roomsWithActiveContract.contains(room.getId())) {
        continue;
      }
      Building building = buildingMap.get(room.getBuildingId());
      payload.add(Map.of(
          "roomId", room.getId(),
          "buildingName", building == null ? "-" : building.getName(),
          "roomNumber", room.getRoomNumber() == null ? "-" : room.getRoomNumber(),
          "floor", room.getFloor() == null ? "" : room.getFloor(),
          "layout", room.getLayout() == null ? "" : room.getLayout(),
          "area", room.getArea() == null ? "" : room.getArea(),
          // 图片统一存 image_blob，房间只持有 ID；imageUrl 字段已废弃不再返回
          "imageIds", room.getImageIdList()));
    }
    return ResponseEntity.ok(payload);
  }

  @PostMapping("/rent-intent")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> rentIntent(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = support.parseLong(body.get("roomId"));
    String message = support.text(body.get("message"));
    String contactPhone = support.normalizePhone(support.text(body.get("contactPhone")));

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
        + "\n手机号: " + (contactPhone.isBlank() ? support.maskPhone(tenant.getPhone()) : contactPhone)
        + "\n意向房间: " + (room.getRoomNumber() == null ? "-" : room.getRoomNumber())
        + "\n留言: " + message);
    msg.setRelatedId(tenant.getId());
    msg.setIsRead(false);
    msg.setCreatedAt(LocalDateTime.now());
    systemMessageRepository.save(msg);

    return ResponseEntity.ok(Map.of("success", true, "message", "已提交沟通申请，管理员会尽快联系您"));
  }

  private double sumPaid(Long billId) {
    return paymentRecordRepository.findByBillId(billId).stream()
        .mapToDouble(item -> item.getPaidAmount() == null ? 0D : item.getPaidAmount())
        .sum();
  }
}
