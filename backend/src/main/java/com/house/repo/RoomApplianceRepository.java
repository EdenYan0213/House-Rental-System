package com.house.repo;

import com.house.model.RoomAppliance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomApplianceRepository extends JpaRepository<RoomAppliance, Long> {
  List<RoomAppliance> findByRoomIdOrderByIdDesc(Long roomId);
}
