package com.house.repo;

import com.house.model.SignAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignAgreementRepository extends JpaRepository<SignAgreement, Long> {
  Optional<SignAgreement> findByContractIdAndTenantId(Long contractId, Long tenantId);

  Optional<SignAgreement> findBySignFlowNo(String signFlowNo);

  List<SignAgreement> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);
}
