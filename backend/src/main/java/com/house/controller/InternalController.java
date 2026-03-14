package com.house.controller;

import com.house.model.User;
import com.house.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/internal")
@CrossOrigin(origins = "*")
public class InternalController {

    @Autowired
    private UserService userService;

    // 临时接口：仅在本地开发时使用，需传递 token
    @PostMapping("/reset-admin-password")
    public ResponseEntity<?> resetAdminPassword(@RequestParam String token, @RequestParam String newPassword,
            HttpServletRequest request) {
        // allow if correct token provided (dev-only)
        if (!"DEV_RESET_TOKEN_2026".equals(token)) {
            String remote = request.getRemoteAddr();
            if (!"127.0.0.1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote) && !"::1".equals(remote)
                    && !"localhost".equals(remote)) {
                return ResponseEntity.status(403).body("forbidden");
            }
        }

        User admin = userService.findByUsername("admin");
        if (admin == null)
            return ResponseEntity.badRequest().body("admin not found");
        userService.resetPassword(admin.getId(), newPassword);
        return ResponseEntity.ok("ok");
    }
}
