package com.house.service;

import com.house.model.MeterReading;
import com.house.model.Room;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 「上期读数 + 增量」批量生成的边界测试。
 *
 * <p>重点是几个容易出错的判断：
 * 基线必须取自目标日期<b>之前</b>（否则补录历史会生成倒退读数）、
 * 没有历史读数的房间要跳过并说明原因、已存在记录默认不覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("按增量批量生成抄表")
class MeterReadingGenerateServiceTest {

  @Mock
  private MeterReadingRepository meterReadingRepository;

  @Mock
  private RoomRepository roomRepository;

  @Mock
  private MeterReadingBatchService batchService;

  private MeterReadingGenerateService service;

  private final LocalDate target = LocalDate.of(2026, 11, 1);

  @BeforeEach
  void setUp() {
    service = new MeterReadingGenerateService(meterReadingRepository, roomRepository, batchService);
  }

  private Room room(long id, String number) {
    Room r = new Room();
    r.setId(id);
    r.setRoomNumber(number);
    return r;
  }

  private MeterReading reading(Long roomId, LocalDate date, double water, double elec) {
    return MeterReading.builder()
        .roomId(roomId).recordDate(date)
        .waterReading(water).electricReading(elec)
        .build();
  }

  @Test
  @DisplayName("在上期读数基础上累加增量")
  void addsDeltaToBaseline() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target))
        .thenReturn(reading(1L, target.minusMonths(1), 100.0, 1000.0));
    when(meterReadingRepository.findByRoomIdAndRecordDate(1L, target)).thenReturn(null);

    service.generateByDelta(List.of(1L), target, 8.0, 150.0, false);

    ArgumentCaptor<List<MeterReadingBatchService.MeterUpsertItem>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(batchService).upsertBatch(captor.capture());

    MeterReadingBatchService.MeterUpsertItem item = captor.getValue().get(0);
    assertEquals(108.0, item.getWaterReading(), 0.001, "水表 = 上期 100 + 增量 8");
    assertEquals(1150.0, item.getElectricReading(), 0.001, "电表 = 上期 1000 + 增量 150");
    assertEquals(target, item.getRecordDate());
  }

  @Test
  @DisplayName("基线取目标日期之前，不误用更晚的读数")
  void baselineIsBeforeTargetDate() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));
    // 仓库层按「日期 < target」查询，这里验证调用参数确实是 target
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target))
        .thenReturn(reading(1L, target.minusMonths(2), 50.0, 500.0));

    service.generateByDelta(List.of(1L), target, 5.0, 50.0, false);

    // 关键断言：查询条件是「严格小于目标日期」
    verify(meterReadingRepository)
        .findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target);
  }

  @Test
  @DisplayName("没有历史读数的房间被跳过并给出原因")
  void skipsRoomsWithoutBaseline() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target))
        .thenReturn(null);

    MeterReadingGenerateService.GenerateResult result =
        service.generateByDelta(List.of(1L), target, 8.0, 150.0, false);

    assertEquals(0, result.getCreated());
    assertEquals(1, result.getSkipped().size());
    Map<String, Object> skip = result.getSkipped().get(0);
    assertEquals("101", skip.get("roomNumber"));
    assertTrue(String.valueOf(skip.get("reason")).contains("没有历史读数"));
    verify(batchService, never()).upsertBatch(any());
  }

  @Test
  @DisplayName("该日期已有记录且未勾选覆盖时跳过")
  void skipsExistingWhenNotOverwriting() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target))
        .thenReturn(reading(1L, target.minusMonths(1), 100.0, 1000.0));
    when(meterReadingRepository.findByRoomIdAndRecordDate(1L, target))
        .thenReturn(reading(1L, target, 999.0, 9999.0));

    MeterReadingGenerateService.GenerateResult result =
        service.generateByDelta(List.of(1L), target, 8.0, 150.0, false);

    assertEquals(0, result.getCreated());
    assertEquals(1, result.getSkipped().size());
    verify(batchService, never()).upsertBatch(any());
  }

  @Test
  @DisplayName("勾选覆盖时替换已有记录，并计入 updated")
  void overwritesWhenRequested() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target))
        .thenReturn(reading(1L, target.minusMonths(1), 100.0, 1000.0));
    when(meterReadingRepository.findByRoomIdAndRecordDate(1L, target))
        .thenReturn(reading(1L, target, 999.0, 9999.0));

    MeterReadingGenerateService.GenerateResult result =
        service.generateByDelta(List.of(1L), target, 8.0, 150.0, true);

    assertEquals(1, result.getUpdated());
    assertEquals(0, result.getCreated());
    verify(batchService).upsertBatch(any());
  }

  @Test
  @DisplayName("只填水表增量时，电表沿用上期读数不变")
  void onlyWaterDeltaKeepsElectric() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(1L, target))
        .thenReturn(reading(1L, target.minusMonths(1), 100.0, 1000.0));

    service.generateByDelta(List.of(1L), target, 8.0, null, false);

    ArgumentCaptor<List<MeterReadingBatchService.MeterUpsertItem>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(batchService).upsertBatch(captor.capture());
    MeterReadingBatchService.MeterUpsertItem item = captor.getValue().get(0);

    assertEquals(108.0, item.getWaterReading(), 0.001);
    assertEquals(1000.0, item.getElectricReading(), 0.001, "未填电表增量时不应改动电表");
  }

  @Test
  @DisplayName("参数校验：空房间、未来日期、负增量都拒绝")
  void validatesInput() {
    assertThrows(IllegalArgumentException.class,
        () -> service.generateByDelta(List.of(), target, 1.0, 1.0, false), "空房间列表");

    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));

    assertThrows(IllegalArgumentException.class,
        () -> service.generateByDelta(List.of(1L), LocalDate.now().plusDays(1), 1.0, 1.0, false),
        "未来日期");

    assertThrows(IllegalArgumentException.class,
        () -> service.generateByDelta(List.of(1L), target, -1.0, 1.0, false), "负水表增量");

    assertThrows(IllegalArgumentException.class,
        () -> service.generateByDelta(List.of(1L), target, 1.0, -5.0, false), "负电表增量");

    assertThrows(IllegalArgumentException.class,
        () -> service.generateByDelta(List.of(1L), target, null, null, false), "两个增量都为空");
  }

  @Test
  @DisplayName("部分房间不存在时整体拒绝，不静默忽略")
  void rejectsMissingRooms() {
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room(1L, "101")));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.generateByDelta(List.of(1L, 999L), target, 8.0, 150.0, false));
    assertTrue(ex.getMessage().contains("不存在"), "应说明房间不存在");
  }
}
