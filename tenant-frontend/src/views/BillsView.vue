<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">我的账单</div>
        <div>
          <button class="btn btn-light" style="width:78px;margin-right:6px" @click="switchStatus('')">全部</button>
          <button class="btn btn-light" style="width:96px" @click="switchStatus('PENDING')">待支付</button>
        </div>
      </div>
    </section>

    <section style="margin-top:10px">
      <div v-for="item in rows" :key="item.id" class="list-item" @click="openDetail(item)">
        <div class="row-between">
          <strong>{{ item.billMonth }}</strong>
          <span :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
        </div>
        <p style="margin:8px 0 4px">总金额：￥{{ Number(item.totalAmount || 0).toFixed(2) }}</p>
        <p class="muted" style="margin:0">未付：￥{{ Number(item.unpaidAmount || 0).toFixed(2) }}</p>
      </div>

      <div class="panel" v-if="!rows.length">
        <p class="muted">暂无账单</p>
      </div>

      <button class="btn btn-light" style="width:100%" v-if="hasMore" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中...' : '加载更多' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBillList } from '../api'

const route = useRoute()
const router = useRouter()
const rows = ref([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(0)
const size = 10
const status = ref('')

onMounted(() => {
  status.value = String(route.query.status || '')
  resetAndLoad()
})

watch(() => route.query.status, (value) => {
  status.value = String(value || '')
  resetAndLoad()
})

function switchStatus(next) {
  router.replace({ path: '/bills', query: next ? { status: next } : {} })
}

async function resetAndLoad() {
  rows.value = []
  page.value = 0
  hasMore.value = true
  await loadMore()
}

async function loadMore() {
  if (!hasMore.value || loading.value) return
  loading.value = true
  try {
    const res = await getBillList({ status: status.value, page: page.value, size })
    const data = res.data || {}
    const list = data.content || []
    rows.value = rows.value.concat(list)
    page.value += 1
    hasMore.value = rows.value.length < Number(data.totalElements || 0)
  } finally {
    loading.value = false
  }
}

function openDetail(item) {
  router.push(`/bills/${item.id}`)
}

function statusText(value) {
  return {
    PENDING: '待支付',
    PARTIAL: '部分支付',
    PAID: '已支付',
    VOID: '已作废'
  }[String(value || '').toUpperCase()] || value
}

function statusClass(value) {
  const statusValue = String(value || '').toUpperCase()
  if (statusValue === 'PENDING') return 'tag tag-pending'
  if (statusValue === 'PAID') return 'tag tag-paid'
  if (statusValue === 'PARTIAL') return 'tag tag-partial'
  return 'tag tag-void'
}
</script>
