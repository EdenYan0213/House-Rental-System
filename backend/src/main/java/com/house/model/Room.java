package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "building_id")
  private Long buildingId;

  private Integer floor;

  @Column(name = "room_number")
  private String roomNumber;

  private String layout; // 户型

  private Double area;

  private String status; // 空闲/已租/维修中

  @Column(name = "water_meter_id")
  private Long waterMeterId;

  @Column(name = "electric_meter_id")
  private Long electricMeterId;

  /**
   * 图片 ID 列表（JSON 数组字符串，如 "[1,2,3]"）。
   * 图片二进制存 image_blob 表，这里只存引用。
   */
  @Column(name = "image_ids")
  private String imageIds;

  /** 对外输出：图片 ID 数组，前端据此拼接 /api/images/{id}。 */
  @Transient
  public java.util.List<Long> getImageIdList() {
    java.util.List<Long> list = new java.util.ArrayList<>();
    if (imageIds == null || imageIds.isBlank()) {
      return list;
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      Long[] arr = mapper.readValue(imageIds, Long[].class);
      for (Long id : arr) {
        if (id != null && !list.contains(id)) {
          list.add(id);
        }
      }
    } catch (Exception ignore) {
      // 解析失败返回空列表
    }
    return list;
  }


  private Integer deleted;

  public void setId(Long id) { this.id = id; }
  public Long getId() { return id; }
  public void setBuildingId(Long buildingId) { this.buildingId = buildingId; }
  public Long getBuildingId() { return buildingId; }
  public void setFloor(Integer floor) { this.floor = floor; }
  public Integer getFloor() { return floor; }
  public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
  public String getRoomNumber() { return roomNumber; }
  public void setLayout(String layout) { this.layout = layout; }
  public String getLayout() { return layout; }
  public void setArea(Double area) { this.area = area; }
  public Double getArea() { return area; }
  public void setStatus(String status) { this.status = status; }
  public String getStatus() { return status; }
  public void setWaterMeterId(Long waterMeterId) { this.waterMeterId = waterMeterId; }
  public Long getWaterMeterId() { return waterMeterId; }
  public void setElectricMeterId(Long electricMeterId) { this.electricMeterId = electricMeterId; }
  public Long getElectricMeterId() { return electricMeterId; }
  public void setDeleted(Integer deleted) { this.deleted = deleted; }
  public Integer getDeleted() { return deleted; }

  @PrePersist
  void prePersist() {
    if (deleted == null) {
      deleted = 0;
    }
    status = RoomStatus.normalize(status);
  }
}
