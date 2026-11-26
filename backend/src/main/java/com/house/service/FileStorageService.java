package com.house.service;

import com.house.config.FileStorageConfig;
import com.house.model.ImageBlob;
import com.house.repo.ImageBlobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件存储服务（MySQL 版）。
 *
 * <p>历史上文件写本地磁盘（`app.file.upload-dir`），在容器化部署下重启即丢，
 * 且数据库备份不包含这些文件。现在统一写入 `image_blob` 表：
 * 内容存 `data`，同时用 `stored_name` 保留原磁盘文件名，
 * 使旧的 `/api/files/<filename>` URL 仍然可访问（回退查询）。
 *
 * <p>对外契约不变：这些方法仍返回"文件名"，调用方拼 `/api/files/<文件名>` 即可。
 */
@Service
public class FileStorageService {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private final FileStorageConfig fileStorageConfig;
    private final ImageBlobRepository imageBlobRepository;

    public FileStorageService(FileStorageConfig fileStorageConfig, ImageBlobRepository imageBlobRepository) {
        this.fileStorageConfig = fileStorageConfig;
        this.imageBlobRepository = imageBlobRepository;
    }

    public String storeFile(MultipartFile file) throws IOException {
        return storeFileInternal(file, null, null);
    }

    public String storeComplaintImage(MultipartFile file) throws IOException {
        return storeFileInternal(file, 5L * 1024 * 1024, Arrays.asList("jpg", "jpeg", "png"));
    }

    @Transactional
    public String storeFileInternal(MultipartFile file, Long maxSizeBytes, List<String> forcedExtensions)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Cannot upload empty file");
        }

        if (maxSizeBytes != null && file.getSize() > maxSizeBytes) {
            throw new IOException("File size exceeds limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IOException("Filename cannot be null");
        }

        String fileExtension = getFileExtension(originalFilename);
        if (forcedExtensions != null && !forcedExtensions.contains(fileExtension.toLowerCase())) {
            throw new IOException("File type " + fileExtension + " is not allowed");
        }
        if (forcedExtensions == null && !isAllowedExtension(fileExtension)) {
            throw new IOException("File type " + fileExtension + " is not allowed");
        }

        String sanitizedFilename = sanitizeFilename(originalFilename);
        String filename = UUID.randomUUID().toString() + "_" + sanitizedFilename;

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = contentTypeOf(fileExtension);
        }

        ImageBlob blob = ImageBlob.builder()
            .contentType(contentType)
            .fileSize(file.getSize())
            .originalName(originalFilename)
            .storedName(filename)
            .data(file.getBytes())
            // 1 = 业务数据会直接引用该文件名的 URL，不能被孤儿清理任务回收
            .refCount(1)
            .build();

        try {
            imageBlobRepository.save(blob);
            logger.info("Stored file into DB: {} ({} bytes, id={})", filename, file.getSize(), blob.getId());
            return filename;
        } catch (RuntimeException e) {
            logger.error("Failed to store file: {}", originalFilename, e);
            throw new IOException("Failed to store file: " + originalFilename, e);
        }
    }

    public byte[] loadFileAsBytes(String filename) throws IOException {
        ImageBlob blob = findBlobByStoredName(filename);
        if (blob == null || blob.getData() == null) {
            throw new IOException("File not found: " + filename);
        }
        return blob.getData();
    }

    @Transactional
    public boolean deleteFile(String filename) {
        return imageBlobRepository.findByStoredName(filename)
            .map(blob -> {
                imageBlobRepository.delete(blob);
                logger.info("Successfully deleted file: {}", filename);
                return true;
            })
            .orElseGet(() -> {
                logger.warn("File not found for delete: {}", filename);
                return false;
            });
    }

    public long getFileSize(String filename) throws IOException {
        ImageBlob blob = findBlobByStoredName(filename);
        if (blob == null) {
            throw new IOException("File not found: " + filename);
        }
        return blob.getFileSize() == null ? 0L : blob.getFileSize();
    }

    public ImageBlob findBlobByStoredName(String filename) {
        return imageBlobRepository.findByStoredName(filename).orElse(null);
    }

    public String getFileUrl(String filename) {
        return "/api/files/" + filename;
    }

    @Transactional
    public String saveImageFromUrl(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IOException("URL cannot be empty");
        }

        try {
            URL url = URI.create(imageUrl).toURL();
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

            byte[] data;
            try (InputStream inputStream = connection.getInputStream()) {
                data = inputStream.readAllBytes();
            }

            ImageBlob blob = ImageBlob.builder()
                .contentType(contentType)
                .fileSize((long) data.length)
                .originalName(filename)
                .storedName(filename)
                .data(data)
                .refCount(1)
                .build();
            imageBlobRepository.save(blob);

            logger.info("Saved image from URL: {} as {} (id={})", imageUrl, filename, blob.getId());
            return filename;
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

    private String contentTypeOf(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
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

    /**
     * 清理孤儿文件（已废弃）。
     *
     * <p>文件存于数据库后不再需要磁盘清理。保留空实现以兼容既有调用点，
     * 真正的孤儿回收由 {@link ImageStorageService#cleanupOrphans()} 负责。
     */
    @Deprecated
    public void cleanupOldFiles(int daysOld) {
        logger.debug("cleanupOldFiles 已废弃：文件存储于数据库，无需磁盘清理");
    }
}
