package com.house.controller;

import com.house.model.GuideDismiss;
import com.house.model.User;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.GuideDismissRepository;
import com.house.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/guide")
@CrossOrigin(origins = "*")
public class GuideController {

  private final UserService userService;
  private final GuideDismissRepository guideDismissRepository;
  private final BuildingRepository buildingRepository;
  private final ContractRepository contractRepository;

  public GuideController(
      UserService userService,
      GuideDismissRepository guideDismissRepository,
      BuildingRepository buildingRepository,
      ContractRepository contractRepository) {
    this.userService = userService;
    this.guideDismissRepository = guideDismissRepository;
    this.buildingRepository = buildingRepository;
    this.contractRepository = contractRepository;
  }

  @PostMapping("/dismiss")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'USER')")
  public ResponseEntity<?> dismiss(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestBody Map<String, String> request) {
    User user = userService.findByUsername(userDetails.getUsername());
    if (user == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
    }

    String dismissKey = request.get("key");
    if (dismissKey == null || dismissKey.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "key不能为空"));
    }

    GuideDismiss record = guideDismissRepository
        .findByUserIdAndDismissKey(user.getId(), dismissKey)
        .orElseGet(GuideDismiss::new);
    record.setUserId(user.getId());
    record.setDismissKey(dismissKey);
    record.setDismissedAt(LocalDateTime.now());
    guideDismissRepository.save(record);

    return ResponseEntity.ok(Map.of("message", "ok", "key", dismissKey));
  }

  @PostMapping("/reset")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'USER')")
  @Transactional
  public ResponseEntity<?> reset(@AuthenticationPrincipal UserDetails userDetails) {
    User user = userService.findByUsername(userDetails.getUsername());
    if (user == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
    }

    user.setFirstLogin(true);
    user.setShowContextTips(true);
    user.setShowTaskCard(true);
    userService.updateUser(user);

    guideDismissRepository.deleteByUserId(user.getId());

    return ResponseEntity.ok(Map.of("message", "引导状态已重置"));
  }

  @GetMapping("/task-progress")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'USER')")
  public ResponseEntity<?> taskProgress(@AuthenticationPrincipal UserDetails userDetails) {
    User user = userService.findByUsername(userDetails.getUsername());
    if (user == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
    }

    Set<String> dismissedKeys = new HashSet<>();
    for (GuideDismiss item : guideDismissRepository.findByUserId(user.getId())) {
      dismissedKeys.add(item.getDismissKey());
    }

    boolean profileDone = user.getRealName() != null && !user.getRealName().isBlank()
        && ((user.getPhone() != null && !user.getPhone().isBlank())
            || (user.getEmail() != null && !user.getEmail().isBlank()));
    boolean firstBuildingDone = !buildingRepository.findAll().isEmpty();
    boolean firstContractDone = !contractRepository.findAll().isEmpty();
    boolean helpDone = dismissedKeys.contains("help:center");
    boolean tourDone = Boolean.FALSE.equals(user.getFirstLogin()) || dismissedKeys.contains("tour:dashboard");

    List<Map<String, Object>> tasks = new ArrayList<>();
    tasks.add(makeTask("tour", "完成首次欢迎引导", tourDone));
    tasks.add(makeTask("profile", "完善个人信息", profileDone));
    tasks.add(makeTask("building", "录入第一栋房屋", firstBuildingDone));
    tasks.add(makeTask("contract", "创建第一份合同", firstContractDone));
    tasks.add(makeTask("help", "浏览一次帮助中心", helpDone));

    long completed = tasks.stream().filter(t -> Boolean.TRUE.equals(t.get("done"))).count();
    int percent = tasks.isEmpty() ? 0 : (int) Math.round((completed * 100.0) / tasks.size());

    Map<String, Object> result = new HashMap<>();
    result.put("percent", percent);
    result.put("completed", completed);
    result.put("total", tasks.size());
    result.put("tasks", tasks);
    result.put("showTaskCard", user.getShowTaskCard());

    return ResponseEntity.ok(result);
  }

  private Map<String, Object> makeTask(String key, String title, boolean done) {
    Map<String, Object> task = new HashMap<>();
    task.put("key", key);
    task.put("title", title);
    task.put("done", done);
    return task;
  }
}
