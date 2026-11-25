package com.house.service;

import com.house.model.AdminBuildingScope;
import com.house.model.ComplaintSuggestion;
import com.house.model.Role;
import com.house.model.Room;
import com.house.model.User;
import com.house.repo.AdminBuildingScopeRepository;
import com.house.repo.RoomRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 投诉查询的可见范围与筛选条件。
 *
 * <p>从 728 行的 {@code ComplaintService} 抽出。原 `search` 与 `stats`
 * 各自复制了约 60 行「按楼栋范围过滤 + 按条件过滤」的逻辑，任何一侧改动
 * 都会造成两个接口口径不一致，这里收敛成单一实现。
 *
 * <p>楼栋范围语义：
 * <ul>
 *   <li>{@code null}  —— 不受限（SUPER_ADMIN，或 ADMIN 未配置任何范围）</li>
 *   <li>空集合 —— 无任何可见楼栋，调用方应直接返回空结果</li>
 *   <li>非空集合 —— 只能看这些楼栋</li>
 * </ul>
 */
@Component
public class ComplaintScope {

  private final RoomRepository roomRepository;
  private final AdminBuildingScopeRepository adminBuildingScopeRepository;

  public ComplaintScope(RoomRepository roomRepository,
      AdminBuildingScopeRepository adminBuildingScopeRepository) {
    this.roomRepository = roomRepository;
    this.adminBuildingScopeRepository = adminBuildingScopeRepository;
  }

  /**
   * 当前用户的可见楼栋。
   *
   * @return null 表示不限；空集合表示无权限
   */
  public Set<Long> resolveScopedBuildingIds(User currentUser) {
    if (currentUser == null || currentUser.getRole() == null) {
      return Set.of();
    }
    if (currentUser.getRole() == Role.SUPER_ADMIN) {
      return null;
    }
    if (currentUser.getRole() != Role.ADMIN) {
      return Set.of();
    }
    Set<Long> ids = adminBuildingScopeRepository.findByAdminId(currentUser.getId()).stream()
        .map(AdminBuildingScope::getBuildingId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    // 兼容历史数据：ADMIN 没有任何范围记录时视为不受限
    return ids.isEmpty() ? null : ids;
  }

  /** 该楼栋下的房间 ID；楼栋无房间时返回空列表。 */
  public List<Long> roomIdsOfBuilding(Long buildingId) {
    return roomRepository.findByBuildingId(buildingId).stream().map(Room::getId).toList();
  }

  /** 范围内的全部房间 ID（一次查询，不再 findAll 后内存过滤）。 */
  public List<Long> roomIdsInScope(Set<Long> scopedBuildingIds) {
    if (scopedBuildingIds == null) {
      return List.of();
    }
    List<Long> roomIds = new java.util.ArrayList<>();
    for (Long buildingId : scopedBuildingIds) {
      roomIds.addAll(roomIdsOfBuilding(buildingId));
    }
    return roomIds;
  }

  /**
   * 构建投诉查询条件。
   *
   * @param scopedBuildingIds 可见楼栋；null 表示不限
   * @return null 表示结果必定为空（无可见楼栋 / 指定楼栋不在范围内）
   */
  public Specification<ComplaintSuggestion> buildSpec(Set<Long> scopedBuildingIds,
      String status,
      String type,
      String priority,
      String startDate,
      String endDate,
      Long buildingId) {
    Specification<ComplaintSuggestion> spec = (root, query, cb) -> cb.conjunction();

    // 1) 楼栋范围
    if (scopedBuildingIds != null) {
      if (scopedBuildingIds.isEmpty()) {
        return null;
      }
      List<Long> scopedRoomIds = roomIdsInScope(scopedBuildingIds);
      if (scopedRoomIds.isEmpty()) {
        return null;
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(scopedRoomIds));
    }

    // 2) 精确匹配条件
    if (status != null && !status.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (type != null && !type.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
    }
    if (priority != null && !priority.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
    }

    // 3) 时间区间（含首尾日）
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
    }

    // 4) 指定楼栋（必须同时在可见范围内）
    if (buildingId != null) {
      if (scopedBuildingIds != null && !scopedBuildingIds.contains(buildingId)) {
        return null;
      }
      List<Long> roomIds = roomIdsOfBuilding(buildingId);
      if (roomIds.isEmpty()) {
        return null;
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(roomIds));
    }

    return spec;
  }
}
