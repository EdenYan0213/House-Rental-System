package com.house.service;

import com.house.model.MeterReading;
import com.house.model.Room;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 抄表读数倒退校验测试。
 *
 * <p>守护一个真实风险：水电表读数只会增不会减，录入时写成更小的数字
 * 会生成金额离谱的账单（差额为负或异常大）。此前没有任何校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("抄表读数校验")
class MeterReadingRegressionTest {

  @Mock
  private MeterReadingRepository meterReadingRepository;
  @Mock
  private RoomRepository roomRepository;

  private MeterReadingBatchService service;

  private final LocalDate today = LocalDate.of(2026, 6, 1);
  private final LocalDate lastMonth = LocalDate.of(2026, 5, 1);

  @BeforeEach
  void setUp() {
    service = new MeterReadingBatchService(meterReadingRepository, roomRepository);
    Room room = new Room();
    room.setId(10L);
    when(roomRepository.findByIdIn(anyList())).thenReturn(List.of(room));
    when(meterReadingRepository.findByRoomIdAndRecordDate(any(), any())).thenReturn(null);
    when(meterReadingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
  }

  private MeterReading previous(double water, double electric) {
    return MeterReading.builder()
        .roomId(10L)
        .recordDate(lastMonth)
        .waterReading(water)
        .electricReading(electric)
        .build();
  }

  private List<MeterReadingBatchService.MeterUpsertItem> single(double water, double electric) {
    return List.of(new MeterReadingBatchService.MeterUpsertItem(10L, today, water, electric));
  }

  @Test
  @DisplayName("读数正常递增时可以保存")
  void allowsIncreasing() {
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(10L, today))
        .thenReturn(previous(100, 500));

    List<MeterReading> saved = service.upsertBatch(single(120, 560));

    assertEquals(1, saved.size());
    verify(meterReadingRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("水表读数变小则拒绝，并提示上次读数")
  void rejectsWaterRegression() {
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(10L, today))
        .thenReturn(previous(100, 500));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.upsertBatch(single(80, 560)));

    assertTrue(ex.getMessage().contains("水表"), "应指明水表: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("100"), "应提示上次读数: " + ex.getMessage());
    verify(meterReadingRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("电表读数变小则拒绝")
  void rejectsElectricRegression() {
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(10L, today))
        .thenReturn(previous(100, 500));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.upsertBatch(single(120, 400)));

    assertTrue(ex.getMessage().contains("电表"), "应指明电表: " + ex.getMessage());
    verify(meterReadingRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("读数持平允许（未用水电的月份）")
  void allowsEqualReading() {
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(10L, today))
        .thenReturn(previous(100, 500));

    assertDoesNotThrow(() -> service.upsertBatch(single(100, 500)));
  }

  @Test
  @DisplayName("首月无历史记录时不做倒退校验")
  void noPreviousAllowsAnything() {
    when(meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(10L, today))
        .thenReturn(null);

    assertDoesNotThrow(() -> service.upsertBatch(single(10, 20)));
  }

  @Test
  @DisplayName("负数读数仍被拒绝")
  void rejectsNegative() {
    assertThrows(IllegalArgumentException.class, () -> service.upsertBatch(single(-1, 100)));
  }
}
