package com.house.ai;

import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.ContractStatus;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import com.house.repo.TenantRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 指令的参数解析与实体匹配。
 *
 * <p>从 1054 行的 {@code AiAssistantService} 抽出。模型给出的往往是口语化输入
 * （「A栋101」「今天」「上月」），这里负责把它们对齐到数据库实体。
 *
 * <p>匹配不到或匹配到多个时，返回带候选列表的 {@link MatchResult}，
 * 由上层转成「请补充楼栋」这类澄清提示。
 */
@Component
public class AiEntityResolver {

  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;

  public AiEntityResolver(RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      TenantRepository tenantRepository,
      ContractRepository contractRepository) {
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
  }

  // ---------------- 房间匹配 ----------------

  /**
   * 按房间号（可含楼栋前缀）匹配房间。
   *
   * <p>匹配规则：先剥掉「房间/房号/室/号/-」等噪声字符再比较；
   * 若同时给了楼栋，则用「包含」再过滤一次。
   */
  public RoomMatch resolveRoom(String roomNumber, String buildingName) {
    List<Room> all = roomRepository.findAll();
    Map<Long, Building> buildingMap = buildingRepository.findAll().stream()
        .collect(Collectors.toMap(Building::getId, b -> b, (a, b) -> a));

    ParsedRoomInput parsed = parseCombinedRoomInput(roomNumber, buildingName, buildingMap);
    String requestedRoomRaw = hasText(parsed.roomNumber()) ? parsed.roomNumber() : roomNumber;
    String requestedRoomNormalized = normalizeRoomToken(requestedRoomRaw);

    List<Room> matched = all.stream().filter(r -> {
      String roomRaw = safe(r.getRoomNumber());
      String roomNormalized = normalizeRoomToken(roomRaw);
      if (hasText(requestedRoomNormalized)) {
        return requestedRoomNormalized.equalsIgnoreCase(roomNormalized);
      }
      return requestedRoomRaw != null && requestedRoomRaw.equalsIgnoreCase(roomRaw);
    }).toList();

    if (hasText(parsed.buildingName())) {
      String requestedBuilding = parsed.buildingName().trim();
      matched = matched.stream().filter(r -> {
        Building b = buildingMap.get(r.getBuildingId());
        return b != null && safe(b.getName()).contains(requestedBuilding);
      }).toList();
    }

    if (matched.isEmpty()) {
      return RoomMatch.notFound("未找到房间\"" + roomNumber + "\"。");
    }

    if (matched.size() > 1) {
      List<Map<String, Object>> suggestions = matched.stream().map(room -> {
        Map<String, Object> s = new LinkedHashMap<>();
        Building b = buildingMap.get(room.getBuildingId());
        s.put("buildingName", b == null ? "-" : b.getName());
        s.put("roomNumber", room.getRoomNumber());
        s.put("roomId", room.getId());
        return s;
      }).toList();

      StringBuilder text = new StringBuilder("房间号\"").append(roomNumber).append("\"存在多个，请补充楼栋：\n");
      for (Map<String, Object> s : suggestions) {
        text.append("- ").append(s.get("buildingName")).append(s.get("roomNumber")).append("\n");
      }
      text.append("请重新输入，例如\"A栋101房间...\"。");
      return RoomMatch.ambiguous(text.toString().trim(), suggestions);
    }

    return RoomMatch.found(matched.get(0));
  }

  /** 从「A栋101」这类合并输入里拆出楼栋名和房间号。 */
  private ParsedRoomInput parseCombinedRoomInput(String roomNumber, String buildingName,
      Map<Long, Building> buildingMap) {
    String resolvedBuilding = hasText(buildingName) ? buildingName.trim() : null;
    String resolvedRoom = roomNumber;
    if (!hasText(roomNumber)) {
      return new ParsedRoomInput(resolvedRoom, resolvedBuilding);
    }

    String candidate = roomNumber.trim();
    if (!hasText(resolvedBuilding) && buildingMap != null && !buildingMap.isEmpty()) {
      // 长楼栋名优先，避免「A栋」抢先匹配掉「A栋二单元」
      List<Building> buildings = buildingMap.values().stream()
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing((Building b) -> safe(b.getName()).length()).reversed())
          .toList();

      for (Building b : buildings) {
        String name = safe(b.getName());
        if (!hasText(name)) {
          continue;
        }
        if (candidate.contains(name)) {
          resolvedBuilding = name;
          resolvedRoom = candidate.replace(name, "").trim();
          break;
        }
      }
    }

    String normalizedRoom = normalizeRoomToken(resolvedRoom);
    if (hasText(normalizedRoom)) {
      resolvedRoom = normalizedRoom;
    }

    return new ParsedRoomInput(resolvedRoom, resolvedBuilding);
  }

  /** 去掉「房间/房号/房/室/号/-/_/空格」等噪声，便于等值比较。 */
  private String normalizeRoomToken(String value) {
    if (!hasText(value)) {
      return value;
    }
    return value.trim()
        .replace("房间", "")
        .replace("房号", "")
        .replace("房", "")
        .replace("室", "")
        .replace("号", "")
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")
        .replace("　", "");
  }

  // ---------------- 租户匹配 ----------------

  /**
   * 按姓名/手机号找租户，并用房间/楼栋提示收敛到唯一合同。
   *
   * @param activeOnly true 时只看生效合同（发短信场景）
   */
  public TenantMatch resolveTenant(String tenantName, Map<String, Object> entities, boolean activeOnly) {
    List<Tenant> tenants = resolveTenantCandidates(tenantName);
    if (tenants.isEmpty()) {
      return TenantMatch.notFound("未找到租户\"" + tenantName + "\"，请确认姓名是否正确。");
    }

    String roomHint = getText(entities, "room_number");
    String buildingHint = getText(entities, "building_name");

    List<TenantContract> contexts = new ArrayList<>();
    for (Tenant tenant : tenants) {
      List<Contract> contracts = contractRepository.findByTenantId(tenant.getId());
      if (activeOnly) {
        contracts = contracts.stream()
            .filter(c -> ContractStatus.isActive(safe(c.getStatus())))
            .toList();
      }
      for (Contract contract : contracts) {
        Room room = contract.getRoomId() == null ? null
            : roomRepository.findById(contract.getRoomId()).orElse(null);
        Building building = room == null || room.getBuildingId() == null ? null
            : buildingRepository.findById(room.getBuildingId()).orElse(null);
        if (hasText(roomHint) && (room == null || !roomHint.equalsIgnoreCase(safe(room.getRoomNumber())))) {
          continue;
        }
        if (hasText(buildingHint) && (building == null || !safe(building.getName()).contains(buildingHint))) {
          continue;
        }
        contexts.add(new TenantContract(tenant, contract, room, building));
      }
    }

    if (contexts.isEmpty()) {
      return TenantMatch.notFound("未找到可匹配的进行中合同，请补充楼栋和房间号后重试。");
    }

    if (contexts.size() > 1) {
      List<Map<String, Object>> suggestions = contexts.stream().map(c -> {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tenantName", c.tenant().getName());
        item.put("buildingName", c.building() == null ? "-" : c.building().getName());
        item.put("roomNumber", c.room() == null ? "-" : c.room().getRoomNumber());
        item.put("contractStatus", displayContractStatus(c.contract().getStatus()));
        return item;
      }).toList();

      StringBuilder text = new StringBuilder("找到多位\"").append(tenantName).append("\"，请指定具体房间号：\n");
      for (Map<String, Object> s : suggestions) {
        text.append("- ")
            .append(s.get("buildingName"))
            .append(s.get("roomNumber"))
            .append("（合同")
            .append(s.get("contractStatus"))
            .append("）\n");
      }
      text.append("请重新输入并说明具体房间号。");
      return TenantMatch.ambiguous(text.toString().trim(), suggestions);
    }

    return TenantMatch.found(contexts.get(0));
  }

  /**
   * 姓名/手机号模糊查租户。优先返回姓名精确匹配的结果，
   * 否则返回全部模糊命中（由上层给出澄清提示）。
   */
  public List<Tenant> resolveTenantCandidates(String tenantName) {
    List<Tenant> raw = tenantRepository.findByNameContainingOrPhoneContaining(tenantName, tenantName);
    List<Tenant> exact = raw.stream()
        .filter(t -> tenantName.equalsIgnoreCase(safe(t.getName())))
        .toList();
    return exact.isEmpty() ? raw : exact;
  }

  // ---------------- 参数取值 ----------------

  public String getText(Map<String, Object> entities, String key) {
    if (entities == null) {
      return null;
    }
    Object value = entities.get(key);
    return value == null ? null : value.toString().trim();
  }

  public String getTextAny(Map<String, Object> entities, String... keys) {
    for (String key : keys) {
      String value = getText(entities, key);
      if (hasText(value)) {
        return value;
      }
    }
    return null;
  }

  public Double getDouble(Map<String, Object> entities, String key) {
    if (entities == null) {
      return null;
    }
    Object value = entities.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public Double getDoubleAny(Map<String, Object> entities, String... keys) {
    for (String key : keys) {
      Double value = getDouble(entities, key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> toMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add((Map<String, Object>) map);
      }
    }
    return result;
  }

  // ---------------- 日期/月份 ----------------

  /** 解析「今天/昨天/明天/YYYY-MM-DD」，失败返回 fallback。 */
  public LocalDate parseDateOrDefault(String text, LocalDate fallback) {
    if (!hasText(text)) {
      return fallback;
    }
    String normalized = text.trim();
    if ("今天".equals(normalized)) {
      return LocalDate.now();
    }
    if ("昨天".equals(normalized)) {
      return LocalDate.now().minusDays(1);
    }
    if ("明天".equals(normalized)) {
      return LocalDate.now().plusDays(1);
    }
    try {
      return LocalDate.parse(normalized);
    } catch (DateTimeParseException e) {
      return fallback;
    }
  }

  /** 解析「本月/上月/YYYY-MM」，失败返回 fallback。 */
  public YearMonth parseMonthOrDefault(String text, YearMonth fallback) {
    if (!hasText(text)) {
      return fallback;
    }
    String normalized = text.trim();
    if ("本月".equals(normalized) || "这个月".equals(normalized)) {
      return YearMonth.now();
    }
    if ("上月".equals(normalized) || "上个月".equals(normalized)) {
      return YearMonth.now().minusMonths(1);
    }
    try {
      return YearMonth.parse(normalized, MONTH_FORMAT);
    } catch (DateTimeParseException e) {
      return fallback;
    }
  }

  // ---------------- 通用 ----------------

  public boolean isPendingStatus(Bill bill) {
    // 待收 = 待支付或部分支付
    return BillStatus.isPending(bill.getStatus()) || BillStatus.isPartial(bill.getStatus());
  }

  public String displayContractStatus(String status) {
    // 空状态展示为 "-"，不能落到 normalize 的 PENDING 默认值上
    if (status == null || status.isBlank()) {
      return "-";
    }
    // 中文展示名；归一化统一交给 ContractStatus，避免这里再维护一套拼写表
    switch (ContractStatus.normalize(status)) {
      case ContractStatus.ACTIVE:
        return "进行中";
      case ContractStatus.PENDING:
        return "待生效";
      case ContractStatus.EXPIRED:
        return "已到期";
      case ContractStatus.TERMINATED:
        return "已终止";
      default:
        return status;
    }
  }

  public boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  public String safe(String value) {
    return value == null ? "" : value.trim();
  }

  // ---------------- 返回值类型 ----------------

  /** 房间匹配结果：命中 / 未找到 / 需澄清（候选列表）。 */
  public record RoomMatch(Room room, String message, List<Map<String, Object>> suggestions) {
    public static RoomMatch found(Room room) {
      return new RoomMatch(room, null, List.of());
    }

    public static RoomMatch notFound(String message) {
      return new RoomMatch(null, message, List.of());
    }

    public static RoomMatch ambiguous(String message, List<Map<String, Object>> suggestions) {
      return new RoomMatch(null, message, suggestions);
    }

    public boolean isFound() {
      return room != null;
    }

    public boolean isAmbiguous() {
      return !suggestions.isEmpty();
    }
  }

  /** 租户 + 合同 + 房间 + 楼栋的组合上下文。 */
  public record TenantContract(Tenant tenant, Contract contract, Room room, Building building) {
  }

  /** 租户匹配结果：命中 / 未找到 / 需澄清。 */
  public record TenantMatch(TenantContract context, String message, List<Map<String, Object>> suggestions) {
    public static TenantMatch found(TenantContract context) {
      return new TenantMatch(context, null, List.of());
    }

    public static TenantMatch notFound(String message) {
      return new TenantMatch(null, message, List.of());
    }

    public static TenantMatch ambiguous(String message, List<Map<String, Object>> suggestions) {
      return new TenantMatch(null, message, suggestions);
    }

    public boolean isFound() {
      return context != null;
    }

    public boolean isAmbiguous() {
      return !suggestions.isEmpty();
    }
  }

  private record ParsedRoomInput(String roomNumber, String buildingName) {
  }
}
