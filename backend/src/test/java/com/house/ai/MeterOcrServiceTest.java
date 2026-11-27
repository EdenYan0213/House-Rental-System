package com.house.ai;

import com.house.model.Building;
import com.house.model.Room;
import com.house.repo.BuildingRepository;
import com.house.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 抄表拍照识别：房间匹配逻辑测试。
 *
 * <p>守护一个真实数据特征：同一房号在每栋楼都有（101 在 4 栋楼里都叫 101），
 * 只按房间号匹配必然 AMBIGUOUS，无法自动填入。因此必须支持
 * 「楼栋 + 房间号」双键匹配。
 *
 * <p>另外锁住两条安全边界：
 * <ul>
 *   <li>识别结果只做草稿，不写库（本类只验证返回结构）</li>
 *   <li>未配置视觉模型时按配置返回示例数据，且明确标记 sample=true</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("抄表拍照识别")
class MeterOcrServiceTest {

  @Mock
  private RoomRepository roomRepository;
  @Mock
  private BuildingRepository buildingRepository;

  private MeterOcrProperties properties;
  private MeterOcrService service;

  @BeforeEach
  void setUp() {
    properties = new MeterOcrProperties();
    properties.setEnabled(true);
    // 不配置 apiKey，走示例数据分支
    properties.setSampleDataWhenUnconfigured(true);

    service = new MeterOcrService(properties, roomRepository, buildingRepository);

    Building b1 = building(1L, "A栋");
    Building b2 = building(2L, "B栋");
    when(buildingRepository.findAll()).thenReturn(List.of(b1, b2));

    // 同一房号在 2 栋楼都出现
    when(roomRepository.findAll()).thenReturn(List.of(
        room(11L, 1L, "101"),
        room(12L, 2L, "101"),
        room(13L, 1L, "102")
    ));
  }

  private Building building(long id, String name) {
    Building b = new Building();
    b.setId(id);
    b.setName(name);
    return b;
  }

  private Room room(long id, long buildingId, String number) {
    Room r = new Room();
    r.setId(id);
    r.setBuildingId(buildingId);
    r.setRoomNumber(number);
    return r;
  }

  private MockMultipartFile image() {
    return new MockMultipartFile("file", "meter.png", "image/png", new byte[]{1, 2, 3});
  }

  @Test
  @DisplayName("未配置视觉模型时返回示例数据并标记 sample")
  void returnsSampleWhenNotConfigured() {
    Map<String, Object> result = service.recognize(image(), null);

    assertEquals(3, result.get("total"));
    assertEquals(Boolean.TRUE, result.get("sample"));
    assertTrue(((List<?>) result.get("rows")).size() > 0);
  }

  @Test
  @DisplayName("未配置且关闭示例数据时，抛出可读的配置提示")
  void throwsWhenUnconfiguredAndSampleDisabled() {
    properties.setSampleDataWhenUnconfigured(false);

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> service.recognize(image(), null));

    assertTrue(ex.getMessage().contains("AI_VISION_API_KEY"), 
        "提示应告诉用户要配哪个环境变量: " + ex.getMessage());
  }

  @Test
  @DisplayName("功能关闭时直接拒绝")
  void rejectsWhenDisabled() {
    properties.setEnabled(false);
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> service.recognize(image(), null));
    assertTrue(ex.getMessage().contains("未启用"));
  }

  @Test
  @DisplayName("空文件被拒绝")
  void rejectsEmptyFile() {
    MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[]{});
    assertThrows(IllegalArgumentException.class, () -> service.recognize(empty, null));
  }

  @Test
  @DisplayName("示例数据里的房间能按「楼栋+房号」唯一匹配")
  void sampleRowsMatchByBuildingAndNumber() {
    Map<String, Object> result = service.recognize(image(), null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");

    // 每行都应带上楼栋名，否则 101 无法区分
    for (Map<String, Object> row : rows) {
      assertNotNull(row.get("buildingName"), "示例数据必须带楼栋名: " + row);
    }

    // 全部应匹配成功（示例数据取自真实房间）
    assertEquals(rows.size(), result.get("matchedCount"),
        "示例数据应全部匹配成功，实际: " + result);
  }

  @Test
  @DisplayName("properties.isConfigured 只在三项齐全时为真")
  void configuredRequiresAllThree() {
    assertFalse(properties.isConfigured());

    properties.setApiKey("k");
    assertFalse(properties.isConfigured(), "缺 base-url/model 仍视为未配置");

    properties.setBaseUrl("https://x/v1");
    properties.setModel("glm-4v");
    assertTrue(properties.isConfigured());
  }

  @Test
  @DisplayName("候选房间标签带楼栋名，否则 4 个「101」无法区分")
  void candidatesIncludeBuildingName() {
    // 只给房号，不给楼栋 → 触发 AMBIGUOUS（两个楼的 101）
    properties.setSampleDataWhenUnconfigured(true);
    Map<String, Object> result = service.recognize(image(), null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
    // 示例数据自带楼栋，所以应全部 MATCHED；这里验证标签本身含楼栋
    for (Map<String, Object> row : rows) {
      if ("MATCHED".equals(row.get("matchStatus"))) {
        String label = (String) row.get("roomLabel");
        assertTrue(label.contains("/"), "标签应形如「A栋 / 101」: " + label);
      }
    }
  }

  @Test
  @DisplayName("每行都带 needsInput 与 missingFields 字段，供前端决定是否弹补全")
  void everyRowHasInputFlags() {
    Map<String, Object> result = service.recognize(image(), null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
    for (Map<String, Object> row : rows) {
      assertTrue(row.containsKey("needsInput"), "缺 needsInput: " + row);
      assertTrue(row.containsKey("missingFields"), "缺 missingFields: " + row);
      assertNotNull(row.get("missingFields"));
    }
  }

  @Test
  @DisplayName("完整的行 needsInput=false 且 missingFields 为空")
  void completeRowNeedsNoInput() {
    Map<String, Object> result = service.recognize(image(), null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
    long completeCount = rows.stream()
        .filter(r -> r.get("roomId") != null)
        .filter(r -> r.get("waterReading") != null && r.get("electricReading") != null)
        .filter(r -> r.get("recordDate") != null)
        .count();

    for (Map<String, Object> row : rows) {
      boolean complete = row.get("roomId") != null
          && row.get("waterReading") != null
          && row.get("electricReading") != null
          && row.get("recordDate") != null;
      if (complete) {
        assertEquals(Boolean.FALSE, row.get("needsInput"), "完整行不应要求输入: " + row);
        assertTrue(((List<?>) row.get("missingFields")).isEmpty());
      }
    }
    assertTrue(completeCount > 0, "示例数据应至少有一行完整");
  }

  @Test
  @DisplayName("读数为空时必须标记 needsInput，并指出缺哪个字段")
  void missingReadingsFlaggedForInput() {
    // 用反射直接验证 buildResult：构造一行"有房间但缺读数"的识别结果
    try {
      java.lang.reflect.Method m = MeterOcrService.class.getDeclaredMethod(
          "buildResult", List.class, Long.class, boolean.class);
      m.setAccessible(true);

      Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("buildingName", null);
      row.put("roomNumber", "101");
      row.put("recordDate", null);
      row.put("waterReading", null);
      row.put("electricReading", 340.0);
      row.put("confidence", 0.8);

      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) m.invoke(
          service, List.of(row), null, false);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
      Map<String, Object> first = rows.get(0);

      // 101 在两栋楼都有 → AMBIGUOUS，且房间本身算缺失
      assertEquals("AMBIGUOUS", first.get("matchStatus"));
      assertEquals(Boolean.TRUE, first.get("needsInput"));

      @SuppressWarnings("unchecked")
      List<String> missing = (List<String>) first.get("missingFields");
      assertTrue(missing.contains("room"), "应提示房间待选: " + missing);
      assertTrue(missing.contains("waterReading"), "应提示水表缺失: " + missing);
      assertTrue(missing.contains("recordDate"), "应提示日期缺失: " + missing);
      assertFalse(missing.contains("electricReading"), "电表有值不应算缺失: " + missing);

      // 候选必须带楼栋名，否则用户无法在 2 个 101 之间选择
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> candidates = (List<Map<String, Object>>) first.get("candidates");
      assertFalse(candidates.isEmpty());
      for (Map<String, Object> c : candidates) {
        assertNotNull(c.get("buildingName"), "候选必须带楼栋名: " + c);
        assertTrue(((String) c.get("roomLabel")).contains("/"), "标签应含楼栋: " + c);
      }
    } catch (ReflectiveOperationException e) {
      fail("反射调用失败: " + e.getMessage());
    }
  }

  @Test
  @DisplayName("全库都不存在的房号：返回全部房间候选供手选")
  void notFoundReturnsAllRoomsAsCandidates() {
    try {
      java.lang.reflect.Method m = MeterOcrService.class.getDeclaredMethod(
          "buildResult", List.class, Long.class, boolean.class);
      m.setAccessible(true);

      Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("buildingName", null);
      row.put("roomNumber", "999");
      row.put("recordDate", "2026-11-27");
      row.put("waterReading", 100.0);
      row.put("electricReading", 200.0);
      row.put("confidence", 0.9);

      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) m.invoke(
          service, List.of(row), null, false);

      @SuppressWarnings("unchecked")
      Map<String, Object> first = ((List<Map<String, Object>>) result.get("rows")).get(0);

      assertEquals("NOT_FOUND", first.get("matchStatus"));
      assertEquals(Boolean.TRUE, first.get("needsInput"));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> candidates = (List<Map<String, Object>>) first.get("candidates");
      assertEquals(3, candidates.size(), "应返回全部 3 个房间供手选");
    } catch (ReflectiveOperationException e) {
      fail("反射调用失败: " + e.getMessage());
    }
  }
}
