package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String code;
  private String address;

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


  private Integer totalFloors;

  private Double waterUnitPrice;

  private Double electricUnitPrice;
}
