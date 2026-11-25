package com.house.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 助手的编排层。
 *
 * <p>职责只有三件：
 * <ol>
 *   <li>把用户输入交给模型，按 provider 顺序降级重试（{@link AiModelGateway}）</li>
 *   <li>声明 {@code @Tool}，让模型能把自然语言落到具体意图</li>
 *   <li>用 ThreadLocal 捕获「本次调用真的执行了哪个工具」</li>
 * </ol>
 *
 * <p>参数解析与实体匹配在 {@link AiEntityResolver}，
 * 业务读写在 {@link AiIntentHandlers}。模型本身不直接碰数据库。
 */
@Service
public class AiAssistantService {

  private final AiAssistantProperties properties;
  private final AiModelGateway gateway;
  private final AiIntentHandlers handlers;
  private final ObjectMapper objectMapper;

  public AiAssistantService(AiAssistantProperties properties,
      AiModelGateway gateway,
      AiIntentHandlers handlers,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.gateway = gateway;
    this.handlers = handlers;
    this.objectMapper = objectMapper;
  }

  /**
   * 执行一次自然语言指令。
   *
   * <p>按 provider 顺序尝试，每个 provider 都有自己的超时；任一成功即返回。
   * 全部失败抛 {@link RuntimeException}，未配置任何 provider 抛
   * {@link AiNotConfiguredException}（前端据此显示配置指引）。
   */
  public AiExecutionOutcome execute(String userInput) {
    List<AiAssistantProperties.ModelProvider> providers = gateway.resolveOrderedProviders();
    if (providers.isEmpty()) {
      throw new AiNotConfiguredException();
    }

    int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
    maxAttempts = Math.min(maxAttempts, providers.size());

    List<String> errors = new ArrayList<>();
    for (int i = 0; i < maxAttempts; i++) {
      AiAssistantProperties.ModelProvider provider = providers.get(i);
      try {
        InvocationState.clear();
        String modelReply = gateway.createChatClient(provider).prompt()
            .system(buildToolSystemPrompt())
            .user(userInput)
            .tools(this)
            .call()
            .content();

        InvocationState state = InvocationState.get();
        AiAssistantResponse response;
        String intent;
        Map<String, Object> entities;
        if (state.response != null) {
          response = state.response;
          intent = state.intent;
          entities = state.entities;
        } else {
          // 模型没调用任何工具：用它的原话作为回复，不编造业务结果
          response = AiAssistantResponse.fail(
              hasText(modelReply) ? modelReply : properties.getFallbackMessage(), "UNKNOWN");
          intent = "UNKNOWN";
          entities = Map.of();
        }

        response.setIntent(intent);
        response.setModel(provider.getName());
        return new AiExecutionOutcome(response, intent, toJson(entities), provider.getName());
      } catch (Exception ex) {
        errors.add((provider.getName() == null ? "unknown" : provider.getName()) + ": " + ex.getMessage());
      } finally {
        InvocationState.clear();
      }
    }

    throw new RuntimeException("所有模型调用失败: " + String.join(" | ", errors));
  }

  // ---------------- 工具声明 ----------------

  @Tool(description = "录入房间水电读数。参数: roomNumber, waterReading, electricReading, 可选date(YYYY-MM-DD/今天/昨天/明天), 可选buildingName")
  public String toolMeterReading(String roomNumber, Double waterReading, Double electricReading, String date,
      String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("room_number", roomNumber);
    entities.put("water_reading", waterReading);
    entities.put("electric_reading", electricReading);
    entities.put("date", date);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handlers.meterReading(entities);
    InvocationState.set("METER_READING", entities, response);
    return response.getMessage();
  }

  @Tool(description = "批量录入房间水电读数。参数: records数组(每项包含roomNumber, waterReading, electricReading, 可选date, 可选buildingName)，可选全局date和buildingName")
  public String toolMeterReadingBatch(List<Map<String, Object>> records, String date, String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("records", records);
    entities.put("date", date);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handlers.meterReadingBatch(entities);
    InvocationState.set("METER_READING", entities, response);
    return response.getMessage();
  }

  @Tool(description = "给租户发送账单催缴短信。参数: tenantName, 可选roomNumber, 可选buildingName")
  public String toolSendSms(String tenantName, String roomNumber, String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("tenant_name", tenantName);
    entities.put("room_number", roomNumber);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handlers.sendSms(entities);
    InvocationState.set("SEND_SMS", entities, response);
    return response.getMessage();
  }

  @Tool(description = "查询租户合同信息。参数: tenantName")
  public String toolQueryContract(String tenantName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("tenant_name", tenantName);
    AiAssistantResponse response = handlers.queryContract(entities);
    InvocationState.set("QUERY_CONTRACT", entities, response);
    return response.getMessage();
  }

  @Tool(description = "查询房间月用电量。参数: roomNumber, 可选month(YYYY-MM/上月/本月), 可选buildingName")
  public String toolQueryElectricity(String roomNumber, String month, String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("room_number", roomNumber);
    entities.put("month", month);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handlers.queryElectricity(entities);
    InvocationState.set("QUERY_ELECTRICITY", entities, response);
    return response.getMessage();
  }

  // ---------------- 内部 ----------------

  /**
   * 工具调用的系统提示。
   *
   * <p>明确要求「缺参数也要先调用工具」，这样澄清提示由 Java 生成而不是模型臆测，
   * 用户看到的房间候选列表才与数据库一致。
   */
  private String buildToolSystemPrompt() {
    return "你是房屋租赁系统助手。你只能通过已提供的工具来完成任务。"
        + "如果用户要做抄表、发账单短信、查合同、查用电量，必须调用对应工具。"
        + "如果一句话中包含多个房间的抄表，优先调用批量抄表工具。"
        + "如果缺参数，也要先调用工具，由工具返回补充提示。"
        + "如果用户请求不在这4类中，直接简短回复无法处理，不要编造数据。";
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      return "{}";
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  /** 捕获「本次请求真的执行了哪个工具、参数是什么、结果是什么」。 */
  private static class InvocationState {
    private static final ThreadLocal<InvocationState> HOLDER = ThreadLocal.withInitial(InvocationState::new);
    private String intent = "UNKNOWN";
    private Map<String, Object> entities = Map.of();
    private AiAssistantResponse response;

    static void set(String intent, Map<String, Object> entities, AiAssistantResponse response) {
      InvocationState state = HOLDER.get();
      state.intent = intent == null ? "UNKNOWN" : intent;
      state.entities = entities == null ? Map.of() : new LinkedHashMap<>(entities);
      state.response = response;
    }

    static InvocationState get() {
      return HOLDER.get();
    }

    static void clear() {
      HOLDER.remove();
    }
  }

  /**
   * 未配置任何可用模型时抛出，用于和「调用失败」区分开，
   * 让前端能给出「请配置 API Key」而不是模糊的「处理失败」。
   */
  public static class AiNotConfiguredException extends RuntimeException {
    public AiNotConfiguredException() {
      super("AI 助手未配置可用模型。请在环境变量中设置 AI_PRIMARY_API_KEY（或配置 application.yml 中 "
          + "app.ai-assistant.providers 的 api-key）后重启服务。");
    }
  }
}
