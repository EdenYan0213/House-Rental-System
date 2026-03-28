<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">个人中心</div>
      <p>姓名：{{ tenant.name || '-' }}</p>
      <p>手机号：{{ tenant.phone || '-' }}</p>
      <p>身份证：{{ tenant.idCard || '-' }}</p>
      <p>当前房间：{{ roomText }}</p>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">切换房间</div>
      <select class="select" v-model.number="selectedRoomId">
        <option v-for="item in rooms" :key="item.roomId" :value="item.roomId">
          {{ item.buildingName || '' }}{{ item.roomNumber || '' }}
        </option>
      </select>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" @click="onSwitchRoom">确认切换</button>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">常用功能</div>
      <div class="grid-two">
        <button class="btn btn-light" @click="$router.push('/contract')">我的合同</button>
        <button class="btn btn-light" @click="$router.push('/update-phone')">修改手机号</button>
        <button class="btn btn-light" @click="$router.push('/feedback')">意见反馈</button>
        <button class="btn btn-light" @click="$router.push('/notices')">公告通知</button>
      </div>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('me_phase3') }}</div>
      <div class="grid-two">
        <button class="btn btn-light" @click="$router.push('/messages')">{{ t('me_messages') }}</button>
        <button class="btn btn-light" @click="$router.push('/dashboard')">{{ t('me_dashboard') }}</button>
        <button class="btn btn-light" @click="$router.push('/preferences')">{{ t('me_language') }}</button>
        <button class="btn btn-light" @click="$router.push('/contract')">{{ t('me_sign') }}</button>
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
  getTenantCurrent,
  setCurrentRoomCache,
  setTenantInfo,
  setTenantToken,
  switchTenantRoom
} from '../api'
import { t } from '../utils/i18n'

const router = useRouter()
const tenant = ref({})
const rooms = ref([])
const currentRoom = ref(getCurrentRoomCache() || {})
const selectedRoomId = ref(null)

const roomText = computed(() => {
  return currentRoom.value?.roomText || `${currentRoom.value?.buildingName || ''}${currentRoom.value?.roomNumber || ''}` || '-'
})

onMounted(loadCurrent)

async function loadCurrent() {
  const res = await getTenantCurrent()
  const data = res.data || {}
  tenant.value = data.tenant || {}
  rooms.value = data.rooms || []
  currentRoom.value = data.currentRoom || {}
  selectedRoomId.value = Number(data.currentRoomId || currentRoom.value?.roomId || rooms.value[0]?.roomId || 0)
  setTenantInfo(tenant.value)
  setCurrentRoomCache(currentRoom.value)
}

async function onSwitchRoom() {
  if (!selectedRoomId.value) {
    return
  }
  const res = await switchTenantRoom(selectedRoomId.value)
  const data = res.data || {}
  if (data.token) {
    setTenantToken(data.token)
  }
  currentRoom.value = data.currentRoom || currentRoom.value
  setCurrentRoomCache(currentRoom.value)
  alert('房间切换成功')
}

function logout() {
  clearTenantSession()
  router.replace('/login')
}
</script>
