<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>租户管理</h2>
      <div>
        <el-button @click="exportCurrent">导出</el-button>
        <el-button v-if="canManageBlacklist" type="danger" @click="$router.push('/tenants/blacklist')"
          >黑名单列表</el-button
        >
        <el-button type="primary" @click="openCreate" v-if="canAdd"
          >新增租户</el-button
        >
      </div>
    </el-row>

    <el-row v-if="selectedTenantIds.length > 0" style="margin-top: 12px" :gutter="10">
      <el-tag type="info">已选择 {{ selectedTenantIds.length }} 位租户</el-tag>
      <el-button type="primary" @click="openBatchNotify">发送通知</el-button>
    </el-row>

    <el-row style="margin-top: 16px">
      <el-input
        v-model="searchQuery"
        placeholder="按姓名或手机号搜索"
        clearable
        style="width: 300px"
        @input="onSearchInput"
        @clear="loadTenants"
        @keyup.enter="loadTenants"
      >
        <template #append>
          <el-button :icon="Search" @click="loadTenants" />
        </template>
      </el-input>
    </el-row>

    <el-table
      ref="tenantsTableRef"
      :data="tenants"
      row-key="id"
      :highlight-current-row="true"
      :row-class-name="tenantRowClassName"
      style="width: 100%; margin-top: 16px"
      v-loading="loading"
      empty-text="暂无租户，点击「新增租户」开始"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column label="姓名" min-width="80">
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="row.isBlacklisted" type="danger" size="small" style="margin-left: 5px">黑</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="手机号" min-width="110">
        <template #default="{ row }">
          {{ row.phone }}
        </template>
      </el-table-column>
      <el-table-column label="身份证号" min-width="150">
        <template #default="{ row }">
          {{ maskIdCard(row.idCard) }}
        </template>
      </el-table-column>
      <el-table-column label="紧急联系人/电话" min-width="260">
        <template #default="{ row }">
          <span>{{ row.emergencyContact || "-" }}</span>
          <span style="margin: 0 8px; color: var(--border-color)">|</span>
          <span>{{ row.emergencyPhone || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="合同" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.activeContract" type="success" size="small">有合同</el-tag>
          <span v-else class="text-gray">无合同</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right" align="left">
        <template #default="{ row }">
          <div class="tenant-actions">
            <el-button size="small" type="primary" link @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="primary" link @click="goRepair(row)">报修</el-button>
            <el-button size="small" type="primary" link @click="goComplaint(row)">投诉</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)" v-if="canEdit">编辑</el-button>
            <el-button size="small" type="warning" link @click="addToBlacklist(row)" v-if="canEdit && !row.isBlacklisted">拉黑</el-button>
            <el-button size="small" type="danger" link @click="remove(row)" v-if="canDelete && !row.activeContract">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 16px"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadTenants"
    />

    <el-dialog :title="editId ? '编辑租户' : '新增租户'" v-model="showDialog" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.idCard" />
        </el-form-item>
        <el-form-item label="紧急联系人">
          <el-input v-model="form.emergencyContact" />
        </el-form-item>
        <el-form-item label="紧急联系电话">
          <el-input v-model="form.emergencyPhone" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="租户详情" v-model="showDetail" width="600px">
      <el-alert v-if="currentTenant?.isBlacklisted" type="error" :closable="false" style="margin-bottom: 16px">
        <template #title>
          <span>该租户在黑名单中，原因：{{ currentTenant.blacklistReason }}，加入时间：{{ formatDate(currentTenant.blacklistedAt) }}</span>
          <el-button type="text" style="margin-left: 10px" @click="removeFromBlacklist(currentTenant)">移出黑名单</el-button>
        </template>
      </el-alert>
      <el-descriptions :column="2" border v-if="currentTenant">
        <el-descriptions-item label="姓名">{{ currentTenant.name }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ currentTenant.phone }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ currentTenant.idCard }}</el-descriptions-item>
        <el-descriptions-item label="紧急联系人">{{ currentTenant.emergencyContact }}</el-descriptions-item>
        <el-descriptions-item label="紧急联系电话">{{ currentTenant.emergencyPhone }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ currentTenant.remark }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="tenantContracts.length > 0" style="margin-top: 20px">
        <h4>历史合同</h4>
        <el-table :data="tenantContracts" size="small" style="margin-top: 10px">
          <el-table-column prop="roomNumber" label="房间号" />
          <el-table-column prop="startDate" label="开始日期" />
          <el-table-column prop="endDate" label="结束日期" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="getContractStatusType(row.status)">{{
                contractStatusText(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-if="tenantRepairs.length > 0" style="margin-top: 20px">
        <h4>报修记录</h4>
        <el-table :data="tenantRepairs" size="small" style="margin-top: 10px">
          <el-table-column prop="orderNo" label="工单号" width="160" />
          <el-table-column prop="roomNumber" label="房间号" width="90" />
          <el-table-column prop="faultType" label="故障类型" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ repairStatusText(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="totalFee" label="费用" width="80" />
          <el-table-column prop="createdAt" label="报修时间" />
        </el-table>
      </div>
      <div v-if="tenantComplaints.length > 0" style="margin-top: 20px">
        <h4>投诉记录</h4>
        <el-table :data="tenantComplaints" size="small" style="margin-top: 10px">
          <el-table-column prop="complaintNo" label="编号" width="170" />
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="$router.push(`/complaints?status=all&q=${encodeURIComponent(row.complaintNo)}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog title="加入黑名单" v-model="showBlacklistDialog" width="400px">
      <el-form :model="blacklistForm" label-width="80px">
        <el-form-item label="租户">
          <el-input v-model="blacklistForm.tenantName" disabled />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-select v-model="blacklistForm.reasonType" placeholder="请选择原因" style="width: 100%" @change="handleReasonChange">
            <el-option label="欠费" value="欠费" />
            <el-option label="扰民" value="扰民" />
            <el-option label="房屋损坏" value="房屋损坏" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="blacklistForm.reasonType === '其他'" label="详情">
          <el-input v-model="blacklistForm.customReason" placeholder="请输入具体原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBlacklistDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmAddToBlacklist" :loading="addingBlacklist">确认加入</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量发送通知" v-model="showBatchNotify" width="560px">
      <el-form :model="notifyForm" label-width="90px">
        <el-form-item label="通知方式">
          <el-checkbox model-value disabled>系统消息</el-checkbox>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="notifyForm.title" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="notifyForm.content" type="textarea" :rows="6" />
          <div style="color: var(--text-muted); font-size: 12px; margin-top: 4px">
            可用变量：{租户姓名}、{房间号}
          </div>
        </el-form-item>
        <el-form-item label="附加链接">
          <el-input v-model="notifyForm.link" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchNotify = false">取消</el-button>
        <el-button type="primary" :loading="notifySending" @click="submitBatchNotify">确认发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.text-gray {
  color: var(--text-muted);
  font-size: 13px;
}
</style>

<script setup>
import { ref, onMounted, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import {
  addToBlacklist as apiAddToBlacklist,
  batchSendMessages,
  canAddTenant,
  canDeleteTenant,
  canEditTenant,
  createTenant,
  deleteTenant,
  exportModuleData,
  getTenant,
  getErrorMessage,
  getTenants,
  getTenantComplaints,
  getTenantRepairOrders,
  removeFromBlacklist as apiRemoveFromBlacklist,
  updateTenant,
} from "../api";
import { ElMessage, ElMessageBox } from "element-plus";
import { showUndoToast } from "../utils/undo";
import { debounce } from "../utils/debounce";
import { repairStatusText, contractStatusText, contractStatusTag } from "../utils/labels";

const canAdd = computed(() => canAddTenant());
const canEdit = computed(() => canEditTenant());
const canDelete = computed(() => canDeleteTenant());
// 黑名单管理接口仅对管理员开放，普通用户不展示入口
const canManageBlacklist = computed(() => canEditTenant());
const router = useRouter();

const tenants = ref([])
const loading = ref(false)
const searchQuery = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showDialog = ref(false)
const showDetail = ref(false)
const showBlacklistDialog = ref(false)
const editId = ref(null)
const saving = ref(false)
const addingBlacklist = ref(false)
const currentTenant = ref(null)
const tenantContracts = ref([])
const tenantRepairs = ref([])
const tenantComplaints = ref([])
const selectedTenantIds = ref([])
const showBatchNotify = ref(false)
const notifySending = ref(false)
const notifyForm = ref({ title: '系统通知', content: '', link: '' })
const highlightedTenantId = ref(null)
let tenantHighlightTimer = null
const blacklistForm = ref({
  tenantId: null,
  tenantName: '',
  reasonType: '',
  customReason: ''
})
const form = ref({
  name: '',
  phone: '',
  idCard: '',
  emergencyContact: '',
  emergencyPhone: '',
  remark: ''
})

function maskIdCard(idCard) {
  if (!idCard) return '-'
  return idCard.replace(/(\d{4})\d+(\d{4})/, '$1********$2')
}

function getContractStatusType(status) {
  return contractStatusTag(status);
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return dateStr.split('T')[0]
}

async function loadTenants() {
  loading.value = true
  try {
    const res = await getTenants({ q: searchQuery.value, page: page.value - 1, size: pageSize.value })
    tenants.value = res.data.content || res.data || []
    total.value = res.data.totalElements || tenants.value.length
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "租户加载失败"));
  } finally {
    loading.value = false
  }
}

// 输入即搜，300ms 防抖避免每次按键都请求后端
const debouncedSearch = debounce(() => {
  page.value = 1;
  loadTenants();
}, 300);

function onSearchInput() {
  debouncedSearch();
}

onBeforeUnmount(() => {
  debouncedSearch.cancel();
});

function onSelectionChange(rows) {
  selectedTenantIds.value = (rows || []).map(r => r.id)
}

function openCreate() {
  editId.value = null
  form.value = { name: '', phone: '', idCard: '', emergencyContact: '', emergencyPhone: '', remark: '' }
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = { ...row }
  showDialog.value = true
}

async function viewDetail(row) {
  currentTenant.value = row
  try {
    const res = await getTenants({ q: row.id })
    tenantContracts.value = res.data.contracts || []
  } catch (e) {
    tenantContracts.value = []
  }
  try {
    const r = await getTenantRepairOrders(row.id)
    tenantRepairs.value = r.data || []
  } catch (e) {
    tenantRepairs.value = []
  }
  try {
    const c = await getTenantComplaints(row.id)
    tenantComplaints.value = c.data || []
  } catch (e) {
    tenantComplaints.value = []
  }
  showDetail.value = true
}

async function openDetailById(id) {
  if (!id) return
  try {
    const res = await getTenant(id)
    const tenant = res?.data
    if (tenant) {
      // Narrow down current list to improve row locating and user context.
      if (tenant.phone || tenant.name) {
        searchQuery.value = tenant.phone || tenant.name
        page.value = 1
        await loadTenants()
      }
      await viewDetail(tenant)
      await focusTenantRow(id)
    }
  } catch (e) {
    // 忽略打开失败，避免影响页面正常加载
  }
}

function tenantRowClassName({ row }) {
  return row?.id === highlightedTenantId.value ? 'tenant-row-highlight' : ''
}

async function focusTenantRow(id) {
  highlightedTenantId.value = id

  if (tenantHighlightTimer) {
    clearTimeout(tenantHighlightTimer)
  }

  await nextTick()
  const row = tenants.value.find(t => t.id === id)
  if (row && tenantsTableRef.value?.setCurrentRow) {
    tenantsTableRef.value.setCurrentRow(row)
  }

  setTimeout(() => {
    const target = document.querySelector('.tenant-row-highlight')
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, 80)

  tenantHighlightTimer = setTimeout(() => {
    highlightedTenantId.value = null
    if (tenantsTableRef.value?.setCurrentRow) {
      tenantsTableRef.value.setCurrentRow()
    }
  }, 2000)
}

function goRepair(row) {
  router.push(`/repairs/orders?tenantId=${row.id}`)
}

function goComplaint(row) {
  router.push(`/complaints?tenantId=${row.id}`)
}

async function save() {
  saving.value = true
  try {
    if (editId.value) {
      const res = await updateTenant(editId.value, form.value)
      ElMessage.success('更新成功')
      showUndoToast(res?.data, loadTenants)
    } else {
      await createTenant(form.value)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadTenants()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "操作失败"));
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除该租户吗？', '提示', { type: 'warning' })
    const res = await deleteTenant(row.id)
    ElMessage.success('删除成功')
    showUndoToast(res?.data, loadTenants)
    loadTenants()
  } catch (e) {
    if (e !== "cancel") ElMessage.error(getErrorMessage(e, "删除失败"));
  }
}

function addToBlacklist(row) {
  blacklistForm.value = {
    tenantId: row.id,
    tenantName: row.name,
    reasonType: '',
    customReason: ''
  }
  showBlacklistDialog.value = true
}

function handleReasonChange() {
  blacklistForm.value.customReason = ''
}

async function confirmAddToBlacklist() {
  if (!blacklistForm.value.reasonType) {
    return ElMessage.warning('请选择加入原因')
  }
  const reason = blacklistForm.value.reasonType === '其他' ? blacklistForm.value.customReason : blacklistForm.value.reasonType
  if (!reason) {
    return ElMessage.warning('请填写原因')
  }
  addingBlacklist.value = true
  try {
    await apiAddToBlacklist(blacklistForm.value.tenantId, { reason, adminId: 1 })
    ElMessage.success('已加入黑名单')
    showBlacklistDialog.value = false
    loadTenants()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "操作失败"));
  } finally {
    addingBlacklist.value = false
  }
}

async function removeFromBlacklist(row) {
  try {
    await ElMessageBox.confirm('确定将该租户移出黑名单吗？', '提示', { type: 'warning' })
    const res = await apiRemoveFromBlacklist(row.id)
    ElMessage.success('已移出黑名单')
    showUndoToast(res?.data, loadTenants)
    showDetail.value = false
    loadTenants()
  } catch (e) {
    if (e !== "cancel") ElMessage.error(getErrorMessage(e, "操作失败"));
  }
}

function openBatchNotify() {
  if (selectedTenantIds.value.length === 0) {
    ElMessage.warning('请先勾选租户')
    return
  }
  notifyForm.value = { title: '系统通知', content: '', link: '' }
  showBatchNotify.value = true
}

async function submitBatchNotify() {
  if (!notifyForm.value.content) {
    ElMessage.warning('通知内容不能为空')
    return
  }
  notifySending.value = true
  try {
    const res = await batchSendMessages({
      tenantIds: selectedTenantIds.value,
      title: notifyForm.value.title || '系统通知',
      content: notifyForm.value.content,
      link: notifyForm.value.link
    })
    ElMessage.success(`发送成功，共${res.data.sentCount}条消息`)
    showBatchNotify.value = false
    selectedTenantIds.value = []
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "操作失败"));
  } finally {
    notifySending.value = false
  }
}

async function exportCurrent() {
  try {
    const payload = selectedTenantIds.value.length > 0 ? { ids: selectedTenantIds.value } : {}
    const res = await exportModuleData('tenants', payload)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `tenants_${new Date().toISOString().split('T')[0]}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "导出失败"));
  }
}

onMounted(() => {
  const initialQ = String(route.query.q || '').trim()
  if (initialQ) {
    searchQuery.value = initialQ
  }
  loadTenants()

  const detailId = Number(route.query.detailId)
  if (!Number.isNaN(detailId) && detailId > 0) {
    openDetailById(detailId)
  }
})

watch(() => route.query.q, (nextQ) => {
  const q = String(nextQ || '').trim()
  if (q !== searchQuery.value) {
    searchQuery.value = q
    page.value = 1
    loadTenants()
  }
})

watch(() => route.query.detailId, (nextId) => {
  const detailId = Number(nextId)
  if (!Number.isNaN(detailId) && detailId > 0) {
    openDetailById(detailId)
  }
})

onBeforeUnmount(() => {
  if (tenantHighlightTimer) {
    clearTimeout(tenantHighlightTimer)
  }
})
</script>

<style scoped>
.tenant-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
  white-space: nowrap;
}

:deep(.tenant-row-highlight > td) {
  background: #fff7e6 !important;
  transition: background-color 0.35s ease;
}
</style>
