package com.house.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 模型调用链：provider 选择、降级顺序、ChatClient 构建。
 *
 * <p>从 1054 行的 {@code AiAssistantService} 抽出。这里只负责「跟谁说话」，
 * 不涉及任何业务单据逻辑，方便单独调整超时/降级策略。
 */
@Component
public class AiModelGateway {

  private final AiAssistantProperties properties;

  public AiModelGateway(AiAssistantProperties properties) {
    this.properties = properties;
  }

  /** 是否至少配置了一个可用 provider（有 name/baseUrl/apiKey/model）。 */
  public boolean hasUsableProvider() {
    return !resolveOrderedProviders().isEmpty();
  }

  /**
   * 按 {@code default-model} 优先、其余顺延的顺序返回可用 provider。
   *
   * <p>降级重试依赖这个顺序：先试默认模型，失败再试备选。
   */
  public List<AiAssistantProperties.ModelProvider> resolveOrderedProviders() {
    List<AiAssistantProperties.ModelProvider> configured = properties.getProviders() == null
        ? List.of()
        : properties.getProviders();

    List<AiAssistantProperties.ModelProvider> enabled = configured.stream()
        .filter(Objects::nonNull)
        .filter(p -> hasText(p.getName())
            && hasText(p.getBaseUrl())
            && hasText(p.getApiKey())
            && hasText(p.getModel()))
        .toList();

    if (!hasText(properties.getDefaultModel())) {
      return new ArrayList<>(enabled);
    }

    List<AiAssistantProperties.ModelProvider> ordered = new ArrayList<>();
    AiAssistantProperties.ModelProvider defaultProvider = enabled.stream()
        .filter(p -> properties.getDefaultModel().equalsIgnoreCase(p.getName()))
        .findFirst()
        .orElse(null);
    if (defaultProvider != null) {
      ordered.add(defaultProvider);
    }
    enabled.stream()
        .filter(p -> defaultProvider == null || !p.getName().equalsIgnoreCase(defaultProvider.getName()))
        .forEach(ordered::add);
    return ordered;
  }

  /** 用 provider 配置构建一个带工具调用能力的 ChatClient。 */
  public ChatClient createChatClient(AiAssistantProperties.ModelProvider provider) {
    EndpointParts endpoint = resolveEndpoint(provider.getBaseUrl());
    OpenAiApi openAiApi = OpenAiApi.builder()
        .baseUrl(endpoint.baseUrl())
        .completionsPath(endpoint.completionsPath())
        .apiKey(provider.getApiKey())
        .build();

    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model(provider.getModel())
        .temperature(provider.getTemperature() == null ? 0.0 : provider.getTemperature())
        .build();

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(options)
        .build();
    return ChatClient.builder(chatModel).build();
  }

  /**
   * 归一化 base-url：去掉尾部斜杠和可能误配的 completions 路径。
   *
   * <p>各地 provider 的 base-url 写法不一致（有的带 /v1，有的直接到
   * /chat/completions），这里统一成「根地址」，再由 {@link #resolveEndpoint}
   * 拆成 baseUrl + completionsPath 两段交给 OpenAiApi。
   */
  private String normalizeBaseUrl(String baseUrl) {
    String normalized = baseUrl.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.endsWith("/chat/completions")) {
      normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
    }
    return normalized;
  }

  private EndpointParts resolveEndpoint(String rawBaseUrl) {
    String normalized = normalizeBaseUrl(rawBaseUrl);
    if (normalized.endsWith("/v4")) {
      return new EndpointParts(normalized.substring(0, normalized.length() - 3), "/v4/chat/completions");
    }
    if (normalized.endsWith("/v1")) {
      return new EndpointParts(normalized.substring(0, normalized.length() - 3), "/v1/chat/completions");
    }
    return new EndpointParts(normalized, "/v1/chat/completions");
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  /** 拆好的接口地址：baseUrl 给 SDK，completionsPath 是相对路径。 */
  private record EndpointParts(String baseUrl, String completionsPath) {
  }
}
