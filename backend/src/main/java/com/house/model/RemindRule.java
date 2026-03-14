package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemindRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "overdue_days")
  private Integer overdueDays;

  @Column(name = "send_time")
  private String sendTime; // HH:mm

  @Column(name = "sms_template", columnDefinition = "TEXT")
  private String smsTemplate;

  @Column(name = "is_enabled")
  private Integer isEnabled;

  @Column(name = "sort_order")
  private Integer sortOrder;
}
