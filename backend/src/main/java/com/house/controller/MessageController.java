package com.house.controller;

import com.house.model.SystemMessage;
import com.house.model.ContractStatus;
import com.house.model.Tenant;
import com.house.model.Contract;
import com.house.model.Room;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import com.house.repo.SystemMessageRepository;
import com.house.repo.TenantRepository;
import com.house.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
  private final SystemMessageRepository messageRepository;
  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;
  private final OperationLogService operationLogService;

  public MessageController(SystemMessageRepository messageRepository,
      TenantRepository tenantRepository,
      ContractRepository contractRepository,
      RoomRepository roomRepository,
      OperationLogService operationLogService) {
    this.messageRepository = messageRepository;
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
    this.operationLogService = operationLogService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
  public Map<String, Object> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean isRead,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<SystemMessage> messages;
    if (type != null && !type.isBlank() && isRead != null) {
      messages = messageRepository.findByTypeAndIsRead(type, isRead, pageRequest);
    } else if (type != null && !type.isBlank()) {
      messages = messageRepository.findByType(type, pageRequest);
    } else if (isRead != null) {
      messages = messageRepository.findByIsRead(isRead, pageRequest);
    } else {
      messages = messageRepository.findAll(pageRequest);
    }
    long unreadCount = messageRepository.countByIsRead(false);
    return Map.of(
        "content", messages.getContent(),
        "totalElements", messages.getTotalElements(),
        "totalPages", messages.getTotalPages(),
        "unreadCount", unreadCount);
  }

  @GetMapping("/unread-count")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
  public long getUnreadCount() {
    return messageRepository.countByIsRead(false);
  }

  @PutMapping("/{id}/read")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
  public ResponseEntity<?> markAsRead(@PathVariable Long id) {
    return messageRepository.findById(id).map(msg -> {
      msg.setIsRead(true);
      msg.setReadAt(LocalDateTime.now());
      messageRepository.save(msg);
      return ResponseEntity.ok().build();
    }).orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/read-all")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
  public ResponseEntity<?> markAllAsRead() {
    messageRepository.findAll().stream()
        .filter(m -> !m.getIsRead())
        .forEach(m -> {
          m.setIsRead(true);
          m.setReadAt(LocalDateTime.now());
          messageRepository.save(m);
        });
    return ResponseEntity.ok().build();
  }

  @PostMapping("/batch-send")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> batchSend(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    List<?> tenantRaw = (List<?>) body.get("tenantIds");
    if (tenantRaw == null || tenantRaw.isEmpty()) {
      return ResponseEntity.badRequest().body("请选择租户");
    }
    String title = body.get("title") == null ? "系统通知" : body.get("title").toString();
    String content = body.get("content") == null ? "" : body.get("content").toString();
    String link = body.get("link") == null ? "" : body.get("link").toString();
    if (content.isBlank()) {
      return ResponseEntity.badRequest().body("通知内容不能为空");
    }

    List<Long> tenantIds = tenantRaw.stream().map(v -> ((Number) v).longValue()).toList();
    List<Tenant> tenants = tenantRepository.findAllById(tenantIds);
    List<SystemMessage> created = new ArrayList<>();

    for (Tenant tenant : tenants) {
      Contract active = contractRepository.findByTenantId(tenant.getId()).stream()
          .filter(c -> ContractStatus.isActive(c.getStatus()))
          .findFirst()
          .orElse(null);
      String roomNo = "-";
      if (active != null && active.getRoomId() != null) {
        Room room = roomRepository.findById(active.getRoomId()).orElse(null);
        if (room != null && room.getRoomNumber() != null) {
          roomNo = room.getRoomNumber();
        }
      }

      String finalContent = content
          .replace("{租户姓名}", tenant.getName() == null ? "" : tenant.getName())
          .replace("{房间号}", roomNo);
      if (!link.isBlank()) {
        finalContent = finalContent + "\n" + link;
      }

      SystemMessage msg = new SystemMessage();
      msg.setType("BATCH_NOTIFY");
      msg.setTitle(title);
      msg.setContent(finalContent);
      msg.setRelatedId(tenant.getId());
      msg.setIsRead(false);
      msg.setCreatedAt(LocalDateTime.now());
      created.add(messageRepository.save(msg));
    }

    operationLogService.log("批量操作", "批量发送通知: 租户" + created.size() + "人", request);
    return ResponseEntity.ok(Map.of("sentCount", created.size()));
  }
}
