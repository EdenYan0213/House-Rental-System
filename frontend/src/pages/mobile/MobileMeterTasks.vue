<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <div class="title">抄表任务</div>
      <el-button text @click="refresh">刷新</el-button>
    </div>

    <el-card shadow="never" class="panel">
      <el-input v-model="query" clearable placeholder="搜索房间号/租户" @keyup.enter="applyFilter" />
      <el-select v-model="buildingId" clearable placeholder="筛选楼栋" style="margin-top: 8px; width: 100%" @change="applyFilter">
        <el-option v-for="item in buildings" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
    </el-card>

    <div class="meta">今日待抄 {{ filteredRows.length }} 间</div>

    <el-card v-for="row in filteredRows" :key="row.roomId" class="panel" shadow="never">
      <div class="line top">
        <div>{{ row.buildingName || '-' }} {{ row.roomNumber || '-' }}</div>
        <div class="tags">
          <el-tag v-if="row.pendingUpload" type="warning" size="small">待上传</el-tag>
          <el-tag type="info" size="small">{{ row.tenantName || '无租户' }}</el-tag>
        </div>
      </div>
      <div class="line muted">合同号：{{ row.contractNo || '-' }}</div>
      <div class="line muted">上次抄表：{{ row.lastRecordDate || '暂无' }}</div>
      <div class="line reading">
        <span>水 {{ formatNum(row.lastWater) }}</span>
        <span>电 {{ formatNum(row.lastElectric) }}</span>
      </div>
      <el-button type="primary" style="width: 100%" @click="goInput(row.roomId)">录入今日读数</el-button>
    </el-card>

    <el-empty v-if="!loading && filteredRows.length === 0" description="暂无待抄房间" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBuildings, getContracts, getMeterReadings } from '@/api'
import { getOfflineMeterQueue, syncOfflineMeterReadings } from '@/utils/mobileOffline'
import { unwrapList } from '@/utils/mobileApi'

const router = useRouter()
const loading = ref(false)
const query = ref('')
const buildingId = ref(null)
const buildings = ref([])
const rows = ref([])

const filteredRows = computed(() => {
  const q = query.value.trim().toLowerCase()
  return rows.value.filter(item => {
    if (buildingId.value && Number(item.buildingId) !== Number(buildingId.value)) return false
    if (!q) return true
    return [item.roomNumber, item.tenantName, item.contractNo].some(text => String(text || '').toLowerCase().includes(q))
  })
})

function formatNum(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(2)
}

function goInput(roomId) {
  router.push(`/mobile/meter/${roomId}`)
}

function applyFilter() {
  // computed filter only
}

function refresh() {
  load()
}

async function load() {
  loading.value = true
  try {
    const syncResult = await syncOfflineMeterReadings()
    if (syncResult.uploaded > 0) {
      ElMessage.success(`已自动补传${syncResult.uploaded}条离线抄表记录`)
    }

    const [buildingRes, contractRes] = await Promise.all([
      getBuildings(),
      getContracts({ status: 'ACTIVE', page: 0, size: 500 })
    ])

    buildings.value = unwrapList(buildingRes.data)
    const contracts = unwrapList(contractRes.data)
    const today = new Date().toISOString().slice(0, 10)
    const lastDate = new Date(Date.now() - 24 * 3600 * 1000).toISOString().slice(0, 10)

    const meterCache = new Map()
    const pendingQueue = getOfflineMeterQueue()
    const pendingKeySet = new Set(pendingQueue.map(item => `${item.roomId}_${item.recordDate}`))
    const mapped = []
    for (const contract of contracts) {
      if (!contract.roomId) continue

      let roomReadings = meterCache.get(contract.roomId)
      if (!roomReadings) {
        const res = await getMeterReadings({ roomId: contract.roomId })
        roomReadings = unwrapList(res.data)
        meterCache.set(contract.roomId, roomReadings)
      }

      const todayExists = roomReadings.some(item => item.recordDate === today)
      if (todayExists) continue

      const lastRecord = roomReadings.find(item => item.recordDate <= lastDate) || roomReadings[0]
      mapped.push({
        roomId: contract.roomId,
        roomNumber: contract.roomNumber,
        tenantName: contract.tenantName,
        contractNo: contract.contractNo,
        buildingName: contract.buildingName,
        buildingId: buildings.value.find(b => b.name === contract.buildingName)?.id,
        lastRecordDate: lastRecord?.recordDate,
        lastWater: lastRecord?.waterReading,
        lastElectric: lastRecord?.electricReading,
        pendingUpload: pendingKeySet.has(`${contract.roomId}_${today}`)
      })
    }

    rows.value = mapped
  } catch (_) {
    ElMessage.error('加载抄表任务失败')
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
  margin-bottom: 8px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: #1d3266;
}

.panel {
  margin-bottom: 10px;
  border: 1px solid #e8efff;
}

.meta {
  color: #6d7b97;
  font-size: 13px;
  margin: 8px 2px;
}

.line {
  margin-bottom: 8px;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.muted {
  color: #7584a3;
  font-size: 13px;
}

.reading {
  display: flex;
  gap: 18px;
  color: #3258a5;
  font-weight: 600;
}
</style>
