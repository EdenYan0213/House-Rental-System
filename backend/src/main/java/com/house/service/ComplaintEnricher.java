package com.house.service;

import com.house.model.Building;
import com.house.model.ComplaintLog;
import com.house.model.ComplaintSuggestion;
import com.house.model.Handler;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.model.User;
import com.house.repo.BuildingRepository;
import com.house.repo.ComplaintLogRepository;
import com.house.repo.HandlerRepository;
import com.house.repo.RoomRepository;
import com.house.repo.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 投诉的展示字段填充。
 *
 * <p>从 728 行的 {@code ComplaintService} 抽出。原实现里
 * {@code fillDisplayFields} 对每条投诉都单独查 room / building / tenant，
 * 列表 50 条就是 150 次查询（典型 N+1）。
 *
 * <p>这里提供两个入口：
 * <ul>
 *   <li>{@link #fill(List)} —— 批量填充，先一次性取出所有关联实体再回填</li>
 *   <li>{@link #fill(ComplaintSuggestion)} —— 单条填充，内部复用批量逻辑</li>
 * </ul>
 */
@Component
public class ComplaintEnricher {

  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final TenantRepository tenantRepository;
  private final HandlerRepository handlerRepository;
  private final ComplaintLogRepository complaintLogRepository;
  private final UserService userService;

  public ComplaintEnricher(RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      TenantRepository tenantRepository,
      HandlerRepository handlerRepository,
      ComplaintLogRepository complaintLogRepository,
      UserService userService) {
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.tenantRepository = tenantRepository;
    this.handlerRepository = handlerRepository;
    this.complaintLogRepository = complaintLogRepository;
    this.userService = userService;
  }

  /** 批量填充：房间、楼栋、租户各自只查一次。 */
  public void fill(List<ComplaintSuggestion> items) {
    if (items == null || items.isEmpty()) {
      return;
    }

    // 1) 收集所有需要的 ID
    List<Long> roomIds = items.stream()
        .map(ComplaintSuggestion::getRoomId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    List<Long> tenantIds = items.stream()
        .map(ComplaintSuggestion::getTenantId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    // 2) 一次性取出房间，再取房间涉及的楼栋
    Map<Long, Room> roomMap = new HashMap<>();
    if (!roomIds.isEmpty()) {
      for (Room room : roomRepository.findAllById(roomIds)) {
        roomMap.put(room.getId(), room);
      }
    }
    List<Long> buildingIds = roomMap.values().stream()
        .map(Room::getBuildingId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<Long, Building> buildingMap = new HashMap<>();
    if (!buildingIds.isEmpty()) {
      for (Building building : buildingRepository.findAllById(buildingIds)) {
        buildingMap.put(building.getId(), building);
      }
    }

    Map<Long, Tenant> tenantMap = new HashMap<>();
    if (!tenantIds.isEmpty()) {
      for (Tenant tenant : tenantRepository.findAllById(tenantIds)) {
        tenantMap.put(tenant.getId(), tenant);
      }
    }

    // 3) 回填
    for (ComplaintSuggestion item : items) {
      Room room = roomMap.get(item.getRoomId());
      if (room != null) {
        item.setRoomNumber(room.getRoomNumber());
        item.setBuildingId(room.getBuildingId());
        Building building = buildingMap.get(room.getBuildingId());
        if (building != null) {
          item.setBuildingName(building.getName());
        }
      }

      Tenant tenant = tenantMap.get(item.getTenantId());
      if (tenant != null) {
        item.setTenantName(tenant.getName());
        item.setTenantPhone(maskPhone(tenant.getPhone()));
        item.setTenantDisplayName(Objects.equals(item.getIsAnonymous(), 1) ? "匿名" : tenant.getName());
      } else {
        item.setTenantDisplayName("-");
      }

      item.setAssignedName(resolveAssigneeName(item.getAssignedTo()));
    }
  }

  /** 单条填充。 */
  public void fill(ComplaintSuggestion item) {
    if (item == null) {
      return;
    }
    List<ComplaintSuggestion> single = new ArrayList<>();
    single.add(item);
    fill(single);
  }

  /** 操作日志（含操作人姓名）。 */
  public List<ComplaintLog> fillLogs(Long complaintId) {
    List<ComplaintLog> logs = complaintLogRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId);
    for (ComplaintLog log : logs) {
      log.setOperatorName(resolveOperatorName(log.getOperatorType(), log.getOperatorId()));
    }
    return logs;
  }

  /** 处理人姓名：优先维修工/处理人表，其次系统用户。 */
  public String resolveAssigneeName(Long assignedTo) {
    if (assignedTo == null) {
      return "";
    }
    Handler handler = handlerRepository.findById(assignedTo).orElse(null);
    if (handler != null && handler.getName() != null) {
      return handler.getName();
    }
    User user = userService.findById(assignedTo);
    if (user == null) {
      return "";
    }
    return displayNameOf(user);
  }

  /** 操作人姓名：system → 系统，查不到用户 → 管理员。 */
  public String resolveOperatorName(String operatorType, Long operatorId) {
    if ("system".equalsIgnoreCase(operatorType)) {
      return "系统";
    }
    if (operatorId == null) {
      return "管理员";
    }
    User user = userService.findById(operatorId);
    return user == null ? "管理员" : displayNameOf(user);
  }

  private String displayNameOf(User user) {
    if (user.getRealName() != null && !user.getRealName().isBlank()) {
      return user.getRealName();
    }
    return user.getUsername();
  }

  /** 手机号脱敏：138****8001。 */
  private String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone == null ? "" : phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }
}
