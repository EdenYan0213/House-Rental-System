package com.house.controller;

import com.house.model.SignAgreement;
import com.house.repo.SignAgreementRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/internal/sign")
public class SignCallbackController {
  private final SignAgreementRepository signAgreementRepository;

  public SignCallbackController(SignAgreementRepository signAgreementRepository) {
    this.signAgreementRepository = signAgreementRepository;
  }

  @PostMapping("/callback")
  public ResponseEntity<?> callback(@RequestBody Map<String, Object> body) {
    String flowNo = text(body.get("flowNo"));
    String status = text(body.get("status")).toUpperCase();
    String error = text(body.get("error"));

    if (flowNo.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "flowNo不能为空"));
    }

    SignAgreement agreement = signAgreementRepository.findBySignFlowNo(flowNo).orElse(null);
    if (agreement == null) {
      return ResponseEntity.notFound().build();
    }

    if ("SIGNED".equals(status) || "SUCCESS".equals(status)) {
      agreement.setSignStatus("SIGNED");
      agreement.setSignedAt(LocalDateTime.now());
      agreement.setLastError(null);
    } else if ("TIMEOUT".equals(status)) {
      agreement.setSignStatus("TIMEOUT");
      agreement.setLastError(error.isBlank() ? "签署超时" : error);
    } else {
      agreement.setSignStatus("FAILED");
      agreement.setLastError(error.isBlank() ? "签署失败" : error);
    }
    agreement.setUpdatedAt(LocalDateTime.now());
    signAgreementRepository.save(agreement);

    return ResponseEntity.ok(Map.of("success", true, "signStatus", agreement.getSignStatus()));
  }

  private String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }
}
