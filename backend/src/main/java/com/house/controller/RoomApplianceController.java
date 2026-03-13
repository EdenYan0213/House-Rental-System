package com.house.controller;

import com.house.model.ApplianceTemplate;
import com.house.model.RoomAppliance;
import com.house.repo.ApplianceTemplateRepository;
import com.house.repo.RoomApplianceRepository;
import com.house.service.RoomApplianceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/room-appliances")
public class RoomApplianceController {
  private final RoomApplianceService roomApplianceService;
  private final RoomApplianceRepository roomApplianceRepository;
  private final ApplianceTemplateRepository applianceTemplateRepository;

  public RoomApplianceController(RoomApplianceService roomApplianceService,
      RoomApplianceRepository roomApplianceRepository,
      ApplianceTemplateRepository applianceTemplateRepository) {
    this.roomApplianceService = roomApplianceService;
    this.roomApplianceRepository = roomApplianceRepository;
    this.applianceTemplateRepository = applianceTemplateRepository;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam Long roomId) {
    return roomApplianceService.listByRoomId(roomId).stream().map(this::toPayload).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    RoomAppliance appliance = fromBody(body, null);
    return toPayload(roomApplianceService.save(appliance));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return roomApplianceRepository.findById(id).map(exists -> {
      RoomAppliance appliance = fromBody(body, exists);
      appliance.setId(id);
      return ResponseEntity.ok(toPayload(roomApplianceService.save(appliance)));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    roomApplianceService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/batch-from-template")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> batchFromTemplate(@RequestBody Map<String, Object> body) {
    if (body.get("roomId") == null || body.get("templateIds") == null) {
      return ResponseEntity.badRequest().body("参数不完整");
    }
    Long roomId = ((Number) body.get("roomId")).longValue();
    List<?> rawTemplateIds = (List<?>) body.get("templateIds");
    if (rawTemplateIds.isEmpty()) {
      return ResponseEntity.badRequest().body("请至少选择一个模板");
    }
    List<Long> templateIds = rawTemplateIds.stream().map(v -> ((Number) v).longValue()).toList();
    List<ApplianceTemplate> templates = applianceTemplateRepository.findAllById(templateIds);

    List<Map<String, Object>> created = new ArrayList<>();
    for (ApplianceTemplate t : templates) {
      RoomAppliance appliance = RoomAppliance.builder()
          .roomId(roomId)
          .applianceName(t.getName())
          .brand(t.getBrand())
          .quantity(t.getDefaultQuantity() == null ? 1 : t.getDefaultQuantity())
          .unit(t.getUnit() == null ? "个" : t.getUnit())
          .status("正常")
          .photos("[]")
          .remark("")
          .build();
      created.add(toPayload(roomApplianceService.save(appliance)));
    }
    return ResponseEntity.ok(Map.of("createdCount", created.size(), "created", created));
  }

  private RoomAppliance fromBody(Map<String, Object> body, RoomAppliance existing) {
    RoomAppliance appliance = existing == null ? new RoomAppliance() : existing;
    if (body.get("roomId") != null) {
      appliance.setRoomId(((Number) body.get("roomId")).longValue());
    }
    if (body.get("applianceName") != null) {
      appliance.setApplianceName(body.get("applianceName").toString());
    }
    if (body.get("brand") != null) {
      appliance.setBrand(body.get("brand").toString());
    }
    if (body.get("quantity") != null) {
      appliance.setQuantity(((Number) body.get("quantity")).intValue());
    }
    if (body.get("unit") != null) {
      appliance.setUnit(body.get("unit").toString());
    }
    if (body.get("status") != null) {
      appliance.setStatus(body.get("status").toString());
    }
    if (body.get("remark") != null) {
      appliance.setRemark(body.get("remark").toString());
    }
    if (body.get("photos") instanceof List<?> photos) {
      List<String> items = photos.stream().map(Object::toString).toList();
      appliance.setPhotos(roomApplianceService.toJson(items));
    }
    return appliance;
  }

  private Map<String, Object> toPayload(RoomAppliance appliance) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", appliance.getId());
    row.put("roomId", appliance.getRoomId());
    row.put("applianceName", appliance.getApplianceName());
    row.put("brand", appliance.getBrand());
    row.put("quantity", appliance.getQuantity());
    row.put("unit", appliance.getUnit());
    row.put("status", appliance.getStatus());
    row.put("remark", appliance.getRemark());
    row.put("photos", roomApplianceService.parseJson(appliance.getPhotos()));
    row.put("createdAt", appliance.getCreatedAt());
    row.put("updatedAt", appliance.getUpdatedAt());
    return row;
  }
}
