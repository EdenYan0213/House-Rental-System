<template>
  <div class="mobile-page" v-loading="loading">
    <!-- 顶栏 -->
    <header class="bills-topbar">
      <el-button link :icon="ArrowLeft" class="back-link" @click="goBack">返回</el-button>
    </header>

    <!-- 汇总 -->
    <section class="bills-summary" v-if="bills.length > 0">
      <div class="summary-label">未支付账单</div>
      <div class="summary-amount m-num">{{ formatMoney(totalAmount) }}</div>
      <div class="summary-count">共 {{ bills.length }} 笔</div>
    </section>

    <!-- 账单列表 -->
    <div v-if="bills.length > 0" class="bill-list">
      <article v-for="bill in bills" :key="bill.id" class="m-card bill-card">
        <div class="bill-top">
          <span class="bill-month m-num">{{ bill.billMonth || '-' }}</span>
          <el-tag :type="billStatusTag(bill.status)" size="small" effect="light">
            {{ billStatusText(bill.status) }}
          </el-tag>
        </div>
        <div class="bill-bottom">
          <span class="bill-id m-num">#{{ bill.id }}</span>
          <span class="bill-amount m-num">{{ formatMoney(bill.totalAmount) }}</span>
        </div>
      </article>
    </div>

    <!-- 空态 -->
    <div v-else-if="!loading" class="m-card empty-state">
      <el-icon :size="32" class="empty-icon"><CircleCheck /></el-icon>
      <p class="empty-title">暂无未支付账单</p>
      <p class="empty-desc">该房间的账单都已结清。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CircleCheck } from '@element-plus/icons-vue'
import { getMobileRoomSummary } from '@/api'
import { billStatusText, billStatusTag } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const bills = ref([])

/** 未支付合计。 */
const totalAmount = computed(() =>
  bills.value.reduce((sum, b) => sum + (Number(b.totalAmount) || 0), 0)
)

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
.bills-topbar {
  display: flex;
  align-items: center;
  min-height: 36px;
}

.back-link {
  color: var(--accent-3);
  padding: 0;
}

/* 汇总卡 */
.bills-summary {
  padding: 18px var(--m-gutter);
  background: color-mix(in srgb, var(--danger-color) 8%, var(--bg-surface));
  border: 1px solid color-mix(in srgb, var(--danger-color) 25%, transparent);
  border-radius: var(--m-radius);
  text-align: center;
}

.summary-label {
  font-size: 12px;
  color: var(--text-muted);
}

.summary-amount {
  font-size: 28px;
  font-weight: 700;
  color: var(--danger-color);
  line-height: 1.2;
  margin: 6px 0 2px;
  letter-spacing: -0.02em;
}

.summary-count {
  font-size: 12px;
  color: var(--text-muted);
}

/* 账单卡 */
.bill-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.bill-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bill-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.bill-month {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.bill-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.bill-id {
  font-size: 11px;
  color: var(--text-muted);
}

.bill-amount {
  font-size: 16px;
  font-weight: 700;
  color: var(--danger-color);
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
  color: var(--accent-3);
  opacity: 0.5;
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
