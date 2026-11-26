package com.house.repo;

import com.house.model.TenantMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantMessageRepository extends JpaRepository<TenantMessage, Long> {
  List<TenantMessage> findByTenantIdAndIsDeletedOrderByCreatedAtDesc(Long tenantId, Integer isDeleted);

  List<TenantMessage> findByTenantIdAndCategoryAndIsDeletedOrderByCreatedAtDesc(Long tenantId, String category,
      Integer isDeleted);

  Optional<TenantMessage> findTopByTenantIdAndTypeAndRefTypeAndRefIdAndIsDeletedOrderByCreatedAtDesc(
      Long tenantId,
      String type,
      String refType,
      Long refId,
      Integer isDeleted);

  long countByTenantIdAndIsReadAndIsDeleted(Long tenantId, Integer isRead, Integer isDeleted);

  /** 批量已读：取该租客所有未读且未删除的消息。 */
  List<TenantMessage> findByTenantIdAndIsReadAndIsDeleted(Long tenantId, Integer isRead, Integer isDeleted);
}
