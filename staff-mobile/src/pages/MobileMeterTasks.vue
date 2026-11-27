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

      <!-- 整页拍照识别：一拍多行，确认后批量录入 -->
      <div class="ocr-entry">
        <input
          ref="ocrInput"
          type="file"
          accept="image/*"
          capture="environment"
          style="display: none"
          @change="onPickPage"
        />
        <el-button class="ocr-btn" :loading="ocrBusy" @click="ocrInput?.click()">
          <el-icon v-if="!ocrBusy"><Camera /></el-icon>
          {{ ocrBusy ? '识别中…' : '拍整页抄表本' }}
        </el-button>
        <p v-if="ocrHint" class="ocr-tip">{{ ocrHint }}</p>
      </div>

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
      <!-- 识别结果确认弹层 -->
      <el-drawer
        v-model="ocrDrawer"
        title="确认识别结果"
        direction="btt"
        size="80%"
        :close-on-click-modal="false"
      >
        <p class="ocr-summary">
          共 {{ ocrRows.length }} 行，已匹配
          <strong class="ok">{{ ocrMatchedCount }}</strong>，需处理
          <strong class="warn">{{ ocrRows.length - ocrMatchedCount }}</strong>
        </p>
        <p v-if="ocrSample" class="ocr-tip">当前为示例数据（未配置视觉模型）</p>
        <p v-if="ocrNeedsInputCount > 0" class="ocr-low" style="margin-bottom: 10px">
          有 {{ ocrNeedsInputCount }} 行信息不完整（房间或读数缺失），请补全后再导入
        </p>

        <div v-for="(row, idx) in ocrRows" :key="idx" class="ocr-row">
          <div class="ocr-row-head">
            <span class="ocr-room">
              {{ row.roomId ? (row.buildingName ? row.buildingName + ' / ' : '') + row.roomLabel : '未匹配' }}
            </span>
            <el-tag
              size="small"
              :type="row.roomId ? 'success' : 'danger'"
            >{{ row.roomId ? '可导入' : '需选择房间' }}</el-tag>
          </div>

          <el-select
            v-if="!row.roomId"
            v-model="row.roomId"
            filterable
            placeholder="选择房间"
            size="small"
            style="width: 100%; margin-bottom: 8px"
          >
            <el-option v-for="r in roomOptions" :key="r.id" :label="r.label" :value="r.id" />
          </el-select>

          <div class="ocr-fields">
            <div class="ocr-field">
              <label>水表</label>
              <el-input-number v-model="row.waterReading" :min="0" :step="0.1" size="small" controls-position="right" />
            </div>
            <div class="ocr-field">
              <label>电表</label>
              <el-input-number v-model="row.electricReading" :min="0" :step="0.1" size="small" controls-position="right" />
            </div>
          </div>

          <p v-if="row.confidence != null && row.confidence < 0.85" class="ocr-low">
            识别置信度较低（{{ Math.round(row.confidence * 100) }}%），请核对
          </p>
        </div>

        <template #footer>
          <el-button @click="ocrDrawer = false">取消</el-button>
          <el-button type="primary" :loading="ocrSaving" :disabled="ocrValidRows.length === 0" @click="saveOcrRows">
            导入 {{ ocrValidRows.length }} 条
          </el-button>
        </template>
      </el-drawer>
    </div>
  </PullRefresh>
</template>

<script setup>
import PullRefresh from '../components/PullRefresh.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, CircleCheck, Camera } from '@element-plus/icons-vue'
import { getBuildings, getContracts, getMeterReadings, getRoomOptions, recognizeMeterImage, saveMeterReadings, getErrorMessage } from '@/api'
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


// —— 整页拍照识别 ——
const ocrInput = ref(null)
const ocrBusy = ref(false)
const ocrSaving = ref(false)
const ocrDrawer = ref(false)
const ocrRows = ref([])
const ocrHint = ref('')
const ocrSample = ref(false)
const roomOptions = ref([])

const ocrMatchedCount = computed(() => ocrRows.value.filter(r => r.roomId != null).length)
const ocrValidRows = computed(() => ocrRows.value.filter(rowComplete))

/** 一行是否可导入：房间 + 两个读数齐全（日期缺失时用今天兜底，已在识别时补） */
function rowComplete(r) {
  return r.roomId != null && r.waterReading != null && r.electricReading != null
}

/** 仍需补信息的行数 */
const ocrNeedsInputCount = computed(() => ocrRows.value.filter(r => !rowComplete(r)).length)

async function loadRoomOptions() {
  if (roomOptions.value.length > 0) return
  try {
    // 带楼栋名，避免 4 个「101」无法区分
    const { data } = await getRoomOptions()
    roomOptions.value = (Array.isArray(data) ? data : []).map(r => ({
      id: r.id,
      label: r.label || String(r.id),
      roomNumber: r.roomNumber,
      buildingName: r.buildingName
    }))
  } catch {
    roomOptions.value = []
  }
}

async function onPickPage(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  ocrHint.value = ''
  ocrBusy.value = true
  try {
    await loadRoomOptions()
    const { data } = await recognizeMeterImage(file)
    const rows = (data?.rows || []).map(r => ({
      ...r,
      recordDate: r.recordDate || new Date().toISOString().slice(0, 10)
    }))
    if (rows.length === 0) {
      ocrHint.value = '未识别出任何记录，请重拍或改用逐个录入'
      return
    }
    ocrRows.value = rows
    ocrSample.value = Boolean(data.sample)
    ocrDrawer.value = true
  } catch (error) {
    ocrHint.value = getErrorMessage(error, '识别失败，请重试')
  } finally {
    ocrBusy.value = false
  }
}

async function saveOcrRows() {
  const rows = ocrValidRows.value
  if (rows.length === 0) return
  ocrSaving.value = true
  try {
    await saveMeterReadings(rows.map(r => ({
      roomId: r.roomId,
      recordDate: r.recordDate,
      waterReading: r.waterReading,
      electricReading: r.electricReading
    })))
    ElMessage.success(`已导入 ${rows.length} 条`)
    ocrDrawer.value = false
    ocrRows.value = []
    ocrHint.value = ''
    await refresh?.()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '导入失败'))
  } finally {
    ocrSaving.value = false
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

/* 整页拍照识别 */
.ocr-entry {
  margin: 8px 12px 0;
}

.ocr-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
}

.ocr-tip {
  margin: 8px 2px 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-muted, #6c7a96);
}

.ocr-summary {
  margin: 0 0 10px;
  font-size: 13px;
}

.ocr-summary .ok {
  color: var(--success-color, #2ea043);
}

.ocr-summary .warn {
  color: var(--danger-color, #e5484d);
}

.ocr-row {
  border: 1px solid var(--border-color, #e4edff);
  border-radius: 10px;
  padding: 10px;
  margin-bottom: 10px;
}

.ocr-row-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ocr-room {
  font-weight: 600;
  font-size: 14px;
}

.ocr-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.ocr-field label {
  display: block;
  font-size: 12px;
  color: var(--text-muted, #6c7a96);
  margin-bottom: 4px;
}

.ocr-low {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--danger-color, #e5484d);
}
</style>
