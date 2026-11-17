package com.house.controller;

import com.house.model.ImageBlob;
import com.house.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 图片上传与读取（存 MySQL）。
 *
 * <p>上传只返回图片 ID，不产生磁盘文件；业务数据保存时再把 ID 写进 image_ids。
 * 读取路径对登录用户开放即可（图片本身不敏感）。
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

  private final ImageStorageService imageStorageService;

  /** 上传图片，返回 ID 与可直接渲染的 URL。 */
  @PostMapping("/upload")
  public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
    Map<String, Object> body = new HashMap<>();
    try {
      ImageBlob blob = imageStorageService.store(file);
      body.put("success", true);
      body.put("id", blob.getId());
      body.put("url", "/api/images/" + blob.getId());
      body.put("size", blob.getFileSize());
      body.put("originalName", blob.getOriginalName());
      return ResponseEntity.ok(body);
    } catch (IllegalArgumentException e) {
      body.put("success", false);
      body.put("message", e.getMessage());
      return ResponseEntity.badRequest().body(body);
    } catch (Exception e) {
      body.put("success", false);
      body.put("message", "图片上传失败: " + e.getMessage());
      return ResponseEntity.badRequest().body(body);
    }
  }

  /** 读取单张图片。 */
  @GetMapping("/{id}")
  public ResponseEntity<byte[]> get(@PathVariable Long id) {
    ImageBlob blob = imageStorageService.find(id);
    if (blob == null || blob.getData() == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(blob.getContentType()))
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
        .body(blob.getData());
  }

  /**
   * 批量查询图片元信息（id + url），供列表页一次性拿到图片地址，
   * 避免为每条记录单独请求。
   */
  @PostMapping("/batch")
  public List<Map<String, Object>> batch(@RequestBody Map<String, Object> body) {
    Object raw = body.get("ids");
    List<Long> ids = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Number n) {
          ids.add(n.longValue());
        }
      }
    }
    List<Map<String, Object>> result = new ArrayList<>();
    if (ids.isEmpty()) {
      return result;
    }
    for (ImageBlob blob : imageStorageService.findAllByIds(ids)) {
      Map<String, Object> item = new HashMap<>();
      item.put("id", blob.getId());
      item.put("url", "/api/images/" + blob.getId());
      result.add(item);
    }
    return result;
  }
}
