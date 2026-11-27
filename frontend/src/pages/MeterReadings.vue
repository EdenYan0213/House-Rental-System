<template>
  <div class="page">
    <!-- 页头：标题 + 说明 + 操作 -->
    <div class="page-head">
      <div>
        <h2>水电录入</h2>
        <p class="page-sub">
          选择楼栋与日期后直接填写读数，右侧会实时算出本次用量。
          <span v-if="dirtyCount > 0" class="dirty-hint">
            已修改 {{ dirtyCount }} 间，记得点「保存全部」
          </span>
        </p>
      </div>
      <div class="page-actions">
        <el-button v-if="canInput" :icon="Sort" @click="openBatchDelta">批量增量录入</el-button>
        <el-button v-if="canInput" :icon="Upload" @click="openImportDialog">Excel 导入</el-button>
        <el-button v-if="canInput" :icon="Camera" @click="ocrVisible = true">拍照识别</el-button>
        <el-button :icon="Download" @click="exportBuildingCsv">导出 CSV</el-button>
        <el-button
          type="primary"
          :icon="Check"
          :loading="saving"
          :disabled="dirtyCount === 0"
          @click="saveAll"
        >
          保存全部{{ dirtyCount > 0 ? `（${dirtyCount}）` : '' }}
        </el-button>
      </div>
    </div>

    <!-- 筛选栏：每个控件固定宽度，不再被 flex 撑满 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">楼栋</span>
          <el-select
            v-model="buildingFilter"
            placeholder="请选择"
            class="filter-select"
            @change="onBuildingChange"
          >
            <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </div>

        <div class="filter-group">
          <span class="filter-label">楼层</span>
          <el-select
            v-model="floorFilter"
            placeholder="全部"
            clearable
            class="filter-select filter-select--sm"
            @change="onFilterChange"
          >
            <el-option v-for="f in floors" :key="f" :label="f + '层'" :value="f" />
          </el-select>
        </div>

        <div class="filter-group">
          <span class="filter-label">抄表日期</span>
          <el-date-picker
            v-model="recordDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="filter-date"
            @change="loadRooms"
          />
        </div>

        <div class="filter-group">
          <span class="filter-label">房间号</span>
          <el-input
            v-model="roomSearch"
            placeholder="搜索"
            clearable
            class="filter-search"
            :prefix-icon="Search"
            @keyup.enter="onFilterChange"
            @clear="onFilterChange"
          />
        </div>

        <div class="filter-divider"></div>

        <el-switch
          v-model="onlyUnread"
          active-text="只看未抄表"
          inline-prompt
          @change="onFilterChange"
        />

        <el-button :disabled="roomList.length === 0" @click="fillLastReadings">
          填充上次读数
        </el-button>
        <el-button :disabled="dirtyCount === 0" @click="clearAllInputs">
          清空输入
        </el-button>
      </div>
    </el-card>

    <!-- 抄表表格：直接把读数改在行里，右侧实时显示本次用量。
         改动过的行左侧有标记，便于保存前核对。 -->
    <!-- 未选楼栋：给出引导，而不是一张空表 -->
    <el-card v-if="!buildingFilter" shadow="never" class="empty-guide">
      <el-icon :size="40" class="guide-icon"><OfficeBuilding /></el-icon>
      <p class="guide-title">先选择要抄表的楼栋</p>
      <p class="guide-desc">选定楼栋后，这里会列出该楼栋的房间，可直接在表格里填写读数。</p>
      <el-button
        v-if="buildings.length > 0"
        type="primary"
        @click="buildingFilter = buildings[0].id; onBuildingChange()"
      >使用「{{ buildings[0]?.name }}」</el-button>
    </el-card>

    <el-card v-else shadow="never" class="table-card">
    <el-table
      ref="meterTableRef"
      :data="pagedRooms"
      row-key="id"
      style="width: 100%"
      v-loading="loading"
      :row-class-name="meterRowClass"
      empty-text="该楼栋下没有匹配的房间"
      @selection-change="onRoomSelectionChange"
      @row-click="onRowClick"
    >
      <el-table-column type="selection" width="46" reserve-selection />
      <!-- 固定宽度：这一列原本用 min-width，会把剩余空间全吃掉，
           在宽屏上撑到近千像素，右侧真正要填的输入框反被挤到边上 -->
      <el-table-column label="房间 / 租户" min-width="200">
        <template #default="{ row }">
          <div class="meter-room-cell">
            <div class="mr-room">
              <span class="mr-no">{{ row.roomNumber }}</span>
              <span v-if="row.floor != null" class="mr-floor">{{ row.floor }}F</span>
            </div>
            <div class="mr-sub">
              <span class="mr-tenant">{{ row.tenantName || '空置' }}</span>
              <span v-if="row.buildingName" class="mr-building">{{ row.buildingName }}</span>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="上次读数（水 / 电）" min-width="170">
        <template #default="{ row }">
          <div v-if="row.lastWater == null && row.lastElectric == null" class="meter-none">
            无记录
          </div>
          <div v-else class="meter-last-block">
            <span class="meter-last">
              <span>{{ row.lastWater ?? '-' }}</span>
              <span class="meter-sep">/</span>
              <span>{{ row.lastElectric ?? '-' }}</span>
            </span>
            <span v-if="row.lastDate" class="meter-last-date">{{ row.lastDate }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="水表读数" min-width="140">
        <template #default="{ row }">
          <el-input-number
            v-model="row.waterReading"
            :min="0"
            :step="1"
            size="small"
            controls-position="right"
            @change="markDirty(row)"
          />
        </template>
      </el-table-column>

      <el-table-column label="电表读数" min-width="140">
        <template #default="{ row }">
          <el-input-number
            v-model="row.electricReading"
            :min="0"
            :step="1"
            size="small"
            controls-position="right"
            @change="markDirty(row)"
          />
        </template>
      </el-table-column>

      <!-- 本次用量：抄表时最关心的数字，实时算出来 -->
      <el-table-column label="本次用量（水 / 电）" min-width="150">
        <template #default="{ row }">
          <span class="meter-usage">
            <span :class="{ 'usage-warn': isUsageInvalid(row.waterUsage) }">{{ row.waterUsage ?? '-' }}</span>
            <span class="meter-sep">/</span>
            <span :class="{ 'usage-warn': isUsageInvalid(row.electricUsage) }">{{ row.electricUsage ?? '-' }}</span>
          </span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页：房间多时按页抄表，底部同时给出保存入口 -->
    <div class="table-footer">
      <el-pagination
        v-if="filteredRooms.length > pageSize"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="filteredRooms.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
      <span v-else-if="filteredRooms.length > 0" class="footer-count">
        共 {{ filteredRooms.length }} 间房间
      </span>
      <div class="footer-actions">
        <el-button @click="loadRooms">刷新</el-button>
        <el-button
          type="primary"
          :icon="Check"
          :loading="saving"
          :disabled="dirtyCount === 0"
          @click="saveAll"
        >保存全部{{ dirtyCount > 0 ? `（${dirtyCount}）` : '' }}</el-button>
      </div>
    </div>
    </el-card>

    <MeterOcrDialog v-model="ocrVisible" @saved="onOcrSaved" />

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
            <div style="margin-top: 8px; color: var(--text-muted)">
              仅支持 .xlsx / .xls，大小不超过 10MB
              <el-link type="primary" :underline="false" style="margin-left: 12px" @click="downloadTemplate">下载模板</el-link>
            </div>
          </template>
        </el-upload>
        <div v-if="importUploadError" style="color: var(--danger-color); margin-top: 10px">{{ importUploadError }}</div>
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
        <div style="margin-bottom: 10px; color: var(--text-muted)">
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

    <!-- 批量增量录入：勾选房间 + 给增量，按上期读数自动累加 -->
    <el-dialog v-model="batchDeltaVisible" title="批量增量录入" width="560px">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 14px"
        title="按「上期读数 + 增量」生成当期读数。没有历史读数的房间会被跳过。"
      />

      <el-form label-width="96px">
        <el-form-item label="抄表日期">
          <el-date-picker
            v-model="batchDeltaForm.recordDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
            :disabled-date="d => d.getTime() > Date.now()"
          />
        </el-form-item>

        <el-form-item label="已选房间">
          <div class="bd-selected">
            <el-tag v-if="selectedRoomIds.length === 0" type="info" effect="plain">
              尚未勾选，请先在表格左侧勾选房间
            </el-tag>
            <template v-else>
              <el-tag type="primary" effect="light">已选 {{ selectedRoomIds.length }} 间</el-tag>
              <el-button link type="primary" @click="fillSelectionWithAllRooms">选中当前页全部</el-button>
              <el-button link @click="clearRoomSelection">清空选择</el-button>
            </template>
          </div>
        </el-form-item>

        <el-form-item label="水表增量">
          <el-input-number v-model="batchDeltaForm.waterDelta" :min="0" :step="1" :precision="2" placeholder="留空表示不改" style="width: 180px" />
          <span class="bd-unit">吨</span>
          <span class="bd-hint">留空则水表沿用上期读数</span>
        </el-form-item>

        <el-form-item label="电表增量">
          <el-input-number v-model="batchDeltaForm.electricDelta" :min="0" :step="10" :precision="2" placeholder="留空表示不改" style="width: 180px" />
          <span class="bd-unit">度</span>
          <span class="bd-hint">留空则电表沿用上期读数</span>
        </el-form-item>

        <el-form-item label="">
          <el-checkbox v-model="batchDeltaForm.overwrite">
            覆盖该日期已存在的抄表记录
          </el-checkbox>
        </el-form-item>
      </el-form>

      <div v-if="batchDeltaPreview.length > 0" class="bd-preview">
        <div class="bd-preview-title">预览（前 5 间）</div>
        <el-table :data="batchDeltaPreview" size="small" border max-height="220">
          <el-table-column prop="roomNumber" label="房间" width="90" />
          <el-table-column label="水表" width="150">
            <template #default="{ row }">
              <span v-if="row.water === null" class="bd-na">无历史读数</span>
              <span v-else class="num">{{ row.waterBefore }} → {{ row.waterAfter }}</span>
            </template>
          </el-table-column>
          <el-table-column label="电表">
            <template #default="{ row }">
              <span v-if="row.electric === null" class="bd-na">无历史读数</span>
              <span v-else class="num">{{ row.electricBefore }} → {{ row.electricAfter }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="batchDeltaResult" class="bd-result">
        <el-alert
          :type="batchDeltaResult.skipped?.length ? 'warning' : 'success'"
          :closable="false"
          :title="`新增 ${batchDeltaResult.created} 条，更新 ${batchDeltaResult.updated} 条，跳过 ${batchDeltaResult.skipped?.length || 0} 条`"
        />
        <ul v-if="batchDeltaResult.skipped?.length" class="bd-skip-list">
          <li v-for="s in batchDeltaResult.skipped" :key="s.roomId">
            {{ s.roomNumber }}：{{ s.reason }}
          </li>
        </ul>
      </div>

      <template #footer>
        <el-button @click="batchDeltaVisible = false">关闭</el-button>
        <el-button type="primary" :loading="batchDeltaLoading" @click="submitBatchDelta">
          生成读数
        </el-button>
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
  exportMeterReadingsCsv,
  getBuildings,
  getLastMeterReadings,
  getRooms,
  parseMeterReadingImportFile,
  saveMeterReadings,
  generateMeterReadingsByDelta,
  getErrorMessage} from '../api'
import { ElMessage } from 'element-plus'
import MeterOcrDialog from '../components/MeterOcrDialog.vue'
import {
  UploadFilled, Upload, Download, Check, Search, OfficeBuilding, Sort, Camera
} from '@element-plus/icons-vue'

const buildings = ref([])
const buildingFilter = ref(null)
const floors = ref([])
const floorFilter = ref(null)
const roomSearch = ref('')
const recordDate = ref(new Date().toISOString().split('T')[0])
const roomList = ref([])
const loading = ref(false)

// 前端分页：房间数据一次性拿到，按页渲染，避免一次画几百行卡顿
const currentPage = ref(1)
const pageSize = ref(20)

// 只看还没填读数的房间（已填的隐藏），便于逐间抄表
const onlyUnread = ref(false)

/** 经「只看未抄表」过滤后的房间 */
const filteredRooms = computed(() => {
  if (!onlyUnread.value) return roomList.value
  return roomList.value.filter(r => r.waterReading == null && r.electricReading == null)
})

/** 当前页要渲染的房间 */
const pagedRooms = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRooms.value.slice(start, start + pageSize.value)
})

/** 已改动（未保存）的房间数，用于「保存全部」按钮文案与禁用态 */
const dirtyCount = computed(() => roomList.value.filter(r => r.dirty).length)
const saving = ref(false)
const canInput = canInputMeterReadings()

/* ---------- 批量增量录入 ---------- */
const batchDeltaVisible = ref(false)
const batchDeltaLoading = ref(false)
const batchDeltaResult = ref(null)
const selectedRoomIds = ref([])
const batchDeltaForm = ref({
  recordDate: new Date().toISOString().split('T')[0],
  waterDelta: null,
  electricDelta: null,
  overwrite: false
})

function onRoomSelectionChange(rows) {
  // 表格行是 Room 实体展开而来，房间主键是 row.id（没有 roomId 字段）
  selectedRoomIds.value = (rows || []).map(r => r.id).filter(v => v != null)
}

function openBatchDelta() {
  batchDeltaResult.value = null
  batchDeltaForm.value.recordDate = recordDate.value || new Date().toISOString().split('T')[0]
  batchDeltaVisible.value = true
}

/** 把当前页全部房间加入选择，方便「整页一起生成」。 */
function fillSelectionWithAllRooms() {
  const ids = pagedRooms.value.map(r => r.id).filter(v => v != null)
  selectedRoomIds.value = Array.from(new Set([...selectedRoomIds.value, ...ids]))
}

function clearRoomSelection() {
  selectedRoomIds.value = []
}

/**
 * 预览：用表格里已有的「上次读数」推算生成后的值，
 * 让用户在提交前就能看出增量是否合理。
 */
const batchDeltaPreview = computed(() => {
  if (!batchDeltaVisible.value) return []
  const ids = new Set(selectedRoomIds.value)
  const wd = batchDeltaForm.value.waterDelta
  const ed = batchDeltaForm.value.electricDelta
  return roomList.value
    .filter(r => ids.has(r.id))
    .slice(0, 5)
    .map((r) => {
      // 上期读数由 loadRooms 注入：lastWater / lastElectric
      const hasWater = r.lastWater !== null && r.lastWater !== undefined
      const hasElec = r.lastElectric !== null && r.lastElectric !== undefined
      return {
        roomId: r.id,
        roomNumber: r.roomNumber,
        water: hasWater ? true : null,
        waterBefore: hasWater ? r.lastWater : '-',
        waterAfter: hasWater ? (wd != null ? Number(r.lastWater) + Number(wd) : r.lastWater) : '-',
        electric: hasElec ? true : null,
        electricBefore: hasElec ? r.lastElectric : '-',
        electricAfter: hasElec ? (ed != null ? Number(r.lastElectric) + Number(ed) : r.lastElectric) : '-'
      }
    })
})

async function submitBatchDelta() {
  if (selectedRoomIds.value.length === 0) {
    ElMessage.warning('请先在表格里勾选房间')
    return
  }
  if (!batchDeltaForm.value.recordDate) {
    ElMessage.warning('请选择抄表日期')
    return
  }
  if (batchDeltaForm.value.waterDelta == null && batchDeltaForm.value.electricDelta == null) {
    ElMessage.warning('请至少填写水表或电表的增量')
    return
  }

  batchDeltaLoading.value = true
  try {
    const res = await generateMeterReadingsByDelta({
      roomIds: selectedRoomIds.value,
      recordDate: batchDeltaForm.value.recordDate,
      waterDelta: batchDeltaForm.value.waterDelta,
      electricDelta: batchDeltaForm.value.electricDelta,
      overwrite: batchDeltaForm.value.overwrite
    })
    batchDeltaResult.value = res.data || {}
    const created = batchDeltaResult.value.created || 0
    const updated = batchDeltaResult.value.updated || 0
    if (created + updated > 0) {
      ElMessage.success(`已生成 ${created + updated} 条读数`)
      await loadRooms()
    } else {
      ElMessage.warning('没有房间被处理，请查看跳过原因')
    }
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '批量生成失败'))
  } finally {
    batchDeltaLoading.value = false
  }
}

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

/** 切换楼栋：重置楼层筛选与页码，再加载。 */
function onBuildingChange() {
  floorFilter.value = null
  currentPage.value = 1
  loadRooms()
}

/** 楼层/搜索变化：回到第一页。 */
function onFilterChange() {
  currentPage.value = 1
  loadRooms()
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

    // 上次读数：取每个房间在抄表日期之前的**最后一条**记录。
    // 原实现取「日期前一天」，隔月抄表时永远匹配不到，界面一片「无记录」。
    const lastRes = await getLastMeterReadings({
      buildingId: buildingFilter.value,
      beforeDate: recordDate.value || undefined
    })
    const lastMap = lastRes.data || {}

    roomList.value = rooms.map(r => {
      const last = lastMap[String(r.id)]
      return {
        ...r,
        lastWater: last?.waterReading ?? null,
        lastElectric: last?.electricReading ?? null,
        lastDate: last?.recordDate || null
      }
    })
    // 数据刷新后页码可能越界，收敛到最后一页
    const maxPage = Math.max(1, Math.ceil(filteredRooms.value.length / pageSize.value))
    if (currentPage.value > maxPage) currentPage.value = maxPage
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 标记改动并重算本次用量。
 *
 * <p>用量 = 本次读数 − 上次读数。负数说明抄错了（或换了表），
 * 用红字提示，避免带着错误数据保存。
 */
function markDirty(row) {
  row.dirty = true
  row.waterUsage = calcUsage(row.waterReading, row.lastWater)
  row.electricUsage = calcUsage(row.electricReading, row.lastElectric)
}

function calcUsage(current, last) {
  if (current == null || last == null) return null
  const diff = Number(current) - Number(last)
  return Number.isFinite(diff) ? Math.round(diff * 100) / 100 : null
}

/** 用量为负 → 读数倒退，需要人工确认。 */
function isUsageInvalid(usage) {
  return usage != null && usage < 0
}

const meterTableRef = ref(null)

/**
 * 点击整行 → 切换该行的勾选状态。
 *
 * <p>抄表时选择房间是高频操作，只用 46px 的复选框太难点。
 * 但点在输入框、按钮或复选框上时要放行，否则会「想填读数结果反被取消勾选」。
 */
function onRowClick(row, column, event) {
  const el = event?.target
  if (el) {
    // 输入框 / 数字调节按钮 / 复选框 / 链接：交给控件自己处理
    if (el.closest('input, textarea, button, .el-input, .el-input-number, .el-checkbox, a, .el-link')) {
      return
    }
  }
  meterTableRef.value?.toggleRowSelection(row)
}

/** 改动过的行高亮，保存前一眼能看出哪些是这次录的。 */
function meterRowClass({ row }) {
  return row.dirty ? 'meter-row-dirty' : ''
}

/** 清空所有已填读数（不影响已保存的数据）。 */
function clearAllInputs() {
  roomList.value.forEach(r => {
    r.waterReading = null
    r.electricReading = null
    r.waterUsage = null
    r.electricUsage = null
    r.dirty = false
  })
  ElMessage.success('已清空未保存的输入')
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
    // 填充后用量归零（读数与上次相同）
    r.waterUsage = 0
    r.electricUsage = 0
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
    const msg = getErrorMessage(e, '解析失败')
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
    const msg = getErrorMessage(e, '导入失败')
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

async function exportBuildingCsv() {
  if (!buildingFilter.value) {
    ElMessage.warning('请先选择楼栋')
    return
  }
  try {
    const params = {
      buildingId: buildingFilter.value
    }
    if (recordDate.value) {
      params.date = recordDate.value
    }
    const res = await exportMeterReadingsCsv(params)
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `meter-building-${buildingFilter.value}-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('楼栋水电CSV导出成功')
  } catch (e) {
    ElMessage.error('楼栋水电CSV导出失败')
  }
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


// —— 拍照识别 ——
const ocrVisible = ref(false)

/** 识别导入成功后刷新列表（本页数据由 loadRooms 一并拉取） */
function onOcrSaved() {
  loadRooms()
}


onMounted(async () => {
  await loadBuildings()
  if (buildings.value.length > 0) {
    buildingFilter.value = buildings.value[0].id
    await loadRooms()
  }
})
</script>

<style scoped>
/* ==========================================================================
   水电录入页
   ========================================================================== */

/* 筛选栏：控件固定宽度，靠标签对齐，不再被 flex 撑满 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex: 0 0 auto;
}

.filter-label {
  font-size: var(--fs-xs);
  color: var(--text-muted);
  white-space: nowrap;
}

/* Element Plus 的 select 内部还有一层 wrapper，
   只给外层 class 设宽度不够，需要同时约束内部元素。 */
.filter-select,
.filter-select :deep(.el-select__wrapper) {
  width: 180px;
}

.filter-select--sm,
.filter-select--sm :deep(.el-select__wrapper) {
  width: 110px;
}

.filter-date :deep(.el-input__wrapper) {
  width: 160px;
}

.filter-search :deep(.el-input__wrapper) {
  width: 140px;
}

/* 未选楼栋时的引导 */
.empty-guide {
  text-align: center;
  padding: var(--space-8) var(--space-4);
}

.guide-icon {
  color: var(--accent-3);
  opacity: 0.5;
  margin-bottom: var(--space-3);
}

.guide-title {
  margin: 0 0 var(--space-2);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-main);
}

.guide-desc {
  margin: 0 0 var(--space-4);
  font-size: var(--fs-sm);
  color: var(--text-muted);
  line-height: 1.6;
}

/* 页头里的「已修改 N 间」提示 */
.dirty-hint {
  display: inline-block;
  margin-left: var(--space-2);
  padding: 1px 8px;
  border-radius: 999px;
  font-size: var(--fs-xs);
  color: var(--warning-color);
  background: color-mix(in srgb, var(--warning-color) 12%, transparent);
}

/* 表格底部：分页居左、操作居右 */
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-color);
}

.footer-count {
  font-size: var(--fs-sm);
  color: var(--text-muted);
}

.filter-divider {
  width: 1px;
  height: 22px;
  background: var(--border-color);
}

/* 上次读数：数值 + 该次读数日期 */
.meter-last-block {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.meter-last-date {
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.footer-actions {
  display: flex;
  gap: var(--space-2);
  margin-left: auto;
}

/* ---------- 抄表表格 ---------- */
.meter-room-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  line-height: 1.3;
  min-width: 0;
}

.mr-room {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

/* 房号是这一列的锚点，给足字重和字距，扫读时第一眼就落到它上面 */
.mr-no {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.01em;
}

.mr-floor {
  flex: 0 0 auto;
  font-family: var(--font-num);
  font-size: 11px;
  font-weight: 600;
  color: var(--accent-3);
  background: var(--accent-1);
  padding: 1px 5px;
  border-radius: 4px;
  line-height: 1.4;
}

/* 租户与楼栋并排：楼栋是次要信息，超长时让它先省略 */
.mr-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.mr-tenant {
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mr-building {
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.75;
}

.mr-building::before {
  content: '·';
  margin-right: 6px;
}

.meter-last,
.meter-usage {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-sm);
  display: inline-flex;
  gap: 6px;
  align-items: baseline;
}

.meter-usage {
  color: var(--text-main);
  font-weight: 600;
}

.meter-sep {
  color: var(--border-strong);
}

.meter-none {
  color: var(--text-muted);
  font-size: var(--fs-xs);
}

.usage-warn {
  color: var(--danger-color);
}

/* 改动过的行：左侧色条 + 极浅底色 */
.el-table .meter-row-dirty > td {
  background: var(--accent-1) !important;
}

/* 整行可点选：给手型提示 */
.table-card .el-table__body tr {
  cursor: pointer;
}

/* 输入框区域恢复默认光标，避免误以为整块都能点选 */
.table-card .el-table__body tr .el-input-number,
.table-card .el-table__body tr input {
  cursor: auto;
}

/* 选中行：左侧主色条 + 极淡底色，和「已改动」的橙色标记不冲突 */
.table-card .el-table__body tr.el-table__row--striped.current-row > td,
.table-card .el-table__body tr.current-row > td {
  background: var(--accent-1);
}

.table-card .el-table__body tr.current-row > td:first-child {
  box-shadow: inset 3px 0 0 var(--accent-3);
}

.el-table .meter-row-dirty > td:first-child {
  box-shadow: inset 3px 0 0 var(--accent-3);
}

/* ---------- 批量增量录入 ---------- */
.bd-selected {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.bd-unit {
  margin-left: var(--space-2);
  color: var(--text-muted);
  font-size: 13px;
}

.bd-hint {
  margin-left: var(--space-3);
  color: var(--text-muted);
  font-size: 12px;
}

.bd-preview {
  margin-top: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-color);
}

.bd-preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: var(--space-2);
}

.bd-na {
  color: var(--text-muted);
  font-size: 12px;
}

.bd-result {
  margin-top: var(--space-3);
}

.bd-skip-list {
  margin: var(--space-2) 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.7;
}
</style>
