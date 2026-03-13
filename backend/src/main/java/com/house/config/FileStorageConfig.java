package com.house.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfig {
    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.file.max-size}")
    private long maxFileSize;

    @Value("${app.file.allowed-extensions}")
    private String allowedExtensions;

    public String getUploadDir() {
        return uploadDir;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public String getAllowedExtensions() {
        return allowedExtensions;
    }
}
