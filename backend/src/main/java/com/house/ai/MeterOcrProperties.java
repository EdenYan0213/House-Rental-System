package com.house.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 抄表拍照识别（OCR）配置。
 *
 * <p>与 {@link AiAssistantProperties} 分开，因为视觉模型和文本模型的
 * 能力/价格/超时完全不同，通常是不同的服务商或不同的模型名。
 * 混用会导致「配了 key 却报不支持图片」这种难以排查的问题。
 */
@Component
@ConfigurationProperties(prefix = "app.ai-assistant.ocr")
public class MeterOcrProperties {

  /** 总开关。关闭后 OCR 接口直接返回未启用。 */
  private boolean enabled = true;

  private String baseUrl;
  private String apiKey;
  private String model;
  private Double temperature = 0.0;
  private Integer timeoutMillis = 30000;

  /**
   * 未配置 apiKey 时是否返回示例数据。
   *
   * <p>便于演示与前端联调；生产环境建议设为 false，
   * 避免用户以为真的识别成功而提交了假数据。
   */
  private boolean sampleDataWhenUnconfigured = true;

  /** 是否已配置可用的视觉模型。 */
  public boolean isConfigured() {
    return apiKey != null && !apiKey.trim().isEmpty()
        && baseUrl != null && !baseUrl.trim().isEmpty()
        && model != null && !model.trim().isEmpty();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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

  public boolean isSampleDataWhenUnconfigured() {
    return sampleDataWhenUnconfigured;
  }

  public void setSampleDataWhenUnconfigured(boolean sampleDataWhenUnconfigured) {
    this.sampleDataWhenUnconfigured = sampleDataWhenUnconfigured;
  }
}
