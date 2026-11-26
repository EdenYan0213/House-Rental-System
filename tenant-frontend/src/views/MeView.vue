<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">个人中心</div>
      <p style="margin:6px 0">姓名：{{ tenant.name || '-' }}</p>
      <p style="margin:6px 0">手机号：{{ tenant.phone || '-' }}</p>
      <p style="margin:6px 0">身份证：{{ tenant.idCard || '-' }}</p>
      <p style="margin:6px 0">当前房间：{{ roomText }}</p>
      <p v-if="isProspect" class="muted" style="margin:8px 0 0">您尚未入住，可先浏览可租房源</p>
    </section>

    <!-- 未入住用户没有房间可切 -->
    <section class="panel" style="margin-bottom:10px" v-if="rooms.length > 1">
      <div class="card-title">切换房间</div>
      <select class="select" v-model.number="selectedRoomId">
        <option v-for="item in rooms" :key="item.roomId" :value="item.roomId">
          {{ item.buildingName || '' }}{{ item.roomNumber || '' }}
        </option>
      </select>
      <button
        class="btn btn-primary"
        style="width:100%;margin-top:10px"
        :disabled="switching || selectedRoomId === currentRoomId"
        @click="onSwitchRoom"
      >
        {{ switching ? '切换中...' : '确认切换' }}
      </button>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">常用功能</div>
      <div class="grid-two">
        <button class="btn btn-light" @click="$router.push('/contract')">我的合同</button>
        <button class="btn btn-light" @click="$router.push('/bills')">我的账单</button>
        <button class="btn btn-light" @click="$router.push('/deposit')">我的押金</button>
        <button class="btn btn-light" @click="$router.push('/notices')">公告通知</button>
        <button class="btn btn-light" @click="$router.push('/available-rooms')">可租房源</button>
      </div>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">账号与设置</div>
      <div class="grid-two">
        <button class="btn btn-light" @click="$router.push('/messages')">
          消息中心<template v-if="unreadCount > 0">（{{ unreadCount }}）</template>
        </button>
        <button class="btn btn-light" @click="$router.push('/dashboard')">租客看板</button>
        <button class="btn btn-light" @click="$router.push('/preferences')">语言与提醒</button>
        <button class="btn btn-light" @click="$router.push('/update-phone')">修改手机号</button>
        <button class="btn btn-light" @click="$router.push('/feedback')">意见反馈</button>
        <button class="btn btn-light" @click="$router.push('/contract')">电子签署</button>
      </div>
    </section>

    <button class="btn btn-danger" style="width:100%" @click="logout">退出登录</button>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  clearTenantSession,
  getCurrentRoomCache,
  getErrorMessage,
  getTenantCurrent,
  getTenantMessages,
  setCurrentRoomCache,
  setTenantInfo,
  setTenantToken,
  switchTenantRoom
} from '../api'
import { confirmDialog, showError, showSuccess } from '../utils/toast'

const router = useRouter()
const tenant = ref({})
const rooms = ref([])
const currentRoom = ref(getCurrentRoomCache() || {})
const currentRoomId = ref(null)
const selectedRoomId = ref(null)
const unreadCount = ref(0)
const switching = ref(false)
const isProspect = ref(false)

const roomText = computed(() => {
  if (isProspect.value) return '尚未入住'
  return currentRoom.value?.roomText
    || `${currentRoom.value?.buildingName || ''}${currentRoom.value?.roomNumber || ''}`
    || '-'
})

onMounted(loadCurrent)

async function loadCurrent() {
  try {
    const res = await getTenantCurrent()
    const data = res.data || {}
    tenant.value = data.tenant || {}
    rooms.value = data.rooms || []
    currentRoom.value = data.currentRoom || {}
    currentRoomId.value = Number(data.currentRoomId || data.currentRoom?.roomId || 0) || null
    selectedRoomId.value = currentRoomId.value || rooms.value[0]?.roomId || null
    isProspect.value = Boolean(data.isProspect)

    setTenantInfo(tenant.value)
    setCurrentRoomCache(currentRoom.value)
  } catch (error) {
    showError(getErrorMessage(error, '个人信息加载失败'))
  }

  // 未读数独立加载，失败不阻塞页面
  try {
    const res = await getTenantMessages({ page: 0, size: 1 })
    unreadCount.value = Number(res.data?.unreadCount || 0)
  } catch (_) {
    unreadCount.value = 0
  }
}

async function onSwitchRoom() {
  if (!selectedRoomId.value) return

  const target = rooms.value.find(item => item.roomId === selectedRoomId.value)
  const label = target ? `${target.buildingName || ''}${target.roomNumber || ''}` : '该房间'
  const ok = await confirmDialog(`确认切换到「${label}」？`, { title: '切换房间', okText: '切换' })
  if (!ok) return

  switching.value = true
  try {
    const res = await switchTenantRoom(selectedRoomId.value)
    const data = res.data || {}
    // 切房后后端下发新 token（含新的 roomId 与权限），必须替换
    if (data.token) {
      setTenantToken(data.token)
    }
    currentRoom.value = data.currentRoom || currentRoom.value
    currentRoomId.value = Number(data.currentRoomId || selectedRoomId.value) || null
    setCurrentRoomCache(currentRoom.value)
    showSuccess('房间切换成功')
    await loadCurrent()
  } catch (error) {
    showError(getErrorMessage(error, '切换失败，请重试'))
  } finally {
    switching.value = false
  }
}

async function logout() {
  const ok = await confirmDialog('确认退出登录？', { title: '退出登录', okText: '退出' })
  if (!ok) return
  clearTenantSession()
  router.replace('/login')
}
</script>
