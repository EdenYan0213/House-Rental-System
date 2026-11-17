<template>
  <div style="padding: 20px">
    <el-page-header @back="goBack" :content="pageTitle" />

    <el-row style="margin-top: 16px">
      <el-card>
        <el-descriptions :column="2" border v-if="roomData">
          <el-descriptions-item label="房间号">{{ roomData.roomNumber }}</el-descriptions-item>
          <el-descriptions-item label="楼栋">{{ roomData.buildingName }}</el-descriptions-item>
          <el-descriptions-item label="户型">{{ roomData.layout }}</el-descriptions-item>
          <el-descriptions-item label="面积">{{ roomData.area }}m²</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-row>

    <el-row style="margin-top: 16px" :gutter="10" align="middle">
      <el-date-picker
        v-model="searchDateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        format="YYYY-MM-DD"
        @change="handleSearch"
        style="width: 280px"
      />
      <el-button icon="Search" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
      <el-button type="success" plain @click="exportRoomCsv">导出CSV</el-button>
    </el-row>

    <el-row style="margin-top: 16px" justify="space-between" align="middle">
      <h3>水电记录 ({{ meterReadings.length }}条)</h3>
      <el-button type="primary" @click="showAddDialog = true">录入水电</el-button>
    </el-row>

    <el-table :data="meterReadings" style="width: 100%; margin-top: 16px" v-loading="loading">
      <el-table-column prop="recordDate" label="录入日期" width="120" sortable />
      <el-table-column prop="waterReading" label="水表读数(m³)" width="120" />
      <el-table-column prop="electricReading" label="电表读数(度)" width="120" />
      <el-table-column label="用水量(m³)" width="120">
        <template #default="{ row, $index }">
          <span v-if="$index < meterReadings.length - 1" :style="{ color: (row.waterReading - meterReadings[$index + 1].waterReading) < 0 ? 'var(--danger-color)' : 'var(--success-color)' }">
            {{ (row.waterReading - meterReadings[$index + 1].waterReading).toFixed(2) }}
          </span>
          <span v-else style="color: var(--text-muted)">-</span>
        </template>
      </el-table-column>
      <el-table-column label="用电量(度)" width="120">
        <template #default="{ row, $index }">
          <span v-if="$index < meterReadings.length - 1" :style="{ color: (row.electricReading - meterReadings[$index + 1].electricReading) < 0 ? 'var(--danger-color)' : 'var(--success-color)' }">
            {{ (row.electricReading - meterReadings[$index + 1].electricReading).toFixed(2) }}
          </span>
          <span v-else style="color: var(--text-muted)">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="操作时间" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button size="small" type="danger" link @click="deleteRecord(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && meterReadings.length === 0" description="暂无水电记录" />

    <el-dialog title="录入水电" v-model="showAddDialog" width="400px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="录入日期" required>
          <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="水表读数" required>
          <el-input-number v-model="form.waterReading" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="电表读数" required>
          <el-input-number v-model="form.electricReading" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  getRoom,
  getMeterReadings,
  saveMeterReadings,
  getBuilding,
  deleteMeterReading,
  exportMeterReadingsCsv,
  getErrorMessage
} from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()

const roomId = Number(route.params.roomId)
const buildingId = Number(route.params.buildingId)

const roomData = ref(null)
const allReadings = ref([])
const meterReadings = ref([])
const loading = ref(false)
const showAddDialog = ref(false)
const saving = ref(false)
const searchDateRange = ref([])

const form = ref({
  recordDate: new Date().toISOString().split('T')[0],
  waterReading: 0,
  electricReading: 0
})

const pageTitle = computed(() => roomData.value ? `房间水电 - ${roomData.value.buildingName} ${roomData.value.roomNumber}` : '水电管理')

function goBack() {
  router.push(`/buildings/${buildingId}/rooms`)
}

async function loadRoom() {
  try {
    const res = await getRoom(roomId)
    roomData.value = res.data
    if (roomData.value.buildingId) {
      const b = await getBuilding(roomData.value.buildingId)
      roomData.value.buildingName = b.data.name
    }
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载房间信息失败'))
  }
}

async function loadMeterReadings() {
  loading.value = true
  try {
    const res = await getMeterReadings({ roomId: roomId })
    allReadings.value = (res.data || []).sort((a, b) => new Date(b.recordDate) - new Date(a.recordDate))
    filterReadings()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载水电记录失败'))
  } finally {
    loading.value = false
  }
}

function filterReadings() {
  if (searchDateRange.value && searchDateRange.value.length === 2) {
    const [start, end] = searchDateRange.value
    meterReadings.value = allReadings.value.filter(r => {
      return r.recordDate >= start && r.recordDate <= end
    })
  } else {
    meterReadings.value = [...allReadings.value]
  }
}

function handleSearch() {
  filterReadings()
}

function resetSearch() {
  searchDateRange.value = []
  meterReadings.value = [...allReadings.value]
}

async function submit() {
  if (!form.value.recordDate) {
    return ElMessage.warning('请选择录入日期')
  }
  saving.value = true
  try {
    await saveMeterReadings([{
      roomId: roomId,
      recordDate: form.value.recordDate,
      waterReading: form.value.waterReading || 0,
      electricReading: form.value.electricReading || 0
    }])
    ElMessage.success('保存成功')
    showAddDialog.value = false
    form.value = {
      recordDate: new Date().toISOString().split('T')[0],
      waterReading: 0,
      electricReading: 0
    }
    await loadMeterReadings()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function deleteRecord(row) {
  try {
    await ElMessageBox.confirm('确定删除该水电记录吗？', '提示', { type: 'warning' })
    await deleteMeterReading(row.id)
    ElMessage.success('删除成功')
    await loadMeterReadings()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(getErrorMessage(e, '删除失败'))
  }
}

async function exportRoomCsv() {
  try {
    const params = {
      roomId,
      buildingId
    }
    if (searchDateRange.value && searchDateRange.value.length === 2) {
      params.startDate = searchDateRange.value[0]
      params.endDate = searchDateRange.value[1]
    }

    const res = await exportMeterReadingsCsv(params)
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `meter-room-${roomId}-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('房间水电CSV导出成功')
  } catch (e) {
    ElMessage.error('房间水电CSV导出失败')
  }
}

onMounted(async () => {
  await loadRoom()
  await loadMeterReadings()
})
</script>
