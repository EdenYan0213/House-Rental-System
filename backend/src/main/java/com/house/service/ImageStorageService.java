package com.house.service;

import com.house.model.ImageBlob;
import com.house.repo.ImageBlobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 图片存储服务（MySQL 版）。
 *
 * <p>图片内容直接写 image_blob.data，接口按 ID 读取。
 * 上传后 refCount=0，只有当业务数据（楼栋/房间）保存时把 ID 写入 image_ids，
 * 才会被 {@link #markReferenced} 标记为已引用。
 */
@Service
@RequiredArgsConstructor
public class ImageStorageService {

  private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

  private static final List<String> ALLOWED_TYPES = Arrays.asList(
      "image/jpeg", "image/png", "image/gif", "image/webp");

  private static final long MAX_SIZE = 10L * 1024 * 1024;

  private final ImageBlobRepository imageBlobRepository;

  /** 保存上传的图片，返回新纪录。 */
  @Transactional
  public ImageBlob store(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("上传文件为空");
    }
    if (file.getSize() > MAX_SIZE) {
      throw new IllegalArgumentException("图片大小不能超过 10MB");
    }
    String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new IllegalArgumentException("仅支持 jpg / png / gif / webp 格式");
    }

    ImageBlob blob = ImageBlob.builder()
        .contentType(contentType)
        .fileSize(file.getSize())
        .originalName(file.getOriginalFilename())
        .data(file.getBytes())
        .refCount(0)
        .build();
    return imageBlobRepository.save(blob);
  }

  public ImageBlob find(Long id) {
    return imageBlobRepository.findById(id).orElse(null);
  }

  public List<ImageBlob> findAllByIds(List<Long> ids) {
    return imageBlobRepository.findAllById(ids);
  }

  /**
   * 标记图片已被业务数据引用。
   *
   * <p>简单实现：把传入 ID 的 refCount 置为 1，其余仍为 0 的旧图会在定时任务里被清理。
   * 对于小规模系统足够用；如需精确计数可改为统计各表引用。
   */
  @Transactional
  public void markReferenced(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    for (Long id : ids) {
      imageBlobRepository.findById(id).ifPresent(b -> {
        if (b.getRefCount() == null || b.getRefCount() == 0) {
          b.setRefCount(1);
          imageBlobRepository.save(b);
        }
      });
    }
  }

  /**
   * 清理孤儿图片：上传超过 1 天仍未被任何业务数据引用的图片。
   * 用户「上传后取消编辑」产生的图片会在这里被回收。
   */
  @Scheduled(cron = "${app.image.cleanup-cron:0 30 3 * * *}")
  @Transactional
  public void cleanupOrphans() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(
        Math.max(1, orphanRetentionDays()));
    List<ImageBlob> orphans = imageBlobRepository.findByRefCountAndCreatedAtBefore(0, cutoff);
    if (orphans.isEmpty()) {
      return;
    }
    imageBlobRepository.deleteAll(orphans);
    log.info("清理孤儿图片 {} 张", orphans.size());
  }

  private int orphanRetentionDays() {
    return 1;
  }
}
