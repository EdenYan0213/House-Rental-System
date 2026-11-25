<template>
  <PullRefresh :on-refresh="loadAll">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">消息中心</h1>
            <p class="m-head-sub">
              待办 <span class="m-num count">{{ todos.length }}</span>
              <span v-if="unreadCount > 0" class="unread-hint">
                · {{ unreadCount }} 条未读通知
              </span>
            </p>
          </div>
          <el-button :icon="Refresh" circle class="refresh-btn" @click="loadAll" />
        </div>
      </header>

      <!-- 标签切换：chips 而不是 tabs，移动端更紧凑 -->
      <div class="tab-chips">
        <button
          type="button"
          class="chip"
          :class="{ active: activeTab === 'todo' }"
          @click="activeTab = 'todo'"
        >待办 {{ todos.length > 0 ? `(${todos.length})` : '' }}</button>
        <button
          type="button"
          class="chip"
          :class="{ active: activeTab === 'notice' }"
          @click="activeTab = 'notice'"
        >通知 {{ unreadCount > 0 ? `(${unreadCount})` : '' }}</button>
      </div>

      <!-- 待办 -->
      <template v-if="activeTab === 'todo'">
        <div v-if="todos.length === 0" class="m-card empty-state">
          <el-icon :size="32" class="empty-icon"><CircleCheck /></el-icon>
          <p class="empty-title">暂无待办</p>
          <p class="empty-desc">所有事项都已处理完成。</p>
        </div>

        <div v-else class="msg-list">
          <article v-for="item in todos" :key="item.id" class="m-card msg-card msg-card--todo">
            <div class="msg-top">
              <span class="msg-title">{{ item.title }}</span>
              <el-tag type="warning" size="small" effect="light">待办</el-tag>
            </div>
            <p class="msg-content">{{ item.content }}</p>
            <div class="msg-foot">
              <span class="msg-time">{{ item.time }}</span>
              <el-button type="primary" size="small" @click="goTodo(item)">去处理</el-button>
            </div>
          </article>
        </div>
      </template>

      <!-- 通知 -->
      <template v-else>
        <div v-if="messages.length === 0" class="m-card empty-state">
          <el-icon :size="32" class="empty-icon"><Bell /></el-icon>
          <p class="empty-title">暂无通知</p>
          <p class="empty-desc">系统通知会显示在这里。</p>
        </div>

        <div v-else class="msg-list">
          <article
            v-for="item in messages"
            :key="item.id"
            class="m-card msg-card"
            :class="{ 'is-unread': !item.isRead }"
            @click="openNotice(item)"
          >
            <div class="msg-top">
              <span class="msg-title">{{ item.title || '-' }}</span>
              <span v-if="!item.isRead" class="unread-dot"></span>
            </div>
            <p class="msg-content line-clamp">{{ item.content || '-' }}</p>
            <div class="msg-foot">
              <span class="msg-time">{{ formatTime(item.createdAt) }}</span>
              <el-button
                v-if="!item.isRead"
                type="primary"
                link
                size="small"
                @click.stop="markRead(item)"
              >标记已读</el-button>
            </div>
          </article>
        </div>
      </template>

    <el-drawer v-model="showNotice" title="通知详情" size="100%">
      <template v-if="currentNotice">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标题">{{ currentNotice.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatExactTime(currentNotice.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ currentNotice.content || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="notice-actions">
          <el-button @click="markRead(currentNotice)" :disabled="currentNotice.isRead">标记已读</el-button>
          <el-button type="danger" plain @click="removeNotice(currentNotice)">删除</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
  </PullRefresh>
</template>

<script setup>
import { formatDateTime, formatRelative } from '@/utils/format'
import PullRefresh from '../components/PullRefresh.vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, CircleCheck, Bell } from '@element-plus/icons-vue'
import { getMessages, getRepairOrders, getBills, getContracts, markMessageRead, getUnreadCount } from '@/api'
import { unwrapList } from '@/utils/mobileApi'
import { getMobileCache, setMobileCache } from '@/utils/mobileCache'

const MESSAGE_CACHE_KEY = 'messages_center_v2'
const TODO_CACHE_KEY = 'todos_center_v2'
const CACHE_TTL = 24 * 60 * 60 * 1000

const router = useRouter()
const loading = ref(false)
const activeTab = ref('todo')
const unreadCount = ref(0)
const messages = ref([])
const todos = ref([])
const removedIds = ref(new Set())
const showNotice = ref(false)
const currentNotice = ref(null)

// 列表用相对时间（更适合消息场景），详情用精确时间
function formatTime(value) {
  return formatRelative(value)
}

function formatExactTime(value) {
  return formatDateTime(value)
}

function readCacheFirst() {
  const msgCache = getMobileCache(MESSAGE_CACHE_KEY)
  const todoCache = getMobileCache(TODO_CACHE_KEY)
  if (Array.isArray(msgCache)) messages.value = msgCache
  if (Array.isArray(todoCache)) todos.value = todoCache
}

function buildTodos({ repairs, bills, contracts }) {
  const list = []
  repairs.filter(item => item.status === 'pending').forEach(item => {
    list.push({
      id: `repair_pending_${item.id}`,
      type: 'repair_pending',
      title: '新工单待分配',
      content: `${item.roomNumber || '-'} ${item.faultType || ''}`,
      time: formatRelative(item.createdAt),
      route: '/mobile/repairs'
    })
  })

  repairs.filter(item => item.status === 'pending_accept').forEach(item => {
    list.push({
      id: `repair_accept_${item.id}`,
      type: 'repair_accept',
      title: '工单待验收',
      content: `${item.roomNumber || '-'} ${item.faultType || ''}`,
      time: item.updatedAt || item.createdAt || '-',
      route: '/mobile/repairs?status=pending_accept'
    })
  })

  const unpaidCount = bills.filter(item => item.status === 'PENDING' || item.status === 'PARTIAL').length
  if (unpaidCount > 0) {
    list.push({
      id: 'bill_unpaid_total',
      type: 'bill_unpaid',
      title: '待收款账单',
      content: `当前共有${unpaidCount}笔待处理账单`,
      time: new Date().toLocaleString('zh-CN'),
      route: '/mobile/bills'
    })
  }

  contracts.filter(item => Number(item.daysToExpire || 9999) <= 7 && item.status === 'ACTIVE').forEach(item => {
    list.push({
      id: `contract_expire_${item.id}`,
      type: 'contract_expire',
      title: '合同即将到期',
      content: `${item.roomNumber || '-'} ${item.tenantName || '-'}，剩余${item.daysToExpire}天`,
      time: item.endDate || '-',
      route: '/mobile/tenants'
    })
  })

  return list
}

async function loadAll() {
  loading.value = true
  try {
    const [msgRes, unreadRes, repairRes, billRes, contractRes] = await Promise.all([
      getMessages({ page: 0, size: 100 }),
      getUnreadCount(),
      getRepairOrders({ page: 0, size: 100 }),
      getBills({ page: 0, size: 120 }),
      getContracts({ page: 0, size: 120 })
    ])

    const allMessages = unwrapList(msgRes.data).filter(item => !removedIds.value.has(item.id))
    messages.value = allMessages
    unreadCount.value = Number(unreadRes.data?.count || unreadRes.data || 0)

    const todoList = buildTodos({
      repairs: unwrapList(repairRes.data),
      bills: unwrapList(billRes.data),
      contracts: unwrapList(contractRes.data)
    })
    todos.value = todoList

    setMobileCache(MESSAGE_CACHE_KEY, allMessages, CACHE_TTL)
    setMobileCache(TODO_CACHE_KEY, todoList, CACHE_TTL)
  } catch (_) {
    ElMessage.warning('网络异常，已展示缓存消息')
  } finally {
    loading.value = false
  }
}

async function markRead(item) {
  if (!item || item.isRead) return
  try {
    await markMessageRead(item.id)
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (_) {
    ElMessage.error('标记已读失败')
  }
}

function openNotice(item) {
  currentNotice.value = item
  showNotice.value = true
  markRead(item)
}

function removeNotice(item) {
  removedIds.value.add(item.id)
  messages.value = messages.value.filter(msg => msg.id !== item.id)
  showNotice.value = false
}

function goTodo(item) {
  router.push(item.route)
}

onMounted(() => {
  readCacheFirst()
  loadAll()
})
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.refresh-btn {
  flex: 0 0 auto;
  min-height: 36px;
  min-width: 36px;
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.unread-hint {
  color: var(--danger-color);
  font-size: 12px;
}

/* 标签切换 chips */
.tab-chips {
  display: flex;
  gap: 8px;
}

.chip {
  flex: 1;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-muted);
  border-radius: 10px;
  padding: 9px 12px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  min-height: 40px;
  transition: all 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}

.chip.active {
  background: var(--accent-3);
  border-color: var(--accent-3);
  color: #fff;
  font-weight: 600;
}

/* 消息列表 */
.msg-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.msg-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
  position: relative;
}

/* 待办卡左侧加色条，与通知区分 */
.msg-card--todo {
  border-left: 3px solid var(--warning-color, #b3761a);
}

/* 未读通知：左侧主色条 + 淡底 */
.msg-card.is-unread {
  border-left: 3px solid var(--accent-3);
  background: color-mix(in srgb, var(--accent-3) 4%, var(--bg-surface));
}

.msg-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.msg-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.4;
  flex: 1;
  min-width: 0;
}

.unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--danger-color);
  flex: 0 0 auto;
  margin-top: 6px;
}

.msg-content {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.55;
}

.line-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.msg-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--border-color);
}

.msg-time {
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

/* 空态 */
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
