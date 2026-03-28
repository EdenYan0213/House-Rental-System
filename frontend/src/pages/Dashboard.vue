<template>
  <div style="padding: 24px">
    <h2>仪表盘</h2>
    <el-row :gutter="20" style="margin-top: 16px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">今日收款</div>
            <div class="stat-value">¥{{ formatMoney(stats.todayPayment) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">待收租金</div>
            <div class="stat-value">¥{{ formatMoney(stats.pendingAmount) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">在租房间数</div>
            <div class="stat-value">{{ stats.rentedRooms }} / {{ stats.totalRooms }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">空闲房间数</div>
            <div class="stat-value">{{ stats.idleRooms }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="8">
        <el-card>
          <div slot="header">月度收支趋势</div>
          <div ref="trendChart" style="height: 250px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div slot="header">房间出租率</div>
          <div ref="rentChart" style="height: 250px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div slot="header">楼栋出租率</div>
          <div ref="buildingChart" style="height: 250px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24" v-if="showTaskCard">
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

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <div slot="header">
            <span>快捷入口</span>
          </div>
          <el-button type="primary" icon="Plus" @click="$router.push('/contracts/new')">新建合同</el-button>
          <el-button type="success" icon="Edit" @click="$router.push('/meter-readings')">录入水电</el-button>
          <el-button type="warning" icon="Document" @click="$router.push('/bills')">账单管理</el-button>
          <el-button type="info" icon="Bell" @click="$router.push('/messages')">消息中心</el-button>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <div slot="header">
            <span>合同到期提醒（3天内）</span>
            <el-link type="primary" style="margin-left: 10px" @click="$router.push('/contracts?expiring=true')">查看更多</el-link>
            <el-badge :value="expiringCount" class="badge" />
          </div>
          <div v-if="expiringContracts.length === 0" class="empty-tip">暂无即将到期合同</div>
          <el-table v-else :data="expiringContracts" size="small" @row-click="viewContract">
            <el-table-column prop="roomNumber" label="房间号" width="100" />
            <el-table-column prop="tenantName" label="租户" />
            <el-table-column prop="endDate" label="到期日期" width="120" />
            <el-table-column label="剩余天数">
              <template #default="{ row }">
                <el-tag :type="getDaysLeftType(row.endDate)">
                  {{ getDaysLeft(row.endDate) }}天
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { CircleCheckFilled, Clock } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getExpiringContracts, getGuideTaskProgress, updateProfile, getUser, setUser } from '../api'

const router = useRouter()
const stats = ref({
  todayPayment: 0,
  pendingAmount: 0,
  rentedRooms: 0,
  idleRooms: 0,
  totalRooms: 0
})
const expiringContracts = ref([])
const expiringCount = ref(0)
const trendChart = ref(null)
const rentChart = ref(null)
const buildingChart = ref(null)
const guideTasks = ref([])
const guideProgress = ref({ percent: 0, completed: 0, total: 0 })
const showTaskCard = ref(false)

function formatMoney(val) {
  return val ? val.toLocaleString() : '0'
}

function getDaysLeft(endDate) {
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
  try {
    const res = await fetch('/api/dashboard/stats', {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.ok) {
      stats.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to load stats', e)
  }
}

async function loadCharts() {
  try {
    const res = await fetch('/api/dashboard/charts', {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.ok) {
      const data = await res.json()
      initCharts(data)
    }
  } catch (e) {
    initCharts({})
  }
}

async function loadExpiringContracts() {
  try {
    const res = await getExpiringContracts(3)
    expiringContracts.value = res.data.contracts || []
    expiringCount.value = res.data.count || 0
  } catch (e) {
    console.error('Failed to load expiring contracts', e)
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

function initCharts(data) {
  const trendChartEl = echarts.init(trendChart.value)
  trendChartEl.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '支出'] },
    xAxis: { type: 'category', data: data.trendMonths || ['1月','2月','3月','4月','5月','6月'] },
    yAxis: { type: 'value' },
    series: [
      { name: '收入', type: 'line', data: data.trendIncome || [12000, 15000, 14000, 18000, 16000, 20000] },
      { name: '支出', type: 'line', data: data.trendExpense || [3000, 3500, 3200, 4000, 3800, 4500] }
    ]
  })

  const rentChartEl = echarts.init(rentChart.value)
  rentChartEl.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { value: data.rentedRooms || 15, name: '已租' },
        { value: data.idleRooms || 5, name: '空闲' }
      ]
    }]
  })

  const buildingChartEl = echarts.init(buildingChart.value)
  buildingChartEl.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.buildingNames || ['A栋', 'B栋', 'C栋'] },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: data.buildingRates || [80, 65, 90]
    }]
  })
}

onMounted(() => {
  loadStats()
  loadCharts()
  loadExpiringContracts()
  loadGuideProgress()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 8px;
}
.stat-value {
  color: #303133;
  font-size: 24px;
  font-weight: bold;
}
.badge {
  margin-left: 10px;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 20px;
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
