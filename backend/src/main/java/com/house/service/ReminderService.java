package com.house.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.*;
import com.house.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReminderService {
  private final ContractRepository contractRepository;
  private final ReminderSettingsRepository reminderSettingsRepository;
  private final SystemMessageRepository systemMessageRepository;
  private final RoomRepository roomRepository;
  private final TenantRepository tenantRepository;
  private final ObjectMapper objectMapper;

  public ReminderService(ContractRepository contractRepository,
      ReminderSettingsRepository reminderSettingsRepository,
      SystemMessageRepository systemMessageRepository,
      RoomRepository roomRepository,
      TenantRepository tenantRepository,
      ObjectMapper objectMapper) {
    this.contractRepository = contractRepository;
    this.reminderSettingsRepository = reminderSettingsRepository;
    this.systemMessageRepository = systemMessageRepository;
    this.roomRepository = roomRepository;
    this.tenantRepository = tenantRepository;
    this.objectMapper = objectMapper;
  }

  /** 未配置时的默认提醒节点（天）。 */
  private static final List<Integer> DEFAULT_REMINDER_DAYS = List.of(30, 7, 1);

  @Scheduled(cron = "0 5 0 * * ?")
  @Transactional
  public void checkExpiringContracts() {
    Optional<ReminderSettings> settingsOpt = reminderSettingsRepository.findById(1L);

    // 没有配置行时不应静默跳过——这会导致功能「看起来存在但从不触发」。
    // 历史库中 reminder_settings 常为空表，这里回落到默认节点。
    boolean enabled = settingsOpt.map(ReminderSettings::getEnabled).orElse(true);
    if (!enabled) {
      return;
    }

    List<Integer> reminderDays = settingsOpt
        .map(ReminderSettings::getReminderDays)
        .map(this::parseReminderDays)
        .filter(list -> !list.isEmpty())
        .orElse(DEFAULT_REMINDER_DAYS);

    LocalDate today = LocalDate.now();
    List<Contract> activeContracts = contractRepository.findAll().stream()
        .filter(c -> "ACTIVE".equals(c.getStatus()))
        .filter(c -> c.getEndDate() != null)
        .toList();

    for (Contract contract : activeContracts) {
      long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, contract.getEndDate());
      if (daysLeft < 0) {
        continue; // 已过期的不再提醒
      }

      for (Integer reminderDay : reminderDays) {
        // 用「已经进入该区间且尚未发送」判断，而不是 daysLeft == reminderDay。
        // 原实现精确匹配，服务停机一天就会永久漏发这个节点。
        if (daysLeft <= reminderDay && !hasSentReminder(contract, reminderDay)) {
          sendReminder(contract, reminderDay, daysLeft);
          recordReminderSent(contract, reminderDay);
          break; // 一次只发最近的一个节点，避免同一天刷多条
        }
      }
    }
  }

  /** 解析「30,7,1」这类配置；非法值忽略。 */
  private List<Integer> parseReminderDays(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<Integer> days = new ArrayList<>();
    for (String part : raw.split(",")) {
      try {
        int v = Integer.parseInt(part.trim());
        if (v > 0) {
          days.add(v);
        }
      } catch (NumberFormatException ignored) {
        // 非法项直接跳过
      }
    }
    // 从大到小，保证先匹配最远的节点
    days.sort(Comparator.reverseOrder());
    return days;
  }

  private boolean hasSentReminder(Contract contract, Integer day) {
    if (contract.getReminderSent() == null || contract.getReminderSent().isEmpty()) {
      return false;
    }
    try {
      Map<String, String> sentMap = objectMapper.readValue(
          contract.getReminderSent(),
          new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
          });
      return sentMap.containsKey(day.toString());
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  private void sendReminder(Contract contract, Integer reminderDay, long actualDaysLeft) {
    final String[] roomNumber = { "" };
    final String[] tenantName = { "" };

    if (contract.getRoomId() != null) {
      roomRepository.findById(contract.getRoomId()).ifPresent(r -> roomNumber[0] = r.getRoomNumber());
    }
    if (contract.getTenantId() != null) {
      tenantRepository.findById(contract.getTenantId()).ifPresent(t -> tenantName[0] = t.getName());
    }

    String timeDesc = actualDaysLeft <= 1 ? "明天" : actualDaysLeft + "天";
    String title = "合同到期提醒";
    String content = String.format("【合同到期提醒】合同%s（租户%s，房间%s）将于%s（%s）到期，请及时处理。",
        contract.getContractNo(), tenantName[0], roomNumber[0], timeDesc, contract.getEndDate());

    SystemMessage message = new SystemMessage();
    message.setType("CONTRACT_EXPIRE");
    message.setTitle(title);
    message.setContent(content);
    message.setRelatedId(contract.getId());
    message.setIsRead(false);
    message.setCreatedAt(LocalDateTime.now());

    systemMessageRepository.save(message);
  }

  private void recordReminderSent(Contract contract, Integer day) {
    Map<String, String> sentMap = new HashMap<>();
    if (contract.getReminderSent() != null && !contract.getReminderSent().isEmpty()) {
      try {
        sentMap = objectMapper.readValue(
            contract.getReminderSent(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
            });
      } catch (JsonProcessingException e) {
      }
    }
    sentMap.put(day.toString(), LocalDate.now().toString());
    try {
      contract.setReminderSent(objectMapper.writeValueAsString(sentMap));
      contractRepository.save(contract);
    } catch (JsonProcessingException e) {
    }
  }

  public List<Contract> getExpiringContracts(int days) {
    LocalDate threshold = LocalDate.now().plusDays(days);
    return contractRepository.findAll().stream()
        .filter(c -> "ACTIVE".equals(c.getStatus()))
        .filter(c -> c.getEndDate() != null && !c.getEndDate().isAfter(threshold))
        .filter(c -> {
          long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
          return daysLeft >= 0;
        })
        .peek(c -> {
          if (c.getRoomId() != null) {
            roomRepository.findById(c.getRoomId()).ifPresent(r -> c.setRoomNumber(r.getRoomNumber()));
          }
          if (c.getTenantId() != null) {
            tenantRepository.findById(c.getTenantId()).ifPresent(t -> {
              c.setTenantName(t.getName());
              c.setTenantPhone(t.getPhone());
            });
          }
        })
        .toList();
  }

  public long getExpiringCount(int days) {
    return getExpiringContracts(days).size();
  }
}
