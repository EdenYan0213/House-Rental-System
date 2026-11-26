<template>
  <div class="page">
    <PageHeader title="消息中心" to="/me" />

    <section class="panel">
      <div class="row-between">
        <div>
          <div class="card-title" style="margin:0">全部消息</div>
          <p class="muted" style="margin:6px 0 0">未读 {{ unreadCount }} 条</p>
        </div>
        <button class="btn btn-light" style="width:96px" :disabled="!unreadCount || marking" @click="onReadAll">
          {{ marking ? '处理中' : '全部已读' }}
        </button>
      </div>

      <div class="segmented" style="margin-top:12px">
        <button
          v-for="option in filters"
          :key="option.value"
          :class="{ active: filter === option.value }"
          @click="changeFilter(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </section>

    <section style="margin-top:10px">
      <LoadingBlock v-if="loading && !rows.length" />

      <template v-else>
        <div
          v-for="item in rows"
          :key="item.id"
          class="list-item"
          :style="{ opacity: Number(item.isRead || 0) === 0 ? 1 : 0.72 }"
        >
          <div class="row-between" @click="$router.push(`/messages/${item.id}`)">
            <strong>
              <span v-if="Number(item.isRead || 0) === 0" class="dot-unread"></span>
              {{ item.title || '-' }}
            </strong>
            <span v-if="item.category" class="tag tag-partial">{{ categoryText(item.category) }}</span>
          </div>

          <p class="muted" style="margin:8px 0 0" @click="$router.push(`/messages/${item.id}`)">
            {{ item.summary || item.content || '-' }}
          </p>
          <p class="muted" style="margin:6px 0 0">{{ formatTime(item.createdAt) }}</p>

          <div class="grid-two" style="margin-top:10px">
            <button class="btn btn-light" @click="onRead(item)">标记已读</button>
            <button class="btn btn-light" @click="onDelete(item)">删除</button>
            <button
              v-if="item.needAction === 1 && item.actionStatus !== 'DONE'"
              class="btn btn-primary"
              style="grid-column: span 2"
              @click="onProcess(item)"
            >
              去处理
            </button>
          </div>
        </div>

        <div class="panel" v-if="!rows.length">
          <EmptyState :text="filter ? '当前分类下暂无消息' : '暂无消息'" />
        </div>

        <button class="btn btn-light" style="width:100%" v-if="hasMore" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </template>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteTenantMessage,
  getErrorMessage,
  getTenantMessages,
  processTenantMessage,
  readAllTenantMessages,
  readTenantMessage
} from '../api'
import { formatTime } from '../utils/format'
import { confirmDialog, showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const router = useRouter()
const rows = ref([])
const unreadCount = ref(0)
const loading = ref(false)
const marking = ref(false)
const hasMore = ref(true)
const page = ref(0)
const size = 10
const filter = ref('')

// 后端按 category 过滤：TODO/NOTICE/SYSTEM
const filters = [
  { label: '全部', value: '' },
  { label: '待办', value: 'TODO' },
  { label: '通知', value: 'NOTICE' }
]

const CATEGORY_TEXT = { TODO: '待办', NOTICE: '通知', SYSTEM: '系统', BILL: '账单', REPAIR: '报修' }

onMounted(resetAndLoad)

function changeFilter(value) {
  if (filter.value === value) return
  filter.value = value
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
    if (filter.value) params.category = filter.value
    const res = await getTenantMessages(params)
    const data = res.data || {}
    rows.value = rows.value.concat(data.content || [])
    page.value += 1
    hasMore.value = rows.value.length < Number(data.totalElements || 0)
    unreadCount.value = Number(data.unreadCount || 0)
  } catch (error) {
    showError(getErrorMessage(error, '消息加载失败'))
  } finally {
    loading.value = false
  }
}

async function onRead(item) {
  if (Number(item.isRead || 0) === 1) {
    showSuccess('该消息已读')
    return
  }
  try {
    await readTenantMessage(item.id)
    await resetAndLoad()
  } catch (error) {
    showError(getErrorMessage(error, '标记失败'))
  }
}

async function onDelete(item) {
  const ok = await confirmDialog(`确认删除消息「${item.title || ''}」？`, {
    title: '删除消息',
    okText: '删除'
  })
  if (!ok) return
  try {
    await deleteTenantMessage(item.id)
    showSuccess('已删除')
    await resetAndLoad()
  } catch (error) {
    showError(getErrorMessage(error, '删除失败'))
  }
}

async function onProcess(item) {
  try {
    await processTenantMessage(item.id)
    showSuccess('已标记为处理完成')
    await resetAndLoad()
  } catch (error) {
    showError(getErrorMessage(error, '操作失败'))
  }
}

async function onReadAll() {
  if (!unreadCount.value) return
  const ok = await confirmDialog('将全部未读消息标记为已读？', { title: '全部已读', okText: '确定' })
  if (!ok) return
  marking.value = true
  try {
    const res = await readAllTenantMessages()
    showSuccess(`已标记 ${res.data?.updated ?? ''} 条为已读`)
    await resetAndLoad()
  } catch (error) {
    showError(getErrorMessage(error, '操作失败'))
  } finally {
    marking.value = false
  }
}

function categoryText(category) {
  return CATEGORY_TEXT[String(category || '').toUpperCase()] || category
}
</script>
