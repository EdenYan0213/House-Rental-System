package com.house.repo;

import com.house.model.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {
  List<MeterReading> findByRecordDate(LocalDate recordDate);

  MeterReading findByRoomIdAndRecordDate(Long roomId, LocalDate recordDate);

  List<MeterReading> findByRoomIdAndRecordDateBetweenOrderByRecordDateAsc(Long roomId, LocalDate start, LocalDate end);

  List<MeterReading> findByRoomIdOrderByRecordDateDesc(Long roomId);

  MeterReading findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(Long roomId, LocalDate date);

  MeterReading findTopByRoomIdAndRecordDateLessThanEqualOrderByRecordDateDesc(Long roomId, LocalDate date);
}
