<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">合同管理</h1>
            <p class="m-head-sub">
              共 <span class="m-num count">{{ filtered.length }}</span> 份
            </p>
          </div>
          <el-button
            v-if="canAdd"
            type="primary"
            :icon="Plus"
            class="head-btn"
            @click="openCreate"
          >新建</el-button>
        </div>
      </header>

      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索合同号、租户或房间"
        :prefix-icon="Search"
      />

      <!-- 状态分段 -->
      <div class="seg">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          type="button"
          class="seg-btn"
          :class="{ active: statusFilter === opt.value }"
          @click="statusFilter = opt.value"
        >{{ opt.label }}</button>
      </div>

      <div v-if="!loading && filtered.length === 0" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><Document /></el-icon>
        <p class="empty-title">{{ contracts.length === 0 ? '暂无合同' : '没有匹配的合同' }}</p>
        <p class="empty-desc">{{ canAdd ? '点击右上角「新建」创建合同。' : '换个筛选条件试试。' }}</p>
      </div>

      <div v-else class="contract-list">
        <article v-for="c in filtered" :key="c.id" class="m-card contract-card">
          <div class="contract-main">
            <div class="contract-top">
              <span class="contract-no">{{ c.contractNo || '-' }}</span>
              <span class="contract-tag" :class="statusClass(c.status)">{{ contractStatusText(c.status) }}</span>
            </div>
            <div class="contract-who">
              <span>{{ c.tenantName || '未知租户' }}</span>
              <span v-if="c.roomNumber" class="muted">· {{ c.roomNumber }}</span>
            </div>
            <div class="contract-dates">
              {{ fmtDate(c.startDate) }} ~ {{ fmtDate(c.endDate) }}
              <span v-if="daysLeft(c)" class="days-left" :class="{ 'is-urgent': daysLeft(c) <= 30 }">
                {{ daysLeft(c) }} 天
              </span>
            </div>
            <div class="contract-rent m-num">
              ￥{{ Number(c.monthlyRent || 0).toFixed(2) }}<span class="rent-unit">/月</span>
              <span v-if="c.deposit" class="deposit">押金 {{ Number(c.deposit).toFixed(0) }}</span>
            </div>
          </div>

          <div v-if="hasActions(c)" class="contract-ops">
            <button
              v-if="canRenew && isActive(c.status)"
              type="button"
              class="op-btn"
              @click.stop="openRenew(c)"
            >续签</button>
            <button
              v-if="canTerminate && isActive(c.status)"
              type="button"
              class="op-btn op-btn--danger"
              @click.stop="openTerminate(c)"
            >终止</button>
          </div>
        </article>
      </div>
    </div>

    <!-- 新建合同 -->
    <el-dialog v-model="createVisible" title="新建合同" width="92%" align-center>
      <el-form :model="form" label-position="top" class="create-form">
        <el-form-item label="选择房间" required>
          <el-select
            v-model="form.roomId"
            filterable
            :loading="roomLoading"
            placeholder="选择空闲房间"
            class="full-width"
          >
            <el-option
              v-for="r in vacantRooms"
              :key="r.id"
              :label="`${r.buildingName || ''}${r.roomNumber || ''}`.trim() || `房间 #${r.id}`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="租户" required>
          <el-select
            v-model="form.tenantId"
            filterable
            remote
            :remote-method="searchTenants"
            :loading="tenantLoading"
            placeholder="搜索姓名或手机号"
            class="full-width"
          >
            <el-option
              v-for="t in tenantOptions"
              :key="t.id"
              :label="`${t.name} ${t.phone || ''}`.trim()"
              :value="t.id"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="开始日期" required>
              <el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" required>
              <el-date-picker
                v-model="form.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="结束"
                class="full-width"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="月租金（元）" required>
              <el-input-number v-model="form.monthlyRent" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="押金（元）">
              <el-input-number v-model="form.deposit" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 续签 -->
    <el-dialog v-model="renewVisible" title="续签合同" width="92%" align-center>
      <p class="dialog-hint">{{ renewTarget?.contractNo }} · {{ renewTarget?.tenantName }}</p>
      <el-form :model="renewForm" label-position="top" class="create-form">
        <el-form-item label="新结束日期" required>
          <el-date-picker
            v-model="renewForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择新的结束日期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="新月租金（元）">
          <el-input-number v-model="renewForm.monthlyRent" :min="0" :precision="2" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRenew">确认续签</el-button>
      </template>
    </el-dialog>

    <!-- 终止 -->
    <el-dialog v-model="terminateVisible" title="终止合同" width="92%" align-center>
      <p class="dialog-hint">{{ terminateTarget?.contractNo }} · {{ terminateTarget?.tenantName }}</p>
      <el-form :model="terminateForm" label-position="top" class="create-form">
        <el-form-item label="终止日期" required>
          <el-date-picker
            v-model="terminateForm.terminateDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="终止原因">
          <el-input
            v-model="terminateForm.reason"
            type="textarea"
            :rows="2"
            placeholder="如：租户提前退租"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="terminateVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitTerminate">确认终止</el-button>
      </template>
    </el-dialog>
  </PullRefresh>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Document } from '@element-plus/icons-vue'
import PullRefresh from '@/components/PullRefresh.vue'
import {
  getContracts, createContract, renewContract, terminateContract,
  getRooms, getTenants,
  canAddContract, canRenewContract, canTerminateContract, getErrorMessage
} from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const loading = ref(false)
const contracts = ref([])
const keyword = ref('')
const statusFilter = ref('')

const canAdd = canAddContract()
const canRenew = canRenewContract()
const canTerminate = canTerminateContract()

const statusOptions = [
  { label: '全部', value: '' },
  { label: '生效中', value: 'ACTIVE' },
  { label: '待生效', value: 'PENDING' },
  { label: '已到期', value: 'EXPIRED' },
  { label: '已终止', value: 'TERMINATED' }
]

/* ---------- 状态归一化（与后端 ContractStatus 对齐）---------- */
function normalizeStatus(s) {
  const raw = String(s || '').trim().toUpperCase()
  if (!raw) return ''
  if (['ACTIVE', '进行中', '生效中'].includes(raw)) return 'ACTIVE'
  if (['PENDING', '待生效'].includes(raw)) return 'PENDING'
  if (['EXPIRED', '已到期'].includes(raw)) return 'EXPIRED'
  if (['TERMINATED', '已终止'].includes(raw)) return 'TERMINATED'
  return raw
}

function isActive(s) {
  return normalizeStatus(s) === 'ACTIVE'
}

function contractStatusText(s) {
  return {
    ACTIVE: '生效中', PENDING: '待生效', EXPIRED: '已到期', TERMINATED: '已终止'
  }[normalizeStatus(s)] || s || '-'
}

function statusClass(s) {
  return {
    ACTIVE: 'is-active',
    PENDING: 'is-pending',
    EXPIRED: 'is-expired',
    TERMINATED: 'is-terminated'
  }[normalizeStatus(s)] || ''
}

function fmtDate(v) {
  if (!v) return '—'
  return String(v).slice(0, 10)
}

/** 距结束日期还剩多少天；非生效中返回 null。 */
function daysLeft(c) {
  if (!isActive(c.status) || !c.endDate) return null
  const end = new Date(String(c.endDate).slice(0, 10))
  if (Number.isNaN(end.getTime())) return null
  const diff = Math.ceil((end - new Date()) / 86400000)
  return diff
}

function hasActions(c) {
  return (canRenew || canTerminate) && isActive(c.status)
}

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return contracts.value.filter(c => {
    if (statusFilter.value && normalizeStatus(c.status) !== statusFilter.value) return false
    if (!q) return true
    return [c.contractNo, c.tenantName, c.roomNumber]
      .filter(Boolean)
      .some(v => String(v).toLowerCase().includes(q))
  })
})

/* ---------- 新建 ---------- */
const createVisible = ref(false)
const submitting = ref(false)
const vacantRooms = ref([])
const roomLoading = ref(false)
const tenantOptions = ref([])
const tenantLoading = ref(false)

const emptyForm = () => ({
  roomId: null,
  tenantId: null,
  startDate: new Date().toISOString().slice(0, 10),
  endDate: '',
  monthlyRent: 0,
  deposit: 0,
  remark: ''
})
const form = ref(emptyForm())

async function loadVacantRooms() {
  roomLoading.value = true
  try {
    // 只列空闲房间：后端 status 过滤支持 RoomStatus 归一化
    const res = await getRooms({ status: 'IDLE' })
    vacantRooms.value = unwrapList(res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载空闲房间失败'))
  } finally {
    roomLoading.value = false
  }
}

async function searchTenants(kw) {
  tenantLoading.value = true
  try {
    const res = await getTenants({ q: kw || '' })
    tenantOptions.value = unwrapList(res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '搜索租户失败'))
  } finally {
    tenantLoading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  // 默认签一年
  const end = new Date()
  end.setFullYear(end.getFullYear() + 1)
  form.value.endDate = end.toISOString().slice(0, 10)
  createVisible.value = true
  loadVacantRooms()
  if (tenantOptions.value.length === 0) searchTenants('')
}

async function submitCreate() {
  if (!form.value.roomId) return ElMessage.warning('请选择房间')
  if (!form.value.tenantId) return ElMessage.warning('请选择租户')
  if (!form.value.startDate || !form.value.endDate) return ElMessage.warning('请选择起止日期')
  if (new Date(form.value.endDate) <= new Date(form.value.startDate)) {
    return ElMessage.warning('结束日期需晚于开始日期')
  }
  if (!(Number(form.value.monthlyRent) > 0)) return ElMessage.warning('请填写月租金')

  submitting.value = true
  try {
    await createContract({
      roomId: form.value.roomId,
      tenantId: form.value.tenantId,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      monthlyRent: Number(form.value.monthlyRent),
      deposit: Number(form.value.deposit) || 0,
      remark: form.value.remark || null
    })
    ElMessage.success('合同已创建')
    createVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '创建失败'))
  } finally {
    submitting.value = false
  }
}

/* ---------- 续签 ---------- */
const renewVisible = ref(false)
const renewTarget = ref(null)
const renewForm = ref({ endDate: '', monthlyRent: 0 })

function openRenew(c) {
  renewTarget.value = c
  // 默认在原结束日期上顺延一年
  const base = c.endDate ? new Date(String(c.endDate).slice(0, 10)) : new Date()
  base.setFullYear(base.getFullYear() + 1)
  renewForm.value = {
    endDate: base.toISOString().slice(0, 10),
    monthlyRent: Number(c.monthlyRent || 0)
  }
  renewVisible.value = true
}

async function submitRenew() {
  if (!renewForm.value.endDate) return ElMessage.warning('请选择新的结束日期')
  submitting.value = true
  try {
    await renewContract(renewTarget.value.id, {
      endDate: renewForm.value.endDate,
      monthlyRent: Number(renewForm.value.monthlyRent) || undefined
    })
    ElMessage.success('已续签')
    renewVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '续签失败'))
  } finally {
    submitting.value = false
  }
}

/* ---------- 终止 ---------- */
const terminateVisible = ref(false)
const terminateTarget = ref(null)
const terminateForm = ref({ terminateDate: '', reason: '' })

function openTerminate(c) {
  terminateTarget.value = c
  terminateForm.value = {
    terminateDate: new Date().toISOString().slice(0, 10),
    reason: ''
  }
  terminateVisible.value = true
}

async function submitTerminate() {
  if (!terminateForm.value.terminateDate) return ElMessage.warning('请选择终止日期')
  submitting.value = true
  try {
    await terminateContract(terminateTarget.value.id, {
      terminateDate: terminateForm.value.terminateDate,
      reason: terminateForm.value.reason || null
    })
    ElMessage.success('合同已终止')
    terminateVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '终止失败'))
  } finally {
    submitting.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getContracts({ paged: true, page: 0, size: 200 })
    contracts.value = unwrapList(res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载合同失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-btn {
  flex: 0 0 auto;
  min-height: 36px;
  border-radius: 9px;
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

/* 状态分段：5 个较多，允许横向滚动 */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.seg::-webkit-scrollbar {
  display: none;
}

.seg-btn {
  flex: 1 0 auto;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 12.5px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  min-height: 36px;
  white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
}

.seg-btn.active {
  background: var(--accent-3);
  color: var(--text-inverse);
  font-weight: 600;
}

.contract-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: calc(56px + var(--m-safe-bottom));
}

.contract-card {
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 0;
  overflow: hidden;
}

.contract-main {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
}

.contract-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.contract-no {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
  font-variant-numeric: tabular-nums;
}

.contract-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

.contract-tag.is-active {
  color: var(--accent-3);
  background: var(--accent-1);
  border: 1px solid var(--accent-2);
}

.contract-tag.is-pending {
  color: var(--warning-color);
  background: color-mix(in srgb, var(--warning-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--warning-color) 30%, transparent);
}

.contract-tag.is-expired,
.contract-tag.is-terminated {
  color: var(--text-muted);
  background: var(--bg-subtle);
  border: 1px solid var(--border-color);
}

.contract-who {
  margin-top: 5px;
  font-size: 13px;
  color: var(--text-main);
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.muted {
  color: var(--text-muted);
}

.contract-dates {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.days-left {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--bg-subtle);
  border: 1px solid var(--border-color);
}

.days-left.is-urgent {
  color: var(--warning-color);
  border-color: color-mix(in srgb, var(--warning-color) 40%, transparent);
  background: color-mix(in srgb, var(--warning-color) 10%, transparent);
}

.contract-rent {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.rent-unit {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-muted);
}

.deposit {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-muted);
}

.contract-ops {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 10px 10px;
  border-left: 1px solid var(--border-color);
  flex: 0 0 auto;
}

.op-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-main);
  border-radius: 8px;
  padding: 7px 11px;
  font-size: 12px;
  font-family: inherit;
  min-height: 32px;
  cursor: pointer;
  white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
}

.op-btn:active {
  background: var(--accent-1);
  border-color: var(--accent-2);
}

.op-btn--danger {
  color: var(--danger-color);
  border-color: color-mix(in srgb, var(--danger-color) 30%, transparent);
}

.create-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 4px;
}

.create-form :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.create-form :deep(.el-input-number),
.create-form :deep(.el-date-editor) {
  width: 100%;
}

.full-width {
  width: 100%;
}

.dialog-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-muted);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  color: var(--text-muted);
  opacity: 0.4;
}

.empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
