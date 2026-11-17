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

  /**
   * 全部图片（JSON 数组字符串）。imageUrl 作为封面保留，
   * 前端用 getImageList() 拿到合并后的列表做轮播。
   */
  @Column(name = "image_urls", columnDefinition = "text")
  @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
  private String imageUrls;

  /** 对外输出：优先用 image_urls，为空时回退到单张 imageUrl。 */
  @Transient
  public java.util.List<String> getImageList() {
    java.util.List<String> list = new java.util.ArrayList<>();
    if (imageUrls != null && !imageUrls.isBlank()) {
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        String[] arr = mapper.readValue(imageUrls, String[].class);
        for (String u : arr) {
          if (u != null && !u.isBlank() && !list.contains(u)) {
            list.add(u);
          }
        }
      } catch (Exception ignore) {
        // 解析失败时退回封面图
      }
    }
    if (list.isEmpty() && imageUrl != null && !imageUrl.isBlank()) {
      list.add(imageUrl);
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
