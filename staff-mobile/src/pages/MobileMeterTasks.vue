<template>
  <PullRefresh :on-refresh="refresh">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <h1 class="m-head-title">抄表任务</h1>
        <p class="m-head-sub">
          待抄 <span class="m-num count">{{ filteredRows.length }}</span> 间
          <span v-if="pendingUploadCount > 0" class="pending-hint">
            · {{ pendingUploadCount }} 条待上传
          </span>
        </p>
      </header>

      <!-- 筛选：搜索 + 楼栋 -->
      <div class="filter-row">
        <el-input
          v-model="query"
          clearable
          placeholder="搜索房间号 / 租户"
          :prefix-icon="Search"
          class="filter-search"
          @keyup.enter="applyFilter"
          @clear="applyFilter"
        />
        <el-select
          v-model="buildingId"
          clearable
          placeholder="楼栋"
          class="filter-select"
          @change="applyFilter"
        >
          <el-option v-for="item in buildings" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </div>

      <!-- 任务卡列表 -->
      <div v-if="filteredRows.length > 0" class="task-list">
        <article
          v-for="row in filteredRows"
          :key="row.roomId"
          class="m-card task-card"
        >
          <div class="task-top">
            <div class="task-room">
              <span class="room-no m-num">{{ row.roomNumber || '-' }}</span>
              <span class="room-building">{{ row.buildingName || '' }}</span>
            </div>
            <el-tag v-if="row.pendingUpload" type="warning" size="small" effect="light">
              待上传
            </el-tag>
          </div>

          <div class="task-meta">
            <span class="meta-tenant">{{ row.tenantName || '空置房间' }}</span>
            <span class="meta-sep">·</span>
            <span>上次 {{ row.lastRecordDate || '从未抄表' }}</span>
          </div>

          <div class="task-readings">
            <div class="reading">
              <span class="reading-label">水</span>
              <span class="reading-value m-num">{{ formatNum(row.lastWater) }}</span>
            </div>
            <div class="reading">
              <span class="reading-label">电</span>
              <span class="reading-value m-num">{{ formatNum(row.lastElectric) }}</span>
            </div>
            <el-button type="primary" size="small" class="reading-action" @click="goInput(row.roomId)">
              录入
            </el-button>
          </div>
        </article>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><CircleCheck /></el-icon>
        <p class="empty-title">今日抄表已完成</p>
        <p class="empty-desc">没有待抄的房间，下拉可刷新。</p>
      </div>
    </div>
  </PullRefresh>
</template>

<script setup>
import PullRefresh from '../components/PullRefresh.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, CircleCheck } from '@element-plus/icons-vue'
import { getBuildings, getContracts, getMeterReadings } from '@/api'
import { getOfflineMeterQueue, syncOfflineMeterReadings } from '@/utils/mobileOffline'
import { unwrapList } from '@/utils/mobileApi'

const router = useRouter()
const loading = ref(false)
const query = ref('')
const buildingId = ref(null)
const buildings = ref([])
const rows = ref([])

const pendingUploadCount = computed(() =>
  rows.value.filter(item => item.pendingUpload).length
)

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
  router.push({
    path: `/mobile/meter/${roomId}`,
    query: { from: 'meter-tasks' }
  })
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
/* 筛选行：搜索占主要宽度，楼栋下拉定宽 */
.filter-row {
  display: flex;
  gap: 10px;
}

.filter-search {
  flex: 1;
  min-width: 0;
}

.filter-select {
  width: 108px;
  flex: 0 0 auto;
}

.filter-row :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.filter-row :deep(.el-select__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.pending-hint {
  color: var(--warning-color, #b3761a);
  font-size: 12px;
}

/* ---------- 任务卡 ---------- */
.task-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.task-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.task-room {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.room-no {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.room-building {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-muted);
  flex-wrap: wrap;
}

.meta-tenant {
  color: var(--text-main);
  font-weight: 500;
}

.meta-sep {
  opacity: 0.4;
}

/* 读数 + 录入按钮同一行 */
.task-readings {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
}

.reading {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.reading-label {
  font-size: 11px;
  color: var(--text-muted);
}

.reading-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.reading-action {
  margin-left: auto;
  min-height: 34px;
  border-radius: 8px;
}

/* ---------- 空态 ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  color: var(--accent-3);
  opacity: 0.5;
}

.empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
