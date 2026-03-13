package com.house.controller;

import com.house.model.FeeItem;
import com.house.repo.FeeItemRepository;
import org.springframework.http.ResponseEntity;
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
  public List<FeeItem> list() {
    return feeItemRepository.findAll();
  }

  @PostMapping
  public FeeItem create(@RequestBody FeeItem feeItem) {
    return feeItemRepository.save(feeItem);
  }

  @PutMapping("/{id}")
  public ResponseEntity<FeeItem> update(@PathVariable Long id, @RequestBody FeeItem feeItem) {
    return feeItemRepository.findById(id).map(existing -> {
      feeItem.setId(id);
      return ResponseEntity.ok(feeItemRepository.save(feeItem));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    feeItemRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
