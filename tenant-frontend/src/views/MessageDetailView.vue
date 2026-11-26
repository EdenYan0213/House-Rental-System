<template>
  <div class="page">
    <PageHeader title="消息详情" to="/messages" />

    <LoadingBlock v-if="loading" />

    <section class="panel" v-else-if="item.id">
      <div class="row-between">
        <div class="card-title" style="margin:0">{{ item.title || '-' }}</div>
        <span v-if="item.category" class="tag tag-partial">{{ categoryText }}</span>
      </div>
      <p class="muted" style="margin:8px 0 0">{{ formatTime(item.createdAt) }}</p>

      <p style="white-space:pre-wrap;line-height:1.7;margin:14px 0 0">{{ item.content || '-' }}</p>

      <div v-if="item.targetUrl" class="grid-two" style="margin-top:16px">
        <button class="btn btn-primary" style="grid-column: span 2" @click="goTarget">去处理</button>
      </div>
    </section>

    <EmptyState v-else text="消息不存在或已删除" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getErrorMessage, getTenantMessageDetail } from '../api'
import { formatTime } from '../utils/format'
import { showError } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const route = useRoute()
const router = useRouter()
const item = ref({})
const loading = ref(true)

const CATEGORY_TEXT = { TODO: '待办', NOTICE: '通知', SYSTEM: '系统', BILL: '账单', REPAIR: '报修' }
const categoryText = computed(() => CATEGORY_TEXT[String(item.value.category || '').toUpperCase()] || item.value.category)

onMounted(load)

async function load() {
  loading.value = true
  try {
    // 详情接口打开即自动标记已读，无需前端额外调用
    const res = await getTenantMessageDetail(route.params.id)
    item.value = res.data || {}
  } catch (error) {
    showError(getErrorMessage(error, '消息加载失败'))
  } finally {
    loading.value = false
  }
}

function goTarget() {
  const target = item.value.targetUrl
  if (target) router.push(target)
}
</script>
