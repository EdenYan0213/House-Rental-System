<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-row :gutter="10">
        <el-col :span="4">
          <el-select v-model="filters.operator" placeholder="操作人" clearable filterable>
            <el-option v-for="item in optionData.operators" :key="item" :label="item" :value="item" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filters.module" placeholder="模块" clearable filterable>
            <el-option v-for="item in optionData.modules" :key="item" :label="item" :value="item" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filters.action" placeholder="类型" clearable>
            <el-option v-for="item in optionData.actions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filters.logLevel" placeholder="级别" clearable>
            <el-option label="INFO" :value="1" />
            <el-option label="WARN" :value="2" />
            <el-option label="ERROR" :value="3" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filters.result" placeholder="结果" clearable>
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-input v-model="filters.businessKeyword" placeholder="业务ID/名称" clearable />
        </el-col>
      </el-row>
      <el-row :gutter="10" style="margin-top: 10px">
        <el-col :span="8">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-col>
        <el-col :span="3">
          <el-input-number v-model="filters.minDuration" :min="0" placeholder="最小耗时" style="width: 100%" />
        </el-col>
        <el-col :span="3">
          <el-input-number v-model="filters.maxDuration" :min="0" placeholder="最大耗时" style="width: 100%" />
        </el-col>
        <el-col :span="10" style="text-align: right">
          <el-button @click="quickRange('today')">今天</el-button>
          <el-button @click="quickRange('week')">本周</el-button>
          <el-button @click="quickRange('month')">本月</el-button>
          <el-button type="primary" @click="loadLogs">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button @click="exportCurrent">导出CSV</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-table :data="logs" v-loading="loading" border>
      <el-table-column prop="operationTime" label="时间" width="170" />
      <el-table-column label="操作人" width="120">
        <template #default="scope">{{ scope.row.username || scope.row.operator }}</template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="action" label="类型" width="100" />
      <el-table-column prop="businessName" label="业务对象" width="160" />
      <el-table-column prop="changeSummary" label="变更摘要" min-width="220" show-overflow-tooltip />
      <el-table-column label="结果" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.result === 1 ? 'success' : 'danger'">{{ scope.row.result === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="90">
        <template #default="scope">
          <el-tag :type="levelTag(scope.row.logLevel)">{{ levelText(scope.row.logLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" sortable />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="showDetail(scope.row)">详情</el-button>
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
      @current-change="onPageChange"
    />

    <el-dialog v-model="detailVisible" title="日志详情" width="980px">
      <el-row :gutter="14">
        <el-col :span="10">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="时间">{{ currentDetail?.operationTime }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ currentDetail?.username || currentDetail?.operator }}</el-descriptions-item>
            <el-descriptions-item label="模块">{{ currentDetail?.module }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ currentDetail?.action }}</el-descriptions-item>
            <el-descriptions-item label="业务ID">{{ currentDetail?.businessId }}</el-descriptions-item>
            <el-descriptions-item label="业务名称">{{ currentDetail?.businessName }}</el-descriptions-item>
            <el-descriptions-item label="请求IP">{{ currentDetail?.requestIp || currentDetail?.ip }}</el-descriptions-item>
            <el-descriptions-item label="UserAgent">{{ currentDetail?.userAgent }}</el-descriptions-item>
            <el-descriptions-item label="请求参数">
              <pre class="json-box">{{ formatJson(currentDetail?.requestParams) }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </el-col>
        <el-col :span="14">
          <el-row :gutter="10">
            <el-col :span="12">
              <el-card shadow="never">
                <template #header>操作前</template>
                <pre class="json-box">{{ formatJson(currentDetail?.detailBefore) }}</pre>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never">
                <template #header>操作后</template>
                <pre class="json-box">{{ formatJson(currentDetail?.detailAfter) }}</pre>
              </el-card>
            </el-col>
          </el-row>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportLogs, getLogFilters, getOperationLogs, getErrorMessage } from '../api'

const logs = ref([])
const loading = ref(false)
const optionData = ref({ operators: [], modules: [], actions: [] })
const dateRange = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const currentDetail = ref(null)

const filters = ref({
  operator: '',
  module: '',
  action: '',
  businessKeyword: '',
  logLevel: null,
  result: null,
  minDuration: null,
  maxDuration: null
})

function levelText(value) {
  if (value === 2) return 'WARN'
  if (value === 3) return 'ERROR'
  return 'INFO'
}

function levelTag(value) {
  if (value === 2) return 'warning'
  if (value === 3) return 'danger'
  return 'info'
}

function buildParams() {
  return {
    ...filters.value,
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1],
    page: page.value - 1,
    size: pageSize.value
  }
}

async function loadFilterData() {
  const res = await getLogFilters()
  optionData.value = res.data || { operators: [], modules: [], actions: [] }
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await getOperationLogs(buildParams())
    logs.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载日志失败'))
  } finally {
    loading.value = false
  }
}

function onPageChange(nextPage) {
  page.value = nextPage
  loadLogs()
}

function resetFilters() {
  filters.value = {
    operator: '',
    module: '',
    action: '',
    businessKeyword: '',
    logLevel: null,
    result: null,
    minDuration: null,
    maxDuration: null
  }
  dateRange.value = []
  page.value = 1
  loadLogs()
}

function quickRange(type) {
  const now = new Date()
  const end = now.toISOString().slice(0, 10)
  const start = new Date(now)
  if (type === 'today') {
    dateRange.value = [end, end]
    return
  }
  if (type === 'week') {
    const day = now.getDay() || 7
    start.setDate(now.getDate() - day + 1)
  } else {
    start.setDate(1)
  }
  dateRange.value = [start.toISOString().slice(0, 10), end]
}

async function exportCurrent() {
  try {
    const params = buildParams()
    delete params.page
    delete params.size
    const res = await exportLogs(params)
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `operation_logs_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (_) {
    ElMessage.error('导出失败')
  }
}

function showDetail(row) {
  currentDetail.value = row
  detailVisible.value = true
}

function formatJson(value) {
  if (!value) return '-'
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2)
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (_) {
    return String(value)
  }
}

onMounted(async () => {
  await loadFilterData()
  await loadLogs()
})
</script>

<style scoped>
.json-box {
  max-height: 380px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
}
</style>
