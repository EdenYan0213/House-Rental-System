<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col v-for="card in statCards" :key="card.label" :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card" v-loading="loading">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :class="card.tone">{{ card.value }}</div>
          <div class="stat-hint" v-if="card.hint">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="section-row">
      <el-col :xs="24" :md="8">
        <el-card v-loading="loading">
          <template #header>月度收支趋势</template>
          <div v-if="hasTrendData" ref="trendChart" class="chart"></div>
          <el-empty v-else description="暂无账单数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card v-loading="loading">
          <template #header>房间出租率</template>
          <div v-if="stats.totalRooms > 0" ref="rentChart" class="chart"></div>
          <el-empty v-else description="还没有房间，请先创建楼栋与房间" :image-size="70">
            <el-button type="primary" size="small" @click="$router.push('/buildings')">去创建</el-button>
          </el-empty>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card v-loading="loading">
          <template #header>楼栋出租率</template>
          <div v-if="chartData.buildingNames.length > 0" ref="buildingChart" class="chart"></div>
          <el-empty v-else description="暂无楼栋数据" :image-size="70">
            <el-button type="primary" size="small" @click="$router.push('/buildings')">去创建</el-button>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="section-row" v-if="showTaskCard">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="task-card-header">
              <span>新手任务进度</span>
              <el-button link @click="hideTaskCard">隐藏卡片</el-button>
            </div>
          </template>
          <el-progress :percentage="guideProgress.percent" :stroke-width="16" />
          <div class="task-summary">已完成 {{ guideProgress.completed }} / {{ guideProgress.total }}</div>
          <el-row :gutter="12" class="task-list-row">
            <el-col :span="8" v-for="task in guideTasks" :key="task.key">
              <div class="task-item" :class="{ done: task.done }">
                <el-icon v-if="task.done"><CircleCheckFilled /></el-icon>
                <el-icon v-else><Clock /></el-icon>
                <span>{{ task.title }}</span>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="section-row">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>快捷入口</template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/contracts/new')">
              <el-icon><Plus /></el-icon>新建合同
            </el-button>
            <el-button type="success" @click="$router.push('/meter-readings')">
              <el-icon><Edit /></el-icon>录入水电
            </el-button>
            <el-button type="warning" @click="$router.push('/bills')">
              <el-icon><Document /></el-icon>账单管理
            </el-button>
            <el-button @click="$router.push('/messages')">
              <el-icon><Bell /></el-icon>消息中心
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>
                合同到期提醒（{{ expiringDays }}天内）
                <el-badge v-if="expiringCount > 0" :value="expiringCount" class="badge" />
              </span>
              <el-link type="primary" @click="$router.push('/contracts?expiring=true')">查看更多</el-link>
            </div>
          </template>
          <el-empty v-if="expiringContracts.length === 0" description="暂无即将到期合同" :image-size="70" />
          <el-table v-else :data="expiringContracts" size="small" @row-click="viewContract">
            <el-table-column prop="roomNumber" label="房间号" width="100" />
            <el-table-column prop="tenantName" label="租户" />
            <el-table-column prop="endDate" label="到期日期" width="120" />
            <el-table-column label="剩余天数" width="100">
              <template #default="{ row }">
                <el-tag :type="getDaysLeftType(row.endDate)">{{ getDaysLeft(row.endDate) }}天</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Document, Bell, CircleCheckFilled, Clock } from '@element-plus/icons-vue'
import {
  getDashboardStats,
  getDashboardCharts,
  getExpiringContracts,
  getGuideTaskProgress,
  updateProfile,
  getUser,
  setUser,
  getErrorMessage
} from '@/api'

const router = useRouter()

const loading = ref(false)
const expiringDays = ref(3)
const stats = reactive({
  todayPayment: 0,
  pendingAmount: 0,
  rentedRooms: 0,
  idleRooms: 0,
  totalRooms: 0,
  overdueCount: 0
})
const chartData = reactive({
  trendMonths: [],
  trendIncome: [],
  trendExpense: [],
  buildingNames: [],
  buildingRates: []
})
const expiringContracts = ref([])
const expiringCount = ref(0)

const trendChart = ref(null)
const rentChart = ref(null)
const buildingChart = ref(null)
const guideTasks = ref([])
const guideProgress = ref({ percent: 0, completed: 0, total: 0 })
const showTaskCard = ref(false)

// 统一持有实例，卸载时 dispose，避免路由切换后内存泄漏
const chartInstances = []

const hasTrendData = computed(
  () => chartData.trendMonths.length > 0 && chartData.trendIncome.some(v => Number(v) > 0)
)

const rentRate = computed(() =>
  stats.totalRooms > 0 ? Math.round((stats.rentedRooms / stats.totalRooms) * 100) : 0
)

const statCards = computed(() => [
  {
    label: '今日收款',
    value: `¥${formatMoney(stats.todayPayment)}`,
    tone: 'tone-primary',
    hint: null
  },
  {
    label: '待收租金',
    value: `¥${formatMoney(stats.pendingAmount)}`,
    tone: 'tone-warning',
    hint: stats.overdueCount > 0 ? `共 ${stats.overdueCount} 张未结清账单` : null
  },
  {
    label: '在租房间数',
    value: `${stats.rentedRooms} / ${stats.totalRooms}`,
    tone: 'tone-success',
    hint: stats.totalRooms > 0 ? `出租率 ${rentRate.value}%` : null
  },
  {
    label: '空闲房间数',
    value: String(stats.idleRooms),
    tone: 'tone-info',
    hint: null
  }
])

function formatMoney(val) {
  const num = Number(val || 0)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function getDaysLeft(endDate) {
  if (!endDate) return '-'
  const end = new Date(endDate)
  const today = new Date()
  return Math.ceil((end - today) / (1000 * 60 * 60 * 24))
}

function getDaysLeftType(endDate) {
  const days = getDaysLeft(endDate)
  if (days <= 0) return 'danger'
  if (days <= 2) return 'warning'
  return 'success'
}

function viewContract(row) {
  router.push({ path: '/contracts', query: { id: row.id } })
}

async function loadStats() {
  const res = await getDashboardStats()
  Object.assign(stats, res.data || {})
}

async function loadCharts() {
  const res = await getDashboardCharts()
  const data = res.data || {}
  Object.assign(chartData, {
    trendMonths: data.trendMonths || [],
    trendIncome: data.trendIncome || [],
    trendExpense: data.trendExpense || [],
    buildingNames: data.buildingNames || [],
    buildingRates: data.buildingRates || []
  })
}

async function loadExpiringContracts() {
  const res = await getExpiringContracts(expiringDays.value)
  expiringContracts.value = res.data?.contracts || []
  expiringCount.value = res.data?.count || 0
}

function disposeCharts() {
  while (chartInstances.length) {
    chartInstances.pop().dispose()
  }
}

async function loadGuideProgress() {
  try {
    const res = await getGuideTaskProgress()
    guideTasks.value = res.data.tasks || []
    guideProgress.value = {
      percent: res.data.percent || 0,
      completed: res.data.completed || 0,
      total: res.data.total || 0
    }
    const user = getUser()
    showTaskCard.value = user?.showTaskCard !== false && res.data.showTaskCard !== false
  } catch (e) {
    console.error('Failed to load guide task progress', e)
  }
}

async function hideTaskCard() {
  showTaskCard.value = false
  try {
    await updateProfile({ showTaskCard: 'false' })
    const user = getUser()
    if (user) setUser({ ...user, showTaskCard: false })
  } catch (_) {
    // Ignore sync errors and keep local UI state.
  }
}

function renderCharts() {
  disposeCharts()

  if (hasTrendData.value && trendChart.value) {
    const instance = echarts.init(trendChart.value)
    instance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['收入', '支出'] },
      grid: { left: 48, right: 16, top: 40, bottom: 28 },
      xAxis: { type: 'category', data: chartData.trendMonths },
      yAxis: { type: 'value' },
      series: [
        { name: '收入', type: 'line', smooth: true, data: chartData.trendIncome },
        { name: '支出', type: 'line', smooth: true, data: chartData.trendExpense }
      ]
    })
    chartInstances.push(instance)
  }

  if (stats.totalRooms > 0 && rentChart.value) {
    const instance = echarts.init(rentChart.value)
    const other = Math.max(stats.totalRooms - stats.rentedRooms - stats.idleRooms, 0)
    instance.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 间 ({d}%)' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: stats.rentedRooms, name: '已租' },
          { value: stats.idleRooms, name: '空闲' },
          { value: other, name: '维修/其他' }
        ].filter(item => item.value > 0)
      }]
    })
    chartInstances.push(instance)
  }

  if (chartData.buildingNames.length > 0 && buildingChart.value) {
    const instance = echarts.init(buildingChart.value)
    instance.setOption({
      tooltip: { trigger: 'axis', formatter: '{b}: 出租率 {c}%' },
      grid: { left: 48, right: 16, top: 24, bottom: 40 },
      xAxis: { type: 'category', data: chartData.buildingNames },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{ type: 'bar', barMaxWidth: 48, data: chartData.buildingRates }]
    })
    chartInstances.push(instance)
  }
}

function handleResize() {
  chartInstances.forEach(instance => instance.resize())
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadCharts(), loadExpiringContracts()])
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '仪表盘数据加载失败'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAll()
  loadGuideProgress()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}
.section-row {
  margin-top: 20px;
}
.stat-card {
  text-align: center;
  margin-bottom: 16px;
}
.stat-card :deep(.el-card__body) {
  padding: 18px 12px;
}
.stat-label {
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 8px;
}
.stat-value {
  color: var(--text-main);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.tone-primary {
  color: var(--accent-color);
}
.tone-warning {
  color: #d97706;
}
.tone-success {
  color: var(--success-color);
}
.tone-info {
  color: var(--text-main);
}
.stat-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
.chart {
  height: 250px;
  width: 100%;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.badge {
  margin-left: 8px;
}
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.quick-actions .el-button {
  margin-left: 0;
}

.task-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-summary {
  margin-top: 8px;
  color: #6b7280;
  font-size: 13px;
}

.task-list-row {
  margin-top: 14px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f3f4f6;
  color: #4b5563;
  margin-bottom: 10px;
}

.task-item.done {
  background: #ecfdf3;
  color: #047857;
}
</style>
