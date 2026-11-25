<template>
  <div class="mobile-page" v-loading="loading">
    <!-- 页头：问候 + 日期 -->
    <header class="m-head">
      <h1 class="m-head-title">{{ greeting }}，{{ userName }}</h1>
      <p class="m-head-sub">{{ todayText }}</p>
    </header>

    <!-- 关键数字：两列，带语义色条 -->
    <section class="m-stats">
      <div class="m-stat m-stat--accent">
        <div class="m-stat-label">进行中合同</div>
        <div class="m-stat-value">{{ stats.activeContracts }}</div>
      </div>
      <div class="m-stat" :class="stats.pendingRepairs > 0 ? 'm-stat--warn' : 'm-stat--muted'">
        <div class="m-stat-label">待分配工单</div>
        <div class="m-stat-value">{{ stats.pendingRepairs }}</div>
      </div>
      <div class="m-stat" :class="stats.pendingAcceptRepairs > 0 ? 'm-stat--warn' : 'm-stat--muted'">
        <div class="m-stat-label">待验收工单</div>
        <div class="m-stat-value">{{ stats.pendingAcceptRepairs }}</div>
      </div>
      <div class="m-stat" :class="stats.unpaidRooms > 0 ? 'm-stat--danger' : 'm-stat--muted'">
        <div class="m-stat-label">本月欠费房间</div>
        <div class="m-stat-value">{{ stats.unpaidRooms }}</div>
      </div>
    </section>

    <!-- 快捷入口：磁贴网格，图标 + 文字 -->
    <section>
      <div class="m-section-title">常用功能</div>
      <div class="m-tiles">
        <button
          v-for="tile in tiles"
          :key="tile.path"
          type="button"
          class="m-tile"
          @click="go(tile.path)"
        >
          <span class="m-tile-icon">
            <el-icon :size="19"><component :is="tile.icon" /></el-icon>
          </span>
          <span class="m-tile-label">{{ tile.label }}</span>
          <el-badge
            v-if="tile.badge && unreadCount > 0"
            :value="unreadCount"
            class="tile-badge"
          />
        </button>
      </div>
    </section>

    <!-- 最近工单 -->
    <section>
      <div class="m-section-title">
        <span>最近工单</span>
        <span class="m-section-more" @click="openRepairs('')">查看全部 ›</span>
      </div>

      <div v-if="recentRepairs.length === 0" class="m-card empty-card">
        <el-icon :size="28" class="empty-icon"><Document /></el-icon>
        <p class="empty-text">暂无工单</p>
      </div>

      <div v-else class="m-list">
        <div
          v-for="item in recentRepairs"
          :key="item.id"
          class="m-list-item"
          @click="openRepairs(item.status)"
        >
          <div class="m-list-main">
            <div class="m-list-title">
              <span class="m-num">{{ item.roomNumber || '-' }}</span>
              <span class="dot">·</span>
              <span>{{ item.faultType || '-' }}</span>
            </div>
            <div class="m-list-desc">{{ item.description || '无描述' }}</div>
          </div>
          <el-tag size="small" :type="statusTag(item.status)" effect="light">
            {{ statusText(item.status) }}
          </el-tag>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContracts, getRepairOrders, getBills, getUser, getUnreadCount } from '@/api'
import { unwrapList } from '@/utils/mobileApi'
import {
  Edit, Tools, Wallet, Bell, OfficeBuilding, Search, MagicStick, FullScreen, Document
} from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const recentRepairs = ref([])
const unreadCount = ref(0)
const stats = ref({
  activeContracts: 0,
  pendingRepairs: 0,
  pendingAcceptRepairs: 0,
  unpaidRooms: 0
})

const userName = computed(() => {
  const user = getUser()
  return user?.realName || user?.username || '管理员'
})

/** 按时间给问候语，比固定「欢迎」更自然。 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const canUseAiAssistant = computed(() => {
  const role = getUser()?.role?.toUpperCase()
  return role === 'SUPER_ADMIN' || role === 'ADMIN'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

/** 快捷入口配置：图标 + 文字，避免一排彩色按钮。 */
const tiles = computed(() => {
  const list = [
    { label: '抄表任务', path: '/mobile/meter-tasks', icon: Edit },
    { label: '报修工单', path: '/mobile/repairs', icon: Tools },
    { label: '账单收款', path: '/mobile/bills', icon: Wallet },
    { label: '消息中心', path: '/mobile/messages', icon: Bell, badge: true },
    { label: '楼栋房间', path: '/mobile/buildings', icon: OfficeBuilding },
    { label: '租户查询', path: '/mobile/tenants', icon: Search }
  ]
  if (canUseAiAssistant.value) {
    list.push({ label: 'AI 助手', path: '/mobile/ai-assistant', icon: MagicStick })
  }
  list.push({ label: '扫码进房', path: '/mobile/scan', icon: FullScreen })
  return list
})

/** 工单状态对应的标签色。 */
function statusTag(status) {
  return {
    pending: 'warning',
    processing: 'primary',
    pending_accept: 'warning',
    completed: 'success',
    cancelled: 'info'
  }[status] || 'info'
}

/** 员工无权限访问 AI 助手时不应出现在磁贴里。 */

function go(path) {
  router.push(path)
}

function statusText(status) {
  return {
    pending: '待分配',
    processing: '维修中',
    pending_accept: '待验收',
    completed: '已完成',
    cancelled: '已取消'
  }[status] || status || '-'
}

function openRepairs(status) {
  router.push({ path: '/mobile/repairs', query: status ? { status } : {} })
}

async function load() {
  loading.value = true
  try {
    const [contractRes, repairRes, billRes] = await Promise.all([
      getContracts({ status: 'ACTIVE', page: 0, size: 500 }),
      getRepairOrders({ page: 0, size: 10 }),
      getBills({ page: 0, size: 300 })
    ])

    try {
      const unreadRes = await getUnreadCount()
      unreadCount.value = Number(unreadRes.data?.count || unreadRes.data || 0)
    } catch (_) {
      unreadCount.value = 0
    }

    const contracts = unwrapList(contractRes.data)
    const repairs = unwrapList(repairRes.data)
    const bills = unwrapList(billRes.data)

    stats.value.activeContracts = contracts.length
    stats.value.pendingRepairs = repairs.filter(item => item.status === 'pending').length
    stats.value.pendingAcceptRepairs = repairs.filter(item => item.status === 'pending_accept').length
    stats.value.unpaidRooms = new Set(
      bills.filter(item => item.status !== 'PAID' && item.status !== 'VOID' && item.roomId).map(item => item.roomId)
    ).size
    recentRepairs.value = repairs.slice(0, 6)
  } catch (_) {
    ElMessage.error('加载移动工作台失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* 首页只保留页面独有的细节，通用布局在 theme.css 里 */
.tile-badge {
  position: absolute;
  top: 8px;
  right: 8px;
}

.dot {
  margin: 0 4px;
  opacity: 0.4;
}

.empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
}

.empty-icon {
  color: var(--text-muted);
  opacity: 0.4;
}

.empty-text {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}
</style>
