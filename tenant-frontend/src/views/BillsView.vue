<template>
  <div class="page">
    <section class="panel">
      <div class="card-title" style="margin-bottom:10px">我的账单</div>

      <!-- 汇总来自后端全量统计，不是当前页求和 -->
      <div class="stat-row" v-if="summary">
        <div class="stat-card">
          <div class="stat-value">￥{{ money(summary.totalAmount) }}</div>
          <div class="stat-label">应缴合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-value" style="color: var(--danger)">￥{{ money(summary.unpaidAmount) }}</div>
          <div class="stat-label">未缴合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ summary.billCount || 0 }}</div>
          <div class="stat-label">账单笔数</div>
        </div>
      </div>

      <div class="segmented" style="margin-top:12px">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          :class="{ active: status === option.value }"
          @click="switchStatus(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </section>

    <section style="margin-top:10px">
      <LoadingBlock v-if="loading && !rows.length" />

      <template v-else>
        <div v-for="item in rows" :key="item.id" class="list-item" @click="openDetail(item)">
          <div class="row-between">
            <strong>{{ item.billMonth || '-' }}</strong>
            <span :class="billStatusClass(item.status)">{{ billStatusText(item.status) }}</span>
          </div>
          <p style="margin:8px 0 4px">总金额：￥{{ money(item.totalAmount) }}</p>
          <p class="muted" style="margin:0">
            未付：￥{{ money(item.unpaidAmount) }}
            <span v-if="Number(item.paidAmount) > 0"> · 已付 ￥{{ money(item.paidAmount) }}</span>
          </p>
        </div>

        <div class="panel" v-if="!rows.length">
          <EmptyState :text="status ? '当前筛选下没有账单' : '暂无账单'" />
        </div>

        <button class="btn btn-light" style="width:100%" v-if="hasMore" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </template>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBillList } from '../api'
import { billStatusClass, billStatusText, money } from '../utils/format'
import { usePagedList } from '../composables/usePagedList'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const route = useRoute()
const router = useRouter()
const status = ref('')

// extra 承载后端返回的全量汇总（summary），不受当前页/筛选影响
const { rows, loading, hasMore, loadMore, resetAndLoad, extra: summary } = usePagedList(
  (page, size) => getBillList({ status: status.value, page, size }),
  { errorText: '账单加载失败' }
)

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'PENDING' },
  { label: '部分支付', value: 'PARTIAL' },
  { label: '已支付', value: 'PAID' }
]

onMounted(() => {
  status.value = String(route.query.status || '')
  resetAndLoad()
})

watch(() => route.query.status, value => {
  const next = String(value || '')
  if (next === status.value) return
  status.value = next
  resetAndLoad()
})

function switchStatus(next) {
  router.replace({ path: '/bills', query: next ? { status: next } : {} })
}



function openDetail(item) {
  router.push(`/bills/${item.id}`)
}
</script>
