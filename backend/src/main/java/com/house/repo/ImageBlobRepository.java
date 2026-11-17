package com.house.repo;

import com.house.model.ImageBlob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ImageBlobRepository extends JpaRepository<ImageBlob, Long> {

  /** 清理上传后从未被保存引用的孤儿图片（超过指定时间仍未被引用）。 */
  List<ImageBlob> findByRefCountAndCreatedAtBefore(Integer refCount, LocalDateTime before);
}
