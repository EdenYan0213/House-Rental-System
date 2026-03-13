package com.house.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.RoomAppliance;
import com.house.repo.RoomApplianceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class RoomApplianceService {
  private final RoomApplianceRepository roomApplianceRepository;
  private final ObjectMapper objectMapper;

  public RoomApplianceService(RoomApplianceRepository roomApplianceRepository, ObjectMapper objectMapper) {
    this.roomApplianceRepository = roomApplianceRepository;
    this.objectMapper = objectMapper;
  }

  public List<RoomAppliance> listByRoomId(Long roomId) {
    return roomApplianceRepository.findByRoomIdOrderByIdDesc(roomId);
  }

  public RoomAppliance save(RoomAppliance appliance) {
    LocalDateTime now = LocalDateTime.now();
    if (appliance.getCreatedAt() == null) {
      appliance.setCreatedAt(now);
    }
    appliance.setUpdatedAt(now);
    if (appliance.getStatus() == null || appliance.getStatus().isBlank()) {
      appliance.setStatus("正常");
    }
    if (appliance.getQuantity() == null || appliance.getQuantity() < 1) {
      appliance.setQuantity(1);
    }
    if (appliance.getUnit() == null || appliance.getUnit().isBlank()) {
      appliance.setUnit("个");
    }
    return roomApplianceRepository.save(appliance);
  }

  public void delete(Long id) {
    roomApplianceRepository.deleteById(id);
  }

  public String toJson(List<String> photos) {
    try {
      if (photos == null) {
        return "[]";
      }
      return objectMapper.writeValueAsString(photos);
    } catch (Exception e) {
      return "[]";
    }
  }

  public List<String> parseJson(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Collections.emptyList();
      }
      return objectMapper.readValue(json, new TypeReference<List<String>>() {
      });
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}
