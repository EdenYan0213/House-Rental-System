<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="card-title" style="margin:0">公告通知</div>
      <p class="muted">按发布时间倒序</p>
    </section>

    <section>
      <div v-for="item in rows" :key="item.id" class="list-item" @click="openDetail(item)">
        <h4 style="margin:0 0 8px">{{ item.title }}</h4>
        <p class="muted" style="margin:0 0 8px">{{ item.summary || '暂无摘要' }}</p>
        <p class="muted" style="margin:0">{{ formatTime(item.createdAt) }}</p>
      </div>

      <div class="panel" v-if="!rows.length">
        <p class="muted">暂无公告</p>
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
import { getNoticeList } from '../api'

const router = useRouter()
const rows = ref([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(0)
const size = 10

onMounted(resetAndLoad)

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
    const res = await getNoticeList({ page: page.value, size })
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
  router.push(`/notices/${item.id}`)
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
