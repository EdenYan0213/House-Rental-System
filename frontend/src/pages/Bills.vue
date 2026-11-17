<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div class="title-block">
            <span class="page-title">账单管理</span>
            <span class="page-subtitle">
              水费 ¥{{ waterUnit != null ? Number(waterUnit).toFixed(2) : '-' }} / 电费
              ¥{{ electricUnit != null ? Number(electricUnit).toFixed(2) : '-' }}
            </span>
          </div>
          <div class="page-actions">
            <el-button :icon="Download" @click="handleExportCsv">导出 CSV</el-button>
            <el-button type="primary" plain :icon="Plus" @click="openRoomGenerate" v-if="canGenerate">
              单房间补生成
            </el-button>
            <el-button type="primary" :icon="DocumentAdd" :loading="generating" @click="handleGenerate" v-if="canGenerate">
              生成本月账单
            </el-button>
          </div>
        </div>
      </template>

      <!-- 汇总条：财务视角的四个关键数字 -->
      <div class="summary-row">
        <div class="summary-item">
          <div class="summary-label">本期应收</div>
          <div class="summary-value">¥{{ money(summary.total) }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">已收金额</div>
          <div class="summary-value tone-success">¥{{ money(summary.paid) }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">待收金额</div>
          <div class="summary-value tone-warning">¥{{ money(summary.unpaid) }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">收租率</div>
          <div class="summary-value">
            {{ summary.rate }}%
            <span class="summary-meta">{{ summary.paidCount }} / {{ summary.activeCount }} 张已结清</span>
          </div>
        </div>
      </div>

      <div class="toolbar">
        <el-input v-model="queryForm.month" placeholder="账单月份，如 2026-10" clearable class="toolbar-search" />
        <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="待付款" value="PENDING" />
          <el-option label="部分付款" value="PARTIAL" />
          <el-option label="已付清" value="PAID" />
          <el-option label="已作废" value="VOID" />
        </el-select>
        <el-button type="primary" plain @click="loadBills">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table
        :data="bills"
        border
        stripe
        v-loading="loading"
        empty-text="当前筛选条件下没有账单，可调整月份或状态后重试"
        style="width: 100%"
      >
        <el-table-column label="账单月份" width="110" sortable prop="billMonth" />
        <el-table-column label="房间" min-width="130">
          <template #default="{ row }">
            <div class="room-cell">
              <span class="room-no">{{ row.roomNumber || '-' }}</span>
              <span class="room-building">{{ row.buildingName || '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" width="100" sortable />
        <el-table-column label="金额" width="150" sortable prop="totalAmount" align="right">
          <template #default="{ row }">
            <div class="amount-cell">
              <span class="amount-total">¥{{ money(row.totalAmount) }}</span>
              <span class="amount-paid">已付 ¥{{ money(row.paidAmount) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
            <div v-if="isOverdue(row)" class="overdue-hint">逾期 {{ overdueDays(row) }} 天</div>
          </template>
        </el-table-column>
        <el-table-column label="水电用量" width="130" align="center">
          <template #default="{ row }">
            <div v-if="hasMeterData(row)" class="meter-cell">
              <span>水 {{ row.waterUsage }}</span>
              <span>电 {{ row.electricUsage }}</span>
            </div>
            <el-tooltip v-else content="该账单没有水电读数，可能是当月未抄表" placement="top">
              <el-tag type="warning" size="small" effect="plain">缺读数</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <div class="bill-actions">
              <el-button type="primary" link size="small" @click="viewDetail(row)">详情</el-button>
              <el-button type="success" link size="small" @click="openPayment(row)" v-if="canPay && !isBillFinal(row)">收款</el-button>
              <el-dropdown trigger="click" @command="cmd => handleMoreCommand(cmd, row)">
                <el-button type="primary" link size="small">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="regenerate" v-if="canGenerate && !isBillFinal(row)">重新生成</el-dropdown-item>
                    <el-dropdown-item command="sms">发送账单短信</el-dropdown-item>
                    <el-dropdown-item command="void" divided v-if="canVoid && !isBillFinal(row)">作废账单</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="allBills.length > pageSize"
        class="pager"
        layout="total, prev, pager, next, jumper"
        :total="allBills.length"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 收款弹窗 -->
    <el-dialog v-model="paymentVisible" title="收款" width="500px">
      <el-form :model="paymentForm" :rules="paymentRules" ref="paymentRef" label-width="100px">
        <el-form-item label="账单金额">
          <span class="amount">¥{{ currentBill?.totalAmount?.toFixed(2) }}</span>
        </el-form-item>
        <el-form-item label="已付金额">
          <span>¥{{ currentBill?.paidAmount?.toFixed(2) || '0.00' }}</span>
        </el-form-item>
        <el-form-item label="应付金额">
          <span class="amount">¥{{ (currentBill?.totalAmount - currentBill?.paidAmount)?.toFixed(2) }}</span>
        </el-form-item>
        <el-form-item label="收款金额" prop="paidAmount">
          <el-input-number v-model="paymentForm.paidAmount" :min="0" :precision="2" :step="100" />
        </el-form-item>
        <el-form-item label="收款方式" prop="paymentMethod">
          <el-select v-model="paymentForm.paymentMethod" placeholder="请选择">
            <el-option label="现金" value="CASH" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="微信" value="WECHAT" />
            <el-option label="银行转账" value="BANK" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="paymentForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPayment" :loading="paying">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 批量生成失败清单：可逐条或全部重试 -->
    <el-dialog v-model="generationVisible" title="部分账单生成失败" width="620px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 14px">
        <template #title>
          以下 {{ generationFailures.length }} 个房间未能生成账单，通常是因为当月没有水电读数。
        </template>
      </el-alert>
      <el-table :data="generationFailures" size="small" border max-height="360">
        <el-table-column label="房间" width="110">
          <template #default="{ row }">{{ row.roomNumber || row.roomId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contractNo" label="合同编号" width="170" />
        <el-table-column prop="reason" label="失败原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="retryFailure(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="generationVisible = false">稍后处理</el-button>
        <el-button type="primary" :loading="generating" @click="retryAllFailures">全部重试</el-button>
      </template>
    </el-dialog>

    <!-- 重新生成账单弹窗 -->
    <el-dialog v-model="regenerateVisible" title="重新生成账单" width="400px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 20px">
        确定要重新生成该账单吗？现有账单将被覆盖。
      </el-alert>
      <template #footer>
        <el-button @click="regenerateVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRegenerate" :loading="regenerating">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 按房间生成账单 -->
    <el-dialog v-model="roomGenerateVisible" title="按房间生成账单" width="500px">
      <el-form label-width="100px">
        <el-form-item label="楼栋" required>
          <el-select
            v-model="roomGenerateForm.buildingId"
            placeholder="请选择楼栋"
            style="width: 100%"
            filterable
            @change="onBuildingChange"
          >
            <el-option v-for="b in buildingOptions" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层" required>
          <el-select
            v-model="roomGenerateForm.floor"
            placeholder="请选择楼层"
            style="width: 100%"
            :disabled="!roomGenerateForm.buildingId"
            @change="onFloorChange"
          >
            <el-option v-for="f in floorOptions" :key="f" :label="`${f}层`" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="账单月份">
          <el-input v-model="roomGenerateForm.month" placeholder="不填默认上月，如 2026-03" />
        </el-form-item>
        <el-form-item label="选择房间" required>
          <el-select
            v-model="roomGenerateForm.roomId"
            placeholder="请选择房间"
            filterable
            style="width: 100%"
            :disabled="!roomGenerateForm.floor"
          >
            <el-option
              v-for="room in roomOptionsFiltered"
              :key="room.id"
              :label="formatRoomOption(room)"
              :value="room.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomGenerateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generatingRoom" @click="submitRoomGenerate">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, DocumentAdd } from '@element-plus/icons-vue'
import {
  getBills,
  generateBill,
  generateBillForRoom,
  regenerateBill,
  voidBill,
  recordPayment,
  sendBillSms,
  exportBillsCsv,
  getFeeItems,
  getBuildings,
  getRooms,
  canGenerateBill,
  canRecordPayment,
  canVoidBill
} from '@/api'
import { billStatusText, billStatusTag, paymentMethodText } from '@/utils/labels'
import { getErrorMessage } from '@/api'
import { showUndoToast } from '../utils/undo'

const router = useRouter()
const route = useRoute()

const canGenerate = computed(() => canGenerateBill())
const canPay = computed(() => canRecordPayment())
const canVoid = computed(() => canVoidBill())

const loading = ref(false)
const paying = ref(false)
const regenerating = ref(false)
const bills = ref([])
// 后端 /api/bills 返回全量列表（不分页），这里做前端分页保证大月份数据也能浏览
const allBills = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const queryForm = reactive({ month: '', status: '' })
const roomIdFilter = ref('')
const paymentVisible = ref(false)
const paymentRef = ref(null)
const currentBill = ref(null)
const regenerateVisible = ref(false)
const regenerateBillId = ref(null)
const roomGenerateVisible = ref(false)
const generatingRoom = ref(false)
const buildingOptions = ref([])
const floorOptions = ref([])
const roomOptions = ref([])
const roomOptionsFiltered = ref([])
const roomGenerateForm = reactive({
  buildingId: null,
  floor: null,
  roomId: null,
  month: ''
})

const paymentForm = reactive({
  paidAmount: 0,
  paymentMethod: 'CASH',
  remark: ''
})

const waterUnit = ref(null)
const electricUnit = ref(null)
const generating = ref(false)
// 批量生成失败明细（房间 + 原因），允许逐条重试
const generationFailures = ref([])
const generationVisible = ref(false)

// 财务汇总：仅统计未作废账单
const summary = computed(() => {
  const valid = allBills.value.filter(b => String(b.status || '').toUpperCase() !== 'VOID')
  const total = valid.reduce((sum, b) => sum + (Number(b.totalAmount) || 0), 0)
  const paid = valid.reduce((sum, b) => sum + (Number(b.paidAmount) || 0), 0)
  const paidCount = valid.filter(b => isBillFinal(b)).length
  return {
    total,
    paid,
    unpaid: Math.max(total - paid, 0),
    rate: total > 0 ? Math.round((paid / total) * 100) : 0,
    activeCount: valid.length,
    paidCount
  }
})

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 到期日缺失时无法判断逾期，返回 false 避免误标
function isOverdue(row) {
  if (isBillFinal(row) || !row.dueDate) return false
  return new Date(row.dueDate) < new Date(new Date().toDateString())
}

function overdueDays(row) {
  if (!row.dueDate) return 0
  const diff = new Date(new Date().toDateString()) - new Date(new Date(row.dueDate).toDateString())
  return Math.max(Math.floor(diff / 86400000), 0)
}

// 水电用量都为 0/空时视为缺读数
function hasMeterData(row) {
  const w = Number(row.waterUsage || 0)
  const e = Number(row.electricUsage || 0)
  return w > 0 || e > 0
}

async function loadUnits() {
  try {
    const res = await getFeeItems()
    const items = res.data || []
    const w = items.find(i => i.category === 'WATER' || i.name.includes('水'))
    const e = items.find(i => i.category === 'ELECTRIC' || i.name.includes('电'))
    waterUnit.value = w ? w.unitPrice : null
    electricUnit.value = e ? e.unitPrice : null
  } catch (err) {
    // ignore
  }
}

const paymentRules = {
  paidAmount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择收款方式', trigger: 'change' }]
}

// 状态展示统一走 utils/labels，避免各页面各自维护映射
function getStatusType(status) {
  return billStatusTag(status)
}

function getStatusText(status) {
  return billStatusText(status)
}

// 终态账单不可再收款/作废/重新生成
function isBillFinal(row) {
  const s = String(row?.status || '').trim().toUpperCase()
  return ['PAID', 'VOID', 'OBSOLETE'].includes(s)
}

async function loadBills() {
  loading.value = true
  try {
    const params = {}
    if (queryForm.month) params.month = queryForm.month
    if (queryForm.status) params.status = queryForm.status
    if (roomIdFilter.value) params.roomId = Number(roomIdFilter.value)
    const res = await getBills(params)
    allBills.value = res.data || []
    currentPage.value = 1
    applyPaging()
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '账单加载失败'))
  } finally {
    loading.value = false
  }
}

function applyPaging() {
  const start = (currentPage.value - 1) * pageSize.value
  bills.value = allBills.value.slice(start, start + pageSize.value)
}

function handlePageChange(page) {
  currentPage.value = page
  applyPaging()
}

function resetQuery() {
  queryForm.month = ''
  queryForm.status = ''
  roomIdFilter.value = ''
  loadBills()
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generateBill(queryForm.month)
    const result = res.data || {}
    const failures = result.failures || []
    if (failures.length > 0) {
      // 有失败项：展示清单，用户可以逐条补生成
      generationFailures.value = failures
      generationVisible.value = true
      ElMessage.warning(`成功 ${result.successCount || 0} 张，失败 ${failures.length} 张`)
    } else {
      generationFailures.value = []
      ElMessage.success(`账单生成成功，共 ${result.successCount ?? result.created ?? 0} 张`)
    }
    await loadBills()
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '账单生成失败'))
  }
}

// 行内「更多」菜单
function handleMoreCommand(cmd, row) {
  if (cmd === 'regenerate') return openRegenerate(row)
  if (cmd === 'sms') return handleSendSms(row)
  if (cmd === 'void') return handleVoid(row)
}

/**
 * 单房间补生成。失败清单里的「重试」和顶部「单房间补生成」都走这里。
 * 月份优先取失败项自带的 billMonth，其次取筛选条件。
 */
async function retryGenerateForRoom(roomId, month) {
  if (!roomId) {
    ElMessage.warning('该记录未关联房间')
    return false
  }
  try {
    await generateBillForRoom(roomId, month || queryForm.month || '')
    return true
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '补生成失败'))
    return false
  }
}

async function retryFailure(item) {
  const ok = await retryGenerateForRoom(item.roomId, item.billMonth)
  if (ok) {
    ElMessage.success(`房间 ${item.roomNumber || item.roomId} 补生成成功`)
    generationFailures.value = generationFailures.value.filter(f => f !== item)
    if (generationFailures.value.length === 0) {
      generationVisible.value = false
    }
    await loadBills()
  }
}

async function retryAllFailures() {
  const pending = [...generationFailures.value]
  let okCount = 0
  for (const item of pending) {
    const ok = await retryGenerateForRoom(item.roomId, item.billMonth)
    if (ok) {
      okCount++
      generationFailures.value = generationFailures.value.filter(f => f !== item)
    }
  }
  if (okCount > 0) {
    ElMessage.success(`已补生成 ${okCount} 张`)
    await loadBills()
  }
  if (generationFailures.value.length === 0) {
    generationVisible.value = false
  }
}

function formatRoomOption(room) {
  const building = room?.buildingName ? `${room.buildingName}-` : ''
  return `${building}${room?.roomNumber || room?.id}`
}

async function loadRoomOptions() {
  try {
    const params = { size: 1000 }
    if (roomGenerateForm.buildingId) {
      params.buildingId = roomGenerateForm.buildingId
    }
    if (roomGenerateForm.floor !== null && roomGenerateForm.floor !== undefined) {
      params.floor = roomGenerateForm.floor
    }
    const res = await getRooms(params)
    roomOptions.value = res.data?.content || res.data || []
  } catch (err) {
    roomOptions.value = []
  }
}

async function loadBuildings() {
  try {
    const res = await getBuildings()
    buildingOptions.value = res.data || []
  } catch (err) {
    buildingOptions.value = []
  }
}

function refreshFloorOptions() {
  const set = new Set((roomOptions.value || []).map(r => r.floor).filter(v => v !== null && v !== undefined))
  floorOptions.value = Array.from(set).sort((a, b) => a - b)
}

function refreshRoomOptionsFiltered() {
  if (roomGenerateForm.floor === null || roomGenerateForm.floor === undefined) {
    roomOptionsFiltered.value = []
    return
  }
  roomOptionsFiltered.value = (roomOptions.value || []).filter(r => r.floor === roomGenerateForm.floor)
}

async function onBuildingChange() {
  roomGenerateForm.floor = null
  roomGenerateForm.roomId = null
  roomOptionsFiltered.value = []
  await loadRoomOptions()
  refreshFloorOptions()
}

async function onFloorChange() {
  roomGenerateForm.roomId = null
  // 同楼栋下楼层切换时直接过滤本地数据，避免重复请求
  refreshRoomOptionsFiltered()
}

async function openRoomGenerate() {
  roomGenerateForm.buildingId = null
  roomGenerateForm.floor = null
  roomGenerateForm.roomId = null
  roomGenerateForm.month = queryForm.month || ''
  roomOptions.value = []
  floorOptions.value = []
  roomOptionsFiltered.value = []
  await loadBuildings()
  await loadRoomOptions()
  roomGenerateVisible.value = true
}

async function submitRoomGenerate() {
  if (!roomGenerateForm.buildingId) {
    ElMessage.warning('请选择楼栋')
    return
  }
  if (roomGenerateForm.floor === null || roomGenerateForm.floor === undefined) {
    ElMessage.warning('请选择楼层')
    return
  }
  if (!roomGenerateForm.roomId) {
    ElMessage.warning('请选择房间')
    return
  }

  generatingRoom.value = true
  try {
    await generateBillForRoom(roomGenerateForm.roomId, roomGenerateForm.month)
    ElMessage.success('按房间生成账单成功')
    roomGenerateVisible.value = false
    await loadBills()
  } catch (err) {
    ElMessage.error(err?.response?.data || err?.message || '按房间生成失败')
  } finally {
    generatingRoom.value = false
  }
}

async function handleExportCsv() {
  try {
    const params = {}
    if (queryForm.month) params.month = queryForm.month
    if (queryForm.status) params.status = queryForm.status
    if (roomIdFilter.value) params.roomId = Number(roomIdFilter.value)
    const res = await exportBillsCsv(params)

    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `bills-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('账单CSV导出成功')
  } catch (err) {
    ElMessage.error('账单CSV导出失败')
  }
}

function viewDetail(row) {
  router.push(`/bills/${row.id}`)
}

function openPayment(row) {
  currentBill.value = row
  paymentForm.paidAmount = row.totalAmount - row.paidAmount
  paymentForm.paymentMethod = 'CASH'
  paymentForm.remark = ''
  paymentVisible.value = true
}

function openRegenerate(row) {
  regenerateBillId.value = row.id
  regenerateVisible.value = true
}

async function submitRegenerate() {
  try {
    await regenerateBill(regenerateBillId.value)
    ElMessage.success('账单重新生成成功')
    regenerateVisible.value = false
    loadBills()
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '重新生成失败'))
  }
}

async function handleVoid(row) {
  try {
    await ElMessageBox.confirm('确定作废该账单吗？操作后可在短时间内撤销。', '确认作废', {
      type: 'warning'
    })
    const res = await voidBill(row.id)
    ElMessage.success('账单已作废')
    showUndoToast(res?.data, loadBills)
    await loadBills()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(getErrorMessage(err, '作废失败'))
    }
  }
}

async function submitPayment() {
  if (!paymentRef.value) return
  
  await paymentRef.value.validate(async (valid) => {
    if (!valid) return
    
    paying.value = true
    try {
      await recordPayment(currentBill.value.id, paymentForm)
      ElMessage.success('收款成功')
      paymentVisible.value = false
      loadBills()
    } catch (err) {
      ElMessage.error(getErrorMessage(err, '收款失败'))
    } finally {
      paying.value = false
    }
  })
}

async function handleSendSms(row) {
  try {
    const res = await sendBillSms(row.id)
    const data = res?.data || {}
    const latestTitle = data.latestMessageTitle || '-'
    const latestContent = data.latestMessageContent || '-'
    const debugTag = data.isDebug ? '（模拟发送）' : ''
    await ElMessageBox.alert(
      `结果: ${data.message || '发送完成'}${debugTag}\n最新消息: ${latestTitle}\n${latestContent}`,
      '账单短信发送结果',
      {
        confirmButtonText: '知道了',
        type: 'success'
      }
    )
  } catch (err) {
    const data = err?.response?.data || {}
    const latestTitle = data.latestMessageTitle || '-'
    const latestContent = data.latestMessageContent || '-'
    await ElMessageBox.alert(
      `发送失败: ${data.failReason || data.message || err.message || '未知错误'}\n最新消息: ${latestTitle}\n${latestContent}`,
      '账单短信发送结果',
      {
        confirmButtonText: '知道了',
        type: 'error'
      }
    )
  }
}

onMounted(() => {
  const routeMonth = String(route.query.month || '').trim()
  const routeStatus = String(route.query.status || '').trim()
  const routeRoomId = String(route.query.roomId || '').trim()
  if (routeMonth) queryForm.month = routeMonth
  if (routeStatus) queryForm.status = routeStatus
  if (routeRoomId) roomIdFilter.value = routeRoomId

  loadBills()
  loadUnits()
})
</script>

<style scoped>
.page {
  padding: 20px;
}

.page-card {
  border-radius: 8px;
}

.page-card :deep(.el-card__header) {
  padding: 14px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.title-block {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.page-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-main);
}

.page-subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.page-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* 财务汇总条 */
.summary-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}

.summary-item {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--border-color);
}

.summary-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.summary-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.summary-value.tone-success {
  color: var(--success-color);
}

.summary-value.tone-warning {
  color: #d97706;
}

.summary-meta {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: 240px;
}

/* 表格单元格 */
.room-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.room-no {
  font-weight: 600;
  color: var(--text-main);
}

.room-building {
  font-size: 12px;
  color: var(--text-muted);
}

.amount-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  align-items: flex-end;
}

.amount-total {
  font-weight: 600;
  color: var(--text-main);
}

.amount-paid {
  font-size: 12px;
  color: var(--text-muted);
}

.meter-cell {
  display: flex;
  gap: 8px;
  justify-content: center;
  font-size: 12px;
  color: var(--text-muted);
}

.overdue-hint {
  margin-top: 2px;
  font-size: 11px;
  color: var(--danger-color);
}

.bill-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.amount {
  font-size: 18px;
  font-weight: bold;
  color: var(--el-color-primary);
}

/* keep table cells single-line and actions inline */
.el-table .cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bill-actions .el-button {
  margin-right: 6px;
  white-space: nowrap;
}
</style>

<style scoped>
/* stronger rules to prevent wrapping in bills table */
.el-table .cell,
.el-table .el-table__cell,
.el-table td .cell {
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
}
.bill-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  white-space: nowrap;
}
.bill-actions .el-button {
  margin-right: 0;
  padding: 4px 8px;
  font-size: 12px;
}
</style>
