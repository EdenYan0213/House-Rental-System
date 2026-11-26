<template>
  <div class="page">
    <PageHeader title="租客看板" to="/me" />

    <LoadingBlock v-if="loading" />

    <template v-else>
      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">入住时长</div>
        <div class="stat-row">
          <div class="stat-card">
            <div class="stat-value">{{ data.stay?.months || 0 }}</div>
            <div class="stat-label">个月</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ data.stay?.days || 0 }}</div>
            <div class="stat-label">天</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ data.repair?.pending || 0 }}</div>
            <div class="stat-label">处理中报修</div>
          </div>
        </div>
      </section>

      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">近 12 个月账单</div>

        <!-- 用 CSS 条形图展示，不引入图表库 -->
        <div v-if="trend.length" class="bar-chart">
          <div v-for="item in trend" :key="item.month" class="bar-col">
            <div class="bar-wrap">
              <div class="bar" :style="{ height: barHeight(item.amount) }" :title="`￥${money(item.amount)}`"></div>
            </div>
            <div class="bar-label">{{ shortMonth(item.month) }}</div>
          </div>
        </div>
        <p class="muted" style="margin:10px 0 0" v-if="trend.length">
          合计 ￥{{ money(trendTotal) }} · 月均 ￥{{ money(trend.length ? trendTotal / trend.length : 0) }}
        </p>
        <EmptyState v-else text="暂无账单数据" />
      </section>

      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">报修与投诉</div>
        <div class="stat-row">
          <div class="stat-card">
            <div class="stat-value">{{ data.repair?.pending || 0 }}</div>
            <div class="stat-label">报修处理中</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ data.repair?.completed || 0 }}</div>
            <div class="stat-label">报修已完成</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ data.complaint?.pending || 0 }}</div>
            <div class="stat-label">投诉处理中</div>
          </div>
        </div>
        <p class="muted" style="margin:10px 0 0">
          累计投诉 {{ data.complaint?.total || 0 }} 条
        </p>
      </section>

      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">续租提醒</div>
        <p style="margin:6px 0">待处理申请：{{ data.renew?.pendingCount || 0 }} 条</p>
        <template v-if="data.renew?.hint">
          <p v-if="Number(data.renew.hint.daysLeft) >= 0" class="muted" style="margin:6px 0">
            距离合同到期还有 <strong>{{ data.renew.hint.daysLeft }}</strong> 天
          </p>
          <button
            v-if="data.renew.hint.canApplyRenew"
            class="btn btn-primary"
            style="width:100%;margin-top:10px"
            @click="goRenew"
          >
            去申请续租
          </button>
          <p v-else class="muted" style="margin:6px 0">距离到期 90 天内可申请续租</p>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getErrorMessage, getTenantDashboard } from '../api'
import { money } from '../utils/format'
import { showError } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const router = useRouter()
const data = ref({})
const loading = ref(true)

const trend = computed(() => {
  const list = data.value?.billTrend
  return Array.isArray(list) ? list : []
})

const trendTotal = computed(() =>
  trend.value.reduce((sum, item) => sum + Number(item.amount || 0), 0)
)

// 用最大值归一化，保证最高柱占满容器
const maxAmount = computed(() =>
  Math.max(1, ...trend.value.map(item => Number(item.amount || 0)))
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getTenantDashboard({ months: 12 })
    data.value = res.data || {}
  } catch (error) {
    showError(getErrorMessage(error, '看板数据加载失败'))
  } finally {
    loading.value = false
  }
}

function barHeight(amount) {
  const ratio = Number(amount || 0) / maxAmount.value
  // 最小 4% 保证 0 值也有基线可见
  return `${Math.max(4, Math.round(ratio * 100))}%`
}

/** 2026-03 → 3月 */
function shortMonth(value) {
  const text = String(value || '')
  const month = text.split('-')[1]
  return month ? `${Number(month)}月` : text
}

function goRenew() {
  const contractId = data.value?.renew?.hint?.contractId
  if (!contractId) {
    showError('未找到可续租的合同')
    return
  }
  router.push({ path: '/renew', query: { contractId } })
}
</script>

<style scoped>
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 120px;
  margin-top: 8px;
}

.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
}

.bar {
  width: 100%;
  min-height: 4px;
  border-radius: 4px 4px 2px 2px;
  background: linear-gradient(180deg, var(--brand-2), var(--brand));
}

.bar-label {
  font-size: 10px;
  color: var(--muted);
  margin-top: 4px;
  white-space: nowrap;
}
</style>
