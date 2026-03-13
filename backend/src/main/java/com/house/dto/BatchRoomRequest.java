package com.house.dto;

import lombok.Data;

@Data
public class BatchRoomRequest {
  private Long buildingId;
  private Integer floor;
  private Integer startNumber;
  private Integer endNumber;
  private String layout;
  private Double area;
  private String status; // 默认 空闲
}
