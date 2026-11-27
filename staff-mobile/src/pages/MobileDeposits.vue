<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">押金管理</h1>
            <p class="m-head-sub">
              共 <span class="m-num count">{{ records.length }}</span> 条记录
            </p>
          </div>
          <el-button
            v-if="canManage"
            type="primary"
            :icon="Plus"
            class="head-btn"
            @click="openCreate"
          >新建</el-button>
        </div>
      </header>

      <!-- 汇总 -->
      <div class="summary">
        <div class="summary-card">
          <span class="summary-label">收取</span>
          <span class="summary-value m-num">{{ money(totalReceived) }}</span>
        </div>
        <div class="summary-card">
          <span class="summary-label">退还</span>
          <span class="summary-value m-num">{{ money(totalRefunded) }}</span>
        </div>
        <div class="summary-card summary-card--accent">
          <span class="summary-label">结余</span>
          <span class="summary-value m-num">{{ money(balance) }}</span>
        </div>
      </div>

      <!-- 类型筛选 -->
      <div class="seg">
        <button
          v-for="opt in typeOptions"
          :key="opt.value"
          type="button"
          class="seg-btn"
          :class="{ active: typeFilter === opt.value }"
          @click="typeFilter = opt.value"
        >{{ opt.label }}</button>
      </div>

      <div v-if="!loading && filteredRecords.length === 0" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><Wallet /></el-icon>
        <p class="empty-title">{{ records.length === 0 ? '暂无押金记录' : '没有匹配的记录' }}</p>
        <p class="empty-desc">{{ records.length === 0 ? '押金通常在签约时自动产生。' : '换个筛选条件试试。' }}</p>
      </div>

      <div v-else class="rec-list">
        <article v-for="rec in filteredRecords" :key="rec.id" class="m-card rec-card">
          <div class="rec-main">
            <div class="rec-top">
              <span class="rec-type" :class="isRefund(rec.type) ? 'is-refund' : 'is-receive'">
                {{ typeText(rec.type) }}
              </span>
              <span class="rec-amount m-num" :class="isRefund(rec.type) ? 'is-refund' : ''">
                {{ isRefund(rec.type) ? '-' : '+' }}{{ money(rec.amount) }}
              </span>
            </div>
            <div class="rec-who">
              <span v-if="rec.tenantName">{{ rec.tenantName }}</span>
              <span v-if="rec.roomNumber">· {{ rec.roomNumber }}</span>
              <span v-if="rec.contractNo" class="rec-no">· {{ rec.contractNo }}</span>
            </div>
            <div class="rec-date">{{ formatTime(rec.date) }}</div>
            <div v-if="rec.remark" class="rec-remark">{{ rec.remark }}</div>
          </div>
        </article>
      </div>
    </div>

    <!-- 新建押金记录 -->
    <el-dialog v-model="formVisible" title="新建押金记录" width="92%" align-center>
      <el-form :model="form" label-position="top" class="create-form">
        <el-form-item label="合同" required>
          <el-select
            v-model="form.contractId"
            filterable
            remote
            :remote-method="searchContracts"
            :loading="contractLoading"
            placeholder="搜索房间号或租户姓名"
            class="full-width"
            @change="onContractChange"
          >
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="contractLabel(c)"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.type" class="full-width">
                <el-option label="收取" value="收取" />
                <el-option label="退还" value="退还" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额（元）" required>
              <el-input-number v-model="form.amount" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="日期">
          <el-date-picker
            v-model="form.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">创建</el-button>
      </template>
    </el-dialog>
  </PullRefresh>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Wallet } from '@element-plus/icons-vue'
import PullRefresh from '@/components/PullRefresh.vue'
import {
  getDeposits, createDeposit, getContracts,
  canRefundDeposit, getErrorMessage
} from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const loading = ref(false)
const records = ref([])
// 后端 /api/deposits 仅 ADMIN/SUPER_ADMIN 可访问，与 canRefundDeposit 一致
const canManage = canRefundDeposit()

const typeFilter = ref('')
const typeOptions = [
  { label: '全部', value: '' },
  { label: '收取', value: '收取' },
  { label: '退还', value: '退还' }
]

/** 后端 type 可能是「收取/退还」或 RECEIVE/REFUND，统一判断。 */
function isRefund(type) {
  const raw = String(type || '').trim().toUpperCase()
  return ['退还', 'REFUND', 'REFUNDED'].includes(raw)
}

/** 类型显示名：后端存的是 RECEIVE/REFUND，直接显示英文很难看。 */
function typeText(type) {
  return isRefund(type) ? '退还' : '收取'
}

const filteredRecords = computed(() => {
  if (!typeFilter.value) return records.value
  const wantRefund = typeFilter.value === '退还'
  return records.value.filter(r => isRefund(r.type) === wantRefund)
})

const totalReceived = computed(() =>
  records.value.filter(r => !isRefund(r.type)).reduce((s, r) => s + Number(r.amount || 0), 0))
const totalRefunded = computed(() =>
  records.value.filter(r => isRefund(r.type)).reduce((s, r) => s + Number(r.amount || 0), 0))
const balance = computed(() => totalReceived.value - totalRefunded.value)

function money(v) {
  return Number(v || 0).toFixed(2)
}

function formatTime(v) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/* ---------- 新建 ---------- */
const formVisible = ref(false)
const submitting = ref(false)
const form = ref({ contractId: null, type: '收取', amount: 0, date: '', remark: '' })

const contractOptions = ref([])
const contractLoading = ref(false)

function contractLabel(c) {
  const parts = [c.roomNumber, c.tenantName].filter(Boolean)
  return parts.join(' · ') || c.contractNo || `合同 #${c.id}`
}

async function searchContracts(keyword) {
  contractLoading.value = true
  try {
    const res = await getContracts({ q: keyword || '', paged: true, page: 0, size: 20 })
    contractOptions.value = unwrapList(res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '搜索合同失败'))
  } finally {
    contractLoading.value = false
  }
}

function onContractChange(id) {
  const hit = contractOptions.value.find(c => c.id === id)
  // 默认把合同上的押金金额带过来，减少手工输入
  if (hit?.deposit && !form.value.amount) {
    form.value.amount = Number(hit.deposit)
  }
}

function openCreate() {
  form.value = {
    contractId: null,
    type: '收取',
    amount: 0,
    date: new Date().toISOString().slice(0, 10),
    remark: ''
  }
  formVisible.value = true
  if (contractOptions.value.length === 0) searchContracts('')
}

async function submitForm() {
  if (!form.value.contractId) {
    ElMessage.warning('请选择合同')
    return
  }
  if (!(Number(form.value.amount) > 0)) {
    ElMessage.warning('请填写金额')
    return
  }
  submitting.value = true
  try {
    await createDeposit({
      contractId: form.value.contractId,
      type: form.value.type,
      amount: Number(form.value.amount),
      date: form.value.date ? `${form.value.date}T00:00:00` : null,
      remark: form.value.remark || null
    })
    ElMessage.success('已创建')
    formVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '创建失败'))
  } finally {
    submitting.value = false
  }
}

async function load() {
  loading.value = true
  try {
    // 一次取全量用于汇总；后端支持分页，但押金记录量级不大
    const res = await getDeposits({ page: 0, size: 500 })
    records.value = unwrapList(res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载押金失败'))
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

/* 汇总三卡 */
.summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--m-radius);
  background: var(--bg-surface);
}

.summary-card--accent {
  border-color: var(--accent-2);
  background: var(--accent-1);
}

.summary-label {
  font-size: 11px;
  color: var(--text-muted);
}

.summary-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.summary-card--accent .summary-value {
  color: var(--accent-3);
}

/* 分段筛选 */
.seg {
  display: flex;
  gap: 6px;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
}

.seg-btn {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 13px;
  padding: 8px 0;
  border-radius: 8px;
  cursor: pointer;
  min-height: 36px;
  -webkit-tap-highlight-color: transparent;
}

.seg-btn.active {
  background: var(--accent-3);
  color: var(--text-inverse);
  font-weight: 600;
}

.rec-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: calc(56px + var(--m-safe-bottom));
}

.rec-main {
  padding: 12px 14px;
}

.rec-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rec-type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
}

.rec-type.is-receive {
  color: var(--accent-3);
  background: var(--accent-1);
  border: 1px solid var(--accent-2);
}

.rec-type.is-refund {
  color: var(--warning-color);
  background: color-mix(in srgb, var(--warning-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--warning-color) 30%, transparent);
}

.rec-amount {
  font-size: 16px;
  font-weight: 700;
  color: var(--accent-3);
}

.rec-amount.is-refund {
  color: var(--warning-color);
}

.rec-who {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-main);
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.rec-no {
  color: var(--text-muted);
  font-size: 12px;
}

.rec-date {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.rec-remark {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
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
