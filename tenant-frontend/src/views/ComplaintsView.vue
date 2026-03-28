<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">在线投诉</div>
        <button class="btn btn-primary" style="width:96px" @click="$router.push('/complaints/create')">发起投诉</button>
      </div>
      <div style="margin-top:10px" class="grid-two">
        <button class="btn btn-light" @click="switchStatus('all')">全部</button>
        <button class="btn btn-light" @click="switchStatus('processing')">处理中</button>
      </div>
      <div style="margin-top:8px">
        <button class="btn btn-light" style="width:100%" @click="switchStatus('completed')">已完成</button>
      </div>
    </section>

    <section style="margin-top:10px">
      <div v-for="item in rows" :key="item.id" class="list-item" @click="openDetail(item.id)">
        <div class="row-between">
          <strong>{{ item.title || '-' }}</strong>
          <span :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
        </div>
        <p style="margin:8px 0 4px">类型：{{ item.type || '-' }}</p>
        <p class="muted" style="margin:0">{{ item.roomNumber || '-' }} | {{ formatTime(item.createdAt) }}</p>
      </div>

      <div class="panel" v-if="!rows.length">
        <p class="muted">暂无投诉记录</p>
      </div>

      <button class="btn btn-light" style="width:100%" v-if="hasMore" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中...' : '加载更多' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getComplaintList } from '../api'

const router = useRouter()
const rows = ref([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(0)
const size = 10
const status = ref('all')

onMounted(resetAndLoad)

function switchStatus(next) {
  status.value = next
  resetAndLoad()
}

async function resetAndLoad() {
  rows.value = []
  page.value = 0
  hasMore.value = true
  await loadMore()
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const params = { page: page.value, size }
    if (status.value && status.value !== 'all') {
      params.status = status.value
    }
    const res = await getComplaintList(params)
    const data = res.data || {}
    const list = data.content || []
    rows.value = rows.value.concat(list)
    page.value += 1
    hasMore.value = rows.value.length < Number(data.totalElements || 0)
  } finally {
    loading.value = false
  }
}

function openDetail(id) {
  router.push(`/complaints/${id}`)
}

function statusText(value) {
  const key = String(value || '').toLowerCase()
  if (['pending', 'processing', 'pending_confirm'].includes(key)) return '处理中'
  if (['completed', 'closed'].includes(key)) return '已完成'
  return value || '-'
}

function statusClass(value) {
  const key = String(value || '').toLowerCase()
  if (['pending', 'processing', 'pending_confirm'].includes(key)) return 'tag tag-pending'
  if (['completed', 'closed'].includes(key)) return 'tag tag-paid'
  return 'tag tag-void'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
