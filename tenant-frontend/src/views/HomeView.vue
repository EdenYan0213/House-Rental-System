<template>
  <div class="page">
    <LoadingBlock v-if="loading" />

    <template v-else>
      <section class="panel">
        <div class="row-between">
          <div>
            <div class="muted">您好，{{ home.tenantName || tenantInfo?.name || '-' }}</div>
            <h3 style="margin:6px 0 0">{{ roomText }}</h3>
          </div>
          <button v-if="canSwitchRoom" class="btn btn-light" style="width:94px" @click="$router.push('/me')">
            切换房间
          </button>
        </div>
        <p v-if="isProspect" class="muted" style="margin:10px 0 0">
          您还未入住，可先浏览
          <span class="link" @click="$router.push('/available-rooms')">可租房源</span>
        </p>
      </section>

      <!-- 待支付：未入住用户不展示 -->
      <section v-if="!isProspect" class="panel" style="margin-top:10px">
        <div class="card-title">待支付账单</div>
        <template v-if="unpaidCount > 0">
          <p>
            您有 <strong>{{ unpaidCount }}</strong> 笔账单待支付，共计
            <strong style="color: var(--danger)">￥{{ money(home.unpaidAmount) }}</strong>
          </p>
          <button class="btn btn-primary" style="width:100%" @click="goPending">去支付</button>
        </template>
        <EmptyState v-else text="暂无待支付账单，感谢您的按时缴费" />
      </section>

      <section class="panel" style="margin-top:10px">
        <div class="card-title">快捷入口</div>
        <div class="grid-two">
          <button class="btn btn-primary" @click="$router.push('/bills')">我的账单</button>
          <button class="btn btn-light" @click="$router.push('/repairs')">在线报修</button>
          <button class="btn btn-light" @click="$router.push('/messages')">消息中心</button>
          <button class="btn btn-light" @click="$router.push('/contract')">我的合同</button>
        </div>
      </section>

      <section class="panel" style="margin-top:10px">
        <div class="row-between">
          <div class="card-title" style="margin:0">最新公告</div>
          <span class="link" @click="$router.push('/notices')">查看更多</span>
        </div>
        <template v-if="home.latestNotice && home.latestNotice.title">
          <h4 style="margin:8px 0" @click="openNotice(home.latestNotice.id)">
            {{ home.latestNotice.title }}
          </h4>
          <p class="muted" style="margin:0">{{ formatTime(home.latestNotice.createdAt) }}</p>
        </template>
        <EmptyState v-else text="暂无公告" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  getTenantHome,
  getTenantInfo,
  getCurrentRoomCache,
  getTenantCurrent,
  setCurrentRoomCache,
  getErrorMessage
} from '../api'
import { formatTime, money } from '../utils/format'
import { showError } from '../utils/toast'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const router = useRouter()
const tenantInfo = ref(getTenantInfo())
const roomCache = ref(getCurrentRoomCache())
const home = ref({})
const loading = ref(true)

const isProspect = computed(() => Boolean(home.value?.isProspect))
const unpaidCount = computed(() => Number(home.value?.unpaidCount || 0))
// 待租用户没有房间可切，隐藏入口
const canSwitchRoom = computed(() => !isProspect.value)

const roomText = computed(() => {
  if (isProspect.value) return '尚未入住'
  const currentRoom = home.value?.currentRoom || roomCache.value || {}
  return currentRoom.roomText
    || `${currentRoom.buildingName || ''}${currentRoom.roomNumber || ''}`
    || '-'
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    // 首页两个请求独立：任一个失败不应让整页空白
    const [current, homeRes] = await Promise.allSettled([getTenantCurrent(), getTenantHome()])

    if (current.status === 'fulfilled') {
      const data = current.value.data || {}
      tenantInfo.value = data.tenant || tenantInfo.value
      roomCache.value = data.currentRoom || roomCache.value
      setCurrentRoomCache(roomCache.value)
    }

    if (homeRes.status === 'fulfilled') {
      home.value = homeRes.value.data || {}
    } else {
      showError(getErrorMessage(homeRes.reason, '首页数据加载失败'))
    }
  } finally {
    loading.value = false
  }
}

function goPending() {
  router.push({ path: '/bills', query: { status: 'PENDING' } })
}

function openNotice(id) {
  if (id) router.push(`/notices/${id}`)
}
</script>
