<template>
  <div class="mobile-page" v-loading="loading">
    <el-page-header @back="goBack" content="欠费账单" />
    <el-card class="panel" v-for="bill in bills" :key="bill.id">
      <div class="row">
        <span>{{ bill.billMonth }}</span>
        <el-tag :type="billStatusTag(bill.status)">{{ billStatusText(bill.status) }}</el-tag>
      </div>
      <div class="muted">账单ID: {{ bill.id }}</div>
      <div class="amount">{{ formatMoney(bill.totalAmount) }}</div>
    </el-card>
    <el-empty v-if="!loading && bills.length === 0" description="暂无未支付账单" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMobileRoomSummary } from '@/api'
import { billStatusText, billStatusTag } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const bills = ref([])

function formatMoney(v) {
  return `￥${Number(v || 0).toFixed(2)}`
}

function goBack() {
  router.push(`/mobile/room/${route.params.roomId}`)
}

async function load() {
  loading.value = true
  try {
    const res = await getMobileRoomSummary(route.params.roomId)
    if (!res.data?.canOperate) {
      ElMessage.warning('当前账号仅可查看房间信息')
      goBack()
      return
    }
    bills.value = res.data.unpaidBills || []
  } catch (_) {
    ElMessage.error('加载账单失败')
  } finally {
    loading.value = false
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
.panel {
  margin-top: 10px;
}
.row {
  display: flex;
  justify-content: space-between;
}
.muted {
  color: var(--text-muted);
  margin-top: 6px;
}
.amount {
  margin-top: 8px;
  font-size: 20px;
  color: var(--danger-color);
  font-weight: 700;
}
</style>
