package com.house.controller;

import com.house.model.Building;
import com.house.model.OperationSnapshot;
import com.house.repo.BuildingRepository;
import com.house.repo.RoomRepository;
import com.house.service.OperationSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

  @GetMapping
  public List<Building> list() {
    return buildingRepository.findAll();
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
