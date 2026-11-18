<template>
  <div class="mobile-page" v-loading="loading">
    <header class="head">
      <div class="title">移动工作台</div>
      <div class="subtitle">欢迎，{{ userName }}</div>
    </header>

    <section class="stats">
      <article class="card">
        <div class="label">进行中合同</div>
        <div class="value">{{ stats.activeContracts }}</div>
      </article>
      <article class="card">
        <div class="label">待分配工单</div>
        <div class="value">{{ stats.pendingRepairs }}</div>
      </article>
      <article class="card">
        <div class="label">待验收工单</div>
        <div class="value">{{ stats.pendingAcceptRepairs }}</div>
      </article>
      <article class="card">
        <div class="label">本月欠费房间</div>
        <div class="value">{{ stats.unpaidRooms }}</div>
      </article>
    </section>

    <el-card class="panel" shadow="never">
      <template #header>快捷入口</template>
      <div class="actions">
        <el-button type="primary" @click="go('/mobile/meter-tasks')">抄表任务</el-button>
        <el-button type="warning" @click="go('/mobile/repairs')">报修工单</el-button>
        <el-button type="danger" plain @click="go('/mobile/bills')">账单收款</el-button>
        <el-badge :value="unreadCount" :hidden="unreadCount === 0">
          <el-button @click="go('/mobile/messages')">消息中心</el-button>
        </el-badge>
        <el-button @click="go('/mobile/buildings')">楼栋房间</el-button>
        <el-button @click="go('/mobile/tenants')">租户查询</el-button>
        <el-button v-if="canUseAiAssistant" type="success" plain @click="go('/mobile/ai-assistant')">AI助手</el-button>
        <el-button @click="go('/mobile/scan')">扫码进房间</el-button>
      </div>
    </el-card>

    <el-card class="panel" shadow="never">
      <template #header>最近工单</template>
      <el-empty v-if="recentRepairs.length === 0" description="暂无工单" />
      <div v-for="item in recentRepairs" :key="item.id" class="list-item" @click="openRepairs(item.status)">
        <div>{{ item.roomNumber || '-' }} · {{ item.faultType || '-' }}</div>
        <el-tag size="small">{{ statusText(item.status) }}</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContracts, getRepairOrders, getBills, getUser, getUnreadCount } from '@/api'
import { unwrapList } from '@/utils/mobileApi'

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

const canUseAiAssistant = computed(() => {
  const role = getUser()?.role?.toUpperCase()
  return role === 'SUPER_ADMIN' || role === 'ADMIN'
})

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
.mobile-page {
  padding: 14px;
  max-width: 760px;
  margin: 0 auto;
}

.head {
  margin: 6px 2px 14px;
}

.title {
  font-size: 22px;
  font-weight: 700;
  color: #1a3365;
}

.subtitle {
  color: #6c7b97;
  margin-top: 4px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.card {
  padding: 12px;
  border-radius: 12px;
  background: var(--bg-surface);
  border: 1px solid #e8efff;
}

.label {
  color: #6b7a99;
  font-size: 12px;
}

.value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  color: #0f4de2;
}

.panel {
  margin-bottom: 12px;
  border: 1px solid #e8efff;
}

.actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.actions :deep(.el-button) {
  width: 100%;
  min-height: 42px;
  margin: 0;
  border-radius: 10px;
}

.actions :deep(.el-badge) {
  width: 100%;
  display: block;
}

.actions :deep(.el-badge .el-button) {
  width: 100%;
}

.list-item {
  padding: 10px 0;
  border-bottom: 1px solid #ecf0fb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-item:last-child {
  border-bottom: none;
}

@media (max-width: 420px) {
  .actions {
    grid-template-columns: 1fr;
  }
}
</style>
