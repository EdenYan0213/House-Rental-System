package com.house.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.Building;
import com.house.model.Room;
import com.house.repo.BuildingRepository;
import com.house.repo.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 水电账单拍照识别。
 *
 * <p>只负责「图片 → 结构化草稿」，<b>不写数据库</b>。识别结果一律回到前端
 * 让用户确认（可编辑）后再走原有的 {@code upsertBatch} 入库，
 * 避免识别错误直接生成错误账单。
 *
 * <p>识别分两步，不让模型猜房间：
 * <ol>
 *   <li>视觉模型只做 OCR：提取「房间号 / 日期 / 水表读数 / 电表读数」文本</li>
 *   <li>本服务用房间号在库里精确匹配，匹配不到就标记为「未匹配」</li>
 * </ol>
 * 模型只负责认字，实体解析留在 Java 侧，这是项目既有约定
 * （见 {@link AiEntityResolver}）。
 */
@Service
public class MeterOcrService {

  private static final Logger log = LoggerFactory.getLogger(MeterOcrService.class);

  private static final long MAX_SIZE = 10L * 1024 * 1024;

  private final MeterOcrProperties properties;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public MeterOcrService(MeterOcrProperties properties, RoomRepository roomRepository,
      BuildingRepository buildingRepository) {
    this.properties = properties;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
  }

  /**
   * 识别一张图片。
   *
   * @param file    图片
   * @param hintRoomId 单房间模式下的目标房间（可为 null）；非空时优先返回该行
   * @return 草稿：识别出的行 + 汇总信息
   */
  public Map<String, Object> recognize(MultipartFile file, Long hintRoomId) {
    if (!properties.isEnabled()) {
      throw new IllegalStateException("拍照识别功能未启用，请联系管理员开启 AI_OCR_ENABLED");
    }
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("请先选择要识别的图片");
    }
    if (file.getSize() > MAX_SIZE) {
      throw new IllegalArgumentException("图片不能超过 10MB");
    }

    // 未配置视觉模型：按配置返回示例数据，便于前端联调
    if (!properties.isConfigured()) {
      if (!properties.isSampleDataWhenUnconfigured()) {
        throw new IllegalStateException(
            "未配置视觉模型，无法识别。请设置 AI_VISION_API_KEY（支持 glm-4v / qwen-vl / gpt-4o 等）");
      }
      log.warn("OCR 未配置视觉模型，返回示例数据（AI_OCR_SAMPLE_DATA=true）");
      return buildResult(sampleRows(hintRoomId), hintRoomId, true);
    }

    try {
      String raw = callVisionModel(file);
      List<Map<String, Object>> rows = parseModelOutput(raw, hintRoomId);
      return buildResult(rows, hintRoomId, false);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("抄表图片识别失败", e);
      throw new IllegalStateException("识别失败，请重试或手工录入：" + safeMessage(e));
    }
  }

  /** 调视觉模型，返回原始文本（期望是 JSON 数组）。 */
  private String callVisionModel(MultipartFile file) throws Exception {
    OpenAiApi api = OpenAiApi.builder()
        .baseUrl(resolveBaseUrl(properties.getBaseUrl()))
        .completionsPath(resolveCompletionsPath(properties.getBaseUrl()))
        .apiKey(properties.getApiKey())
        .build();

    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model(properties.getModel())
        .temperature(properties.getTemperature() == null ? 0.0 : properties.getTemperature())
        .build();

    OpenAiChatModel model = OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(options)
        .build();

    Media media = new Media(
        MimeTypeUtils.parseMimeType(resolveContentType(file)),
        new ByteArrayResource(file.getBytes()));

    ChatClient client = ChatClient.builder(model).build();
    return client.prompt()
        .system(buildExtractPrompt())
        .user(u -> u.text("请识别这张水电抄表图片，按要求返回 JSON。").media(media))
        .call()
        .content();
  }

  /**
   * 提取指令。
   *
   * <p>要点：
   * <ul>
   *   <li>明确「只做认字，不要推测」——模型倾向于"帮忙"补全不存在的房间号</li>
   *   <li>要求纯 JSON，便于解析；同时兼容被 ```json 包裹的情况</li>
   *   <li>看不清就置 null，而不是编一个数字</li>
   * </ul>
   */
  private String buildExtractPrompt() {
    return """
        你是水电表读数识别助手。请从图片中提取抄表记录，只做识别，不要推测或补全。

        输出要求：
        1. 只输出 JSON 数组，不要任何解释文字、不要 markdown 代码块。
        2. 每个元素格式：
           {"buildingName":"楼栋名或null","roomNumber":"房间号原文","recordDate":"YYYY-MM-DD 或 null","waterReading":数字或null,"electricReading":数字或null,"confidence":0到1之间的小数}
        3. 图中每一行抄表记录对应一个元素；单条记录就返回单元素数组。
        4. 读数只保留数字（可带小数点），不要带单位。
        5. 看不清的字段填 null，并在 confidence 里体现不确定程度，绝对不要编造。
        6. buildingName 只填图中明确出现的楼栋名（如「A栋」「阳光花园B栋」），
           没有就填 null —— 不要从房间号里推断楼栋。
        7. roomNumber 保留图中原文（如 101、A栋101、3-201），不要改写。
        8. 日期如果图中只有月/日没有年份，按当前年份补齐；完全没有日期则填 null。
        """;
  }

  /** 解析模型返回。容错：去掉 ```json 包裹、截取首个 [ 到末个 ]。 */
  private List<Map<String, Object>> parseModelOutput(String raw, Long hintRoomId) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("未能从图片中识别到内容，请换一张更清晰的图片");
    }

    String json = raw.trim();
    int start = json.indexOf('[');
    int end = json.lastIndexOf(']');
    if (start >= 0 && end > start) {
      json = json.substring(start, end + 1);
    }

    JsonNode node;
    try {
      node = objectMapper.readTree(json);
    } catch (Exception e) {
      log.warn("模型返回非预期格式: {}", raw);
      throw new IllegalStateException("识别结果格式异常，请重试");
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (JsonNode item : node) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("buildingName", textOrNull(item, "buildingName"));
      row.put("roomNumber", textOrNull(item, "roomNumber"));
      row.put("recordDate", textOrNull(item, "recordDate"));
      row.put("waterReading", numberOrNull(item, "waterReading"));
      row.put("electricReading", numberOrNull(item, "electricReading"));
      row.put("confidence", numberOrNull(item, "confidence"));
      rows.add(row);
    }
    return rows;
  }

  private String textOrNull(JsonNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    String text = v.asText();
    return text == null || text.isBlank() ? null : text.trim();
  }

  private Double numberOrNull(JsonNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    if (v.isNumber()) {
      return v.asDouble();
    }
    try {
      // 模型有时把数字写成字符串，或带单位
      String cleaned = v.asText().replaceAll("[^0-9.\\-]", "");
      return cleaned.isEmpty() ? null : Double.parseDouble(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 把识别行与房间匹配，并补充「上次读数」便于用户核对。
   *
   * <p>匹配策略（逐级降级，任一级唯一命中即采用）：
   * <ol>
   *   <li>楼栋 + 房间号都识别到 → 精确定位</li>
   *   <li>只有房间号，且全库唯一 → 直接命中</li>
   *   <li>只有房间号但多栋楼重名 → AMBIGUOUS，返回候选让用户选</li>
   *   <li>都没有 → NOT_FOUND</li>
   * </ol>
   * 现实数据里 101/102 这类房号在每栋楼都有，所以「楼栋」这一步不能省。
   */
  private Map<String, Object> buildResult(List<Map<String, Object>> rows, Long hintRoomId, boolean sample) {
    List<Room> allRooms = roomRepository.findAll();

    // 房间号 → 候选；同时建立 楼栋名+房间号 → 候选 的索引
    Map<String, List<Room>> roomsByNumber = new HashMap<>();
    Map<String, Room> roomsByBuildingAndNumber = new HashMap<>();
    Map<Long, String> buildingNameById = new HashMap<>();
    for (Building b : buildingRepository.findAll()) {
      if (b.getId() != null && b.getName() != null) {
        buildingNameById.put(b.getId(), b.getName());
      }
    }

    for (Room room : allRooms) {
      if (room.getRoomNumber() == null) {
        continue;
      }
      String numKey = normalizeRoomNumber(room.getRoomNumber());
      roomsByNumber.computeIfAbsent(numKey, k -> new ArrayList<>()).add(room);

      String buildingName = room.getBuildingId() == null ? null : buildingNameById.get(room.getBuildingId());
      if (buildingName != null) {
        roomsByBuildingAndNumber.put(normalizeRoomNumber(buildingName) + "|" + numKey, room);
      }
    }

    List<Map<String, Object>> enriched = new ArrayList<>();
    int matched = 0;
    int unmatched = 0;

    for (Map<String, Object> row : rows) {
      Map<String, Object> item = new LinkedHashMap<>(row);
      String roomNumber = (String) row.get("roomNumber");
      String buildingName = (String) row.get("buildingName");

      String numKey = normalizeRoomNumber(roomNumber);
      List<Room> candidates = numKey.isEmpty()
          ? List.of()
          : roomsByNumber.getOrDefault(numKey, List.of());

      // 级别 1：楼栋 + 房间号
      Room exact = null;
      if (!normalizeRoomNumber(buildingName).isEmpty() && !numKey.isEmpty()) {
        exact = roomsByBuildingAndNumber.get(normalizeRoomNumber(buildingName) + "|" + numKey);
      }

      if (exact != null) {
        item.put("roomId", exact.getId());
        item.put("roomLabel", buildRoomLabel(exact, buildingNameById.get(exact.getBuildingId())));
        item.put("roomNumber", exact.getRoomNumber());
        item.put("buildingId", exact.getBuildingId());
        item.put("buildingName", buildingNameById.get(exact.getBuildingId()));
        item.put("matchStatus", "MATCHED");
        // 读数缺失同样需要用户补，不能当作「识别成功」
        item.put("missingFields", missingValueFields(item));
        item.put("needsInput", !((List<?>) item.get("missingFields")).isEmpty());
        matched++;
      } else if (candidates.size() == 1) {
        Room room = candidates.get(0);
        item.put("roomId", room.getId());
        item.put("roomLabel", buildRoomLabel(room, buildingNameById.get(room.getBuildingId())));
        item.put("roomNumber", room.getRoomNumber());
        item.put("buildingId", room.getBuildingId());
        item.put("buildingName", buildingNameById.get(room.getBuildingId()));
        item.put("matchStatus", "MATCHED");
        item.put("missingFields", missingValueFields(item));
        item.put("needsInput", !((List<?>) item.get("missingFields")).isEmpty());
        matched++;
      } else if (candidates.size() > 1) {
        // 多栋楼同名房号：必须让用户选，不能替用户猜
        item.put("roomId", null);
        item.put("matchStatus", "AMBIGUOUS");
        item.put("candidates", candidates.stream().map(c -> toCandidate(c, buildingNameById)).toList());
        // 房间未定，读数的缺失一并提示
        List<String> missing = new ArrayList<>();
        missing.add("room");
        missing.addAll(missingValueFields(item));
        item.put("missingFields", missing);
        item.put("needsInput", true);
        unmatched++;
      } else {
        item.put("roomId", null);
        item.put("matchStatus", "NOT_FOUND");
        // 全库都没有这个房号：给出全部房间让用户自己选
        item.put("candidates", allRooms.stream()
            .map(c -> toCandidate(c, buildingNameById))
            .toList());
        List<String> missing = new ArrayList<>();
        missing.add("room");
        missing.addAll(missingValueFields(item));
        item.put("missingFields", missing);
        item.put("needsInput", true);
        unmatched++;
      }
      enriched.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("rows", enriched);
    result.put("total", enriched.size());
    result.put("matchedCount", matched);
    result.put("unmatchedCount", unmatched);
    result.put("sample", sample);
    result.put("hintRoomId", hintRoomId);
    return result;
  }

  /**
   * 该行缺失的数值字段。
   *
   * <p>返回给前端的字段名与确认表格的列一一对应，便于高亮需要补的输入框：
   * {@code waterReading} / {@code electricReading} / {@code recordDate}。
   */
  private List<String> missingValueFields(Map<String, Object> item) {
    List<String> missing = new ArrayList<>();
    if (item.get("waterReading") == null) {
      missing.add("waterReading");
    }
    if (item.get("electricReading") == null) {
      missing.add("electricReading");
    }
    if (item.get("recordDate") == null) {
      missing.add("recordDate");
    }
    return missing;
  }

  private Map<String, Object> toCandidate(Room room, Map<Long, String> buildingNameById) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("roomId", room.getId());
    m.put("roomNumber", room.getRoomNumber());
    m.put("buildingId", room.getBuildingId());
    String buildingName = room.getBuildingId() == null ? null : buildingNameById.get(room.getBuildingId());
    m.put("buildingName", buildingName);
    m.put("roomLabel", buildRoomLabel(room, buildingName));
    return m;
  }

  /**
   * 房间显示名：楼栋 + 房号。
   *
   * <p>必须带楼栋：真实数据里 101 在每栋楼都有，只显示房号用户无法区分。
   */
  private String buildRoomLabel(Room room, String buildingName) {
    String number = room.getRoomNumber() == null ? "-" : room.getRoomNumber();
    return buildingName == null || buildingName.isBlank()
        ? number
        : buildingName + " / " + number;
  }

  /** 房间号归一化：去空白、去"房间"字样、转大写，用于宽松匹配。 */
  private String normalizeRoomNumber(String raw) {
    return raw == null ? "" : raw.replaceAll("\\s", "")
        .replace("房间", "")
        .replace("室", "")
        .toUpperCase();
  }

  /** 未配置模型时的示例数据，形状与真实识别一致。 */
  private List<Map<String, Object>> sampleRows(Long hintRoomId) {
    Map<Long, String> buildingNames = new HashMap<>();
    for (Building b : buildingRepository.findAll()) {
      if (b.getId() != null && b.getName() != null) {
        buildingNames.put(b.getId(), b.getName());
      }
    }

    List<Room> rooms = roomRepository.findAll().stream()
        .filter(r -> r.getRoomNumber() != null && r.getBuildingId() != null)
        .limit(3)
        .toList();

    List<Map<String, Object>> rows = new ArrayList<>();
    double water = 120.5;
    double electric = 340.0;
    for (Room room : rooms) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("buildingName", buildingNames.get(room.getBuildingId()));
      row.put("roomNumber", room.getRoomNumber());
      row.put("recordDate", LocalDate.now().toString());
      row.put("waterReading", water);
      row.put("electricReading", electric);
      row.put("confidence", 0.92);
      rows.add(row);
      water += 3.2;
      electric += 12.5;
    }

    // 库里没有房间时也给一条，保证前端能渲染
    if (rows.isEmpty()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("roomNumber", "101");
      row.put("recordDate", LocalDate.now().toString());
      row.put("waterReading", 120.5);
      row.put("electricReading", 340.0);
      row.put("confidence", 0.9);
      rows.add(row);
    }
    return rows;
  }

  private String resolveContentType(MultipartFile file) {
    String type = file.getContentType();
    if (type != null && type.startsWith("image/")) {
      return type;
    }
    String name = Objects.toString(file.getOriginalFilename(), "");
    String lower = name.toLowerCase();
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    return "image/jpeg";
  }

  /** 归一化 base-url（与 AiModelGateway 保持一致的拆分方式）。 */
  private String resolveBaseUrl(String raw) {
    String n = raw == null ? "" : raw.trim();
    if (n.endsWith("/chat/completions")) {
      n = n.substring(0, n.length() - "/chat/completions".length());
    }
    if (n.endsWith("/v4")) {
      return n.substring(0, n.length() - 3);
    }
    if (n.endsWith("/v1")) {
      return n.substring(0, n.length() - 3);
    }
    return n;
  }

  private String resolveCompletionsPath(String raw) {
    String n = raw == null ? "" : raw.trim();
    if (n.endsWith("/v4/chat/completions") || n.endsWith("/v4")) {
      return "/v4/chat/completions";
    }
    return "/v1/chat/completions";
  }

  private String safeMessage(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }
}
