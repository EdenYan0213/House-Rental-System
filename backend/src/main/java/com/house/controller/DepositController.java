package com.house.controller;

import com.house.model.DepositRecord;
import com.house.repo.ContractRepository;
import com.house.repo.DepositRecordRepository;
import com.house.repo.RoomRepository;
import com.house.repo.TenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deposits")
public class DepositController {

  private final DepositRecordRepository depositRecordRepository;
  private final ContractRepository contractRepository;
  private final TenantRepository tenantRepository;
  private final RoomRepository roomRepository;

  public DepositController(DepositRecordRepository depositRecordRepository,
      ContractRepository contractRepository, TenantRepository tenantRepository,
      RoomRepository roomRepository) {
    this.depositRecordRepository = depositRecordRepository;
    this.contractRepository = contractRepository;
    this.tenantRepository = tenantRepository;
    this.roomRepository = roomRepository;
  }

  /** 填充列表展示所需的合同号/租户姓名/房间号。 */
  private void enrich(DepositRecord record) {
    if (record.getContractId() == null) {
      return;
    }
    contractRepository.findById(record.getContractId()).ifPresent(c -> {
      record.setContractNo(c.getContractNo());
      record.setRoomNumber(c.getRoomNumber());
      record.setTenantName(c.getTenantName());
      // 合同上的冗余字段可能为空，回查房间与租户兜底
      if (record.getRoomNumber() == null && c.getRoomId() != null) {
        roomRepository.findById(c.getRoomId())
            .ifPresent(r -> record.setRoomNumber(r.getRoomNumber()));
      }
      if (record.getTenantName() == null && c.getTenantId() != null) {
        tenantRepository.findById(c.getTenantId())
            .ifPresent(t -> record.setTenantName(t.getName()));
      }
    });
  }

  /**
   * 押金记录列表。
   *
   * <p>此前忽略全部查询参数并直接返回 findAll()，导致前端筛选与分页无效。
   * 现在按类型/日期过滤后再分页，返回结构与其它分页接口一致（content/totalElements）。
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public Map<String, Object> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    List<DepositRecord> records = depositRecordRepository.findAll();

    if (type != null && !type.isBlank()) {
      records = records.stream()
          .filter(r -> type.equalsIgnoreCase(r.getType()))
          .collect(Collectors.toList());
    }
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      records = records.stream()
          .filter(r -> r.getDate() != null && !r.getDate().isBefore(start))
          .collect(Collectors.toList());
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      records = records.stream()
          .filter(r -> r.getDate() != null && !r.getDate().isAfter(end))
          .collect(Collectors.toList());
    }

    // 按日期倒序，最新的押金变动排前面
    records = records.stream()
        .sorted(Comparator.comparing(DepositRecord::getDate,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .collect(Collectors.toList());

    // 填充合同号/租户/房间，否则前端这几列始终为空
    records.forEach(this::enrich);

    int total = records.size();
    int from = Math.max(0, page * size);
    int to = Math.min(total, from + size);
    List<DepositRecord> content = from >= total ? List.of() : records.subList(from, to);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", total);
    result.put("page", page);
    result.put("size", size);
    return result;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public DepositRecord create(@RequestBody DepositRecord depositRecord) {
    return depositRecordRepository.save(depositRecord);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public ResponseEntity<DepositRecord> update(@PathVariable Long id, @RequestBody DepositRecord depositRecord) {
    return depositRecordRepository.findById(id).map(existing -> {
      depositRecord.setId(id);
      return ResponseEntity.ok(depositRecordRepository.save(depositRecord));
    }).orElse(ResponseEntity.notFound().build());
  }
}
