<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>水电录入</h2>
      <div>
        <el-button v-if="canInput" type="primary" plain icon="Upload" @click="openImportDialog" style="margin-right: 10px">
          Excel导入
        </el-button>
        <el-button type="primary" @click="saveAll" :loading="saving">保存全部</el-button>
      </div>
    </el-row>

    <el-row style="margin-top: 16px" :gutter="10">
      <el-select v-model="buildingFilter" placeholder="选择楼栋" clearable @change="loadRooms">
        <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-select v-model="floorFilter" placeholder="选择楼层" clearable @change="loadRooms">
        <el-option v-for="f in floors" :key="f" :label="f + '层'" :value="f" />
      </el-select>
      <el-input v-model="roomSearch" placeholder="搜索房间号" clearable style="width: 150px" @keyup.enter="loadRooms" />
      <el-date-picker v-model="recordDate" type="date" value-format="yyyy-MM-dd" placeholder="录入日期" style="width: 150px" />
      <el-button icon="Search" @click="loadRooms">查询</el-button>
      <el-button @click="fillLastReadings">一键填充上次读数</el-button>
    </el-row>

    <el-table :data="roomList" style="width: 100%; margin-top: 16px" v-loading="loading">
      <el-table-column prop="roomNumber" label="房间号" width="100" />
      <el-table-column prop="buildingName" label="楼栋" width="100" />
      <el-table-column prop="floor" label="楼层" width="80" />
      <el-table-column prop="tenantName" label="当前租户" width="120">
        <template #default="{ row }">{{ row.tenantName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="waterMeterId" label="水表编号" width="120" />
      <el-table-column prop="electricMeterId" label="电表编号" width="120" />
      <el-table-column label="上次读数" width="150">
        <template #default="{ row }">
          <span v-if="row.lastWater">水: {{ row.lastWater }}</span>
          <span v-if="row.lastElectric" style="margin-left: 10px">电: {{ row.lastElectric }}</span>
          <span v-if="!row.lastWater && !row.lastElectric" style="color: #909399">无记录</span>
        </template>
      </el-table-column>
      <el-table-column label="水表读数" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.waterReading" :min="0" :step="1" size="small" controls-position="right" @change="markDirty(row)" />
        </template>
      </el-table-column>
      <el-table-column label="电表读数" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.electricReading" :min="0" :step="1" size="small" controls-position="right" @change="markDirty(row)" />
        </template>
      </el-table-column>
    </el-table>

    <el-row style="margin-top: 20px">
      <el-button type="primary" @click="saveAll" :loading="saving">保存全部</el-button>
      <el-button @click="loadRooms">刷新</el-button>
    </el-row>

    <el-dialog v-model="importDialogVisible" title="水电读数 Excel 导入" width="900px" destroy-on-close>
      <el-steps :active="stepIndex" finish-status="success" align-center style="margin-bottom: 20px">
        <el-step title="上传文件" />
        <el-step title="解析校验" />
        <el-step title="预览确认" />
        <el-step title="导入结果" />
      </el-steps>

      <div v-if="importStep === 'upload'">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="onImportFileChange"
          :on-exceed="onImportFileExceed"
        >
          <el-icon style="font-size: 28px"><UploadFilled /></el-icon>
          <div style="margin-top: 8px">将 Excel 文件拖到此处，或点击上传</div>
          <template #tip>
            <div style="margin-top: 8px; color: #606266">
              仅支持 .xlsx / .xls，大小不超过 10MB
              <el-link type="primary" :underline="false" style="margin-left: 12px" @click="downloadTemplate">下载模板</el-link>
            </div>
          </template>
        </el-upload>
        <div v-if="importUploadError" style="color: #f56c6c; margin-top: 10px">{{ importUploadError }}</div>
      </div>

      <div v-else-if="importStep === 'parsed'">
        <el-alert
          :type="importSummary.errorCount > 0 ? 'error' : 'success'"
          :closable="false"
          :title="`总记录 ${importSummary.totalRows || 0}，成功 ${importSummary.successCount || 0}，错误 ${importSummary.errorCount || 0}`"
          style="margin-bottom: 14px"
        />
        <el-alert
          v-if="importSummary.futureDateCount > 0"
          type="warning"
          :closable="false"
          :title="`存在 ${importSummary.futureDateCount} 条未来日期读数，系统允许导入，请确认是否符合业务规则。`"
          style="margin-bottom: 14px"
        />
        <el-table v-if="importErrors.length > 0" :data="importErrors" max-height="360" border>
          <el-table-column prop="sheetName" label="Sheet名" width="130" />
          <el-table-column prop="rowNumber" label="行号" width="90" />
          <el-table-column prop="recordDate" label="日期" width="120" />
          <el-table-column prop="roomNumber" label="房间号" width="100" />
          <el-table-column prop="errorReason" label="错误原因" min-width="260" />
        </el-table>
        <el-empty v-else description="解析通过，可进入预览确认" />
      </div>

      <div v-else-if="importStep === 'preview'">
        <el-alert
          type="info"
          :closable="false"
          :title="`共 ${importSummary.buildingCount || 0} 栋楼，${importSummary.successCount || 0} 条记录，覆盖 ${importSummary.dateCount || 0} 个日期。`"
          style="margin-bottom: 14px"
        />
        <div style="margin-bottom: 10px; color: #606266">
          导入规则：按“房间 + 日期”唯一确定记录，已存在则覆盖，不存在则新增。
        </div>
        <el-table :data="importPreviewRows" max-height="360" border>
          <el-table-column prop="buildingName" label="楼栋" width="120" />
          <el-table-column prop="recordDate" label="日期" width="120" />
          <el-table-column prop="roomNumber" label="房间号" width="100" />
          <el-table-column prop="waterReading" label="水表读数" width="120" />
          <el-table-column prop="electricReading" label="电表读数" width="120" />
          <el-table-column label="来源行" width="100">
            <template #default="{ row }">{{ row.sheetName }}#{{ row.rowNumber }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else>
        <el-result
          :icon="importResult.failCount > 0 ? 'error' : 'success'"
          :title="importResult.failCount > 0 ? '导入失败' : '导入成功'"
          :sub-title="`成功导入 ${importResult.successCount || 0} 条，失败 ${importResult.failCount || 0} 条`"
        />
      </div>

      <template #footer>
        <template v-if="importStep === 'upload'">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importLoading" @click="parseImportFile">开始解析</el-button>
        </template>

        <template v-else-if="importStep === 'parsed'">
          <el-button @click="resetImportWizard">重新上传</el-button>
          <el-button v-if="importErrors.length > 0" type="warning" @click="downloadErrorReport">下载错误报告</el-button>
          <el-button v-if="importErrors.length === 0 && importSuccessRows.length > 0" type="primary" @click="goPreview">
            进入确认
          </el-button>
          <el-button @click="importDialogVisible = false">关闭</el-button>
        </template>

        <template v-else-if="importStep === 'preview'">
          <el-button @click="importStep = 'parsed'">返回修改</el-button>
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importLoading" @click="confirmImport">确认导入</el-button>
        </template>

        <template v-else>
          <el-button @click="importDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="afterImportView">查看水电读数</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import {
  canInputMeterReadings,
  confirmMeterReadingImport,
  downloadMeterReadingImportTemplate,
  getBuildings,
  getMeterReadings,
  getRooms,
  parseMeterReadingImportFile,
  saveMeterReadings
} from '../api'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const buildings = ref([])
const buildingFilter = ref(null)
const floors = ref([])
const floorFilter = ref(null)
const roomSearch = ref('')
const recordDate = ref(new Date().toISOString().split('T')[0])
const roomList = ref([])
const loading = ref(false)
const saving = ref(false)
const canInput = canInputMeterReadings()

const importDialogVisible = ref(false)
const importStep = ref('upload')
const importLoading = ref(false)
const importUploadFile = ref(null)
const importUploadError = ref('')
const importSummary = ref({ totalRows: 0, successCount: 0, errorCount: 0, buildingCount: 0, dateCount: 0, futureDateCount: 0 })
const importErrors = ref([])
const importSuccessRows = ref([])
const importResult = ref({ successCount: 0, failCount: 0 })
const importPreviewRows = computed(() => importSuccessRows.value.slice(0, 30))
const stepIndex = computed(() => {
  if (importStep.value === 'upload') return 0
  if (importStep.value === 'parsed') return 1
  if (importStep.value === 'preview') return 2
  return 3
})

async function loadBuildings() {
  const res = await getBuildings()
  buildings.value = res.data
}

async function loadRooms() {
  if (!buildingFilter.value) return
  loading.value = true
  try {
    const building = buildings.value.find(b => b.id === buildingFilter.value)
    floors.value = []
    for (let i = 1; i <= building.totalFloors; i++) floors.value.push(i)

    const res = await getRooms({
      buildingId: buildingFilter.value,
      floor: floorFilter.value,
      q: roomSearch.value
    })
    const rooms = (res.data.content || res.data).map(r => ({
      ...r,
      buildingName: building.name,
      waterReading: null,
      electricReading: null,
      dirty: false
    }))

    const dateStr = recordDate.value
    const lastDate = new Date(recordDate.value)
    lastDate.setDate(lastDate.getDate() - 1)
    const lastDateStr = lastDate.toISOString().split('T')[0]

    const lastReadingsRes = await getMeterReadings({ date: lastDateStr, buildingId: buildingFilter.value })
    const lastReadings = (lastReadingsRes.data || []).reduce((acc, r) => {
      acc[r.roomId] = r
      return acc
    }, {})

    roomList.value = rooms.map(r => ({
      ...r,
      lastWater: lastReadings[r.id]?.waterReading,
      lastElectric: lastReadings[r.id]?.electricReading
    }))
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function markDirty(row) {
  row.dirty = true
}

function fillLastReadings() {
  roomList.value.forEach(r => {
    if (r.lastWater !== null && r.lastWater !== undefined) {
      r.waterReading = r.lastWater
      r.dirty = true
    }
    if (r.lastElectric !== null && r.lastElectric !== undefined) {
      r.electricReading = r.lastElectric
      r.dirty = true
    }
  })
  ElMessage.success('已填充上次读数')
}

function openImportDialog() {
  resetImportWizard()
  importDialogVisible.value = true
}

function resetImportWizard() {
  importStep.value = 'upload'
  importUploadFile.value = null
  importUploadError.value = ''
  importSummary.value = { totalRows: 0, successCount: 0, errorCount: 0, buildingCount: 0, dateCount: 0, futureDateCount: 0 }
  importErrors.value = []
  importSuccessRows.value = []
  importResult.value = { successCount: 0, failCount: 0 }
}

function onImportFileExceed() {
  ElMessage.warning('一次只能上传一个文件')
}

function onImportFileChange(file) {
  importUploadError.value = ''
  const raw = file.raw || file
  if (!raw) return
  const name = raw.name?.toLowerCase() || ''
  if (!(name.endsWith('.xlsx') || name.endsWith('.xls'))) {
    importUploadFile.value = null
    importUploadError.value = '请上传Excel文件（.xlsx或.xls）'
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    importUploadFile.value = null
    importUploadError.value = '文件大小不能超过10MB'
    return
  }
  importUploadFile.value = raw
}

async function downloadTemplate() {
  try {
    const res = await downloadMeterReadingImportTemplate()
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'meter-reading-import-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('模板下载失败')
  }
}

async function parseImportFile() {
  if (!importUploadFile.value) {
    ElMessage.warning('请先选择导入文件')
    return
  }
  importLoading.value = true
  try {
    const res = await parseMeterReadingImportFile(importUploadFile.value)
    importSummary.value = res.data?.summary || {}
    importErrors.value = res.data?.errorData || []
    importSuccessRows.value = res.data?.successData || []
    importStep.value = 'parsed'
    if (importErrors.value.length === 0 && importSuccessRows.value.length > 0) {
      ElMessage.success('解析完成，请确认导入')
    } else if (importErrors.value.length > 0) {
      ElMessage.warning('解析完成，存在错误，请先修正')
    } else {
      ElMessage.warning('未解析到可导入数据')
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '解析失败'
    ElMessage.error(msg)
  } finally {
    importLoading.value = false
  }
}

function goPreview() {
  importStep.value = 'preview'
}

async function confirmImport() {
  if (importSuccessRows.value.length === 0) {
    ElMessage.warning('没有可导入的数据')
    return
  }
  importLoading.value = true
  try {
    const res = await confirmMeterReadingImport(importSuccessRows.value)
    importResult.value = {
      successCount: res.data?.successCount || importSuccessRows.value.length,
      failCount: res.data?.failCount || 0
    }
    importStep.value = 'result'
    await loadRooms()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '导入失败'
    ElMessage.error(msg)
    importResult.value = { successCount: 0, failCount: importSuccessRows.value.length }
    importStep.value = 'result'
  } finally {
    importLoading.value = false
  }
}

function downloadErrorReport() {
  if (importErrors.value.length === 0) {
    ElMessage.warning('暂无错误数据')
    return
  }
  const escapeCsv = (v) => {
    const text = String(v ?? '')
    if (text.includes(',') || text.includes('"') || text.includes('\n')) {
      return `"${text.replaceAll('"', '""')}"`
    }
    return text
  }
  const rows = [
    ['Sheet名', '行号', '日期', '房间号', '错误原因'],
    ...importErrors.value.map(e => [e.sheetName, e.rowNumber, e.recordDate, e.roomNumber, e.errorReason])
  ]
  const csv = rows.map(r => r.map(escapeCsv).join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `meter-reading-import-errors-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function afterImportView() {
  importDialogVisible.value = false
  loadRooms()
}

async function saveAll() {
  const toSave = roomList.value.filter(r => r.dirty && (r.waterReading !== null || r.electricReading !== null))
  if (toSave.length === 0) {
    return ElMessage.warning('没有需要保存的数据')
  }

  saving.value = true
  try {
    const data = toSave.map(r => ({
      roomId: r.id,
      recordDate: recordDate.value,
      waterReading: r.waterReading || 0,
      electricReading: r.electricReading || 0
    }))
    await saveMeterReadings(data)
    ElMessage.success('保存成功')
    roomList.value.forEach(r => r.dirty = false)
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadBuildings()
  if (buildings.value.length > 0) {
    buildingFilter.value = buildings.value[0].id
    await loadRooms()
  }
})
</script>
