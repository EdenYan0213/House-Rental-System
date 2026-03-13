package com.house.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_message")
public class SystemMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "type", length = 50, nullable = false)
  private String type;

  @Column(name = "title", length = 200, nullable = false)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "related_id")
  private Long relatedId;

  @Column(name = "is_read")
  private Boolean isRead;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  public SystemMessage() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public Long getRelatedId() { return relatedId; }
  public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }
  public Boolean getIsRead() { return isRead; }
  public void setIsRead(Boolean isRead) { this.isRead = isRead; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getReadAt() { return readAt; }
  public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
