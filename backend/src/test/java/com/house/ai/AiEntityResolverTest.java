package com.house.ai;

import com.house.model.Building;
import com.house.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * AI 参数解析与实体匹配。
 *
 * <p>模型本身不可单测（要真调用），但「把口语转成实体」这一段是纯逻辑，
 * 也正是最容易出错的地方——同一房间号存在于多个楼栋时必须澄清而不是随便挑一个。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI 实体解析")
class AiEntityResolverTest {

  @Mock
  private com.house.repo.RoomRepository roomRepository;
  @Mock
  private com.house.repo.BuildingRepository buildingRepository;
  @Mock
  private com.house.repo.TenantRepository tenantRepository;
  @Mock
  private com.house.repo.ContractRepository contractRepository;

  private AiEntityResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new AiEntityResolver(roomRepository, buildingRepository, tenantRepository, contractRepository);
  }

  // ---------------- 房间号归一化 ----------------

  @Test
  @DisplayName("「101房间」与「101」视为同一房间")
  void roomNoiseCharactersAreStripped() {
    Room room = room(1L, 10L, "101");
    when(roomRepository.findAll()).thenReturn(List.of(room));
    when(buildingRepository.findAll()).thenReturn(List.of(building(10L, "A栋")));

    AiEntityResolver.RoomMatch match = resolver.resolveRoom("101房间", null);

    assertTrue(match.isFound(), "带「房间」后缀应能匹配到 101");
    assertEquals("101", match.room().getRoomNumber());
  }

  @Test
  @DisplayName("楼栋名可从房间号里拆出：「A栋101」")
  void buildingPrefixIsParsedOut() {
    Room room = room(1L, 10L, "101");
    when(roomRepository.findAll()).thenReturn(List.of(room));
    when(buildingRepository.findAll()).thenReturn(List.of(building(10L, "A栋")));

    AiEntityResolver.RoomMatch match = resolver.resolveRoom("A栋101", null);

    assertTrue(match.isFound(), "应能同时拆出楼栋 A栋 与房间 101");
  }

  @Test
  @DisplayName("同一房间号存在于多个楼栋时必须澄清，不能随便选")
  void duplicateRoomNumberRequiresClarification() {
    Room a101 = room(1L, 10L, "101");
    Room b101 = room(2L, 20L, "101");
    when(roomRepository.findAll()).thenReturn(List.of(a101, b101));
    when(buildingRepository.findAll()).thenReturn(List.of(building(10L, "A栋"), building(20L, "B栋")));

    AiEntityResolver.RoomMatch match = resolver.resolveRoom("101", null);

    assertFalse(match.isFound(), "重复房间号不应直接返回结果");
    assertTrue(match.isAmbiguous(), "应标记为需要澄清");
    assertEquals(2, match.suggestions().size(), "应给出两个候选");
  }

  @Test
  @DisplayName("给了楼栋就能消除歧义")
  void buildingHintResolvesAmbiguity() {
    Room a101 = room(1L, 10L, "101");
    Room b101 = room(2L, 20L, "101");
    when(roomRepository.findAll()).thenReturn(List.of(a101, b101));
    when(buildingRepository.findAll()).thenReturn(List.of(building(10L, "A栋"), building(20L, "B栋")));

    AiEntityResolver.RoomMatch match = resolver.resolveRoom("101", "B栋");

    assertTrue(match.isFound());
    assertEquals(2L, match.room().getId(), "应命中 B栋 101");
  }

  @Test
  @DisplayName("找不到房间时返回 notFound 而不是抛异常")
  void missingRoomReturnsNotFound() {
    when(roomRepository.findAll()).thenReturn(List.of());
    when(buildingRepository.findAll()).thenReturn(List.of());

    AiEntityResolver.RoomMatch match = resolver.resolveRoom("999", null);

    assertFalse(match.isFound());
    assertFalse(match.isAmbiguous());
    assertTrue(match.message().contains("未找到房间"));
  }

  // ---------------- 参数取值 ----------------

  @Test
  @DisplayName("getText 去空白；缺键返回 null，空串返回空串")
  void getTextHandlesNullAndBlank() {
    assertEquals("abc", resolver.getText(Map.of("k", " abc "), "k"));
    assertNull(resolver.getText(Map.of(), "k"), "键不存在应为 null");
    // 空串保持空串（与原始实现一致），调用方统一用 hasText 判断，不会漏判
    assertEquals("", resolver.getText(Map.of("k", "   "), "k"));
  }

  @Test
  @DisplayName("getTextAny 按顺序取第一个非空值")
  void getTextAnyFallsBackInOrder() {
    Map<String, Object> m = Map.of("camel", "  ", "snake", "101");
    assertEquals("101", resolver.getTextAny(m, "camel", "snake"));
    assertNull(resolver.getTextAny(m, "missing1", "missing2"));
  }

  @Test
  @DisplayName("getDouble 接受数字与数字字符串，非法返回 null")
  void getDoubleParsesNumbersAndStrings() {
    assertEquals(12.5, resolver.getDouble(Map.of("k", 12.5), "k"));
    assertEquals(300.0, resolver.getDouble(Map.of("k", "300"), "k"));
    assertNull(resolver.getDouble(Map.of("k", "abc"), "k"), "非法数字应为 null 而非抛异常");
    assertNull(resolver.getDouble(Map.of(), "k"));
  }

  // ---------------- 日期/月份 ----------------

  @Test
  @DisplayName("中文日期词：今天/昨天/明天")
  void chineseDateWords() {
    LocalDate today = LocalDate.now();
    assertEquals(today, resolver.parseDateOrDefault("今天", null));
    assertEquals(today.minusDays(1), resolver.parseDateOrDefault("昨天", null));
    assertEquals(today.plusDays(1), resolver.parseDateOrDefault("明天", null));
  }

  @Test
  @DisplayName("ISO 日期解析，非法时回退")
  void isoDateWithFallback() {
    LocalDate fallback = LocalDate.of(2020, 1, 1);
    assertEquals(LocalDate.of(2026, 3, 15), resolver.parseDateOrDefault("2026-03-15", fallback));
    assertEquals(fallback, resolver.parseDateOrDefault("不是日期", fallback));
    assertEquals(fallback, resolver.parseDateOrDefault(null, fallback));
  }

  @Test
  @DisplayName("中文月份词：本月/上月")
  void chineseMonthWords() {
    YearMonth now = YearMonth.now();
    assertEquals(now, resolver.parseMonthOrDefault("本月", null));
    assertEquals(now.minusMonths(1), resolver.parseMonthOrDefault("上月", null));
    assertEquals(YearMonth.of(2026, 5), resolver.parseMonthOrDefault("2026-05", null));
  }

  // ---------------- 合同状态展示 ----------------

  @Test
  @DisplayName("合同状态中英双写都归一化为中文展示")
  void contractStatusDisplayedInChinese() {
    assertEquals("进行中", resolver.displayContractStatus("ACTIVE"));
    assertEquals("进行中", resolver.displayContractStatus("进行中"));
    assertEquals("已到期", resolver.displayContractStatus("EXPIRED"));
    assertEquals("已终止", resolver.displayContractStatus("TERMINATED"));
    assertEquals("-", resolver.displayContractStatus(null));
  }

  private Room room(Long id, Long buildingId, String number) {
    Room room = new Room();
    room.setId(id);
    room.setBuildingId(buildingId);
    room.setRoomNumber(number);
    return room;
  }

  private Building building(Long id, String name) {
    Building building = new Building();
    building.setId(id);
    building.setName(name);
    return building;
  }
}
