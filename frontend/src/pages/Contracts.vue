<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div>
            <span class="page-title">合同管理</span>
            <span class="page-subtitle">共 {{ total }} 份合同</span>
          </div>
          <div class="page-actions">
            <el-button :icon="Download" @click="exportCurrent">导出</el-button>
            <el-button type="primary" :icon="Plus" @click="$router.push('/contracts/new')" v-if="canAdd">
              新建合同
            </el-button>
          </div>
        </div>
      </template>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="合同状态" clearable style="width: 140px" @change="loadContracts">
          <el-option label="全部" value="" />
          <el-option label="待生效" value="PENDING" />
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="已到期" value="EXPIRED" />
          <el-option label="已解约" value="TERMINATED" />
        </el-select>
        <el-select v-model="expiringFilter" placeholder="即将到期" clearable style="width: 140px" @change="loadContracts">
          <el-option label="全部" value="" />
          <el-option label="3天内" value="3" />
          <el-option label="7天内" value="7" />
          <el-option label="30天内" value="30" />
        </el-select>
        <el-input
          v-model="searchQuery"
          placeholder="合同编号 / 房间号 / 租户姓名"
          clearable
          class="toolbar-search"
          :prefix-icon="Search"
          @keyup.enter="loadContracts"
        />
        <el-button type="primary" plain @click="loadContracts">搜索</el-button>
        <transition name="el-fade-in">
          <div v-if="selectedContractIds.length > 0" class="selection-bar">
            <el-tag type="primary" effect="light" round>
              已选 {{ selectedContractIds.length }} 份
            </el-tag>
            <el-button type="warning" size="small" @click="openBatchRenew" v-if="canRenew">批量续租</el-button>
            <el-button type="danger" size="small" @click="openBatchTerminate" v-if="canTerminate">批量退租</el-button>
          </div>
        </transition>
      </div>

    <el-table
      ref="contractsTableRef"
      :data="contracts"
      row-key="id"
      :highlight-current-row="true"
      :row-class-name="contractRowClassName"
      style="width: 100%; margin-top: 16px"
      v-loading="loading"
      @selection-change="onSelectionChange"
    >
      <template #empty>
        <EmptyState icon="document" title="还没有合同" description="创建合同后，房间会自动标记为已租，账单将按月生成" primary-text="新建合同" @primary="openCreateFromEmpty" compact />
      </template>
      <el-table-column type="selection" width="50" />
      <el-table-column label="合同编号" width="160">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="viewDetail(row)">
            {{ row.contractNo }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="房间" min-width="140">
        <template #default="{ row }">
          <span>{{ row.buildingName ? row.buildingName + ' ' : '' }}{{ row.roomNumber || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="tenantName" label="租户姓名" />
      <el-table-column prop="startDate" label="起租日期" width="110" />
      <el-table-column prop="endDate" label="结束日期" width="110" />
      <el-table-column label="剩余天数" width="120">
        <template #default="{ row }">
          <el-tooltip :content="'到期日期: ' + row.endDate" placement="top">
            <el-tag :type="getDaysLeftType(row.endDate)">
              {{ getDaysLeftText(row.endDate) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="monthlyRent" label="月租金" width="100">
        <template #default="{ row }">¥{{ row.monthlyRent?.toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="deposit" label="押金" width="100">
        <template #default="{ row }">¥{{ row.deposit?.toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right" align="center">
        <template #default="{ row }">
          <div class="contract-actions">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="warning" @click="renew(row)" :disabled="row.status !== 'ACTIVE'" v-if="canRenew">续租</el-button>
            <el-button size="small" type="danger" @click="openMoveOut(row)" :disabled="row.status !== 'ACTIVE'" v-if="canTerminate">办理退租</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

      <div class="table-footer">
        <el-pagination
          v-if="total > 0"
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="loadContracts"
        />
      </div>
    </el-card>

    <el-dialog title="合同详情" v-model="showDetail" width="700px" top="5vh">
      <div v-if="isExpiring(currentContract)" style="margin-bottom: 16px">
        <el-alert type="warning" :closable="false">
          <template #title>
            <span>合同将于 {{ getDaysLeft(currentContract.endDate) }} 天后到期 </span>
            <el-button type="primary" size="small" style="margin-left: 10px" @click="renew(currentContract)">立即续租</el-button>
          </template>
        </el-alert>
      </div>
      <el-descriptions :column="2" border v-if="currentContract">
        <el-descriptions-item label="合同编号">{{ currentContract.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="房间号">{{ currentContract.roomNumber }}</el-descriptions-item>
        <el-descriptions-item label="租户姓名">{{ currentContract.tenantName }}</el-descriptions-item>
        <el-descriptions-item label="租户电话">{{ currentContract.tenantPhone }}</el-descriptions-item>
        <el-descriptions-item label="起租日期">{{ currentContract.startDate }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{ currentContract.endDate }}</el-descriptions-item>
        <el-descriptions-item label="月租金">¥{{ currentContract.monthlyRent?.toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="押金">¥{{ currentContract.deposit?.toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ paymentCycleText(currentContract.paymentMethod) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentContract.status)">{{ getStatusText(currentContract.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentContract.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <!-- 合同附件：支持多版本归档，可追溯「当时签的是哪版」 -->
      <el-divider content-position="left">合同附件</el-divider>

      <div class="attach-bar">
        <el-upload
          :show-file-list="false"
          :http-request="uploadContractAttachment"
          :before-upload="beforeAttachUpload"
        >
          <el-button type="primary" size="small" :icon="Upload" :loading="attachUploading">
            上传新版本
          </el-button>
        </el-upload>
        <span class="attach-hint">
          每次上传都会保留一个版本，合同当前附件自动指向最新版
        </span>
      </div>

      <el-table
        v-if="attachments.length"
        :data="attachments"
        size="small"
        border
        max-height="220"
        style="margin-top: 10px"
      >
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.id === attachments[0]?.id ? 'success' : 'info'" effect="plain">
              v{{ row.version }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件" min-width="180">
          <template #default="{ row }">
            <el-link :href="row.fileUrl" target="_blank" type="primary">{{ row.fileName || '查看附件' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类别" width="100">
          <template #default="{ row }">{{ attachCategoryText(row.category) }}</template>
        </el-table-column>
        <el-table-column label="上传人" width="100">
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.id !== attachments[0]?.id"
              size="small"
              link
              type="danger"
              @click="removeAttachment(row)"
            >删除</el-button>
            <span v-else class="attach-current">当前</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState icon="document" title="还没有上传附件" description="上传合同扫描件后可按版本追溯" compact />
        </template>
      </el-table>
      <EmptyState
        v-else
        icon="document"
        title="还没有上传附件"
        description="上传合同扫描件后，每次替换都会保留历史版本"
        compact
      />

      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <MoveOutWizard v-model="showMoveOut" :contract-id="moveOutContractId" @done="onMoveOutDone" />

    <el-dialog title="续租" v-model="showRenew" width="480px">
      <el-form :model="renewForm" label-width="110px">
        <el-form-item label="合同编号">
          <el-input :model-value="currentContract?.contractNo || '-'" disabled />
        </el-form-item>
        <el-form-item label="租户/房间">
          <el-input :model-value="`${currentContract?.tenantName || '-'} / ${currentContract?.roomNumber || '-'}`" disabled />
        </el-form-item>
        <el-form-item label="当前结束日期">
          <el-input :model-value="currentContract?.endDate || '-'" disabled />
        </el-form-item>
        <el-form-item label="新结束日期" required>
          <el-date-picker
            v-model="renewForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRenew = false">取消</el-button>
        <el-button type="primary" :loading="renewing" @click="confirmRenew">确认续租</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量续租" v-model="showBatchRenew" width="560px">
      <el-form :model="batchRenewForm" label-width="120px">
        <el-form-item label="续租结束日期">
          <el-date-picker v-model="batchRenewForm.endDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="续租月数(可选)">
          <el-input-number v-model="batchRenewForm.durationMonths" :min="1" :max="60" style="width: 100%" />
        </el-form-item>
        <el-form-item label="租金调整方式">
          <el-radio-group v-model="batchRenewForm.rentMode">
            <el-radio label="FIXED">固定金额</el-radio>
            <el-radio label="PERCENT">百分比</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="batchRenewForm.rentMode === 'FIXED'" label="固定金额">
          <el-input-number v-model="batchRenewForm.fixedAmount" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item v-else label="百分比(%)">
          <el-input-number v-model="batchRenewForm.percent" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="batchRenewForm.inheritDeposit">沿用原押金</el-checkbox>
          <el-checkbox v-model="batchRenewForm.inheritPaymentMethod" style="margin-left: 12px">沿用付款方式</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchRenew = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="submitBatchRenew">确认续租</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量退租" v-model="showBatchTerminate" width="520px">
      <el-form :model="batchTerminateForm" label-width="120px">
        <el-form-item label="统一退租日期" required>
          <el-date-picker v-model="batchTerminateForm.terminateDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="退租原因">
          <el-input v-model="batchTerminateForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchTerminate = false">取消</el-button>
        <el-button type="danger" :loading="batchLoading" @click="submitBatchTerminate">确认退租</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  batchRenewContracts,
  batchTerminateContracts,
  canAddContract,
  canRenewContract,
  canTerminateContract,
  exportModuleData,
  getContract,
  getContracts,
  renewContract,
  terminateContract,
  getErrorMessage,
  getContractAttachments,
  addContractAttachment,
  deleteContractAttachment,
  uploadFile
} from '../api'
import EmptyState from '../components/EmptyState.vue'
import { contractStatusText, contractStatusTag, paymentCycleText } from '../utils/labels'
import { ElMessage, ElMessageBox } from 'element-plus'
import MoveOutWizard from '../components/MoveOutWizard.vue'
import { Search, Plus, Download, Upload } from '@element-plus/icons-vue'
import { showUndoToast } from '../utils/undo'

const router = useRouter()
const route = useRoute()
const contractsTableRef = ref(null)

const canAdd = computed(() => canAddContract())
const canRenew = computed(() => canRenewContract())
const canTerminate = computed(() => canTerminateContract())
const contracts = ref([])
const loading = ref(false)
const statusFilter = ref('')
const expiringFilter = ref('')
const searchQuery = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showDetail = ref(false)
const showRenew = ref(false)
const renewing = ref(false)
const currentContract = ref(null)
// 合同附件版本
const attachments = ref([])
const attachUploading = ref(false)

const ATTACH_CATEGORY = { CONTRACT: '合同正文', ID_CARD: '证件', OTHER: '其他' }

function attachCategoryText(c) {
  return ATTACH_CATEGORY[c] || c || '其他'
}

function formatDateTime(v) {
  if (!v) return '-'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? String(v).replace('T', ' ').slice(0, 19)
    : d.toLocaleString('zh-CN', { hour12: false })
}

async function loadAttachments(contractId) {
  try {
    const res = await getContractAttachments(contractId)
    attachments.value = res.data || []
  } catch {
    attachments.value = []
  }
}

function beforeAttachUpload(file) {
  const maxMb = 20
  if (file.size / 1024 / 1024 > maxMb) {
    ElMessage.warning(`文件不能超过 ${maxMb}MB`)
    return false
  }
  return true
}

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
    await loadAttachments(currentContract.value.id)
    await loadContracts()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '附件上传失败'))
  } finally {
    attachUploading.value = false
  }
}

async function removeAttachment(row) {
  try {
    await ElMessageBox.confirm(`确定删除 v${row.version} 这个附件版本吗？`, '确认删除', { type: 'warning' })
    await deleteContractAttachment(currentContract.value.id, row.id)
    ElMessage.success('已删除')
    await loadAttachments(currentContract.value.id)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(getErrorMessage(e, '删除失败'))
  }
}

const showMoveOut = ref(false)
const moveOutContractId = ref(null)

// 打开退租向导：预检 + 一次性完成退押金/解约/房间置空/加黑名单
// 空态引导：直接跳转新建合同
function openCreateFromEmpty() {
  router.push('/contracts/new')
}

function openMoveOut(row) {
  if (!row?.id) return
  moveOutContractId.value = row.id
  showMoveOut.value = true
}

function onMoveOutDone(payload) {
  // 展示执行步骤，让管理员清楚系统替他做了什么
  const steps = payload?.steps || []
  if (steps.length) {
    ElMessageBox.alert(steps.join('\n'), '退租已办理', { type: 'success' })
  }
  if (payload?.undoSnapshotId) {
    showUndoToast(payload, loadContracts)
  }
  loadContracts()
}
const renewForm = ref({ endDate: '' })
const selectedContractIds = ref([])
const showBatchRenew = ref(false)
const showBatchTerminate = ref(false)
const batchLoading = ref(false)
const highlightedContractId = ref(null)
let contractHighlightTimer = null
const batchRenewForm = ref({
  endDate: '',
  durationMonths: 12,
  rentMode: 'FIXED',
  fixedAmount: 0,
  percent: 0,
  inheritDeposit: true,
  inheritPaymentMethod: true
})
const batchTerminateForm = ref({ terminateDate: '', reason: '' })

function getStatusType(status) {
  return contractStatusTag(status)
}

function getStatusText(status) {
  return contractStatusText(status)
}

function getDaysLeft(endDate) {
  if (!endDate) return 999
  const end = new Date(endDate)
  const today = new Date()
  return Math.ceil((end - today) / (1000 * 60 * 60 * 24))
}

function getDaysLeftText(endDate) {
  const days = getDaysLeft(endDate)
  if (days < 0) return `已逾期${Math.abs(days)}天`
  if (days === 0) return '今天到期'
  return `${days}天后`
}

function getDaysLeftType(endDate) {
  const days = getDaysLeft(endDate)
  if (days < 0) return 'danger'
  if (days <= 2) return 'warning'
  if (days <= 7) return 'success'
  return 'info'
}

function isExpiring(contract) {
  if (!contract || contract.status !== 'ACTIVE') return false
  return getDaysLeft(contract.endDate) <= 3 && getDaysLeft(contract.endDate) >= 0
}

async function loadContracts() {
  loading.value = true
  try {
    const params = {
      status: statusFilter.value,
      q: searchQuery.value,
      page: page.value - 1,
      size: pageSize.value
    }
    if (expiringFilter.value) {
      params.expiring = expiringFilter.value
    }
    const res = await getContracts(params)
    contracts.value = res.data.content || res.data || []
    total.value = res.data.totalElements || contracts.value.length
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selectedContractIds.value = (rows || []).map(r => r.id)
}

function viewDetail(row) {
  currentContract.value = row
  showDetail.value = true
  loadAttachments(row.id)
}

async function openDetailById(id) {
  if (!id) return
  try {
    const res = await getContract(id)
    if (res?.data) {
      if (res.data.contractNo) {
        searchQuery.value = res.data.contractNo
        page.value = 1
        await loadContracts()
      }
      currentContract.value = res.data
      showDetail.value = true
      await focusContractRow(id)
    }
  } catch (e) {
    // ignore
  }
}

function contractRowClassName({ row }) {
  return row?.id === highlightedContractId.value ? 'contract-row-highlight' : ''
}

async function focusContractRow(id) {
  highlightedContractId.value = id

  if (contractHighlightTimer) {
    clearTimeout(contractHighlightTimer)
  }

  await nextTick()
  const row = contracts.value.find(c => c.id === id)
  if (row && contractsTableRef.value?.setCurrentRow) {
    contractsTableRef.value.setCurrentRow(row)
  }

  setTimeout(() => {
    const target = document.querySelector('.contract-row-highlight')
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, 80)

  contractHighlightTimer = setTimeout(() => {
    highlightedContractId.value = null
    if (contractsTableRef.value?.setCurrentRow) {
      contractsTableRef.value.setCurrentRow()
    }
  }, 2000)
}

function renew(row) {
  currentContract.value = row
  const oldEnd = row?.endDate ? new Date(row.endDate) : new Date()
  oldEnd.setFullYear(oldEnd.getFullYear() + 1)
  renewForm.value.endDate = oldEnd.toISOString().slice(0, 10)
  showRenew.value = true
}

async function confirmRenew() {
  if (!currentContract.value?.id) {
    return ElMessage.warning('未找到合同信息')
  }
  if (!renewForm.value.endDate) {
    return ElMessage.warning('请选择新结束日期')
  }
  renewing.value = true
  try {
    await renewContract(currentContract.value.id, { endDate: renewForm.value.endDate })
    ElMessage.success('续租成功，合同时间已延长')
    showRenew.value = false
    showDetail.value = false
    await loadContracts()
  } catch (e) {
    ElMessage.error(e.response?.data || e.message || '续租失败')
  } finally {
    renewing.value = false
  }
}

function goComplaint(row) {
  const q = new URLSearchParams()
  if (row.roomId) q.set('roomId', row.roomId)
  if (row.tenantId) q.set('tenantId', row.tenantId)
  router.push(`/complaints?${q.toString()}`)
}

function openBatchRenew() {
  if (selectedContractIds.value.length === 0) {
    ElMessage.warning('请先勾选合同')
    return
  }
  showBatchRenew.value = true
}

function openBatchTerminate() {
  if (selectedContractIds.value.length === 0) {
    ElMessage.warning('请先勾选合同')
    return
  }
  batchTerminateForm.value.terminateDate = new Date().toISOString().split('T')[0]
  showBatchTerminate.value = true
}

async function submitBatchRenew() {
  batchLoading.value = true
  try {
    const payload = {
      contractIds: selectedContractIds.value,
      endDate: batchRenewForm.value.endDate,
      durationMonths: batchRenewForm.value.durationMonths,
      rentMode: batchRenewForm.value.rentMode,
      fixedAmount: batchRenewForm.value.fixedAmount,
      percent: batchRenewForm.value.percent,
      inheritDeposit: batchRenewForm.value.inheritDeposit,
      inheritPaymentMethod: batchRenewForm.value.inheritPaymentMethod
    }
    const res = await batchRenewContracts(payload)
    ElMessage.success(`批量续租完成：新增${res.data.createdCount}份，跳过${res.data.skippedCount}份`)
    showBatchRenew.value = false
    selectedContractIds.value = []
    await loadContracts()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  } finally {
    batchLoading.value = false
  }
}

async function submitBatchTerminate() {
  if (!batchTerminateForm.value.terminateDate) {
    ElMessage.warning('请选择退租日期')
    return
  }
  batchLoading.value = true
  try {
    const payload = {
      contractIds: selectedContractIds.value,
      terminateDate: batchTerminateForm.value.terminateDate,
      reason: batchTerminateForm.value.reason,
      allowForce: false
    }
    const res = await batchTerminateContracts(payload)
    ElMessage.success(`批量退租完成：成功${res.data.terminatedCount}份，阻止${res.data.blockedCount}份`)
    showBatchTerminate.value = false
    selectedContractIds.value = []
    await loadContracts()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  } finally {
    batchLoading.value = false
  }
}

async function exportCurrent() {
  try {
    const payload = selectedContractIds.value.length > 0 ? { ids: selectedContractIds.value } : {}
    const res = await exportModuleData('contracts', payload)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `contracts_${new Date().toISOString().split('T')[0]}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  if (route.query.expiring === 'true') {
    expiringFilter.value = '3'
  }
  loadContracts()

  const detailId = Number(route.query.detailId)
  if (!Number.isNaN(detailId) && detailId > 0) {
    openDetailById(detailId)
  }
})

watch(() => route.query.detailId, (nextId) => {
  const detailId = Number(nextId)
  if (!Number.isNaN(detailId) && detailId > 0) {
    openDetailById(detailId)
  }
})

onBeforeUnmount(() => {
  if (contractHighlightTimer) {
    clearTimeout(contractHighlightTimer)
  }
})
</script>

<style scoped>
.attach-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.attach-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.attach-current {
  font-size: 12px;
  color: var(--success-color);
}

.page {
  padding: 20px;
}

.page-card {
  border-radius: 8px;
}

.page-card :deep(.el-card__header) {
  padding: 14px 20px;
  background: var(--bg-surface);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-main);
}

.page-subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.page-actions {
  display: flex;
  gap: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: 240px;
}

.selection-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.contract-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  white-space: nowrap;
  gap: 8px;
}

.contract-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

:deep(.contract-row-highlight > td) {
  background: #fff7e6 !important;
  transition: background-color 0.35s ease;
}
</style>
