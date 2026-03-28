package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "handler")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Handler {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String role;

  private String phone;

  private Integer status;
}
