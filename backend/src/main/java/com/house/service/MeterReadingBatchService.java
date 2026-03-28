package com.house.service;

import com.house.model.MeterReading;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MeterReadingBatchService {

  private final MeterReadingRepository meterReadingRepository;
  private final RoomRepository roomRepository;

  public MeterReadingBatchService(MeterReadingRepository meterReadingRepository, RoomRepository roomRepository) {
    this.meterReadingRepository = meterReadingRepository;
    this.roomRepository = roomRepository;
  }

  @Transactional
  public List<MeterReading> upsertBatch(List<MeterUpsertItem> items) {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("没有可保存的数据");
    }

    Set<Long> roomIds = new LinkedHashSet<>();
    Set<String> duplicateKeys = new LinkedHashSet<>();
    List<MeterUpsertItem> normalized = new ArrayList<>();

    for (MeterUpsertItem item : items) {
      if (item == null || item.getRoomId() == null) {
        throw new IllegalArgumentException("房间不能为空");
      }
      if (item.getRecordDate() == null) {
        throw new IllegalArgumentException("录入日期不能为空");
      }

      Double waterReading = item.getWaterReading() == null ? 0.0 : item.getWaterReading();
      Double electricReading = item.getElectricReading() == null ? 0.0 : item.getElectricReading();
      if (waterReading < 0 || electricReading < 0) {
        throw new IllegalArgumentException("水电读数不能为负数");
      }

      String key = item.getRoomId() + "|" + item.getRecordDate();
      if (!duplicateKeys.add(key)) {
        throw new IllegalArgumentException("批量数据包含重复记录: roomId=" + item.getRoomId() + ", date=" + item.getRecordDate());
      }

      roomIds.add(item.getRoomId());
      normalized.add(new MeterUpsertItem(item.getRoomId(), item.getRecordDate(), waterReading, electricReading));
    }

    if (roomRepository.findByIdIn(new ArrayList<>(roomIds)).size() != roomIds.size()) {
      throw new IllegalArgumentException("批量数据中包含不存在的房间");
    }

    Map<String, MeterReading> existingMap = new LinkedHashMap<>();
    for (MeterUpsertItem item : normalized) {
      MeterReading existing = meterReadingRepository.findByRoomIdAndRecordDate(item.getRoomId(), item.getRecordDate());
      if (existing != null) {
        existingMap.put(item.getRoomId() + "|" + item.getRecordDate(), existing);
      }
    }

    List<MeterReading> toSave = new ArrayList<>();
    for (MeterUpsertItem item : normalized) {
      String key = item.getRoomId() + "|" + item.getRecordDate();
      MeterReading reading = existingMap.get(key);
      if (reading == null) {
        reading = MeterReading.builder()
            .roomId(item.getRoomId())
            .recordDate(item.getRecordDate())
            .build();
      }
      reading.setWaterReading(item.getWaterReading());
      reading.setElectricReading(item.getElectricReading());
      toSave.add(reading);
    }

    return meterReadingRepository.saveAll(toSave);
  }

  public static class MeterUpsertItem {
    private final Long roomId;
    private final LocalDate recordDate;
    private final Double waterReading;
    private final Double electricReading;

    public MeterUpsertItem(Long roomId, LocalDate recordDate, Double waterReading, Double electricReading) {
      this.roomId = roomId;
      this.recordDate = recordDate;
      this.waterReading = waterReading;
      this.electricReading = electricReading;
    }

    public Long getRoomId() {
      return roomId;
    }

    public LocalDate getRecordDate() {
      return recordDate;
    }

    public Double getWaterReading() {
      return waterReading;
    }

    public Double getElectricReading() {
      return electricReading;
    }
  }
}
