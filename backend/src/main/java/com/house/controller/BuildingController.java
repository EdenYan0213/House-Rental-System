package com.house.controller;

import com.house.model.Building;
import com.house.model.OperationSnapshot;
import com.house.repo.BuildingRepository;
import com.house.repo.RoomRepository;
import com.house.service.ImageStorageService;
import com.house.service.OperationSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

  private static final Logger log = LoggerFactory.getLogger(BuildingController.class);

  private final BuildingRepository buildingRepository;
  private final RoomRepository roomRepository;
  private final OperationSnapshotService operationSnapshotService;
  private final ImageStorageService imageStorageService;

  public BuildingController(BuildingRepository buildingRepository,
      RoomRepository roomRepository,
      OperationSnapshotService operationSnapshotService,
      ImageStorageService imageStorageService) {
    this.buildingRepository = buildingRepository;
    this.roomRepository = roomRepository;
    this.operationSnapshotService = operationSnapshotService;
    this.imageStorageService = imageStorageService;
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
    Building saved = buildingRepository.save(building);
    markBuildingImagesReferenced(saved);
    return saved;
  }

  @PutMapping("/{id}")
  public ResponseEntity<Building> update(@PathVariable Long id, @RequestBody Building building) {
    return buildingRepository.findById(id).map(b -> {
      b.setName(building.getName());
      b.setCode(building.getCode());
      b.setAddress(building.getAddress());
      // imageUrl 已废弃：不再接受前端写入，图片统一走 imageIds
      if (building.getImageIds() != null) {
        b.setImageIds(building.getImageIds());
      }
      b.setTotalFloors(building.getTotalFloors());
      b.setWaterUnitPrice(building.getWaterUnitPrice());
      b.setElectricUnitPrice(building.getElectricUnitPrice());
      Building saved = buildingRepository.save(b);
      // 图片上传时 refCount=0，必须在这里标记已引用，
      // 否则定时清理任务会把仍在使用中的图片删掉（历史 bug）。
      markBuildingImagesReferenced(saved);
      return ResponseEntity.ok(saved);
    }).orElse(ResponseEntity.notFound().build());
  }

  /** 把楼栋引用的图片标记为已引用，避免被孤儿清理任务删除。 */
  private void markBuildingImagesReferenced(Building building) {
    if (building == null) {
      return;
    }
    try {
      List<Long> ids = building.getImageIdList();
      if (ids != null && !ids.isEmpty()) {
        imageStorageService.markReferenced(ids);
      }
    } catch (Exception e) {
      log.warn("标记楼栋图片引用失败 buildingId={}: {}", building.getId(), e.getMessage());
    }
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
    return ResponseEntity.ok(UndoResponse.of(snapshot));
  }
}
