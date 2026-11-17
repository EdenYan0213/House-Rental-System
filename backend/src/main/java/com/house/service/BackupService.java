package com.house.service;

import com.house.model.*;
import com.house.repo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

    @Autowired
    private BackupRecordRepository backupRecordRepository;

    @Autowired
    private RestoreLogRepository restoreLogRepository;

    @Autowired
    private BackupConfigRepository backupConfigRepository;

    @Autowired
    private MaintenanceModeRepository maintenanceModeRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String dbDriver;

    @Value("${app.file.upload-dir:./src/main/resources/imgs}")
    private String uploadDir;

    @Value("${app.backup.storage-path:./backups}")
    private String backupStoragePath;

    @Value("${app.backup.mysqldump-path:mysqldump}")
    private String mysqldumpPath;

    @Value("${app.backup.mysql-path:mysql}")
    private String mysqlPath;

    public List<BackupRecord> getAllBackups() {
        return backupRecordRepository.findAllByOrderByBackupTimeDesc();
    }

    public List<BackupRecord> getBackupsByType(String type) {
        return backupRecordRepository.findByTypeOrderByBackupTimeDesc(type);
    }

    public BackupConfig getConfig() {
        return backupConfigRepository.findById(1L).orElseGet(() -> {
            BackupConfig config = new BackupConfig();
            config.setEnabled(false);
            config.setBackupType("daily");
            config.setBackupDay(1);
            config.setBackupTime("02:00");
            config.setRetentionCount(7);
            config.setStoragePath("./backups");
            return backupConfigRepository.save(config);
        });
    }

    @Transactional
    public BackupConfig saveConfig(BackupConfig config) {
        config.setId(1L);
        return backupConfigRepository.save(config);
    }

    @Transactional
    public BackupRecord createBackup(String type, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "backup_" + timestamp + ".zip";

        BackupRecord record = new BackupRecord();
        record.setBackupTime(now);
        record.setType(type);
        record.setStatus("success");
        record.setFileName(fileName);
        record.setOperatorId(operatorId);

        try {
            Files.createDirectories(Paths.get(backupStoragePath));

            String dbName = extractDbName(dbUrl);
            String tempDir = System.getProperty("java.io.tmpdir");
            String sqlFile = tempDir + "backup_" + timestamp + ".sql";

            boolean dbBackupSuccess = backupDatabase(dbName, sqlFile);
            if (!dbBackupSuccess) {
                throw new RuntimeException("数据库备份失败");
            }

            String zipFilePath = backupStoragePath + "/" + fileName;
            boolean zipSuccess = createZipArchive(sqlFile, uploadDir, zipFilePath);
            if (!zipSuccess) {
                throw new RuntimeException("文件压缩失败");
            }

            long fileSize = Files.size(Paths.get(zipFilePath));
            String md5 = calculateMD5(zipFilePath);

            record.setFilePath(zipFilePath);
            record.setFileSize(fileSize);
            record.setMd5(md5);

            Files.deleteIfExists(Paths.get(sqlFile));

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
            if (request != null) {
                operationLogService.log("BACKUP", type.equals("auto") ? "自动备份" : "手动备份", request);
            }

            cleanOldBackups(type);

        } catch (Exception e) {
            logger.error("备份失败", e);
            record.setStatus("failed");
            record.setFailReason(e.getMessage());
            // Keep failed records insertable even when backup artifacts are missing.
            if (record.getFilePath() == null) {
                record.setFilePath(backupStoragePath + "/" + fileName);
            }
            if (record.getFileSize() == null) {
                record.setFileSize(0L);
            }
        }

        return backupRecordRepository.save(record);
    }

    private boolean backupDatabase(String dbName, String outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpPath,
                    "-u" + dbUsername,
                    "-p" + dbPassword,
                    "--single-transaction",
                    "--quick",
                    "--lock-tables=false",
                    dbName);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                    BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = process.getInputStream().read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            logger.error("数据库备份失败", e);
            return false;
        }
    }

    private boolean createZipArchive(String sqlFile, String filesDir, String zipFilePath) {
        try {
            Path zipPath = Paths.get(zipFilePath);
            if (Files.exists(zipPath)) {
                Files.delete(zipPath);
            }

            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(zipPath)));

            Path sqlPath = Paths.get(sqlFile);
            if (Files.exists(sqlPath)) {
                zos.putNextEntry(new java.util.zip.ZipEntry("database.sql"));
                Files.copy(sqlPath, zos);
                zos.closeEntry();
            }

            Path uploadPath = Paths.get(filesDir);
            if (Files.exists(uploadPath) && Files.isDirectory(uploadPath)) {
                Files.walk(uploadPath)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                String entryName = "uploads/"
                                        + uploadPath.relativize(file).toString().replace("\\", "/");
                                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                                Files.copy(file, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                logger.error("添加文件到压缩包失败: " + file, e);
                            }
                        });
            }

            zos.close();
            return true;

        } catch (Exception e) {
            logger.error("创建压缩包失败", e);
            return false;
        }
    }

    private String calculateMD5(String filePath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(Paths.get(filePath)))) {
            int read;
            while ((read = bis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void cleanOldBackups(String type) {
        BackupConfig config = getConfig();
        if (config.getEnabled() == null || !config.getEnabled()) {
            return;
        }

        int retentionCount = config.getRetentionCount() != null ? config.getRetentionCount() : 7;
        List<BackupRecord> autoBackups = backupRecordRepository.findByTypeAndStatusOrderByBackupTimeDesc(type,
                "success");

        if (autoBackups.size() > retentionCount) {
            List<BackupRecord> toDelete = autoBackups.subList(retentionCount, autoBackups.size());
            for (BackupRecord backup : toDelete) {
                try {
                    Files.deleteIfExists(Paths.get(backup.getFilePath()));
                    backupRecordRepository.delete(backup);
                } catch (IOException e) {
                    logger.error("删除旧备份失败: " + backup.getFilePath(), e);
                }
            }
        }
    }

    @Transactional
    public RestoreLog restoreBackup(Long backupId, Long operatorId) {
        BackupRecord backup = backupRecordRepository.findById(backupId)
                .orElseThrow(() -> new RuntimeException("备份记录不存在"));

        enableMaintenanceMode("数据恢复中", operatorId);

        RestoreLog log = new RestoreLog();
        log.setRestoreTime(LocalDateTime.now());
        log.setBackupId(backupId);
        log.setOperatorId(operatorId);
        log.setStatus("success");

        try {
            String zipFilePath = backup.getFilePath();
            if (!Files.exists(Paths.get(zipFilePath))) {
                throw new RuntimeException("备份文件不存在");
            }

            String tempDir = System.getProperty("java.io.tmpdir") + "restore_" + System.currentTimeMillis() + "/";
            extractZipArchive(zipFilePath, tempDir);

            String dbName = extractDbName(dbUrl);
            String sqlFile = tempDir + "database.sql";
            if (Files.exists(Paths.get(sqlFile))) {
                restoreDatabase(dbName, sqlFile);
            }

            String extractedUploads = tempDir + "uploads/";
            if (Files.exists(Paths.get(extractedUploads))) {
                Path targetUploads = Paths.get(uploadDir);
                Files.createDirectories(targetUploads);
                copyDirectory(Paths.get(extractedUploads), targetUploads);
            }

            deleteDirectory(Paths.get(tempDir));

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
            if (request != null) {
                operationLogService.log("RESTORE", "数据恢复: " + backup.getFileName(), request);
            }

        } catch (Exception e) {
            logger.error("恢复失败", e);
            log.setStatus("failed");
            log.setFailReason(e.getMessage());
        } finally {
            disableMaintenanceMode();
        }

        return restoreLogRepository.save(log);
    }

    /**
     * 上传备份文件直接恢复（不落库，仅恢复）
     */
    @Transactional
    public RestoreLog restoreFromUploadedFile(String zipFilePath, Long operatorId) {
        enableMaintenanceMode("上传备份恢复", operatorId);

        RestoreLog log = new RestoreLog();
        log.setRestoreTime(LocalDateTime.now());
        log.setBackupId(null);
        log.setOperatorId(operatorId);
        log.setStatus("success");

        try {
            if (!Files.exists(Paths.get(zipFilePath))) {
                throw new RuntimeException("上传的备份文件不存在");
            }

            String tempDir = System.getProperty("java.io.tmpdir") + "restore_uploaded_" + System.currentTimeMillis()
                    + "/";
            extractZipArchive(zipFilePath, tempDir);

            String dbName = extractDbName(dbUrl);
            String sqlFile = tempDir + "database.sql";
            if (Files.exists(Paths.get(sqlFile))) {
                restoreDatabase(dbName, sqlFile);
            }

            String extractedUploads = tempDir + "uploads/";
            if (Files.exists(Paths.get(extractedUploads))) {
                Path targetUploads = Paths.get(uploadDir);
                Files.createDirectories(targetUploads);
                copyDirectory(Paths.get(extractedUploads), targetUploads);
            }

            deleteDirectory(Paths.get(tempDir));

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
            if (request != null) {
                operationLogService.log("RESTORE", "上传备份恢复: " + zipFilePath, request);
            }

        } catch (Exception e) {
            logger.error("上传备份恢复失败", e);
            log.setStatus("failed");
            log.setFailReason(e.getMessage());
        } finally {
            disableMaintenanceMode();
        }

        return restoreLogRepository.save(log);
    }

    private void extractZipArchive(String zipFilePath, String destDir) throws Exception {
        Path destPath = Paths.get(destDir);
        Files.createDirectories(destPath);

        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(zipFilePath)) {
            zipFile.entries().asIterator().forEachRemaining(entry -> {
                try {
                    Path entryPath = destPath.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (InputStream is = zipFile.getInputStream(entry);
                                OutputStream os = Files.newOutputStream(entryPath)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                os.write(buffer, 0, len);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("解压文件失败: " + entry.getName(), e);
                }
            });
        }
    }

    private boolean restoreDatabase(String dbName, String sqlFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlPath,
                    "-u" + dbUsername,
                    "-p" + dbPassword,
                    dbName);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile));
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            logger.error("数据库恢复失败", e);
            return false;
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("复制文件失败: " + sourcePath, e);
            }
        });
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            logger.error("删除目录失败: " + p, e);
                        }
                    });
        }
    }

    @Transactional
    public void deleteBackup(Long backupId) {
        BackupRecord backup = backupRecordRepository.findById(backupId)
                .orElseThrow(() -> new RuntimeException("备份记录不存在"));

        try {
            Files.deleteIfExists(Paths.get(backup.getFilePath()));
        } catch (IOException e) {
            logger.error("删除备份文件失败", e);
        }

        backupRecordRepository.delete(backup);
    }

    public Path getBackupFilePath(Long backupId) {
        BackupRecord backup = backupRecordRepository.findById(backupId)
                .orElseThrow(() -> new RuntimeException("备份记录不存在"));
        return Paths.get(backup.getFilePath());
    }

    public boolean isMaintenanceMode() {
        return maintenanceModeRepository.findTopByEnabledTrueOrderByStartedAtDesc().isPresent();
    }

    @Transactional
    public void enableMaintenanceMode(String reason, Long operatorId) {
        MaintenanceMode mode = new MaintenanceMode();
        mode.setEnabled(true);
        mode.setReason(reason);
        mode.setOperatorId(operatorId);
        mode.setStartedAt(LocalDateTime.now());
        maintenanceModeRepository.save(mode);
    }

    @Transactional
    public void disableMaintenanceMode() {
        maintenanceModeRepository.findTopByEnabledTrueOrderByStartedAtDesc()
                .ifPresent(mode -> {
                    mode.setEnabled(false);
                    mode.setEndedAt(LocalDateTime.now());
                    maintenanceModeRepository.save(mode);
                });
    }

    public List<RestoreLog> getAllRestoreLogs() {
        return restoreLogRepository.findAllByOrderByRestoreTimeDesc();
    }

    public BackupRecord getBackupById(Long id) {
        return backupRecordRepository.findById(id).orElse(null);
    }

    private String extractDbName(String url) {
        String clean = url;
        int paramIndex = clean.indexOf("?");
        if (paramIndex >= 0) {
            clean = clean.substring(0, paramIndex);
        }
        int dbIndex = clean.lastIndexOf("/");
        if (dbIndex < 0 || dbIndex + 1 >= clean.length()) {
            throw new IllegalArgumentException("invalid datasource url: " + url);
        }
        return clean.substring(dbIndex + 1);
    }
}
