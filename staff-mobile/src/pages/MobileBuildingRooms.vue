<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">{{ buildingName || '房间信息' }}</h1>
            <p class="m-head-sub">
              共 <span class="m-num count">{{ filteredRooms.length }}</span> 间
              <template v-if="filteredRooms.length !== rooms.length">
                / {{ rooms.length }} 间
              </template>
            </p>
          </div>
          <div v-if="canAdd" class="head-ops">
            <el-button class="head-btn head-btn--ghost" @click="openBatch">批量</el-button>
            <el-button
              type="primary"
              :icon="Plus"
              class="head-btn"
              @click="openCreate"
            >新建</el-button>
          </div>
        </div>
      </header>

      <!-- 筛选 -->
      <div class="filters">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索房间号"
          :prefix-icon="Search"
          class="filter-keyword"
        />
        <el-select v-model="statusFilter" clearable placeholder="状态" class="filter-select">
          <el-option label="全部" value="" />
          <el-option label="空闲" value="IDLE" />
          <el-option label="已租" value="RENTED" />
          <el-option label="维修" value="MAINTENANCE" />
        </el-select>
        <el-select v-model="floorFilter" clearable placeholder="楼层" class="filter-select">
          <el-option label="全部" value="" />
          <el-option v-for="floor in floorOptions" :key="floor" :label="`${floor}层`" :value="floor" />
        </el-select>
      </div>

      <!-- 空态 -->
      <div v-if="!loading && filteredRooms.length === 0" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><House /></el-icon>
        <p class="empty-title">{{ rooms.length === 0 ? '暂无房间' : '没有匹配的房间' }}</p>
        <p class="empty-desc">
          {{ rooms.length === 0
            ? (canAdd ? '点击右上角「新建」添加房间。' : '请联系管理员添加房间。')
            : '换个筛选条件试试。' }}
        </p>
      </div>

      <!-- 房间卡 -->
      <div v-else class="room-list">
        <article
          v-for="room in filteredRooms"
          :key="room.id"
          class="m-card room-card"
        >
          <div class="room-main" @click="openRoom(room)">
            <div class="room-top">
              <span class="room-name">{{ room.roomNumber || '-' }}</span>
              <span class="room-tag" :class="statusClass(room.status)">
                {{ roomStatusText(room.status) }}
              </span>
            </div>
            <div class="room-meta">
              <span>{{ resolveFloor(room) ? resolveFloor(room) + '层' : '楼层未知' }}</span>
              <span v-if="room.layout">· {{ room.layout }}</span>
              <span v-if="room.area">· {{ room.area }}m²</span>
            </div>
          </div>

          <!-- 行内操作 -->
          <div v-if="canEdit" class="room-ops">
            <button type="button" class="op-btn" @click.stop="openEdit(room)">编辑</button>
            <button
              v-if="canDelete"
              type="button"
              class="op-btn op-btn--danger"
              @click.stop="confirmDelete(room)"
            >删除</button>
          </div>
        </article>
      </div>
    </div>

    <!-- 新建 / 编辑房间 -->
    <el-dialog
      v-model="formVisible"
      :title="editId ? '编辑房间' : '新建房间'"
      width="92%"
      align-center
    >
      <el-form :model="form" label-position="top" class="create-form">
        <el-form-item label="房间号" required>
          <el-input v-model="form.roomNumber" placeholder="如：301" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="楼层">
              <el-input-number v-model="form.floor" :min="1" :max="99" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" class="full-width">
                <el-option label="空闲" value="IDLE" />
                <el-option label="已租" value="RENTED" />
                <el-option label="维修" value="MAINTENANCE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="户型">
              <el-input v-model="form.layout" placeholder="如：一室一厅" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积（㎡）">
              <el-input-number v-model="form.area" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="月租金（元）">
          <el-input-number v-model="form.rent" :min="0" :precision="2" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ editId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量创建 -->
    <el-dialog v-model="batchVisible" title="批量新建房间" width="92%" align-center>
      <el-form :model="batchForm" label-position="top" class="create-form">
        <p class="batch-hint">
          按楼层和房间号区间生成，房间号自动拼成「楼层 + 序号」，
          例如 3 层 1~6 号会生成 301、302 … 306。
        </p>
        <el-form-item label="楼层" required>
          <el-input-number v-model="batchForm.floor" :min="1" :max="99" class="full-width" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="起始号" required>
              <el-input-number v-model="batchForm.startNumber" :min="1" :max="99" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束号" required>
              <el-input-number v-model="batchForm.endNumber" :min="1" :max="99" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="户型">
              <el-input v-model="batchForm.layout" placeholder="如：一室一厅" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积（㎡）">
              <el-input-number v-model="batchForm.area" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <p class="batch-preview">
          将生成：{{ batchPreview || '—' }}
        </p>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batching" @click="submitBatch">
          创建 {{ batchCount }} 间
        </el-button>
      </template>
    </el-dialog>
  </PullRefresh>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, House } from '@element-plus/icons-vue'
import PullRefresh from '@/components/PullRefresh.vue'
import {
  getRooms, createRoom, updateRoom, deleteRoom,
  createRoomsBatch, getBuildings,
  canAddRoom, canEditRoom, canDeleteRoom, getErrorMessage
} from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const rooms = ref([])
const buildings = ref([])
const keyword = ref('')
const statusFilter = ref('')
const floorFilter = ref('')

const canAdd = canAddRoom()
const canEdit = canEditRoom()
const canDelete = canDeleteRoom()

const buildingId = computed(() => Number(route.params.id))
const buildingName = computed(() => {
  const hit = buildings.value.find(b => Number(b.id) === buildingId.value)
  return hit?.name || ''
})

/* ---------- 列表与筛选 ---------- */
const floorOptions = computed(() => {
  const values = rooms.value
    .map(item => Number(resolveFloor(item)))
    .filter(v => Number.isFinite(v) && v > 0)
  return Array.from(new Set(values)).sort((a, b) => a - b)
})

const filteredRooms = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return rooms.value.filter(item => {
    if (statusFilter.value && normalizeRoomStatus(item.status) !== statusFilter.value) return false
    if (String(floorFilter.value || '') !== '' && Number(resolveFloor(item)) !== Number(floorFilter.value)) {
      return false
    }
    if (!q) return true
    return String(item.roomNumber || '').toLowerCase().includes(q)
  })
})

/** 后端返回 IDLE/RENTED/MAINTENANCE 或中文，统一成三态常量。 */
function normalizeRoomStatus(status) {
  const raw = String(status || '').trim().toUpperCase()
  if (!raw) return ''
  if (['IDLE', '空闲', 'VACANT', 'AVAILABLE', 'EMPTY', '0'].includes(raw)) return 'IDLE'
  if (['RENTED', '已租', 'OCCUPIED', 'IN_USE', '1'].includes(raw)) return 'RENTED'
  if (['MAINTENANCE', '维修', '维修中', '2'].includes(raw)) return 'MAINTENANCE'
  return raw
}

function roomStatusText(status) {
  return { IDLE: '空闲', RENTED: '已租', MAINTENANCE: '维修' }[normalizeRoomStatus(status)] || status || '-'
}

function statusClass(status) {
  return {
    IDLE: 'is-idle',
    RENTED: 'is-rented',
    MAINTENANCE: 'is-maintenance'
  }[normalizeRoomStatus(status)] || ''
}

/** 优先用 floor 字段；缺失时从房间号前两位推断（如 301 → 3 层）。 */
function resolveFloor(room) {
  const raw = room?.floor
  if (raw !== undefined && raw !== null && String(raw).trim() !== '') {
    const n = Number(raw)
    if (Number.isFinite(n)) return n
  }
  const match = String(room?.roomNumber || '').match(/^(\d{1,2})\d{2}$/)
  return match ? Number(match[1]) : null
}

/* ---------- 新建 / 编辑 ---------- */
const formVisible = ref(false)
const submitting = ref(false)
const editId = ref(null)
const emptyForm = () => ({
  roomNumber: '',
  floor: 1,
  status: 'IDLE',
  layout: '',
  area: 0,
  rent: 0
})
const form = ref(emptyForm())

function openCreate() {
  editId.value = null
  // 楼层默认取当前筛选，没有则 1
  const guess = Number(floorFilter.value) || 1
  form.value = { ...emptyForm(), floor: guess }
  formVisible.value = true
}

function openEdit(room) {
  editId.value = room.id
  form.value = {
    roomNumber: room.roomNumber || '',
    floor: resolveFloor(room) || 1,
    status: normalizeRoomStatus(room.status) || 'IDLE',
    layout: room.layout || '',
    area: Number(room.area || 0),
    rent: Number(room.rent || 0)
  }
  formVisible.value = true
}

async function submitForm() {
  const roomNumber = String(form.value.roomNumber || '').trim()
  if (!roomNumber) {
    ElMessage.warning('请填写房间号')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form.value, roomNumber, buildingId: buildingId.value }
    if (editId.value) {
      await updateRoom(editId.value, payload)
      ElMessage.success('房间已更新')
    } else {
      await createRoom(payload)
      ElMessage.success('房间已创建')
    }
    formVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, editId.value ? '保存失败' : '创建失败'))
  } finally {
    submitting.value = false
  }
}

/* ---------- 删除 ---------- */
async function confirmDelete(room) {
  const label = room.roomNumber || '该房间'
  try {
    await ElMessageBox.confirm(
      `确定删除房间 ${label} 吗？删除后可在操作记录中撤销。`,
      '删除房间',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await deleteRoom(room.id)
    ElMessage.success('房间已删除')
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '删除失败，可能有合同或账单依赖该房间'))
  }
}

/* ---------- 批量新建 ---------- */
const batchVisible = ref(false)
const batching = ref(false)
const batchForm = ref({ floor: 1, startNumber: 1, endNumber: 6, layout: '', area: 0 })

const batchCount = computed(() => {
  const s = Number(batchForm.value.startNumber) || 0
  const e = Number(batchForm.value.endNumber) || 0
  return e >= s && s > 0 ? e - s + 1 : 0
})

/** 预览实际会生成的房间号，避免用户对拼接规则有误解。 */
const batchPreview = computed(() => {
  const { floor, startNumber, endNumber } = batchForm.value
  const f = Number(floor) || 0
  const s = Number(startNumber) || 0
  const e = Number(endNumber) || 0
  if (!f || e < s || s <= 0) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const list = []
  for (let i = s; i <= e && list.length < 6; i++) list.push(`${f}${pad(i)}`)
  const more = e - s + 1 > list.length ? ` … 共 ${e - s + 1} 间` : ''
  return list.join('、') + more
})

function openBatch() {
  batchForm.value = { floor: Number(floorFilter.value) || 1, startNumber: 1, endNumber: 6, layout: '', area: 0 }
  batchVisible.value = true
}

async function submitBatch() {
  if (batchCount.value <= 0) {
    ElMessage.warning('结束号需要大于等于起始号')
    return
  }
  batching.value = true
  try {
    // 后端按 String.valueOf(i) 生成房间号，所以把「楼层+序号」直接作为号码区间，
    // 一次性覆盖整层（例如 301~306 对应 startNumber=301, endNumber=306）。
    const f = Number(batchForm.value.floor)
    const start = f * 100 + Number(batchForm.value.startNumber)
    const end = f * 100 + Number(batchForm.value.endNumber)
    const payload = {
      buildingId: buildingId.value,
      floor: f,
      startNumber: start,
      endNumber: end,
      layout: batchForm.value.layout || null,
      area: Number(batchForm.value.area) || null,
      status: 'IDLE'
    }
    const res = await createRoomsBatch(payload)
    const data = res.data || {}
    const created = data.created?.length ?? data.successCount ?? 0
    const conflicts = data.conflicts?.length ?? data.conflictCount ?? 0

    if (created > 0) {
      ElMessage.success(`已创建 ${created} 间` + (conflicts ? `，${conflicts} 间已存在被跳过` : ''))
    } else if (conflicts > 0) {
      ElMessage.warning(`这些房间号已存在（${conflicts} 间），未创建`)
    } else {
      ElMessage.warning('没有创建任何房间')
    }
    batchVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '批量创建失败'))
  } finally {
    batching.value = false
  }
}

/* ---------- 加载 ---------- */
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
    const [roomRes, buildingRes] = await Promise.all([
      getRooms({ buildingId: buildingId.value }),
      buildings.value.length ? Promise.resolve(null) : getBuildings()
    ])
    rooms.value = unwrapList(roomRes.data)
    if (buildingRes) buildings.value = unwrapList(buildingRes.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载房间失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-ops {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.head-btn {
  min-height: 36px;
  border-radius: 9px;
}

.head-btn--ghost {
  color: var(--text-main);
  border-color: var(--border-color);
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

/* 筛选：搜索占满一行，两个下拉并排 */
.filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.filter-keyword {
  grid-column: 1 / -1;
}

.room-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
  /* 末尾多留一截：AI 浮窗默认停在右下角，会盖住最后一张卡的操作按钮 */
  padding-bottom: calc(56px + var(--m-safe-bottom));
}

.room-card {
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 0;
  overflow: hidden;
}

.room-main {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.room-main:active {
  background: var(--accent-1);
}

.room-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.room-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.room-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  line-height: 1.6;
}

.room-tag.is-idle {
  color: var(--text-muted);
  background: var(--bg-subtle);
  border: 1px solid var(--border-color);
}

.room-tag.is-rented {
  color: var(--accent-3);
  background: var(--accent-1);
  border: 1px solid var(--accent-2);
}

.room-tag.is-maintenance {
  color: var(--warning-color);
  background: color-mix(in srgb, var(--warning-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--warning-color) 30%, transparent);
}

.room-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* 行内操作：竖排贴在卡片右侧 */
.room-ops {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 10px 12px;
  border-left: 1px solid var(--border-color);
  background: var(--bg-surface);
  flex: 0 0 auto;
}

.op-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-main);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  font-family: inherit;
  min-height: 34px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.op-btn:active {
  background: var(--accent-1);
  border-color: var(--accent-2);
}

.op-btn--danger {
  color: var(--danger-color);
  border-color: color-mix(in srgb, var(--danger-color) 30%, transparent);
}

.op-btn--danger:active {
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
}

/* 弹层表单 */
.create-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 4px;
}

.create-form :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.create-form :deep(.el-input-number) {
  width: 100%;
}

.full-width {
  width: 100%;
}

.batch-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}

.batch-preview {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--accent-3);
  word-break: break-all;
}

/* 空态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  color: var(--text-muted);
  opacity: 0.4;
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
