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

  @Scheduled(cron = "0 5 0 * * ?")
  @Transactional
  public void checkExpiringContracts() {
    Optional<ReminderSettings> settingsOpt = reminderSettingsRepository.findById(1L);
    if (settingsOpt.isEmpty() || !settingsOpt.get().getEnabled()) {
      return;
    }

    ReminderSettings settings = settingsOpt.get();
    String[] daysArray = settings.getReminderDays().split(",");
    List<Integer> reminderDays = new ArrayList<>();
    for (String d : daysArray) {
      try {
        reminderDays.add(Integer.parseInt(d.trim()));
      } catch (NumberFormatException e) {
      }
    }

    LocalDate today = LocalDate.now();
    List<Contract> activeContracts = contractRepository.findAll().stream()
        .filter(c -> "ACTIVE".equals(c.getStatus()))
        .filter(c -> c.getEndDate() != null)
        .toList();

    for (Contract contract : activeContracts) {
      long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, contract.getEndDate());

      for (Integer reminderDay : reminderDays) {
        if (daysLeft == reminderDay) {
          if (!hasSentReminder(contract, reminderDay)) {
            sendReminder(contract, reminderDay);
            recordReminderSent(contract, reminderDay);
          }
        }
      }
    }
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

  private void sendReminder(Contract contract, Integer daysLeft) {
    final String[] roomNumber = { "" };
    final String[] tenantName = { "" };

    if (contract.getRoomId() != null) {
      roomRepository.findById(contract.getRoomId()).ifPresent(r -> roomNumber[0] = r.getRoomNumber());
    }
    if (contract.getTenantId() != null) {
      tenantRepository.findById(contract.getTenantId()).ifPresent(t -> tenantName[0] = t.getName());
    }

    String timeDesc = daysLeft == 1 ? "明天" : daysLeft + "天";
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
