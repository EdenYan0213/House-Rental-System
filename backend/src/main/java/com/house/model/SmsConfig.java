package com.house.model;

import jakarta.persistence.*;
import com.house.util.CryptoConverter;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsConfig {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private String provider; // aliyun

  @Column(name = "access_key")
  private String accessKey;

  @Column(name = "access_secret")
  @Convert(converter = CryptoConverter.class)
  private String accessSecret;

  private String signature;

  @Column(name = "template_code")
  private String templateCode;

  @Column(name = "debug_mode")
  private Integer debugMode;

  private Integer status;
}
