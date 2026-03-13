package com.house.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.house.model.User;
import com.house.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<User> users = userService.searchUsers(username, realName, role, page, size);
        users.getRecords().forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userService.findAll();
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'USER')")
    public ResponseEntity<?> getUser(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        if (currentUser == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (currentUser.getRole() != com.house.model.Role.SUPER_ADMIN &&
                !currentUser.getId().equals(id)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权限查看其他用户信息"));
        }

        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不能为空"));
        }
        if (user.getRole() == null) {
            user.setRole(com.house.model.Role.USER);
        }

        User existing = userService.findByUsername(user.getUsername());
        if (existing != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在"));
        }

        User created = userService.createUser(user);
        created.setPassword(null);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        User existing = userService.findById(id);
        if (existing == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (existing.getRole() == com.house.model.Role.SUPER_ADMIN &&
                user.getRole() != com.house.model.Role.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改超级管理员的角色"));
        }

        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(user.getPassword());
        } else {
            user.setPassword(null);
        }

        User updated = userService.updateUser(user);
        updated.setPassword(null);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (user.getRole() == com.house.model.Role.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能删除超级管理员"));
        }

        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> enableUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (user.getRole() == com.house.model.Role.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能禁用超级管理员"));
        }

        userService.enableUser(id);
        return ResponseEntity.ok(Map.of("message", "启用成功"));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> disableUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (user.getRole() == com.house.model.Role.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能禁用超级管理员"));
        }

        userService.disableUser(id);
        return ResponseEntity.ok(Map.of("message", "禁用成功"));
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String newPassword = request.get("newPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "新密码不能为空"));
        }

        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        userService.resetPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "密码重置成功"));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'USER')")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        if (currentUser == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (request.containsKey("realName")) {
            currentUser.setRealName(request.get("realName"));
        }
        if (request.containsKey("phone")) {
            currentUser.setPhone(request.get("phone"));
        }
        if (request.containsKey("email")) {
            currentUser.setEmail(request.get("email"));
        }
        if (request.containsKey("themePreference")) {
            String value = request.get("themePreference");
            if (value != null) {
                String normalized = value.toLowerCase();
                if (!normalized.equals("light") && !normalized.equals("dark") && !normalized.equals("system")) {
                    return ResponseEntity.badRequest().body(Map.of("message", "主题配置无效"));
                }
                currentUser.setThemePreference(normalized);
            }
        }

        userService.updateUser(currentUser);
        currentUser.setPassword(null);
        return ResponseEntity.ok(currentUser);
    }

    @PutMapping("/change-password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'USER')")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不能为空"));
        }

        User currentUser = userService.findByUsername(userDetails.getUsername());
        if (currentUser == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (!userService.validatePassword(oldPassword, currentUser.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "原密码错误"));
        }

        currentUser.setPassword(newPassword);
        userService.updateUser(currentUser);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }
}
