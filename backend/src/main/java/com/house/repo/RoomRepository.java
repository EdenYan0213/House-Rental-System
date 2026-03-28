package com.house.repo;

import java.util.List;

import com.house.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
  List<Room> findByBuildingId(Long buildingId);

  List<Room> findByBuildingIdAndFloor(Long buildingId, Integer floor);

  List<Room> findByBuildingIdAndFloorAndStatus(Long buildingId, Integer floor, String status);

  java.util.Optional<Room> findByBuildingIdAndRoomNumber(Long buildingId, String roomNumber);

  List<Room> findByIdIn(List<Long> ids);
}
