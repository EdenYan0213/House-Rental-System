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

  @Column(name = "image_url")
  private String imageUrl;

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
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
  public String getImageUrl() { return imageUrl; }
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
