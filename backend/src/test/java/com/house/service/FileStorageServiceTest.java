package com.house.service;

import com.house.config.FileStorageConfig;
import com.house.model.ImageBlob;
import com.house.repo.ImageBlobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 文件存储服务测试。
 *
 * <p>守护「文件统一存数据库」这一改造：此前文件写本地磁盘，容器化部署重启即丢，
 * 且数据库备份不包含它们。现在所有上传都必须落到 {@code image_blob}。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("文件存储到数据库")
class FileStorageServiceTest {

  @Mock
  private FileStorageConfig config;
  @Mock
  private ImageBlobRepository repository;
  @Mock
  private MultipartFile file;

  private FileStorageService service;

  @BeforeEach
  void setUp() {
    when(config.getAllowedExtensions()).thenReturn("jpg,jpeg,png,gif,webp");
    when(repository.save(any(ImageBlob.class))).thenAnswer(inv -> {
      ImageBlob b = inv.getArgument(0);
      b.setId(99L);
      return b;
    });
    service = new FileStorageService(config, repository);
  }

  @Test
  @DisplayName("上传写入 image_blob，且 ref_count=1 防止被孤儿清理")
  void uploadWritesToDatabase() throws IOException {
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("photo.png");
    when(file.getSize()).thenReturn(74L);
    when(file.getContentType()).thenReturn("image/png");
    when(file.getBytes()).thenReturn(new byte[74]);

    String storedName = service.storeFile(file);

    ArgumentCaptor<ImageBlob> captor = ArgumentCaptor.forClass(ImageBlob.class);
    verify(repository).save(captor.capture());
    ImageBlob saved = captor.getValue();

    assertEquals("image/png", saved.getContentType());
    assertEquals(74L, saved.getFileSize());
    assertEquals("photo.png", saved.getOriginalName());
    assertEquals(storedName, saved.getStoredName());
    // 关键：不能被孤儿清理任务回收
    assertEquals(1, saved.getRefCount());
  }

  @Test
  @DisplayName("返回的文件名以 UUID 前缀 + 原始文件名组成")
  void storedNameHasUuidPrefix() throws IOException {
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("a b.png");
    when(file.getSize()).thenReturn(1L);
    when(file.getContentType()).thenReturn("image/png");
    when(file.getBytes()).thenReturn(new byte[]{1});

    String name = service.storeFile(file);

    // "a b.png" -> "a_b.png"（7 字符），UUID 36 位 + 下划线
    assertTrue(name.endsWith("_a_b.png"), "非法字符应被替换: " + name);
    assertEquals(36 + 1 + "a_b.png".length(), name.length(), "应为 UUID + 下划线 + 文件名");
  }

  @Test
  @DisplayName("按 storedName 可读回原字节")
  void loadByStoredName() throws IOException {
    byte[] data = {1, 2, 3};
    ImageBlob blob = ImageBlob.builder()
        .id(1L).storedName("abc_photo.png").data(data).fileSize(3L).contentType("image/png").build();
    when(repository.findByStoredName("abc_photo.png")).thenReturn(Optional.of(blob));

    assertArrayEquals(data, service.loadFileAsBytes("abc_photo.png"));
    assertEquals(3L, service.getFileSize("abc_photo.png"));
  }

  @Test
  @DisplayName("不存在的文件抛 IOException，不返回空数组")
  void missingFileThrows() {
    when(repository.findByStoredName("nope.png")).thenReturn(Optional.empty());
    assertThrows(IOException.class, () -> service.loadFileAsBytes("nope.png"));
    assertThrows(IOException.class, () -> service.getFileSize("nope.png"));
  }

  @Test
  @DisplayName("删除按 storedName 定位并移除记录")
  void deleteByStoredName() {
    ImageBlob blob = ImageBlob.builder().id(5L).storedName("x.png").build();
    when(repository.findByStoredName("x.png")).thenReturn(Optional.of(blob));

    assertTrue(service.deleteFile("x.png"));
    verify(repository).delete(blob);
    assertFalse(service.deleteFile("missing.png"));
  }

  @Test
  @DisplayName("不支持的扩展名被拒绝")
  void rejectsUnsupportedExtension() {
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("evil.exe");
    when(file.getSize()).thenReturn(1L);

    assertThrows(IOException.class, () -> service.storeFile(file));
    verify(repository, never()).save(any());
  }
}
