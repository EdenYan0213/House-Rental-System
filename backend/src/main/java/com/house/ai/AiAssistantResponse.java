package com.house.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AiAssistantResponse {

  private boolean success;
  private String message;
  private String detailLink;
  private String intent;
  private String model;
  private boolean requiresClarification;
  private List<Map<String, Object>> suggestions = new ArrayList<>();

  public static AiAssistantResponse ok(String message, String intent) {
    AiAssistantResponse response = new AiAssistantResponse();
    response.setSuccess(true);
    response.setMessage(message);
    response.setIntent(intent);
    return response;
  }

  public static AiAssistantResponse fail(String message, String intent) {
    AiAssistantResponse response = new AiAssistantResponse();
    response.setSuccess(false);
    response.setMessage(message);
    response.setIntent(intent);
    return response;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getDetailLink() {
    return detailLink;
  }

  public void setDetailLink(String detailLink) {
    this.detailLink = detailLink;
  }

  public String getIntent() {
    return intent;
  }

  public void setIntent(String intent) {
    this.intent = intent;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public boolean isRequiresClarification() {
    return requiresClarification;
  }

  public void setRequiresClarification(boolean requiresClarification) {
    this.requiresClarification = requiresClarification;
  }

  public List<Map<String, Object>> getSuggestions() {
    return suggestions;
  }

  public void setSuggestions(List<Map<String, Object>> suggestions) {
    this.suggestions = suggestions == null ? new ArrayList<>() : suggestions;
  }
}
