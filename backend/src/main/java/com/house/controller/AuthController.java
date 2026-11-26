package com.house.controller;

import com.house.dto.LoginRequest;
import com.house.dto.LoginResponse;
import com.house.model.User;
import com.house.service.OperationLogService;
import com.house.service.UserService;
import com.house.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AuthController.class);


    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userService.findByUsername(request.getUsername());

            if (user == null) {
                operationLogService.log("登录认证", "登录", null, request.getUsername(), null, null,
                        "登录失败: 用户不存在(" + request.getUsername() + ")", httpRequest,
                        false, "用户不存在", null, request);
                return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
            }

            if (!user.getEnabled()) {
                operationLogService.log("登录认证", "登录", String.valueOf(user.getId()), request.getUsername(), null,
                        null,
                        "登录失败: 账号禁用(" + request.getUsername() + ")", httpRequest,
                        false, "账号禁用", null, request);
                return ResponseEntity.badRequest().body(Map.of("message", "账号已被禁用"));
            }

            if (!userService.validatePassword(request.getPassword(), user.getPassword())) {
                operationLogService.log("登录认证", "登录", String.valueOf(user.getId()), request.getUsername(), null,
                        null,
                        "登录失败: 密码错误(" + request.getUsername() + ")", httpRequest,
                        false, "密码错误", null, request);
                return ResponseEntity.badRequest().body(Map.of("message", "密码错误"));
            }

            userService.updateLastLoginTime(user.getId());

            String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole().name());

            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .role(user.getRole().name())
                    .userId(user.getId())
                    .email(user.getEmail())
                    .themePreference(user.getThemePreference())
                    .firstLogin(user.getFirstLogin())
                    .showContextTips(user.getShowContextTips())
                    .showTaskCard(user.getShowTaskCard())
                    .build();

            operationLogService.log("登录认证", "登录", String.valueOf(user.getId()), request.getUsername(), null, null,
                    "登录成功: " + request.getUsername(), httpRequest,
                    true, null, null, request);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("登录异常 username={}", request.getUsername(), e);
            // e.getMessage() 可能为 null，Map.of 会 NPE
            String reason = e.getMessage() == null ? "系统异常" : e.getMessage();
            operationLogService.log("登录认证", "登录", null, request.getUsername(), null, null,
                    "登录失败: 系统异常(" + request.getUsername() + ")", httpRequest,
                    false, reason, null, request);
            return ResponseEntity.status(500).body(Map.of("message", "内部错误", "error", reason));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userService.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在"));
        }

        User created = userService.createUser(user);
        created.setPassword(null);

        return ResponseEntity.ok(created);
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("message", "未登录"));
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.badRequest().body(Map.of("message", "token无效"));
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userService.findById(userId);

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("role", user.getRole().name());
        result.put("phone", user.getPhone());
        result.put("email", user.getEmail());
        result.put("themePreference", user.getThemePreference());
        result.put("firstLogin", user.getFirstLogin());
        result.put("showContextTips", user.getShowContextTips());
        result.put("showTaskCard", user.getShowTaskCard());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/changePassword")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String username = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                username = jwtUtil.getUsernameFromToken(token);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "token无效"));
            }
        }

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "未登录"));
        }

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不能为空"));
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }

        if (!userService.validatePassword(oldPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "原密码错误"));
        }

        user.setPassword(newPassword);
        userService.updateUser(user);

        operationLogService.log(
                "auth", "change_password", String.valueOf(user.getId()), username,
                Map.of("username", username), Map.of("username", username),
                "修改密码: " + username, httpRequest, true, null, null,
                Map.of("username", username, "oldPassword", oldPassword, "newPassword", newPassword));

        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }
}
