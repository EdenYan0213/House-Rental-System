import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContractAttachments,
  addContractAttachment,
  deleteContractAttachment,
  uploadFile,
  getErrorMessage
} from '@/api'

/** 附件分类的中文展示。 */
export const ATTACH_CATEGORY = { CONTRACT: '合同正文', ID_CARD: '证件', OTHER: '其他' }

export function attachCategoryText(category) {
  return ATTACH_CATEGORY[category] || category || '其他'
}

/**
 * 合同附件的版本管理。
 *
 * <p>每次上传都生成新版本（后端自动递增），旧版本保留可追溯；
 * 当前生效版本不允许删除，由后端校验、前端置灰。
 *
 * <p>从 817 行的 `Contracts.vue` 抽出，同时把原来的裸 `catch {}`
 * （静默吞掉加载失败）改成显式提示。
 *
 * @param currentContract 当前查看的合同 ref，需含 id
 * @param onChanged       附件变更后的回调（通常是刷新合同列表，同步附件数）
 */
export function useContractAttachments(currentContract, onChanged) {
  const attachments = ref([])
  const attachUploading = ref(false)

  async function loadAttachments(contractId) {
    const id = contractId ?? currentContract.value?.id
    if (!id) {
      attachments.value = []
      return
    }
    try {
      const res = await getContractAttachments(id)
      attachments.value = res.data || []
    } catch (e) {
      attachments.value = []
      ElMessage.error(getErrorMessage(e, '附件加载失败'))
    }
  }

  /** el-upload 的 before-upload：限制 20MB。 */
  function beforeAttachUpload(file) {
    const maxMb = 20
    if (file.size / 1024 / 1024 > maxMb) {
      ElMessage.warning(`文件不能超过 ${maxMb}MB`)
      return false
    }
    return true
  }

  /** el-upload 自定义上传：先传文件拿 URL，再登记为合同附件。 */
  async function uploadContractAttachment(options) {
    if (!currentContract.value?.id) return
    attachUploading.value = true
    try {
      const uploaded = await uploadFile(options.file)
      const url = uploaded.data?.url
      if (!url) {
        ElMessage.error('文件上传失败')
        return
      }
      await addContractAttachment(currentContract.value.id, {
        fileUrl: url,
        fileName: options.file.name,
        fileSize: options.file.size,
        category: 'CONTRACT'
      })
      ElMessage.success('新版本已上传')
      await loadAttachments()
      onChanged?.()
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '附件上传失败'))
    } finally {
      attachUploading.value = false
    }
  }

  async function removeAttachment(row) {
    try {
      await ElMessageBox.confirm(
        `确定删除 v${row.version} 这个附件版本吗？`,
        '确认删除',
        { type: 'warning' }
      )
    } catch {
      return // 用户取消
    }
    try {
      await deleteContractAttachment(currentContract.value.id, row.id)
      ElMessage.success('已删除')
      await loadAttachments()
      onChanged?.()
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '删除失败'))
    }
  }

  return {
    attachments,
    attachUploading,
    loadAttachments,
    beforeAttachUpload,
    uploadContractAttachment,
    removeAttachment
  }
}
