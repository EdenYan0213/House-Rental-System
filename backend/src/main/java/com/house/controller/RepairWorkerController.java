package com.house.controller;

import com.house.model.RepairWorker;
import com.house.repo.RepairWorkerRepository;
import com.house.service.RepairOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair-workers")
public class RepairWorkerController {
  private final RepairWorkerRepository repairWorkerRepository;
  private final RepairOrderService repairOrderService;

  public RepairWorkerController(RepairWorkerRepository repairWorkerRepository, RepairOrderService repairOrderService) {
    this.repairWorkerRepository = repairWorkerRepository;
    this.repairOrderService = repairOrderService;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(required = false) Integer status) {
    List<RepairWorker> workers = status == null ? repairWorkerRepository.findAll()
        : repairWorkerRepository.findByStatusOrderByIdDesc(status);
    return workers.stream().map(w -> {
      Map<String, Object> row = new HashMap<>();
      row.put("id", w.getId());
      row.put("name", w.getName());
      row.put("phone", w.getPhone());
      row.put("status", w.getStatus());
      row.put("remark", w.getRemark());
      row.put("createdAt", w.getCreatedAt());
      row.putAll(repairOrderService.workerStats(w.getId()));
      return row;
    }).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairWorker create(@RequestBody RepairWorker worker) {
    worker.setCreatedAt(LocalDateTime.now());
    if (worker.getStatus() == null) {
      worker.setStatus(1);
    }
    return repairWorkerRepository.save(worker);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<RepairWorker> update(@PathVariable Long id, @RequestBody RepairWorker worker) {
    return repairWorkerRepository.findById(id).map(exists -> {
      worker.setId(id);
      worker.setCreatedAt(exists.getCreatedAt());
      if (worker.getStatus() == null) {
        worker.setStatus(exists.getStatus() == null ? 1 : exists.getStatus());
      }
      return ResponseEntity.ok(repairWorkerRepository.save(worker));
    }).orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}/toggle")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<RepairWorker> toggle(@PathVariable Long id) {
    return repairWorkerRepository.findById(id).map(worker -> {
      worker.setStatus(worker.getStatus() != null && worker.getStatus() == 1 ? 0 : 1);
      return ResponseEntity.ok(repairWorkerRepository.save(worker));
    }).orElse(ResponseEntity.notFound().build());
  }
}
