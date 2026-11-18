package com.house.service;

import com.house.model.Contract;
import com.house.model.ContractAttachment;
import com.house.repo.ContractAttachmentRepository;
import com.house.repo.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同附件版本管理。
 *
 * <p>每次上传/替换附件都追加一条版本记录，同时把 contract.attachment_url
 * 指向最新版本，保证「当前附件」与「历史版本」同时可用。
 */
@Service
@RequiredArgsConstructor
public class ContractAttachmentService {

  private static final Logger log = LoggerFactory.getLogger(ContractAttachmentService.class);

  private final ContractAttachmentRepository attachmentRepository;
  private final ContractRepository contractRepository;

  /** 列出某合同的所有附件版本（新的在前）。 */
  public List<ContractAttachment> list(Long contractId) {
    return attachmentRepository.findByContractIdOrderByVersionDesc(contractId);
  }

  /**
   * 上传/替换附件：追加新版本，并把合同当前附件指向它。
   *
   * @param category CONTRACT / ID_CARD / OTHER，默认 CONTRACT
   */
  @Transactional
  public Map<String, Object> addVersion(Long contractId, String fileUrl, String fileName,
      Long fileSize, String category, String remark) {
    Contract contract = contractRepository.findById(contractId)
        .orElseThrow(() -> new IllegalArgumentException("合同不存在"));
    if (fileUrl == null || fileUrl.isBlank()) {
      throw new IllegalArgumentException("附件地址不能为空");
    }

    int nextVersion = (int) attachmentRepository.countByContractId(contractId) + 1;

    ContractAttachment record = ContractAttachment.builder()
        .contractId(contractId)
        .version(nextVersion)
        .fileUrl(fileUrl)
        .fileName(fileName)
        .fileSize(fileSize)
        .category(category == null || category.isBlank() ? "CONTRACT" : category)
        .remark(remark)
        .operator(currentOperator())
        .build();
    ContractAttachment saved = attachmentRepository.save(record);

    // 合同当前附件始终指向最新版本
    contract.setAttachmentUrl(fileUrl);
    contractRepository.save(contract);

    log.info("合同附件新增版本 contractId={} version={} category={}",
        contractId, nextVersion, saved.getCategory());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", saved.getId());
    result.put("contractId", contractId);
    result.put("version", saved.getVersion());
    result.put("fileUrl", saved.getFileUrl());
    result.put("category", saved.getCategory());
    return result;
  }

  /** 删除某个历史版本（当前生效版本不允许删除）。 */
  @Transactional
  public void deleteVersion(Long contractId, Long attachmentId) {
    ContractAttachment record = attachmentRepository.findById(attachmentId)
        .orElseThrow(() -> new IllegalArgumentException("附件记录不存在"));
    if (!contractId.equals(record.getContractId())) {
      throw new IllegalArgumentException("附件不属于该合同");
    }
    List<ContractAttachment> all = attachmentRepository
        .findByContractIdOrderByVersionDesc(contractId);
    if (!all.isEmpty() && all.get(0).getId().equals(attachmentId)) {
      throw new IllegalStateException("当前生效的附件不能删除，请先上传新版本");
    }
    attachmentRepository.delete(record);
  }

  private String currentOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
      return "系统";
    }
    return auth.getName();
  }
}
