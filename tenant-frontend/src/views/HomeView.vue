<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div>
          <div class="muted">您好，{{ home.tenantName || tenantInfo?.name || '-' }}</div>
          <h3 style="margin:6px 0 0">{{ roomText }}</h3>
        </div>
        <button class="btn btn-light" style="width:94px" @click="$router.push('/me')">切换房间</button>
      </div>
    </section>

    <section class="panel" style="margin-top:10px">
      <div class="card-title">待支付账单</div>
      <template v-if="Number(home.unpaidCount || 0) > 0">
        <p>您有 {{ home.unpaidCount }} 笔账单待支付，共计 <strong>￥{{ Number(home.unpaidAmount || 0).toFixed(2) }}</strong></p>
        <button class="btn btn-primary" style="width:100%" @click="goPending">去支付</button>
      </template>
      <p v-else class="muted">暂无待支付账单</p>
    </section>

    <section class="panel" style="margin-top:10px">
      <div class="card-title">快捷入口</div>
      <div class="grid-two">
        <button class="btn btn-primary" @click="$router.push('/bills')">我的账单</button>
        <button class="btn btn-light" @click="$router.push('/repairs')">在线报修</button>
        <button class="btn btn-light" @click="$router.push('/messages')">消息中心</button>
        <button class="btn btn-light" @click="$router.push('/dashboard')">租客看板</button>
      </div>
    </section>

    <section class="panel" style="margin-top:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">最新公告</div>
        <span class="link" @click="$router.push('/notices')">查看更多</span>
      </div>
      <template v-if="home.latestNotice && home.latestNotice.title">
        <h4 style="margin:8px 0">{{ home.latestNotice.title }}</h4>
        <p class="muted">{{ formatTime(home.latestNotice.createdAt) }}</p>
      </template>
      <p v-else class="muted">暂无公告</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getTenantHome, getTenantInfo, getCurrentRoomCache, getTenantCurrent, setCurrentRoomCache } from '../api'

const router = useRouter()
const tenantInfo = ref(getTenantInfo())
const roomCache = ref(getCurrentRoomCache())
const home = ref({})

const roomText = computed(() => {
  const currentRoom = home.value?.currentRoom || roomCache.value || {}
  return currentRoom.roomText || `${currentRoom.buildingName || ''}${currentRoom.roomNumber || ''}` || '-'
})

onMounted(async () => {
  await Promise.all([loadCurrent(), loadHome()])
})

async function loadCurrent() {
  try {
    const res = await getTenantCurrent()
    const data = res.data || {}
    tenantInfo.value = data.tenant || tenantInfo.value
    roomCache.value = data.currentRoom || roomCache.value
    setCurrentRoomCache(roomCache.value)
  } catch (_) {
    // ignore
  }
}

async function loadHome() {
  const res = await getTenantHome()
  home.value = res.data || {}
}

function goPending() {
  router.push({ path: '/bills', query: { status: 'PENDING' } })
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
