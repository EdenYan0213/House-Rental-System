<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">在线报修</div>
        <button class="btn btn-primary" style="width:96px" @click="$router.push('/repairs/create')">
          发起报修
        </button>
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
        <div v-for="item in rows" :key="item.id" class="list-item" @click="openDetail(item.id)">
          <div class="row-between">
            <strong>{{ item.orderNo || '-' }}</strong>
            <span :class="repairStatusClass(item.status)">{{ repairStatusText(item.status) }}</span>
          </div>
          <p style="margin:8px 0 4px">故障类型：{{ item.faultType || '-' }}</p>
          <p class="muted" style="margin:0">
            {{ item.roomNumber || '-' }} · {{ formatTime(item.createdAt) }}
          </p>
          <p v-if="needsAccept(item)" style="margin:8px 0 0;color: var(--danger);font-size:13px">
            维修已完成，请确认并评价
          </p>
        </div>

        <div class="panel" v-if="!rows.length">
          <EmptyState
            :text="status ? '当前筛选下没有报修记录' : '暂无报修记录'"
            action-text="发起报修"
            @action="$router.push('/repairs/create')"
          />
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
import { getRepairList } from '../api'
import { formatTime, repairStatusClass, repairStatusText } from '../utils/format'
import { usePagedList } from '../composables/usePagedList'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const router = useRouter()
const status = ref('all')

const { rows, loading, hasMore, loadMore, resetAndLoad } = usePagedList(
  (page, size) => {
    const params = { page, size }
    if (status.value && status.value !== 'all') params.status = status.value
    return getRepairList(params)
  },
  { errorText: '报修记录加载失败' }
)

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '处理中', value: 'processing' },
  { label: '已完成', value: 'completed' }
]

onMounted(resetAndLoad)

function switchStatus(next) {
  if (status.value === next) return
  status.value = next
  resetAndLoad()
}

function openDetail(id) {
  router.push(`/repairs/${id}`)
}

/** 待租客确认的工单，列表上直接提示，避免点进去才知道要操作 */
function needsAccept(item) {
  return String(item.status || '').toLowerCase() === 'pending_accept'
}
</script>
