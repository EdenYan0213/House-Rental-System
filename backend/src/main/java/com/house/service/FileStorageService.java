package com.house.service;

import com.house.config.FileStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileStorageService {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    @Autowired
    private FileStorageConfig fileStorageConfig;

    public String storeFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Cannot upload empty file");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IOException("Filename cannot be null");
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedExtension(fileExtension)) {
            throw new IOException("File type " + fileExtension + " is not allowed");
        }

        String sanitizedFilename = sanitizeFilename(originalFilename);
        String filename = UUID.randomUUID().toString() + "_" + sanitizedFilename;

        Path uploadPath = Paths.get(fileStorageConfig.getUploadDir());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Created upload directory: {}", uploadPath);
        }

        Path filePath = uploadPath.resolve(filename);

        try {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Successfully stored file: {} with size: {} bytes", filename, file.getSize());
            return filename;
        } catch (IOException e) {
            logger.error("Failed to store file: {}", originalFilename, e);
            throw new IOException("Failed to store file: " + originalFilename, e);
        }
    }

    public byte[] loadFileAsBytes(String filename) throws IOException {
        Path filePath = Paths.get(fileStorageConfig.getUploadDir()).resolve(filename);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }

        return Files.readAllBytes(filePath);
    }

    public boolean deleteFile(String filename) {
        try {
            Path filePath = Paths.get(fileStorageConfig.getUploadDir()).resolve(filename);
            Files.deleteIfExists(filePath);
            logger.info("Successfully deleted file: {}", filename);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", filename, e);
            return false;
        }
    }

    public long getFileSize(String filename) throws IOException {
        Path filePath = Paths.get(fileStorageConfig.getUploadDir()).resolve(filename);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }
        return Files.size(filePath);
    }

    public String getFileUrl(String filename) {
        return "/api/files/" + filename;
    }

    public String saveImageFromUrl(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IOException("URL cannot be empty");
        }

        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IOException("URL does not point to an image");
            }

            String extension = getExtensionFromContentType(contentType);
            if (extension == null || !isAllowedExtension(extension)) {
                extension = "jpg";
            }

            String filename = UUID.randomUUID().toString() + "." + extension;

            Path uploadPath = Paths.get(fileStorageConfig.getUploadDir());
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                logger.info("Created upload directory: {}", uploadPath);
            }

            Path filePath = uploadPath.resolve(filename);

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Successfully saved image from URL: {} as {}", imageUrl, filename);
                return filename;
            }
        } catch (IOException e) {
            logger.error("Failed to save image from URL: {}", imageUrl, e);
            throw new IOException("Failed to save image from URL: " + e.getMessage(), e);
        }
    }

    private String getExtensionFromContentType(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> null;
        };
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1).toLowerCase() : "";
    }

    private boolean isAllowedExtension(String extension) {
        List<String> allowedExtensions = Arrays.stream(fileStorageConfig.getAllowedExtensions().split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        return allowedExtensions.contains(extension.toLowerCase());
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public void cleanupOldFiles(int daysOld) {
        Path uploadPath = Paths.get(fileStorageConfig.getUploadDir());
        if (!Files.exists(uploadPath)) {
            return;
        }

        try {
            Files.walk(uploadPath)
                    .filter(Files::isRegularFile)
                    .filter(file -> {
                        try {
                            return file.toFile().lastModified() < System.currentTimeMillis() - daysOld * 24 * 60 * 60 * 1000L;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            logger.info("Cleaned up old file: {}", file.getFileName());
                        } catch (IOException e) {
                            logger.error("Failed to clean up file: {}", file.getFileName(), e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to clean up old files", e);
        }
    }
}
