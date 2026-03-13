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

  private String name; // 基础房费/水费/电费/自定义
  private Double unitPrice; // 单价（元/单位），基础房费可为月单价
  private String unit; // 单位（如 元/月, 元/立方）
  private Boolean enabled;
}
