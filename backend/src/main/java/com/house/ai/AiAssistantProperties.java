package com.house.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.ai-assistant")
public class AiAssistantProperties {

  private String defaultModel;
  private int maxRetries = 2;
  private String smsOverdueTemplate = "尊敬的{租户姓名}，您房间{房间号}有{逾期笔数}笔账单逾期，总欠费{总欠费金额}元，请尽快登录系统支付。";
  private String fallbackMessage = "抱歉，未能理解您的指令，请重新描述。您可以说\"今天101房间水表1250.5，电表3680\"或\"给张三发账单短信\"。";
  private List<ModelProvider> providers = new ArrayList<>();

  public String getDefaultModel() {
    return defaultModel;
  }

  public void setDefaultModel(String defaultModel) {
    this.defaultModel = defaultModel;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public String getSmsOverdueTemplate() {
    return smsOverdueTemplate;
  }

  public void setSmsOverdueTemplate(String smsOverdueTemplate) {
    this.smsOverdueTemplate = smsOverdueTemplate;
  }

  public String getFallbackMessage() {
    return fallbackMessage;
  }

  public void setFallbackMessage(String fallbackMessage) {
    this.fallbackMessage = fallbackMessage;
  }

  public List<ModelProvider> getProviders() {
    return providers;
  }

  public void setProviders(List<ModelProvider> providers) {
    this.providers = providers;
  }

  public static class ModelProvider {
    private String name;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature = 0.0;
    private Integer timeoutMillis = 3000;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }

    public Integer getTimeoutMillis() {
      return timeoutMillis;
    }

    public void setTimeoutMillis(Integer timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
    }
  }
}
