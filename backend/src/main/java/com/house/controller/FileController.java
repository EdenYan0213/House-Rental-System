package com.house.controller;

import com.house.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            String filename = fileStorageService.storeFile(file);
            String fileUrl = fileStorageService.getFileUrl(filename);
            long fileSize = file.getSize();
            
            response.put("success", true);
            response.put("filename", filename);
            response.put("originalFilename", file.getOriginalFilename());
            response.put("url", fileUrl);
            response.put("size", fileSize);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/uploadMultiple")
    public ResponseEntity<Map<String, Object>> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        
        for (MultipartFile file : files) {
            try {
                String filename = fileStorageService.storeFile(file);
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("filename", filename);
                fileInfo.put("originalFilename", file.getOriginalFilename());
                fileInfo.put("url", fileStorageService.getFileUrl(filename));
                fileInfo.put("size", file.getSize());
                result.put(file.getOriginalFilename(), fileInfo);
                successCount++;
            } catch (IOException e) {
                failCount++;
                Map<String, Object> errorInfo = new HashMap<>();
                errorInfo.put("error", e.getMessage());
                result.put(file.getOriginalFilename(), errorInfo);
            }
        }
        
        response.put("success", failCount == 0);
        response.put("total", files.length);
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("files", result);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(fileStorageConfig.getUploadDir()).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                String contentType = determineContentType(filename);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(fileStorageConfig.getUploadDir()).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                String contentType = determineContentType(filename);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{filename:.+}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();
        boolean deleted = fileStorageService.deleteFile(filename);
        
        response.put("success", deleted);
        response.put("message", deleted ? "File deleted successfully" : "Failed to delete file");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info/{filename:.+}")
    public ResponseEntity<Map<String, Object>> getFileInfo(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();
        try {
            long size = fileStorageService.getFileSize(filename);
            response.put("filename", filename);
            response.put("size", size);
            response.put("url", fileStorageService.getFileUrl(filename));
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/uploadByUrl")
    public ResponseEntity<Map<String, Object>> uploadByUrl(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String imageUrl = request.get("url");
        
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "URL cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String filename = fileStorageService.saveImageFromUrl(imageUrl);
            String fileUrl = fileStorageService.getFileUrl(filename);
            
            response.put("success", true);
            response.put("filename", filename);
            response.put("url", fileUrl);
            response.put("originalUrl", imageUrl);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    @Autowired
    private com.house.config.FileStorageConfig fileStorageConfig;
}
