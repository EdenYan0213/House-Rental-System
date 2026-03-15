<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <div class="title">消息中心</div>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0">
        <el-button text @click="loadAll">刷新</el-button>
      </el-badge>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="待办" name="todo" />
      <el-tab-pane label="通知" name="notice" />
    </el-tabs>

    <template v-if="activeTab === 'todo'">
      <el-empty v-if="todos.length === 0" description="暂无待办" />
      <el-card v-for="item in todos" :key="item.id" class="panel" shadow="never">
        <div class="row top">
          <div class="todo-title">{{ item.title }}</div>
          <el-tag type="warning" size="small">待办</el-tag>
        </div>
        <div class="muted">{{ item.content }}</div>
        <div class="row foot">
          <span class="muted">{{ item.time }}</span>
          <el-button type="primary" link @click="goTodo(item)">去处理</el-button>
        </div>
      </el-card>
    </template>

    <template v-else>
      <el-empty v-if="messages.length === 0" description="暂无通知" />
      <el-card v-for="item in messages" :key="item.id" class="panel" shadow="never" @click="openNotice(item)">
        <div class="row top">
          <div>{{ item.title || '-' }}</div>
          <el-tag size="small" :type="item.isRead ? 'info' : 'danger'">{{ item.isRead ? '已读' : '未读' }}</el-tag>
        </div>
        <div class="muted line-clamp">{{ item.content || '-' }}</div>
        <div class="row foot">
          <span class="muted">{{ formatTime(item.createdAt) }}</span>
          <el-button type="primary" link @click.stop="markRead(item)">标记已读</el-button>
        </div>
      </el-card>
    </template>

    <el-drawer v-model="showNotice" title="通知详情" size="100%">
      <template v-if="currentNotice">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标题">{{ currentNotice.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(currentNotice.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ currentNotice.content || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="notice-actions">
          <el-button @click="markRead(currentNotice)" :disabled="currentNotice.isRead">标记已读</el-button>
          <el-button type="danger" plain @click="removeNotice(currentNotice)">删除</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  return d.toLocaleString('zh-CN')
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
      time: item.createdAt || '-',
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
.mobile-page {
  padding: 14px;
  max-width: 760px;
  margin: 0 auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: #1d3266;
}

.panel {
  margin-bottom: 10px;
  border: 1px solid #e8efff;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.top {
  margin-bottom: 6px;
}

.foot {
  margin-top: 6px;
}

.muted {
  color: #7685a4;
  font-size: 13px;
}

.todo-title {
  font-weight: 600;
  color: #1d3266;
}

.line-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notice-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
</style>
