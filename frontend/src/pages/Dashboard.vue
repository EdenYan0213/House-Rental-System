<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stat-row">
      <el-col v-for="card in statCards" :key="card.label" :xs="12" :sm="12" :md="6">
        <el-card
          shadow="hover"
          class="stat-card"
          :class="card.tone"
          v-loading="loading"
          @click="card.to && $router.push(card.to)"
        >
          <div class="stat-card-inner">
            <div class="stat-icon">
              <el-icon :size="18"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-body">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value">{{ card.value }}</div>
            </div>
          </div>
          <div class="stat-hint">{{ card.hint }}</div>
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
          <!-- 快捷入口：统一磁贴样式，靠图标与文案区分，不用四种按钮颜色 -->
          <div class="quick-grid">
            <button
              v-for="q in quickActions"
              :key="q.to"
              type="button"
              class="quick-tile"
              @click="$router.push(q.to)"
            >
              <el-icon :size="20"><component :is="q.icon" /></el-icon>
              <span class="quick-label">{{ q.label }}</span>
              <span class="quick-desc">{{ q.desc }}</span>
            </button>
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
import {
  Plus, Edit, Document, Bell, CircleCheckFilled, Clock,
  Money, Wallet, OfficeBuilding, House
} from '@element-plus/icons-vue'
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

/**
 * 顶部四张统计卡。
 *
 * <p>每张都有实质的副信息（hint），不再用不换行空格占位——
 * 空白行会让卡片显得松散，也让管理员看不出数字背后的含义。
 * 卡片可点击，直接跳到对应模块处理。
 */
const statCards = computed(() => [
  {
    label: '今日收款',
    value: `¥${formatMoney(stats.todayPayment)}`,
    tone: 'tone-primary',
    icon: Money,
    to: '/bills',
    hint: stats.todayPayment > 0 ? '已入账，可在账单流水核对' : '今日还没有收款记录'
  },
  {
    label: '待收租金',
    value: `¥${formatMoney(stats.pendingAmount)}`,
    tone: 'tone-warning',
    icon: Wallet,
    to: '/bills?status=PENDING',
    hint: stats.overdueCount > 0
      ? `${stats.overdueCount} 张账单已逾期，建议尽快催缴`
      : '暂无逾期账单'
  },
  {
    label: '在租房间',
    value: `${stats.rentedRooms} / ${stats.totalRooms}`,
    tone: 'tone-success',
    icon: OfficeBuilding,
    to: '/buildings',
    hint: stats.totalRooms > 0
      ? `整体出租率 ${rentRate.value}%`
      : '还没有房间，先创建楼栋'
  },
  {
    label: '空闲房间',
    value: String(stats.idleRooms),
    tone: 'tone-info',
    icon: House,
    to: '/buildings',
    hint: stats.idleRooms > 0
      ? `可出租，约占 ${idleRate.value}%`
      : '全部已出租'
  }
])

/**
 * 快捷入口。用磁贴而非彩色按钮：四种按钮色会让仪表盘视觉噪音太大，
 * 而图标 + 一句说明足以让人认出功能。
 */
const quickActions = [
  { to: '/contracts/new', label: '新建合同', desc: '录入租约信息', icon: Plus },
  { to: '/meter-readings', label: '录入水电', desc: '批量抄表', icon: Edit },
  { to: '/bills', label: '账单管理', desc: '收款与催缴', icon: Document },
  { to: '/messages', label: '消息中心', desc: '待办与通知', icon: Bell }
]

/** 空闲占比，用于第四张卡的副信息。 */
const idleRate = computed(() => {
  const total = Number(stats.totalRooms || 0)
  if (total <= 0) return 0
  return Math.round((Number(stats.idleRooms || 0) / total) * 100)
})

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
        // 半径收紧 + 居中，避免标签被卡片边缘裁切
        radius: ['42%', '62%'],
        center: ['50%', '52%'],
        avoidLabelOverlap: true,
        label: {
          show: true,
          formatter: '{b}\n{c} 间',
          fontSize: 12,
          color: 'inherit',
          lineHeight: 16
        },
        labelLine: { length: 10, length2: 12 },
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
.stat-row {
  display: flex;
  flex-wrap: wrap;
}

.stat-row > .el-col {
  display: flex;
}

.stat-row > .el-col > .stat-card {
  width: 100%;
}

.dashboard {
  padding: 0;
}
.section-row {
  margin-top: 20px;
}
/* ---------- 统计卡 ----------
 * 左图标 + 右数字，底部一行实质说明。
 * 每张卡都有 tone 色，通过左侧细色条区分语义（收/欠/租/空）。
 */
.stat-card {
  height: 100%;
  margin-bottom: var(--space-4);
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.stat-card :deep(.el-card__body) {
  padding: var(--space-4) var(--space-4) var(--space-3);
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* 左侧语义色条 */
.stat-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--stat-color, var(--accent-3));
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* 图标底座：淡色圆角方块 */
.stat-icon {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--stat-color, var(--accent-3));
  background: color-mix(in srgb, var(--stat-color, var(--accent-3)) 12%, transparent);
}

.stat-body {
  min-width: 0;
  flex: 1;
}

.stat-label {
  color: var(--text-muted);
  font-size: var(--fs-xs);
  letter-spacing: 0.02em;
  margin-bottom: 2px;
}

.stat-value {
  color: var(--text-main);
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

/* 语义色：只染图标与色条，数字保持高对比 */
.tone-primary { --stat-color: var(--accent-3); }
.tone-warning { --stat-color: var(--warning-color); }
.tone-success { --stat-color: var(--accent-3); }
.tone-info    { --stat-color: var(--text-muted); }

.stat-hint {
  font-size: var(--fs-xs);
  line-height: 1.5;
  color: var(--text-muted);
  /* 不再用空格占位：每张卡都有真实文案，高度自然一致 */
  margin: 0;
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
/* 快捷入口磁贴：2x2 网格，图标在上、说明在下 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.quick-tile {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--bg-surface);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease, transform 0.15s ease;
  font-family: inherit;
}

.quick-tile:hover {
  border-color: var(--accent-2);
  background: var(--accent-1);
  transform: translateY(-1px);
}

.quick-tile .el-icon {
  color: var(--accent-3);
  margin-bottom: 4px;
}

.quick-label {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-main);
}

.quick-desc {
  font-size: var(--fs-xs);
  color: var(--text-muted);
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
