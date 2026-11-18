package com.house.repo;

import com.house.model.ContractAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractAttachmentRepository extends JpaRepository<ContractAttachment, Long> {

  List<ContractAttachment> findByContractIdOrderByVersionDesc(Long contractId);

  List<ContractAttachment> findByContractIdAndCategoryOrderByVersionDesc(Long contractId, String category);

  long countByContractId(Long contractId);
}
