<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">报修工单</h1>
            <p class="m-head-sub">
              共 <span class="m-num count">{{ orders.length }}</span> 条
              <span v-if="pendingCount > 0" class="pending-hint">· {{ pendingCount }} 条待分配</span>
            </p>
          </div>
          <el-button type="primary" :icon="Plus" class="head-btn" @click="openCreate">新建</el-button>
        </div>
      </header>

      <!-- 状态筛选：横向可滚动，避免挤成两行 -->
      <div class="status-scroll">
        <div class="status-chips">
          <button
            v-for="item in statusOptions"
            :key="item.value || 'all'"
            type="button"
            class="chip"
            :class="{ active: status === item.value }"
            @click="status = item.value; load()"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <!-- 工单卡 -->
      <div v-if="pagedOrders.length > 0" class="order-list">
        <article v-for="item in pagedOrders" :key="item.id" class="m-card order-card">
          <div class="order-top">
            <div class="order-room">
              <span class="room-no m-num">{{ item.roomNumber || '-' }}</span>
              <span class="fault-type">{{ item.faultType || '-' }}</span>
            </div>
            <el-tag :type="tagType(item.status)" size="small" effect="light">
              {{ statusText(item.status) }}
            </el-tag>
          </div>

          <p class="order-desc">{{ item.description || '无故障描述' }}</p>

          <div class="order-meta">
            <span class="m-num">{{ item.orderNo || '-' }}</span>
            <span class="meta-sep">·</span>
            <span>{{ formatRelative(item.createdAt) }}</span>
          </div>

          <div class="order-actions">
            <el-button size="small" text @click="showDetail(item)">详情</el-button>
            <el-button
              v-if="item.status === 'pending'"
              size="small"
              type="primary"
              @click="openAssign(item)"
            >分配</el-button>
            <el-button
              v-if="item.status === 'processing'"
              size="small"
              type="warning"
              @click="openComplete(item)"
            >完成</el-button>
            <template v-if="item.status === 'pending_accept'">
              <el-button size="small" @click="reject(item)">驳回</el-button>
              <el-button size="small" type="success" @click="accept(item)">验收通过</el-button>
            </template>
          </div>
        </article>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><Tools /></el-icon>
        <p class="empty-title">暂无工单</p>
        <p class="empty-desc">当前筛选条件下没有报修记录。</p>
      </div>

    <el-drawer v-model="showDetailDrawer" title="工单详情" size="100%">
      <el-descriptions :column="1" border v-if="currentOrder">
        <el-descriptions-item label="工单号">{{ currentOrder.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="房间号">{{ currentOrder.roomNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ currentOrder.tenantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="故障类型">{{ currentOrder.faultType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(currentOrder.status) }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentOrder.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修结果">{{ currentOrder.repairResult || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <el-dialog v-model="showAssignDialog" title="分配维修工" width="90%">
      <el-select v-model="assignWorkerId" placeholder="请选择维修工" style="width: 100%">
        <el-option v-for="worker in enabledWorkers" :key="worker.id" :label="worker.name" :value="worker.id" />
      </el-select>
      <template #footer>
        <el-button @click="showAssignDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAssign">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCompleteDialog" title="完成维修" width="90%">
      <el-form :model="completeForm" label-width="90px">
        <el-form-item label="维修结果">
          <el-input v-model="completeForm.repairResult" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="材料费">
          <el-input-number v-model="completeForm.materialFee" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="人工费">
          <el-input-number v-model="completeForm.laborFee" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="租户承担">
          <el-switch v-model="completeForm.isTenantChargeBool" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="completeForm.feeRemark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCompleteDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitComplete">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCreate" title="发起报修" width="92%">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="楼栋" required>
          <el-select v-model="createForm.buildingId" placeholder="请选择楼栋" style="width: 100%" @change="handleCreateBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间" required>
          <el-select v-model="createForm.roomId" :disabled="!createForm.buildingId" placeholder="请选择房间" style="width: 100%">
            <el-option v-for="item in filteredRooms" :key="item.id" :label="item.roomNumber" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障类型" required>
          <el-select v-model="createForm.faultType" style="width: 100%">
            <el-option label="水电" value="水电" />
            <el-option label="门窗" value="门窗" />
            <el-option label="家电" value="家电" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>

      <div v-if="hasMoreOrders" class="load-more">
        <el-button text type="primary" @click="showMoreOrders">
          加载更多（还有 {{ remainingOrders }} 条）
        </el-button>
      </div>
  </PullRefresh>
</template>

<script setup>
import { useIncrementalList } from '@/utils/incrementalList'
import PullRefresh from '../components/PullRefresh.vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Tools } from '@element-plus/icons-vue'
import {
  getRepairOrders,
  getRepairWorkers,
  createRepairOrder,
  assignRepairWorker,
  completeRepairOrder,
  acceptRepairOrder,
  rejectRepairOrder,
  getBuildings,
  getRooms
} from '@/api'
import { unwrapList } from '@/utils/mobileApi'
import { formatDateTime, formatRelative } from '@/utils/format'
import { syncOfflineRepairs } from '@/utils/mobileOffline'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const status = ref(route.query.status || '')
const orders = ref([])

/** 待分配数量，页头提示用。 */
const pendingCount = computed(() =>
  orders.value.filter(o => o.status === 'pending').length
)
const {
  visible: pagedOrders,
  hasMore: hasMoreOrders,
  remaining: remainingOrders,
  showMore: showMoreOrders,
  reset: resetOrders
} =
  useIncrementalList(orders, 20)
const workers = ref([])
const buildings = ref([])
const rooms = ref([])
const currentOrder = ref(null)
const currentId = ref(null)
const assignWorkerId = ref(null)
const showDetailDrawer = ref(false)
const showAssignDialog = ref(false)
const showCompleteDialog = ref(false)
const showCreate = ref(false)

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待分配', value: 'pending' },
  { label: '维修中', value: 'processing' },
  { label: '待验收', value: 'pending_accept' },
  { label: '已完成', value: 'completed' }
]

const completeForm = ref({
  repairResult: '',
  materialFee: 0,
  laborFee: 0,
  isTenantChargeBool: false,
  feeRemark: ''
})

const createForm = ref({
  buildingId: null,
  roomId: null,
  faultType: '水电',
  description: '',
  reporterType: 'admin',
  images: []
})

const enabledWorkers = computed(() => workers.value.filter(item => item.status === 1))
const filteredRooms = computed(() => {
  if (!createForm.value.buildingId) return []
  return rooms.value.filter(item => Number(item.buildingId) === Number(createForm.value.buildingId))
})

function statusText(value) {
  return {
    pending: '待分配',
    processing: '维修中',
    pending_accept: '待验收',
    completed: '已完成',
    cancelled: '已取消'
  }[value] || value
}

function tagType(value) {
  return {
    pending: 'info',
    processing: 'warning',
    pending_accept: 'primary',
    completed: 'success',
    cancelled: 'danger'
  }[value] || 'info'
}

function showDetail(item) {
  currentOrder.value = item
  showDetailDrawer.value = true
}

function openAssign(item) {
  currentId.value = item.id
  assignWorkerId.value = null
  showAssignDialog.value = true
}

function openComplete(item) {
  currentId.value = item.id
  completeForm.value = {
    repairResult: '',
    materialFee: 0,
    laborFee: 0,
    isTenantChargeBool: false,
    feeRemark: ''
  }
  showCompleteDialog.value = true
}

async function submitAssign() {
  if (!assignWorkerId.value || !currentId.value) {
    ElMessage.warning('请选择维修工')
    return
  }
  saving.value = true
  try {
    await assignRepairWorker(currentId.value, { workerId: assignWorkerId.value })
    ElMessage.success('分配成功')
    showAssignDialog.value = false
    load()
  } catch (_) {
    ElMessage.error('分配失败')
  } finally {
    saving.value = false
  }
}

async function submitComplete() {
  if (!currentId.value) return
  saving.value = true
  try {
    await completeRepairOrder(currentId.value, {
      repairResult: completeForm.value.repairResult,
      materialFee: completeForm.value.materialFee,
      laborFee: completeForm.value.laborFee,
      isTenantCharge: completeForm.value.isTenantChargeBool ? 1 : 0,
      feeRemark: completeForm.value.feeRemark
    })
    ElMessage.success('提交成功')
    showCompleteDialog.value = false
    load()
  } catch (_) {
    ElMessage.error('提交失败')
  } finally {
    saving.value = false
  }
}

async function accept(item) {
  try {
    await acceptRepairOrder(item.id)
    ElMessage.success('验收完成')
    load()
  } catch (_) {
    ElMessage.error('验收失败')
  }
}

async function reject(item) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回工单', {
      inputPlaceholder: '例如：仍漏水，请复检',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    await rejectRepairOrder(item.id, { reason: value || '' })
    ElMessage.success('已驳回')
    load()
  } catch (_) {
    // ignore cancel
  }
}

async function submitCreate() {
  if (!createForm.value.roomId) {
    ElMessage.warning('请选择房间')
    return
  }
  saving.value = true
  try {
    await createRepairOrder({ ...createForm.value })
    ElMessage.success('工单创建成功')
    showCreate.value = false
    createForm.value = {
      buildingId: null,
      roomId: null,
      faultType: '水电',
      description: '',
      reporterType: 'admin',
      images: []
    }
    rooms.value = []
    load()
  } catch (_) {
    ElMessage.error('创建失败')
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const syncRes = await syncOfflineRepairs()
    if (syncRes.uploaded > 0) {
      ElMessage.success(`已自动补传${syncRes.uploaded}条离线报修记录`)
    }

    const [ordersRes, workersRes] = await Promise.all([
      getRepairOrders({ status: status.value || undefined, page: 0, size: 100 }),
      getRepairWorkers({ page: 0, size: 200 })
    ])
    orders.value = unwrapList(ordersRes.data)
    workers.value = unwrapList(workersRes.data)
  } catch (_) {
    ElMessage.error('加载工单失败')
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  showCreate.value = true
  if (buildings.value.length > 0) return
  try {
    const res = await getBuildings()
    buildings.value = unwrapList(res.data)
  } catch (_) {
    buildings.value = []
    ElMessage.error('加载楼栋失败')
  }
}

async function handleCreateBuildingChange() {
  createForm.value.roomId = null
  if (!createForm.value.buildingId) {
    rooms.value = []
    return
  }
  try {
    const res = await getRooms({ buildingId: createForm.value.buildingId, page: 0, size: 500 })
    rooms.value = unwrapList(res.data)
  } catch (_) {
    rooms.value = []
    ElMessage.error('加载房间失败')
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

.head-btn {
  min-height: 36px;
  border-radius: 9px;
  flex: 0 0 auto;
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

/* ---------- 状态筛选：横向滚动 chips ---------- */
.status-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  /* 抵消页面左右内边距，让滚动条贴边 */
  margin: 0 calc(var(--m-gutter) * -1);
  padding: 0 var(--m-gutter);
}

.status-scroll::-webkit-scrollbar {
  display: none;
}

.status-chips {
  display: inline-flex;
  gap: 8px;
  padding-bottom: 2px;
}

.chip {
  flex: 0 0 auto;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-muted);
  border-radius: 999px;
  padding: 7px 14px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.chip.active {
  background: var(--accent-3);
  border-color: var(--accent-3);
  color: #fff;
  font-weight: 600;
}

/* ---------- 工单卡 ---------- */
.order-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.order-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.order-room {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.room-no {
  font-size: 19px;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.fault-type {
  font-size: 13px;
  color: var(--text-muted);
}

.order-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-main);
  line-height: 1.55;
  /* 最多两行，超出省略 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.order-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-muted);
}

.meta-sep {
  opacity: 0.4;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.order-actions :deep(.el-button) {
  min-height: 32px;
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
