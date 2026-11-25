package com.house.controller;

import com.house.model.Tenant;
import org.springframework.stereotype.Component;

/**
 * 租客端站内信文案。
 *
 * <p>从 {@code TenantPortalController} 里抽出的 {@code msg}/{@code msgLang}：
 * 一个 key 对应中英两套模板，避免把大段 switch 散落在业务流程里。
 *
 * <p>新增文案只需在两张 switch 里各加一行；key 拼错时返回 key 本身，
 * 便于在页面上直接看出遗漏（而不是抛异常打断业务）。
 */
@Component
public class TenantMessageText {

  private static final String DEFAULT_LANG = "zh-CN";

  /** 按租客偏好语言渲染文案。 */
  public String of(Tenant tenant, String key, Object... args) {
    String lang = tenant == null ? null : tenant.getLanguagePreference();
    if (lang == null || lang.isBlank()) {
      lang = DEFAULT_LANG;
    }
    return ofLang(lang, key, args);
  }

  /** 按指定语言渲染文案（en* 走英文）。 */
  public String ofLang(String lang, String key, Object... args) {
    boolean en = lang != null && lang.startsWith("en");
    String template = en ? englishTemplate(key) : chineseTemplate(key);
    try {
      return String.format(template, args);
    } catch (Exception e) {
      // 占位符与参数个数不匹配时退回模板原文，避免整个接口 500
      return template;
    }
  }

  private String englishTemplate(String key) {
    return switch (key) {
      case "overdue_title" -> "Bill overdue reminder";
      case "overdue_content" -> "Your bill %s is overdue, unpaid amount: %.2f.";
      case "contract_expire_title" -> "Contract nearing expiration";
      case "contract_expire_content" -> "Contract %s expires in %s days. You can submit renewal.";
      case "repair_title" -> "Repair progress update";
      case "repair_content" -> "Repair order %s status changed to %s.";
      case "complaint_title" -> "Complaint update";
      case "complaint_content" -> "Complaint %s status changed to %s.";
      case "notice_title" -> "New announcement";
      case "notice_content" -> "A new announcement is published: %s";
      case "sign_verify_title" -> "Identity verification passed";
      case "sign_verify_content" -> "Contract %s is ready for signing.";
      case "sign_success_title" -> "Contract signed successfully";
      case "sign_success_content" -> "Contract %s has been signed.";
      case "sign_retry_title" -> "Signature retry required";
      case "sign_retry_content" -> "Contract %s signing failed or timed out. Please retry.";
      default -> key;
    };
  }

  private String chineseTemplate(String key) {
    return switch (key) {
      case "overdue_title" -> "账单逾期提醒";
      case "overdue_content" -> "您的账单%s已逾期，未付金额: %.2f元。";
      case "contract_expire_title" -> "合同即将到期";
      case "contract_expire_content" -> "合同%s将在%s天后到期，可提交续租申请。";
      case "repair_title" -> "报修进度更新";
      case "repair_content" -> "报修单%s状态已更新为%s。";
      case "complaint_title" -> "投诉进度更新";
      case "complaint_content" -> "投诉单%s状态已更新为%s。";
      case "notice_title" -> "新公告通知";
      case "notice_content" -> "您有新的公告: %s";
      case "sign_verify_title" -> "实名认证通过";
      case "sign_verify_content" -> "合同%s已可进入签署流程。";
      case "sign_success_title" -> "电子签署成功";
      case "sign_success_content" -> "合同%s已完成电子签署。";
      case "sign_retry_title" -> "签署需重试";
      case "sign_retry_content" -> "合同%s签署失败或超时，请重新签署。";
      default -> key;
    };
  }
}
