package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_appliance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAppliance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "appliance_name")
  private String applianceName;

  private String brand;

  private Integer quantity;

  private String unit;

  private String status;

  private String remark;

  @Column(columnDefinition = "text")
  private String photos;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
