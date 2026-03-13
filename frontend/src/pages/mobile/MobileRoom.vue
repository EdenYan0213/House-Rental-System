<template>
  <div class="mobile-page" v-loading="loading">
    <div class="topbar">
      <el-button text @click="goBack" v-if="canBack">返回</el-button>
      <div class="title">{{ roomTitle }}</div>
    </div>

    <el-card class="section" v-if="summary.room">
      <div class="room-main">
        <div>{{ summary.room.buildingName }} {{ summary.room.roomNumber }}</div>
        <el-tag size="small">{{ summary.room.status || '未知状态' }}</el-tag>
      </div>
      <div class="muted">{{ summary.room.layout }} · {{ summary.room.area }}m² · {{ summary.room.floor }}层</div>
    </el-card>

    <el-card class="section" v-if="summary.tenant">
      <template #header>租户信息</template>
      <div class="line">姓名: {{ summary.tenant.name }}</div>
      <div class="line">
        手机:
        <a :href="`tel:${summary.tenant.phone}`">{{ summary.tenant.phone }}</a>
      </div>
    </el-card>

    <el-card class="section" v-if="summary.contract">
      <template #header>合同信息</template>
      <div class="line">起止: {{ summary.contract.startDate }} ~ {{ summary.contract.endDate }}</div>
      <div class="line">月租: {{ formatMoney(summary.contract.monthlyRent) }}</div>
    </el-card>

    <el-card class="section">
      <template #header>账单摘要</template>
      <div class="arrears">欠费: {{ formatMoney(summary.unpaidAmount || 0) }}</div>
      <el-button text type="primary" :disabled="!summary.canOperate" @click="goUnpaid">查看欠费账单</el-button>
    </el-card>

    <el-card class="section" v-if="!summary.tenant">
      <div class="muted">当前房间空闲</div>
    </el-card>

    <el-alert
      v-if="!summary.canOperate"
      class="section"
      type="info"
      :closable="false"
      title="当前账号为只读权限，可查看房间信息，快捷操作已禁用"
    />

    <div class="actions">
      <el-button type="primary" :disabled="!summary.canOperate" @click="goMeter">录入水电</el-button>
      <el-button type="warning" :disabled="!summary.canOperate" @click="goRepair">发起报修</el-button>
      <el-button :disabled="!summary.canOperate" @click="goUnpaid">查看欠费</el-button>
      <el-button @click="openRepairHistory">维修历史</el-button>
    </div>

    <el-card class="section">
      <template #header>最近维修记录</template>
      <el-empty v-if="(summary.repairHistory || []).length === 0" description="暂无维修记录" />
      <div v-for="item in (summary.repairHistory || [])" :key="item.id" class="repair-item">
        <div>{{ item.faultType }} · {{ item.status }}</div>
        <div class="muted">{{ item.createdAt || '-' }}</div>
      </div>
    </el-card>

    <el-drawer v-model="showAllRepair" title="维修历史" size="100%">
      <el-table :data="allRepairHistory" style="width: 100%">
        <el-table-column prop="faultType" label="故障类型" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMobileRoomSummary, getRoomRepairOrders } from '@/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const showAllRepair = ref(false)
const loadingHistory = ref(false)
const allRepairHistory = ref([])
const summary = ref({
  room: null,
  tenant: null,
  contract: null,
  unpaidAmount: 0,
  unpaidBills: [],
  repairHistory: [],
  canOperate: false
})

const roomId = computed(() => Number(route.params.id || route.params.roomId))
const roomTitle = computed(() => summary.value.room ? `${summary.value.room.roomNumber}` : '房间详情')
const canBack = computed(() => window.history.length > 1)

function formatMoney(v) {
  return `￥${Number(v || 0).toFixed(2)}`
}

async function load() {
  loading.value = true
  try {
    const res = await getMobileRoomSummary(roomId.value)
    summary.value = res.data
  } catch (_) {
    ElMessage.error('加载房间信息失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

function goMeter() {
  if (!summary.value.canOperate) return
  router.push(`/mobile/meter/${roomId.value}`)
}

function goRepair() {
  if (!summary.value.canOperate) return
  router.push(`/mobile/repair/${roomId.value}`)
}

function goUnpaid() {
  if (!summary.value.canOperate) return
  router.push(`/mobile/bills/${roomId.value}`)
}

async function openRepairHistory() {
  showAllRepair.value = true
  if (loadingHistory.value) return
  loadingHistory.value = true
  try {
    const res = await getRoomRepairOrders(roomId.value)
    allRepairHistory.value = res.data || []
  } catch (_) {
    allRepairHistory.value = summary.value.repairHistory || []
  } finally {
    loadingHistory.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mobile-page {
  padding: 12px;
  max-width: 760px;
  margin: 0 auto;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.section {
  margin-bottom: 10px;
}
.room-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.line {
  margin-bottom: 6px;
}
.muted {
  color: var(--text-muted);
}
.arrears {
  color: var(--danger-color);
  font-size: 22px;
  font-weight: 700;
}
.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}
.repair-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
}
@media (max-width: 520px) {
  .actions {
    grid-template-columns: 1fr;
  }
}
</style>
