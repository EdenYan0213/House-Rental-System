package com.house.service;

import com.house.model.Bill;
import com.house.model.RemindLog;
import com.house.model.RemindRule;
import com.house.repo.BillRepository;
import com.house.repo.RemindLogRepository;
import com.house.repo.RemindRuleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RemindService {
  private final RemindRuleRepository remindRuleRepository;
  private final BillRepository billRepository;
  private final RemindLogRepository remindLogRepository;
  private final SmsService smsService;

  public RemindService(RemindRuleRepository remindRuleRepository, BillRepository billRepository,
      RemindLogRepository remindLogRepository, SmsService smsService) {
    this.remindRuleRepository = remindRuleRepository;
    this.billRepository = billRepository;
    this.remindLogRepository = remindLogRepository;
    this.smsService = smsService;
  }

  // run every minute and dispatch for rules matching current HH:mm
  @Scheduled(cron = "0 */1 * * * ?")
  public void checkAndSendReminds() {
    dispatchReminds(false);
  }

  // manual trigger: ignore rule send time and execute immediately
  public int triggerRemindsNow() {
    return dispatchReminds(true);
  }

  private int dispatchReminds(boolean ignoreSendTime) {
    List<RemindRule> enabled = remindRuleRepository.findByIsEnabled(1);
    if (enabled == null || enabled.isEmpty())
      return 0;

    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

    List<RemindRule> toRun = ignoreSendTime
        ? enabled
        : enabled.stream().filter(r -> now.equals(r.getSendTime())).collect(Collectors.toList());
    if (toRun.isEmpty())
      return 0;

    // fetch bills in待支付 or 部分支付
    List<Bill> all = billRepository.findAll();
    List<Bill> overdueCandidates = all.stream().filter(b -> {
      if (b.getStatus() == null)
        return false;
      String s = b.getStatus();
      return s.contains("待支付") || s.contains("部分支付");
    }).collect(Collectors.toList());

    LocalDate today = LocalDate.now();

    // group by tenant identifier (use contractId when available)
    Map<Long, List<Bill>> byTenant = new HashMap<>();
    for (Bill b : overdueCandidates) {
      Long tenantKey = b.getContractId() == null ? (b.getRoomId() == null ? 0L : b.getRoomId()) : b.getContractId();
      byTenant.computeIfAbsent(tenantKey, k -> new ArrayList<>()).add(b);
    }

    int sentCount = 0;

    for (Map.Entry<Long, List<Bill>> e : byTenant.entrySet()) {
      Long tenantId = e.getKey();
      List<Bill> bills = e.getValue();
      // compute overdue days per bill
      List<Bill> valid = new ArrayList<>();
      for (Bill b : bills) {
        if (b.getGeneratedDate() == null)
          continue;
        long gen = b.getGeneratedDate().toLocalDate().toEpochDay();
        long td = today.toEpochDay();
        long overdue = td - gen - 1; // as spec
        if (overdue >= 0) {
          valid.add(b);
        }
      }
      if (valid.isEmpty())
        continue;

      int maxOverdue = valid.stream()
          .mapToInt(b -> (int) (today.toEpochDay() - b.getGeneratedDate().toLocalDate().toEpochDay() - 1)).max()
          .orElse(0);

      for (RemindRule rule : toRun) {
        if (rule.getOverdueDays() == null)
          continue;
        if (maxOverdue != rule.getOverdueDays())
          continue;

        // dedupe: only once per tenant per node
        List<RemindLog> exists = remindLogRepository.findByTenantIdAndOverdueDays(tenantId, rule.getOverdueDays());
        if (exists != null && !exists.isEmpty())
          continue;

        // prepare content
        int count = valid.size();
        double total = valid.stream().mapToDouble(b -> b.getTotalAmount() == null ? 0.0 : b.getTotalAmount()).sum();
        String roomNumber = valid.get(0).getRoomNumber();
        String tenantName = valid.get(0).getTenantName();
        String phone = valid.get(0).getTenantPhone();

        String content = rule.getSmsTemplate();
        if (content == null)
          content = "尊敬的{租户姓名}，您有{逾期笔数}笔账单逾期，总欠费{总欠费金额}元，请尽快处理。";
        content = content.replace("{租户姓名}", tenantName == null ? "" : tenantName);
        content = content.replace("{房间号}", roomNumber == null ? "" : roomNumber);
        content = content.replace("{逾期笔数}", String.valueOf(count));
        content = content.replace("{总欠费金额}", String.format("%.2f", total));
        content = content.replace("{最长逾期天数}", String.valueOf(maxOverdue));

        List<Long> ids = valid.stream().map(Bill::getId).collect(Collectors.toList());
        String billIdsJson = ids.toString();

        RemindLog log = RemindLog.builder()
            .tenantId(tenantId)
            .tenantName(tenantName)
            .phone(phone)
            .roomNumber(roomNumber)
            .overdueDays(rule.getOverdueDays())
            .billIds(billIdsJson)
            .totalAmount(total)
            .content(content)
            .build();

        // send (will record log)
        smsService.sendSms(log);
        sentCount++;
      }
    }

    return sentCount;
  }
}
