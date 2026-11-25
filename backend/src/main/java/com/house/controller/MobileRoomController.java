package com.house.controller;

import com.house.model.*;
import com.house.repo.*;
import com.house.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/mobile")
public class MobileRoomController {

  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final ContractRepository contractRepository;
  private final TenantRepository tenantRepository;
  private final BillRepository billRepository;
  private final RepairOrderRepository repairOrderRepository;
  private final RoomApplianceRepository roomApplianceRepository;
  private final UserService userService;

  public MobileRoomController(RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      ContractRepository contractRepository,
      TenantRepository tenantRepository,
      BillRepository billRepository,
      RepairOrderRepository repairOrderRepository,
      RoomApplianceRepository roomApplianceRepository,
      UserService userService) {
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.contractRepository = contractRepository;
    this.tenantRepository = tenantRepository;
    this.billRepository = billRepository;
    this.repairOrderRepository = repairOrderRepository;
    this.roomApplianceRepository = roomApplianceRepository;
    this.userService = userService;
  }

  @GetMapping("/room/{roomId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
  public ResponseEntity<?> roomSummary(@PathVariable Long roomId, @AuthenticationPrincipal UserDetails userDetails) {
    Room room = roomRepository.findById(roomId).orElse(null);
    if (room == null) {
      return ResponseEntity.notFound().build();
    }

    User user = userService.findByUsername(userDetails.getUsername());
    String role = user == null || user.getRole() == null ? "USER" : user.getRole().name();
    boolean canOperate = "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);

    Building building = buildingRepository.findById(room.getBuildingId()).orElse(null);

    Contract activeContract = contractRepository.findByRoomIdAndStatus(roomId, "ACTIVE")
        .stream()
        .findFirst()
        .orElseGet(() -> contractRepository.findByRoomIdAndStatus(roomId, "进行中").stream().findFirst().orElse(null));

    Tenant tenant = null;
    if (activeContract != null && activeContract.getTenantId() != null) {
      tenant = tenantRepository.findById(activeContract.getTenantId()).orElse(null);
    }

    List<Bill> unpaidBills = List.of();
    Double unpaidAmount = 0D;
    if (activeContract != null) {
      unpaidBills = billRepository.findByContractId(activeContract.getId()).stream()
          .filter(this::isUnpaid)
          .sorted(Comparator.comparing(Bill::getGeneratedDate, Comparator.nullsLast(Comparator.reverseOrder())))
          .toList();
      unpaidAmount = unpaidBills.stream().map(b -> b.getTotalAmount() == null ? 0D : b.getTotalAmount()).reduce(0D,
          Double::sum);
    }

    List<RepairOrder> recentRepairs = repairOrderRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    if (recentRepairs.size() > 3) {
      recentRepairs = recentRepairs.subList(0, 3);
    }

    List<RoomAppliance> appliances = roomApplianceRepository.findByRoomIdOrderByIdDesc(roomId);

    Map<String, Object> roomData = new LinkedHashMap<>();
    roomData.put("id", room.getId());
    roomData.put("roomNumber", room.getRoomNumber());
    roomData.put("status", room.getStatus());
    roomData.put("floor", room.getFloor());
    roomData.put("layout", room.getLayout());
    roomData.put("area", room.getArea());
    roomData.put("buildingId", room.getBuildingId());
    roomData.put("buildingName", building == null ? "-" : building.getName());

    Map<String, Object> tenantData = null;
    if (tenant != null) {
      tenantData = Map.of(
          "id", tenant.getId(),
          "name", tenant.getName() == null ? "" : tenant.getName(),
          "phone", tenant.getPhone() == null ? "" : tenant.getPhone());
    }

    Map<String, Object> contractData = null;
    if (activeContract != null) {
      contractData = Map.of(
          "id", activeContract.getId(),
          "startDate", activeContract.getStartDate(),
          "endDate", activeContract.getEndDate(),
          "monthlyRent", activeContract.getMonthlyRent() == null ? 0D : activeContract.getMonthlyRent());
    }

    // tenantData / contractData 在房间空闲时为 null，
    // Map.of 不接受 null（会抛 NPE），必须用 LinkedHashMap。
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("room", roomData);
    body.put("tenant", tenantData);
    body.put("contract", contractData);
    body.put("unpaidAmount", unpaidAmount);
    body.put("unpaidBills", unpaidBills);
    body.put("repairHistory", recentRepairs);
    body.put("appliances", appliances);
    body.put("canOperate", canOperate);
    body.put("role", role);
    return ResponseEntity.ok(body);
  }

  private boolean isUnpaid(Bill bill) {
    if (bill.getStatus() == null) {
      return true;
    }
    String status = bill.getStatus().toUpperCase();
    return !("PAID".equals(status)
        || "已支付".equals(bill.getStatus())
        || "VOID".equals(status)
        || "作废".equals(bill.getStatus())
        || "OBSOLETE".equals(status));
  }
}
