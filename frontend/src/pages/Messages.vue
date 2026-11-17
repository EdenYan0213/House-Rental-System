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

    <el-table :data="messages" v-loading="loading" style="width: 100%" @row-click="handleRowClick">
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-icon :size="20" color="#E6A23C"><Bell /></el-icon>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMessages, markMessageRead, markAllMessagesRead, getUnreadCount, getErrorMessage } from '../api'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'

const router = useRouter()
const messages = ref([])
const loading = ref(false)
const unreadCount = ref(0)
const activeTab = ref('all')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

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

async function handleRowClick(row) {
  if (!row.isRead) {
    await markMessageRead(row.id)
    row.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (row.type === 'CONTRACT_EXPIRE' && row.relatedId) {
    router.push({ path: '/contracts', query: { id: row.relatedId } })
  }
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
</style>
