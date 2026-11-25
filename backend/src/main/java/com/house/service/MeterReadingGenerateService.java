package com.house.service;

import com.house.model.MeterReading;
import com.house.model.Room;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按「上期读数 + 增量」批量生成抄表记录。
 *
 * <p>对应的日常场景：大批房间的抄表只是估算或统一递增，
 * 逐个手填既慢又容易出错。这里让管理员勾若干房间、给一组增量，
 * 由系统在上期读数基础上累加，生成当期读数。
 *
 * <p>与 Excel 导入的区别：导入要求精确读数，这里只需要增量，
 * 适合「先批量出一版、个别房间再修正」的工作流。
 */
@Service
public class MeterReadingGenerateService {

  private final MeterReadingRepository meterReadingRepository;
  private final RoomRepository roomRepository;
  private final MeterReadingBatchService batchService;

  public MeterReadingGenerateService(MeterReadingRepository meterReadingRepository,
      RoomRepository roomRepository,
      MeterReadingBatchService batchService) {
    this.meterReadingRepository = meterReadingRepository;
    this.roomRepository = roomRepository;
    this.batchService = batchService;
  }

  /**
   * 生成结果。区分「写入成功」和「跳过」，让前端能解释每个房间的去向。
   */
  public static class GenerateResult {
    private final int created;
    private final int updated;
    private final List<Map<String, Object>> skipped;
    private final List<Long> affectedRoomIds;

    public GenerateResult(int created, int updated, List<Map<String, Object>> skipped, List<Long> affectedRoomIds) {
      this.created = created;
      this.updated = updated;
      this.skipped = skipped;
      this.affectedRoomIds = affectedRoomIds;
    }

    public int getCreated() {
      return created;
    }

    public int getUpdated() {
      return updated;
    }

    public List<Map<String, Object>> getSkipped() {
      return skipped;
    }

    public List<Long> getAffectedRoomIds() {
      return affectedRoomIds;
    }
  }

  /**
   * 按上期读数 + 增量生成当期抄表。
   *
   * @param roomIds      目标房间
   * @param recordDate   本次抄表日期
   * @param waterDelta   水表增量（可为 null 表示不生成水表变化）
   * @param electricDelta 电表增量（可为 null）
   * @param overwrite    该日期已有记录时是否覆盖
   */
  @Transactional
  public GenerateResult generateByDelta(List<Long> roomIds, LocalDate recordDate,
      Double waterDelta, Double electricDelta, boolean overwrite) {
    if (roomIds == null || roomIds.isEmpty()) {
      throw new IllegalArgumentException("请先选择房间");
    }
    if (recordDate == null) {
      throw new IllegalArgumentException("请选择抄表日期");
    }
    if (recordDate.isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("抄表日期不能晚于今天");
    }
    if (waterDelta == null && electricDelta == null) {
      throw new IllegalArgumentException("请至少填写水表或电表的增量");
    }
    if (waterDelta != null && waterDelta < 0) {
      throw new IllegalArgumentException("水表增量不能为负数");
    }
    if (electricDelta != null && electricDelta < 0) {
      throw new IllegalArgumentException("电表增量不能为负数");
    }

    List<Room> rooms = roomRepository.findByIdIn(roomIds);
    if (rooms.size() != roomIds.size()) {
      // 房间可能已被删除，指出数量差而不是静默忽略
      throw new IllegalArgumentException("部分房间不存在或已删除，请刷新后重试");
    }

    List<MeterReadingBatchService.MeterUpsertItem> items = new ArrayList<>();
    List<Map<String, Object>> skipped = new ArrayList<>();
    List<Long> affected = new ArrayList<>();
    int updated = 0;

    for (Room room : rooms) {
      // 取「该日期之前」的最后一条作为基线：
      // 补录历史月份时，不能拿更晚的读数当基线，否则会生成倒退的读数。
      MeterReading baseline = meterReadingRepository
          .findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(room.getId(), recordDate);

      Double baseWater = baseline == null ? null : baseline.getWaterReading();
      Double baseElec = baseline == null ? null : baseline.getElectricReading();

      if (baseWater == null && baseElec == null) {
        // 没有任何历史读数就无法推算增量，跳过并说明原因
        Map<String, Object> skip = new LinkedHashMap<>();
        skip.put("roomId", room.getId());
        skip.put("roomNumber", room.getRoomNumber());
        skip.put("reason", "该房间没有历史读数，无法按增量推算，请手动录入首次读数");
        skipped.add(skip);
        continue;
      }

      MeterReading existing = meterReadingRepository
          .findByRoomIdAndRecordDate(room.getId(), recordDate);

      if (existing != null && !overwrite) {
        Map<String, Object> skip = new LinkedHashMap<>();
        skip.put("roomId", room.getId());
        skip.put("roomNumber", room.getRoomNumber());
        skip.put("reason", "该日期已有抄表记录（未勾选覆盖）");
        skipped.add(skip);
        continue;
      }
      if (existing != null) {
        updated++;
      }

      double newWater = waterDelta == null
          ? (baseWater == null ? 0.0 : baseWater)
          : (baseWater == null ? 0.0 : baseWater) + waterDelta;
      double newElec = electricDelta == null
          ? (baseElec == null ? 0.0 : baseElec)
          : (baseElec == null ? 0.0 : baseElec) + electricDelta;

      items.add(new MeterReadingBatchService.MeterUpsertItem(
          room.getId(), recordDate, newWater, newElec));
      affected.add(room.getId());
    }

    if (items.isEmpty()) {
      return new GenerateResult(0, 0, skipped, affected);
    }

    batchService.upsertBatch(items);
    return new GenerateResult(items.size() - updated, updated, skipped, affected);
  }
}
