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

  private Integer totalFloors;

  private Double waterUnitPrice;

  private Double electricUnitPrice;
}
