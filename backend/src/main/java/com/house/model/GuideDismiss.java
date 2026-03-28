package com.house.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "guide_dismiss")
public class GuideDismiss {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "dismiss_key", nullable = false, length = 100)
  private String dismissKey;

  @Column(name = "dismissed_at", nullable = false)
  private LocalDateTime dismissedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getDismissKey() {
    return dismissKey;
  }

  public void setDismissKey(String dismissKey) {
    this.dismissKey = dismissKey;
  }

  public LocalDateTime getDismissedAt() {
    return dismissedAt;
  }

  public void setDismissedAt(LocalDateTime dismissedAt) {
    this.dismissedAt = dismissedAt;
  }
}
