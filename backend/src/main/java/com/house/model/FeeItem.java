package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name; // 水费/电费/垃圾清运费等

  @Column(name = "unit_price")
  private Double unitPrice; // 单价（元/单位），基础房费可为月单价

  private String unit; // 单位（如 元/月, 元/吨）

  /**
   * 类别：WATER / ELECTRIC / BASE / OTHER。
   * 决定该费用项的取值优先级与作用范围，前端据此展示说明。
   */
  private String category;

  private Boolean enabled;

  @Column(name = "created_at")
  private java.time.LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (category == null || category.isBlank()) {
      // 未指定类别时按名称推断，保持历史数据可用
      String n = name == null ? "" : name;
      if (n.contains("水")) {
        category = "WATER";
      } else if (n.contains("电")) {
        category = "ELECTRIC";
      } else if (n.contains("房费") || n.contains("租金")) {
        category = "BASE";
      } else {
        category = "OTHER";
      }
    }
  }
}
