<template>
  <div class="page">
    <PageHeader title="我的押金" to="/me" />

    <LoadingBlock v-if="loading" />

    <template v-else>
      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">押金概览</div>
        <div class="stat-row">
          <div class="stat-card">
            <div class="stat-value">￥{{ money(summary.received) }}</div>
            <div class="stat-label">累计缴纳</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: var(--success)">￥{{ money(summary.refunded) }}</div>
            <div class="stat-label">累计退还</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: var(--brand)">￥{{ money(summary.balance) }}</div>
            <div class="stat-label">当前余额</div>
          </div>
        </div>
        <p class="muted" style="margin:12px 0 0">
          余额为您当前仍存放在房东处的押金，退租结算后按合同约定退还。
        </p>
      </section>

      <section class="panel">
        <div class="card-title">变动明细</div>
        <div v-for="item in rows" :key="item.id" class="list-item">
          <div class="row-between">
            <strong style="color: item.type === 'REFUND' ? 'var(--success)' : 'var(--text)'">
              {{ item.type === 'REFUND' ? '-' : '+' }}￥{{ money(item.amount) }}
            </strong>
            <span :class="item.type === 'REFUND' ? 'tag tag-paid' : 'tag tag-partial'">
              {{ item.typeText }}
            </span>
          </div>
          <p class="muted" style="margin:8px 0 0">{{ formatTime(item.date) }}</p>
          <p v-if="item.remark" style="margin:6px 0 0">备注：{{ item.remark }}</p>
        </div>
        <EmptyState v-if="!rows.length" text="暂无押金记录" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getErrorMessage, getMyDeposits } from '../api'
import { formatTime, money } from '../utils/format'
import { showError } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const rows = ref([])
const summary = ref({ received: 0, refunded: 0, balance: 0 })
const loading = ref(true)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getMyDeposits()
    rows.value = res.data?.content || []
    summary.value = res.data?.summary || { received: 0, refunded: 0, balance: 0 }
  } catch (error) {
    showError(getErrorMessage(error, '押金记录加载失败'))
  } finally {
    loading.value = false
  }
}
</script>
