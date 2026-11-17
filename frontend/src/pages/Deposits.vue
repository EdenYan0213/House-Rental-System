<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div>
            <span class="page-title">押金管理</span>
            <span class="page-subtitle">共 {{ total }} 条记录</span>
          </div>
          <div class="page-actions">
            <el-button type="primary" :icon="Plus" @click="openCreate('RECEIVE')">收取押金</el-button>
            <el-button type="warning" plain :icon="RefreshLeft" @click="openCreate('REFUND')">退还押金</el-button>
          </div>
        </div>
      </template>

      <!-- 汇总条：押金池是房东最关心的资金之一 -->
      <div class="summary-row">
        <div class="summary-item">
          <div class="summary-label">累计收取</div>
          <div class="summary-value tone-success">¥{{ money(summary.received) }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">累计退还</div>
          <div class="summary-value tone-warning">¥{{ money(summary.refunded) }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">当前押金池</div>
          <div class="summary-value">¥{{ money(summary.balance) }}</div>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 130px" @change="reload">
          <el-option label="全部" value="" />
          <el-option label="收取" value="RECEIVE" />
          <el-option label="退还" value="REFUND" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
          @change="reload"
        />
      </div>

      <el-table
        :data="deposits"
        v-loading="loading"
        style="width: 100%"
        empty-text="还没有押金记录，点击「收取押金」开始"
      >
        <el-table-column label="合同编号" width="160">
          <template #default="{ row }">
            <el-link v-if="row.contractId" type="primary" :underline="false" @click="viewContract(row)">
              {{ row.contractNo || `#${row.contractId}` }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="租户" width="110">
          <template #default="{ row }">{{ row.tenantName || '-' }}</template>
        </el-table-column>
        <el-table-column label="房间" width="110">
          <template #default="{ row }">{{ row.roomNumber || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'RECEIVE' ? 'success' : 'warning'" size="small">
              {{ depositTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">
            <span :class="row.type === 'RECEIVE' ? 'tone-success' : 'tone-warning'">
              {{ row.type === 'RECEIVE' ? '+' : '-' }}¥{{ money(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="日期" width="170">
          <template #default="{ row }">{{ formatDateTime(row.date) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
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
          @current-change="loadDeposits"
        />
      </div>
    </el-card>

    <!-- 收取 / 退还押金 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="合同" required>
          <el-select
            v-model="form.contractId"
            placeholder="选择合同"
            filterable
            style="width: 100%"
            :disabled="!!editId"
          >
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="`${c.contractNo} · ${c.tenantName || ''} ${c.roomNumber || ''}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio value="RECEIVE">收取</el-radio>
            <el-radio value="REFUND">退还</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="form.amount" :min="0" :step="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="form.date"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getDeposits,
  createDeposit,
  updateDeposit,
  getContracts,
  getErrorMessage
} from '../api'
import { depositTypeText } from '../utils/labels'

const router = useRouter()
const deposits = ref([])
const allRecords = ref([])
const loading = ref(false)
const typeFilter = ref('')
const dateRange = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const showDialog = ref(false)
const saving = ref(false)
const editId = ref(null)
const contractOptions = ref([])
const form = ref({ contractId: null, type: 'RECEIVE', amount: 0, date: '', remark: '' })

const dialogTitle = computed(() => {
  if (editId.value) return '编辑押金记录'
  return form.value.type === 'RECEIVE' ? '收取押金' : '退还押金'
})

// 汇总基于当前筛选结果的完整集合
const summary = computed(() => {
  const received = allRecords.value
    .filter(r => r.type === 'RECEIVE')
    .reduce((s, r) => s + (Number(r.amount) || 0), 0)
  const refunded = allRecords.value
    .filter(r => r.type === 'REFUND')
    .reduce((s, r) => s + (Number(r.amount) || 0), 0)
  return { received, refunded, balance: Math.max(received - refunded, 0) }
})

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDateTime(v) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function loadDeposits() {
  loading.value = true
  try {
    const res = await getDeposits({
      type: typeFilter.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value - 1,
      size: pageSize.value
    })
    const data = res.data || {}
    deposits.value = data.content || []
    total.value = data.totalElements ?? deposits.value.length
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '押金记录加载失败'))
  } finally {
    loading.value = false
  }
}

// 汇总需要完整集合，单独拉一次大页
async function loadAllForSummary() {
  try {
    const res = await getDeposits({
      type: typeFilter.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: 0,
      size: 9999
    })
    allRecords.value = res.data?.content || []
  } catch {
    allRecords.value = []
  }
}

function reload() {
  page.value = 1
  loadDeposits()
  loadAllForSummary()
}

async function loadContracts() {
  try {
    const res = await getContracts({ page: 0, size: 500 })
    contractOptions.value = res.data?.content || res.data || []
  } catch {
    contractOptions.value = []
  }
}

function openCreate(type) {
  editId.value = null
  form.value = {
    contractId: null,
    type,
    amount: 0,
    date: new Date().toISOString().slice(0, 19),
    remark: ''
  }
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = {
    contractId: row.contractId,
    type: row.type,
    amount: row.amount,
    date: row.date ? String(row.date).slice(0, 19) : '',
    remark: row.remark || ''
  }
  showDialog.value = true
}

async function save() {
  if (!form.value.contractId) {
    return ElMessage.warning('请选择合同')
  }
  if (!(Number(form.value.amount) > 0)) {
    return ElMessage.warning('金额必须大于 0')
  }
  saving.value = true
  try {
    if (editId.value) {
      await updateDeposit(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createDeposit(form.value)
      ElMessage.success('保存成功')
    }
    showDialog.value = false
    reload()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

function viewContract(row) {
  router.push(`/contracts?highlight=${row.contractId}`)
}

onMounted(() => {
  loadDeposits()
  loadAllForSummary()
  loadContracts()
})
</script>

<style scoped>
.page {
  padding: 20px;
}

.page-card {
  border-radius: 8px;
}

.page-card :deep(.el-card__header) {
  padding: 14px 20px;
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

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}

.summary-item {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--border-color);
}

.summary-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.summary-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
}

.summary-value.tone-success {
  color: var(--success-color);
}

.summary-value.tone-warning {
  color: #d97706;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.tone-success {
  color: var(--success-color);
  font-weight: 600;
}

.tone-warning {
  color: #d97706;
  font-weight: 600;
}
</style>
