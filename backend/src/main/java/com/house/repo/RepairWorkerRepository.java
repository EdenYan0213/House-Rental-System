package com.house.repo;

import com.house.model.RepairWorker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairWorkerRepository extends JpaRepository<RepairWorker, Long> {
  List<RepairWorker> findByStatusOrderByIdDesc(Integer status);
}
