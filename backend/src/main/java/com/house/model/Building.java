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

  @Column(name = "image_url")
  private String imageUrl;

  /**
   * 全部图片（JSON 数组字符串）。imageUrl 作为封面保留，
   * 前端用 getImageUrls() 拿到合并后的列表做轮播。
   */
  @Column(name = "image_urls", columnDefinition = "text")
  @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
  private String imageUrls;

  /** 对外输出：优先用 image_urls，为空时回退到单张 imageUrl。 */
  @jakarta.persistence.Transient
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


  private Integer totalFloors;

  private Double waterUnitPrice;

  private Double electricUnitPrice;
}
