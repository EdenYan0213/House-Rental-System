package com.house.controller;

import com.house.model.ApplianceTemplate;
import com.house.repo.ApplianceTemplateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/appliance-templates")
public class ApplianceTemplateController {
  private final ApplianceTemplateRepository applianceTemplateRepository;

  public ApplianceTemplateController(ApplianceTemplateRepository applianceTemplateRepository) {
    this.applianceTemplateRepository = applianceTemplateRepository;
  }

  @GetMapping
  public List<ApplianceTemplate> list() {
    return applianceTemplateRepository.findAllByOrderBySortOrderAscIdAsc();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ApplianceTemplate create(@RequestBody ApplianceTemplate template) {
    if (template.getDefaultQuantity() == null || template.getDefaultQuantity() < 1) {
      template.setDefaultQuantity(1);
    }
    if (template.getUnit() == null || template.getUnit().isBlank()) {
      template.setUnit("个");
    }
    if (template.getSortOrder() == null) {
      template.setSortOrder(0);
    }
    template.setCreatedAt(LocalDateTime.now());
    return applianceTemplateRepository.save(template);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<ApplianceTemplate> update(@PathVariable Long id, @RequestBody ApplianceTemplate template) {
    return applianceTemplateRepository.findById(id).map(exists -> {
      template.setId(id);
      template.setCreatedAt(exists.getCreatedAt());
      if (template.getDefaultQuantity() == null || template.getDefaultQuantity() < 1) {
        template.setDefaultQuantity(1);
      }
      if (template.getUnit() == null || template.getUnit().isBlank()) {
        template.setUnit("个");
      }
      if (template.getSortOrder() == null) {
        template.setSortOrder(0);
      }
      return ResponseEntity.ok(applianceTemplateRepository.save(template));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    applianceTemplateRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
