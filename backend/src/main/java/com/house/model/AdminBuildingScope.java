package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_building_scope")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminBuildingScope {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "admin_id")
  private Long adminId;

  @Column(name = "building_id")
  private Long buildingId;
}
