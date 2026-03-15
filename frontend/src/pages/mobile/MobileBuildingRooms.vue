<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <el-button text @click="goBack">返回</el-button>
      <div class="title">房间信息</div>
      <el-button text @click="load">刷新</el-button>
    </div>

    <div class="filters">
      <el-select v-model="statusFilter" clearable placeholder="状态筛选（全部/空闲/已租）">
        <el-option label="全部状态" value="" />
        <el-option label="空闲" value="空闲" />
        <el-option label="已租" value="已租" />
      </el-select>
      <el-select v-model="floorFilter" clearable placeholder="楼层筛选">
        <el-option label="全部楼层" value="" />
        <el-option v-for="floor in floorOptions" :key="floor" :label="`${floor}层`" :value="floor" />
      </el-select>
    </div>

    <el-input v-model="keyword" clearable placeholder="搜索房间号" />

    <el-empty v-if="!loading && filteredRooms.length === 0" description="暂无房间" />

    <el-card v-for="room in filteredRooms" :key="room.id" class="panel" shadow="never" @click="openRoom(room)">
      <div class="row top">
        <div class="name">{{ room.roomNumber || '-' }}</div>
        <el-tag size="small" :type="statusTag(room.status)">{{ roomStatusText(room.status) }}</el-tag>
      </div>
      <div class="muted">楼层：{{ resolveFloor(room) || '-' }} · 户型：{{ room.layout || '-' }}</div>
      <div class="muted">面积：{{ room.area || '-' }}m² · 租金：￥{{ Number(room.rent || 0).toFixed(2) }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRooms } from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const rooms = ref([])
const statusFilter = ref('')
const floorFilter = ref('')

const buildingId = computed(() => Number(route.params.id))

const floorOptions = computed(() => {
  const values = rooms.value
    .map(item => Number(resolveFloor(item)))
    .filter(v => Number.isFinite(v))
  return Array.from(new Set(values)).sort((a, b) => a - b)
})

const filteredRooms = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return rooms.value.filter(item => {
    if (statusFilter.value && normalizeRoomStatus(item.status) !== String(statusFilter.value)) {
      return false
    }

    if (String(floorFilter.value || '') !== '' && Number(resolveFloor(item)) !== Number(floorFilter.value)) {
      return false
    }

    if (!q) return true
    return String(item.roomNumber || '').toLowerCase().includes(q)
  })
})

function statusTag(status) {
  const normalized = normalizeRoomStatus(status)
  if (normalized === '已租') return 'success'
  if (normalized === '空闲') return 'info'
  return 'warning'
}

function roomStatusText(status) {
  const normalized = normalizeRoomStatus(status)
  return normalized || status || '-'
}

function normalizeRoomStatus(status) {
  const raw = String(status || '').trim().toUpperCase()
  if (!raw) return ''
  if (['空闲', 'VACANT', 'AVAILABLE', 'EMPTY', 'IDLE', '0'].includes(raw)) return '空闲'
  if (['已租', 'RENTED', 'OCCUPIED', 'IN_USE', '1'].includes(raw)) return '已租'
  return String(status || '').trim()
}

function resolveFloor(room) {
  if (room?.floor !== undefined && room?.floor !== null && String(room.floor).trim() !== '') {
    return Number(room.floor)
  }
  const roomNumber = String(room?.roomNumber || '')
  const match = roomNumber.match(/^(\d{1,2})\d{2}$/)
  if (match) return Number(match[1])
  return null
}

function goBack() {
  router.push('/mobile/buildings')
}

function openRoom(room) {
  if (!room?.id) return
  router.push(`/mobile/room/${room.id}`)
}

async function load() {
  loading.value = true
  try {
    const res = await getRooms({ buildingId: buildingId.value })
    rooms.value = unwrapList(res.data)
  } catch (_) {
    ElMessage.error('加载房间失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mobile-page {
  padding: 14px;
  max-width: 760px;
  margin: 0 auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-size: 18px;
  font-weight: 700;
  color: #1d3266;
}

.filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}

.panel {
  margin-top: 10px;
  border: 1px solid #e8efff;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.top {
  margin-bottom: 6px;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.muted {
  color: #7584a3;
  font-size: 13px;
  margin-bottom: 4px;
}
</style>
