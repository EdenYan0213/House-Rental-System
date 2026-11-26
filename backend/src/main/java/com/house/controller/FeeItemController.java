package com.house.controller;

import com.house.model.FeeItem;
import com.house.repo.FeeItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fee-items")
public class FeeItemController {

  private final FeeItemRepository feeItemRepository;

  public FeeItemController(FeeItemRepository feeItemRepository) {
    this.feeItemRepository = feeItemRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public List<FeeItem> list() {
    return feeItemRepository.findAll();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public FeeItem create(@RequestBody FeeItem feeItem) {
    return feeItemRepository.save(feeItem);
  }

  /**
   * 更新费用项（**部分更新**）。
   *
   * <p>编辑弹窗提交全字段，但列表上的"启用/停用"开关只传 `{enabled}`。
   * 直接 `save(incoming)` 会把未传的 name/unitPrice/unit/category 覆盖成 null，
   * 所以这里按字段合并。
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public ResponseEntity<FeeItem> update(@PathVariable Long id, @RequestBody FeeItem feeItem) {
    return feeItemRepository.findById(id).map(existing -> {
      if (feeItem.getName() != null) existing.setName(feeItem.getName());
      if (feeItem.getUnitPrice() != null) existing.setUnitPrice(feeItem.getUnitPrice());
      if (feeItem.getUnit() != null) existing.setUnit(feeItem.getUnit());
      if (feeItem.getCategory() != null) existing.setCategory(feeItem.getCategory());
      if (feeItem.getEnabled() != null) existing.setEnabled(feeItem.getEnabled());
      return ResponseEntity.ok(feeItemRepository.save(existing));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    feeItemRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
