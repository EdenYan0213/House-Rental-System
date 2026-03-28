<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>消息中心</h2>
      <el-button type="primary" link @click="markAllRead" :disabled="unreadCount === 0">全部标为已读</el-button>
    </el-row>

    <el-tabs v-model="activeTab" style="margin-top: 16px" @tab-change="loadMessages">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="未读" name="unread">
        <template #label>
          <span>未读 <el-badge :value="unreadCount" :hidden="unreadCount === 0" /></span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-row style="margin-top: 8px" :gutter="10" align="middle">
      <el-col :span="6">
        <el-select v-model="typeFilter" clearable placeholder="消息类型" style="width: 100%" @change="onTypeFilterChange">
          <el-option label="全部类型" value="" />
          <el-option label="租户注册" value="TENANT_REGISTER" />
          <el-option label="租房沟通" value="TENANT_RENT_INTENT" />
          <el-option label="合同到期" value="CONTRACT_EXPIRE" />
          <el-option label="短信通知" value="SMS_NOTIFY" />
          <el-option label="短信模拟" value="SMS_MOCK" />
        </el-select>
      </el-col>
    </el-row>

    <el-table :data="messages" v-loading="loading" style="width: 100%" @row-click="handleRowClick">
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="150" />
      <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isRead ? 'info' : 'danger'" size="small">
            {{ row.isRead ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <div class="message-actions">
            <el-button class="message-action-btn" type="primary" link @click.stop="openDetail(row)">详情</el-button>
            <el-button
              v-if="canNavigate(row)"
              class="message-action-btn"
              type="warning"
              link
              @click.stop="goFromMessage(row)"
            >
              前往
            </el-button>
          </div>
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
      @current-change="loadMessages"
    />

    <el-dialog v-model="detailVisible" title="消息详情" width="680px">
      <div class="detail-actions" v-if="currentMessage">
        <el-button size="small" @click="copyTitle">复制标题</el-button>
        <el-button size="small" @click="copyContent">复制内容</el-button>
        <el-button size="small" type="primary" @click="copyAll">复制全部</el-button>
      </div>
      <el-descriptions :column="1" border v-if="currentMessage">
        <el-descriptions-item label="标题">{{ currentMessage.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(currentMessage.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ currentMessage.isRead ? '已读' : '未读' }}
        </el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentMessage.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="内容">
          <div class="message-content" v-html="renderMessageContent(currentMessage.content)"></div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMessages, markMessageRead, markAllMessagesRead } from '../api'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'

const router = useRouter()
const messages = ref([])
const loading = ref(false)
const unreadCount = ref(0)
const activeTab = ref('all')
const typeFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const currentMessage = ref(null)

function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function loadMessages() {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: pageSize.value }
    if (activeTab.value === 'unread') {
      params.isRead = false
    }
    if (typeFilter.value) {
      params.type = typeFilter.value
    }
    const res = await getMessages(params)
    messages.value = res.data.content || []
    total.value = res.data.totalElements || 0
    unreadCount.value = res.data.unreadCount || 0
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function onTypeFilterChange() {
  page.value = 1
  loadMessages()
}

function typeText(type) {
  const map = {
    TENANT_REGISTER: '租户注册',
    TENANT_RENT_INTENT: '租房沟通',
    CONTRACT_EXPIRE: '合同到期',
    SMS_NOTIFY: '短信通知',
    SMS_MOCK: '短信模拟'
  }
  return map[type] || (type || '-')
}

function typeTagType(type) {
  if (type === 'TENANT_REGISTER') return 'success'
  if (type === 'TENANT_RENT_INTENT') return 'warning'
  if (type === 'CONTRACT_EXPIRE') return 'danger'
  if (type === 'SMS_NOTIFY' || type === 'SMS_MOCK') return 'info'
  return ''
}

function canNavigate(row) {
  return ['CONTRACT_EXPIRE', 'TENANT_REGISTER', 'TENANT_RENT_INTENT'].includes(row?.type)
}

function extractPhone(content) {
  const text = String(content || '')
  const m = text.match(/1\d{10}/)
  return m ? m[0] : ''
}

function goFromMessage(row) {
  if (row.type === 'CONTRACT_EXPIRE') {
    router.push({ path: '/contracts', query: row.relatedId ? { id: row.relatedId } : {} })
    return
  }
  if (row.type === 'TENANT_REGISTER' || row.type === 'TENANT_RENT_INTENT') {
    const phone = extractPhone(row.content)
    if (phone) {
      router.push({ path: '/tenants', query: { q: phone } })
    } else {
      router.push('/tenants')
    }
  }
}

async function handleRowClick(row) {
  if (!row.isRead) {
    await markMessageRead(row.id)
    row.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (row.type === 'CONTRACT_EXPIRE' && row.relatedId) {
    router.push({ path: '/contracts', query: { id: row.relatedId } })
  } else if (row.type === 'TENANT_REGISTER' || row.type === 'TENANT_RENT_INTENT') {
    goFromMessage(row)
  }
}

async function openDetail(row) {
  if (!row.isRead) {
    try {
      await markMessageRead(row.id)
      row.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (e) {
      ElMessage.warning('标记已读失败，请稍后重试')
    }
  }
  currentMessage.value = row
  detailVisible.value = true
}

async function copyText(text) {
  const finalText = String(text || '')
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(finalText)
      ElMessage.success('复制成功')
      return
    }
  } catch (e) {
    // fallback below
  }

  const area = document.createElement('textarea')
  area.value = finalText
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.left = '-9999px'
  document.body.appendChild(area)
  area.select()
  document.execCommand('copy')
  document.body.removeChild(area)
  ElMessage.success('复制成功')
}

function copyTitle() {
  copyText(currentMessage.value?.title || '')
}

function copyContent() {
  copyText(currentMessage.value?.content || '')
}

function copyAll() {
  const msg = currentMessage.value
  const text = [
    `标题: ${msg?.title || '-'}`,
    `时间: ${formatTime(msg?.createdAt)}`,
    `状态: ${msg?.isRead ? '已读' : '未读'}`,
    `类型: ${msg?.type || '-'}`,
    `内容: ${msg?.content || '-'}`
  ].join('\n')
  copyText(text)
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderMessageContent(content) {
  const raw = content && String(content).trim() ? String(content) : '-'
  const escaped = escapeHtml(raw)
  const withLinks = escaped.replace(
    /(https?:\/\/[^\s<]+)/g,
    '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>'
  )
  return withLinks.replace(/\n/g, '<br>')
}

async function markAllRead() {
  try {
    await markAllMessagesRead()
    ElMessage.success('已全部标为已读')
    loadMessages()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  loadMessages()
})
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}

.detail-actions {
  margin-bottom: 12px;
}

.message-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.message-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.message-action-btn {
  min-width: 34px;
}

.message-content {
  line-height: 1.6;
  max-height: 260px;
  overflow: auto;
  word-break: break-word;
}

.message-content :deep(a) {
  color: #409eff;
  text-decoration: underline;
}
</style>
