<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <div class="title">报修工单</div>
      <el-button type="primary" plain @click="openCreate">新建</el-button>
    </div>

    <el-radio-group v-model="status" class="status" @change="load" size="small">
      <el-radio-button v-for="item in statusOptions" :key="item.value || 'all'" :label="item.value">
        {{ item.label }}
      </el-radio-button>
    </el-radio-group>

    <el-card v-for="item in orders" :key="item.id" class="panel" shadow="never">
      <div class="line top">
        <div>{{ item.roomNumber || '-' }} · {{ item.faultType || '-' }}</div>
        <el-tag :type="tagType(item.status)">{{ statusText(item.status) }}</el-tag>
      </div>
      <div class="line muted">工单号：{{ item.orderNo || '-' }}</div>
      <div class="line muted">报修时间：{{ item.createdAt || '-' }}</div>
      <div class="line">{{ item.description || '-' }}</div>
      <div class="actions">
        <el-button size="small" @click="showDetail(item)">详情</el-button>
        <el-button size="small" type="primary" v-if="item.status === 'pending'" @click="openAssign(item)">分配</el-button>
        <el-button size="small" type="warning" v-if="item.status === 'processing'" @click="openComplete(item)">完成</el-button>
        <el-button size="small" type="success" v-if="item.status === 'pending_accept'" @click="accept(item)">验收通过</el-button>
        <el-button size="small" v-if="item.status === 'pending_accept'" @click="reject(item)">驳回</el-button>
      </div>
    </el-card>

    <el-empty v-if="!loading && orders.length === 0" description="暂无工单" />

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
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
import { syncOfflineRepairs } from '@/utils/mobileOffline'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const status = ref(route.query.status || '')
const orders = ref([])
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
  font-size: 20px;
  font-weight: 700;
  color: #1d3266;
}

.status {
  width: 100%;
  margin-bottom: 10px;
}

.panel {
  margin-bottom: 10px;
  border: 1px solid #e8efff;
}

.line {
  margin-bottom: 7px;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted {
  color: #7382a1;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
