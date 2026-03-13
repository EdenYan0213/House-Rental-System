package com.house.controller;

import com.house.model.DepositRecord;
import com.house.repo.DepositRecordRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deposits")
public class DepositController {

  private final DepositRecordRepository depositRecordRepository;

  public DepositController(DepositRecordRepository depositRecordRepository) {
    this.depositRecordRepository = depositRecordRepository;
  }

  @GetMapping
  public List<DepositRecord> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    List<DepositRecord> records = depositRecordRepository.findAll();
    return records;
  }

  @PostMapping
  public DepositRecord create(@RequestBody DepositRecord depositRecord) {
    return depositRecordRepository.save(depositRecord);
  }

  @PutMapping("/{id}")
  public ResponseEntity<DepositRecord> update(@PathVariable Long id, @RequestBody DepositRecord depositRecord) {
    return depositRecordRepository.findById(id).map(existing -> {
      depositRecord.setId(id);
      return ResponseEntity.ok(depositRecordRepository.save(depositRecord));
    }).orElse(ResponseEntity.notFound().build());
  }
}
