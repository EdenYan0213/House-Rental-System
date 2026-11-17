package com.house.controller;

import com.house.model.Building;
import com.house.model.OperationSnapshot;
import com.house.repo.BuildingRepository;
import com.house.repo.RoomRepository;
import com.house.service.OperationSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

  private final BuildingRepository buildingRepository;
  private final RoomRepository roomRepository;
  private final OperationSnapshotService operationSnapshotService;

  public BuildingController(BuildingRepository buildingRepository,
      RoomRepository roomRepository,
      OperationSnapshotService operationSnapshotService) {
    this.buildingRepository = buildingRepository;
    this.roomRepository = roomRepository;
    this.operationSnapshotService = operationSnapshotService;
  }

  /**
   * 楼栋列表。不传 page 返回纯数组（兼容现有调用），传了 page 返回分页对象。
   */
  @GetMapping
  public Object list(@RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "50") int size) {
    List<Building> all = buildingRepository.findAll();
    if (page == null) {
      return all;
    }
    int total = all.size();
    int safeSize = size <= 0 ? 50 : size;
    int from = Math.max(page, 0) * safeSize;
    int to = Math.min(total, from + safeSize);
    List<Building> content = from >= total ? List.of() : all.subList(from, to);

    Map<String, Object> result = new HashMap<>();
    result.put("content", content);
    result.put("totalElements", total);
    result.put("page", page);
    result.put("size", safeSize);
    return result;
  }

  @GetMapping("/{id}")
  public ResponseEntity<Building> get(@PathVariable Long id) {
    return buildingRepository.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public Building create(@RequestBody Building building) {
    return buildingRepository.save(building);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Building> update(@PathVariable Long id, @RequestBody Building building) {
    return buildingRepository.findById(id).map(b -> {
      b.setName(building.getName());
      b.setCode(building.getCode());
      b.setAddress(building.getAddress());
      b.setImageUrl(building.getImageUrl());
      if (building.getImageUrls() != null) {
        b.setImageUrls(building.getImageUrls());
      }
      b.setTotalFloors(building.getTotalFloors());
      b.setWaterUnitPrice(building.getWaterUnitPrice());
      b.setElectricUnitPrice(building.getElectricUnitPrice());
      buildingRepository.save(b);
      return ResponseEntity.ok(b);
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    Building before = buildingRepository.findById(id).orElse(null);
    if (before == null)
      return ResponseEntity.notFound().build();
    if (!roomRepository.findByBuildingId(id).isEmpty()) {
      return ResponseEntity.badRequest().body("该楼栋下仍有房间，无法删除。请先删除或迁移房间。");
    }
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "房屋管理", "delete", "building", id, before.getName(), before);
    buildingRepository.deleteById(id);
    return ResponseEntity.ok(java.util.Map.of(
        "undoSnapshotId", snapshot.getId(),
        "undoExpireAt", snapshot.getExpiredAt(),
        "undoTip", "操作成功，可在30分钟内撤销"));
  }
}
