package com.house.controller;

import com.house.model.BackupConfig;
import com.house.model.BackupRecord;
import com.house.model.RestoreLog;
import com.house.service.BackupService;
import com.house.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);

    @Autowired
    private BackupService backupService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/records")
    public ResponseEntity<List<BackupRecord>> getAllBackups(
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<BackupRecord> backups;
        if (type != null && !type.isEmpty()) {
            backups = backupService.getBackupsByType(type);
        } else {
            backups = backupService.getAllBackups();
        }
        return ResponseEntity.ok(backups);
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<BackupRecord> getBackup(@PathVariable Long id, HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        BackupRecord backup = backupService.getBackupById(id);
        if (backup == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(backup);
    }

    @PostMapping("/manual")
    public ResponseEntity<BackupRecord> createManualBackup(HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Long operatorId = getCurrentUserId(request);
            BackupRecord backup = backupService.createBackup("manual", operatorId);
            return ResponseEntity.ok(backup);
        } catch (Exception e) {
            BackupRecord failed = new BackupRecord();
            failed.setBackupTime(java.time.LocalDateTime.now());
            failed.setType("manual");
            failed.setStatus("failed");
            failed.setFailReason(e.getMessage());
            return ResponseEntity.ok(failed);
        }
    }

    @GetMapping("/manual")
    public ResponseEntity<BackupRecord> createManualBackupByGet(HttpServletRequest request) {
        return createManualBackup(request);
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Void> deleteBackup(@PathVariable Long id, HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        backupService.deleteBackup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable Long id, HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            java.nio.file.Path filePath = backupService.getBackupFilePath(id);
            Resource resource = new FileSystemResource(filePath);

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            BackupRecord backup = backupService.getBackupById(id);
            String filename = backup != null ? backup.getFileName() : "backup.zip";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/restore/{id}")
    public ResponseEntity<RestoreLog> restoreBackup(@PathVariable Long id, HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Long operatorId = getCurrentUserId(request);
            RestoreLog log = backupService.restoreBackup(id, operatorId);
            return ResponseEntity.ok(log);
        } catch (Exception e) {
            RestoreLog failed = new RestoreLog();
            failed.setBackupId(id);
            failed.setRestoreTime(java.time.LocalDateTime.now());
            failed.setStatus("failed");
            failed.setFailReason(e.getMessage());
            return ResponseEntity.ok(failed);
        }
    }

    @GetMapping("/restore/{id}")
    public ResponseEntity<RestoreLog> restoreBackupByGet(@PathVariable Long id, HttpServletRequest request) {
        return restoreBackup(id, request);
    }

    /**
     * 上传备份文件并直接恢复（跨设备恢复）
     */
    @PostMapping("/upload-and-restore")
    public ResponseEntity<RestoreLog> uploadAndRestore(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String filename = "uploaded_backup_" + System.currentTimeMillis() + ".zip";
            java.nio.file.Path tempPath = java.nio.file.Paths.get(tempDir, filename);
            file.transferTo(tempPath);

            Long operatorId = getCurrentUserId(request);
            RestoreLog log = backupService.restoreFromUploadedFile(tempPath.toString(), operatorId);

            java.nio.file.Files.deleteIfExists(tempPath);
            return ResponseEntity.ok(log);
        } catch (Exception e) {
            RestoreLog failed = new RestoreLog();
            failed.setRestoreTime(java.time.LocalDateTime.now());
            failed.setStatus("failed");
            failed.setFailReason(e.getMessage());
            return ResponseEntity.ok(failed);
        }
    }

    @GetMapping("/config")
    public ResponseEntity<BackupConfig> getConfig(HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(backupService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<BackupConfig> updateConfig(@RequestBody BackupConfig config, HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(backupService.saveConfig(config));
    }

    @GetMapping("/restore-logs")
    public ResponseEntity<List<RestoreLog>> getRestoreLogs(HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(backupService.getAllRestoreLogs());
    }

    @GetMapping("/maintenance-status")
    public ResponseEntity<Map<String, Boolean>> getMaintenanceStatus(HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(Map.of("enabled", backupService.isMaintenanceMode()));
    }

    @PostMapping("/maintenance/enable")
    public ResponseEntity<Void> enableMaintenance(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long operatorId = getCurrentUserId(request);
        String reason = body.getOrDefault("reason", "手动开启维护模式");
        backupService.enableMaintenanceMode(reason, operatorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/maintenance/disable")
    public ResponseEntity<Void> disableMaintenance(HttpServletRequest request) {
        if (!hasAnyRole(request, "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        backupService.disableMaintenanceMode();
        return ResponseEntity.ok().build();
    }

    private boolean hasAnyRole(HttpServletRequest request, String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("backup auth denied: unauthenticated request uri={}", request.getRequestURI());
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String authRole = authority.getAuthority();
            for (String allowed : roles) {
                if (("ROLE_" + allowed).equals(authRole)) {
                    return true;
                }
            }
        }

        logger.warn("backup auth denied: uri={}, user={}, authorities={}, requiredRoles={}",
                request.getRequestURI(),
                authentication.getName(),
                authentication.getAuthorities(),
                java.util.Arrays.toString(roles));

        return false;
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return jwtUtil.getUserIdFromToken(token);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
