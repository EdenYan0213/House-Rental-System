package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long contractId;
  private String type; // 收取/退还
  private Double amount;
  private LocalDateTime date;
  private String remark;

  // 列表展示用的关联信息，查询时填充，不落库
  @Transient
  private String contractNo;
  @Transient
  private String tenantName;
  @Transient
  private String roomNumber;
}
