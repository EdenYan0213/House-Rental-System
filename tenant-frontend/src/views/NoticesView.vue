<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="card-title" style="margin:0">公告通知</div>
      <p class="muted" style="margin:6px 0 0">按发布时间倒序</p>
    </section>

    <section>
      <LoadingBlock v-if="loading && !rows.length" />

      <div v-for="item in rows" :key="item.id" class="list-item" @click="openDetail(item)">
        <h4 style="margin:0 0 8px">{{ item.title }}</h4>
        <p class="muted" style="margin:0 0 8px">{{ item.summary || '暂无摘要' }}</p>
        <p class="muted" style="margin:0">{{ formatTime(item.createdAt) }}</p>
      </div>

      <div class="panel" v-if="!rows.length && !loading">
        <EmptyState text="暂无公告" />
      </div>

      <button class="btn btn-light" style="width:100%" v-if="hasMore" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中...' : '加载更多' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNoticeList } from '../api'
import { formatTime } from '../utils/format'
import { usePagedList } from '../composables/usePagedList'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const router = useRouter()

const { rows, loading, hasMore, loadMore, resetAndLoad } = usePagedList(
  (page, size) => getNoticeList({ page, size }),
  { errorText: '公告加载失败' }
)

onMounted(resetAndLoad)



function openDetail(item) {
  router.push(`/notices/${item.id}`)
}

</script>
