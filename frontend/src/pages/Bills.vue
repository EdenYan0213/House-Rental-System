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
            <el-button :icon="Refresh" @click="loadBills">刷新</el-button>
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
        <el-button type="primary" plain @click="reloadAll">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <transition name="el-fade-in">
          <div v-if="selectedBills.length > 0" class="selection-bar">
            <el-tag type="primary" effect="light" round>
              已选 {{ selectedBills.length }} 张 · 待收 ¥{{ money(selectedRemain) }}
            </el-tag>
            <el-button type="success" size="small" :icon="Wallet" @click="openBatchPay">
              批量收款
            </el-button>
            <el-button type="warning" size="small" :icon="Message" @click="handleBatchSms">
              批量发短信
            </el-button>
          </div>
        </transition>
      </div>

      <el-table
        ref="billsTableRef"
        :data="bills"
        row-key="id"
        border
        stripe
        v-loading="loading"
        empty-text="当前筛选条件下没有账单，可调整月份或状态后重试"
        style="width: 100%"
        @selection-change="onSelectionChange"
        @row-click="onRowClick"
      >
        <el-table-column type="selection" width="46" :selectable="row => !isBillFinal(row)" />
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
              <el-button
                v-if="canPay && !isBillFinal(row)"
                type="success"
                link
                size="small"
                :loading="quickPayingId === row.id"
                @click="quickPay(row)"
              >快收</el-button>
              <el-dropdown trigger="click" @command="cmd => handleMoreCommand(cmd, row)">
                <el-button type="primary" link size="small">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="regenerate" v-if="canGenerate && !isBillFinal(row)">重新生成</el-dropdown-item>
                    <el-dropdown-item
                      command="partial"
                      v-if="canPay && !isBillFinal(row)"
                    >部分收款…</el-dropdown-item>
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
        v-if="totalBills > pageSize"
        class="pager"
        layout="total, prev, pager, next, jumper"
        :total="totalBills"
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

    <!-- 批量收款 -->
    <el-dialog v-model="batchPayVisible" title="批量收款" width="560px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px">
        <template #title>
          将对所选 {{ selectedBills.length }} 张账单按「剩余应收」全额收款，合计
          <b>¥{{ money(selectedRemain) }}</b>
        </template>
      </el-alert>

      <el-table :data="selectedBills" size="small" border max-height="240">
        <el-table-column label="房间" width="100">
          <template #default="{ row }">{{ row.roomNumber || '-' }}</template>
        </el-table-column>
        <el-table-column prop="billMonth" label="月份" width="100" />
        <el-table-column label="待收" width="120" align="right">
          <template #default="{ row }">¥{{ money(remainOf(row)) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-form :model="batchPayForm" label-width="90px" style="margin-top: 14px">
        <el-form-item label="收款方式" required>
          <el-select v-model="batchPayForm.paymentMethod" style="width: 100%">
            <el-option label="现金" value="CASH" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="微信" value="WECHAT" />
            <el-option label="银行转账" value="BANK" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchPayForm.remark" placeholder="可选" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="batchPayVisible = false">取消</el-button>
        <el-button type="success" :loading="batchPaying" @click="submitBatchPay">
          确认收款 ¥{{ money(selectedRemain) }}
        </el-button>
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
import { Download, Plus, DocumentAdd, Wallet, Refresh, Message } from '@element-plus/icons-vue'
import {
  getBills,
  getBillSummary,
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
  canVoidBill,
  batchSendBillSms,
  getErrorMessage
} from '@/api'
import { billStatusText, billStatusTag } from '@/utils/labels'
import { showUndoToast } from '../utils/undo'
import { useBatchPayment } from '@/composables/useBatchPayment'
import { useRoomBillGenerate } from '@/composables/useRoomBillGenerate'
import {
  isBillFinal,
  remainOf,
  formatMoneyValue,
  isOverdue,
  overdueDaysOf as overdueDays,
  hasMeterData
} from '../utils/bills'

const router = useRouter()
const route = useRoute()

const canGenerate = computed(() => canGenerateBill())
const canPay = computed(() => canRecordPayment())
const canVoid = computed(() => canVoidBill())

const loading = ref(false)
const paying = ref(false)
const regenerating = ref(false)
const bills = ref([])
// 后端分页：content 是当前页数据，total 是筛选后的总条数
const currentPage = ref(1)
const pageSize = ref(20)
const totalBills = ref(0)
// 财务汇总由 /api/bills/summary 单独统计，不受分页影响
const summaryData = ref({ total: 0, paid: 0, unpaid: 0, rate: 0, activeCount: 0, paidCount: 0 })
const paymentForm = reactive({
  paidAmount: 0,
  paymentMethod: 'CASH',
  remark: ''
})

const paymentRules = {
  paidAmount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择收款方式', trigger: 'change' }]
}

const waterUnit = ref(null)
const electricUnit = ref(null)

// 财务汇总由 /api/bills/summary 计算，不受分页影响
const summary = computed(() => summaryData.value)

/** 读取水电单价，用于顶部提示；失败不阻塞页面。 */
async function loadUnits() {
  try {
    const res = await getFeeItems()
    const items = res.data || []
    const w = items.find(i => i.category === 'WATER' || (i.name || '').includes('水'))
    const e = items.find(i => i.category === 'ELECTRIC' || (i.name || '').includes('电'))
    waterUnit.value = w ? w.unitPrice : null
    electricUnit.value = e ? e.unitPrice : null
  } catch (e) {
    // 单价只是辅助信息，失败时保持占位符 '-' 即可
    waterUnit.value = null
    electricUnit.value = null
  }
}

const queryForm = reactive({ month: '', status: '' })
const roomIdFilter = ref('')
const paymentVisible = ref(false)
const paymentRef = ref(null)
const currentBill = ref(null)
const regenerateVisible = ref(false)
const regenerateBillId = ref(null)
// 状态展示统一走 utils/labels，避免各页面各自维护映射
function getStatusType(status) {
  return billStatusTag(status)
}

function getStatusText(status) {
  return billStatusText(status)
}

function buildQueryParams() {
  const params = {}
  if (queryForm.month) params.month = queryForm.month
  if (queryForm.status) params.status = queryForm.status
  if (roomIdFilter.value) params.roomId = Number(roomIdFilter.value)
  return params
}

async function loadBills() {
  loading.value = true
  try {
    const params = {
      ...buildQueryParams(),
      // el-pagination 是 1 起始，后端约定 0 起始
      page: currentPage.value - 1,
      size: pageSize.value
    }
    const res = await getBills(params)
    bills.value = res.data?.content || []
    totalBills.value = res.data?.totalElements || 0
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '账单加载失败'))
  } finally {
    loading.value = false
  }
}

function loadSummary() {
  return getBillSummary(buildQueryParams())
    .then(res => {
      summaryData.value = res.data || summaryData.value
    })
    .catch(() => {
      // 汇总失败不阻塞列表，保留上一次数值
    })
}

/** 首次查询 / 重置 / 筛选变化：回到第 1 页并同时刷新汇总 */
async function reloadAll() {
  currentPage.value = 1
  await Promise.all([loadBills(), loadSummary()])
}

/**
 * 点击整行 → 切换勾选。
 *
 * <p>已结清/已作废的账单不可选（selection 列有 :selectable 限制），
 * 这里必须复用同一判断，否则行点击能选上本不该选的账单，
 * 后续批量收款就会出错。行内按钮同样放行。
 */
function onRowClick(row, column, event) {
  const el = event?.target
  if (el && el.closest('button, a, input, textarea, .el-checkbox, .el-link, .el-dropdown')) {
    return
  }
  if (isBillFinal(row)) return
  billsTableRef.value?.toggleRowSelection(row)
}

function handlePageChange(page) {
  currentPage.value = page
  loadBills()
}

function resetQuery() {
  queryForm.month = ''
  queryForm.status = ''
  roomIdFilter.value = ''
  reloadAll()
}

const generating = ref(false)
const sendingSms = ref(false)
// 行内收款中的账单 ID，用于按钮 loading
const quickPayingId = ref(null)
const billsTableRef = ref(null)

// 批量收款：逻辑在 composable，变量名对齐模板
const {
  visible: batchPayVisible,
  paying: batchPaying,
  selectedBills,
  form: batchPayForm,
  selectedRemain,
  onSelectionChange,
  open: openBatchPay,
  submit: submitBatchPay
} = useBatchPayment(billsTableRef, async () => {
  await Promise.all([loadBills(), loadSummary()])
})

// 按房间生成账单：逻辑在 composable，变量名对齐模板
const {
  visible: roomGenerateVisible,
  generating: generatingRoom,
  buildingOptions,
  floorOptions,
  roomOptions: roomOptionsFiltered,
  form: roomGenerateForm,
  formatRoomOption,
  onBuildingChange,
  onFloorChange,
  open: openRoomGenerateRaw,
  submit: submitRoomGenerate
} = useRoomBillGenerate(async () => {
  await Promise.all([loadBills(), loadSummary()])
})

// 模板里 openRoomGenerate 不带参数，这里包一层传入当前筛选月份
function openRoomGenerate() {
  return openRoomGenerateRaw(queryForm.month)
}

// 批量生成失败明细（房间 + 原因），允许逐条重试
const generationFailures = ref([])
const generationVisible = ref(false)

/** 模板里大量调用 money()，实现统一在 utils/bills */
function money(v) {
  return formatMoneyValue(v)
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
    await Promise.all([loadBills(), loadSummary()])
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '账单生成失败'))
  } finally {
    generating.value = false
  }
}

// 行内「更多」菜单
function handleMoreCommand(cmd, row) {
  if (cmd === 'regenerate') return openRegenerate(row)
  if (cmd === 'partial') return openPayment(row)
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

/**
 * 批量发送账单短信。
 *
 * <p>先确认条数，再逐条发送；后端会汇总成功/失败并返回明细，
 * 有失败时把原因列出来，便于逐个排查（如未绑定手机号）。
 */
async function handleBatchSms() {
  const ids = selectedBills.value.map(b => b.id)
  if (ids.length === 0) {
    ElMessage.warning('请先选择要发送短信的账单')
    return
  }

  try {
    await ElMessageBox.confirm(
      `将向所选 ${ids.length} 张账单的租户发送催缴短信，是否继续？`,
      '批量发送短信',
      { confirmButtonText: '发送', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  sendingSms.value = true
  try {
    const res = await batchSendBillSms(ids)
    const data = res.data || {}
    const success = Number(data.successCount || 0)
    const fail = Number(data.failCount || 0)

    if (fail === 0) {
      ElMessage.success(`短信已发送，共 ${success} 张`)
    } else {
      const detail = (data.failures || [])
        .map(f => `账单#${f.billId}${f.roomNumber ? '（' + f.roomNumber + '）' : ''}：${f.reason}`)
        .join('\n')
      await ElMessageBox.alert(detail, `成功 ${success} 张，失败 ${fail} 张`, { type: 'warning' })
    }

    billsTableRef.value?.clearSelection?.()
    selectedBills.value = []
    await loadBills()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '批量发送失败'))
  } finally {
    sendingSms.value = false
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

/**
 * 行内快速收款：按剩余应收全额入账，一步完成。
 *
 * <p>日常最高频的操作就是「这单收齐了」，走弹窗填表太慢；
 * 需要部分收款/改收款方式时，从「更多 → 部分收款」进弹窗。
 */
async function quickPay(row) {
  const remain = remainOf(row)
  if (remain <= 0) {
    ElMessage.warning('该账单已无待收金额')
    return
  }

  quickPayingId.value = row.id
  try {
    await recordPayment(row.id, {
      paidAmount: remain,
      paymentMethod: 'CASH',
      remark: '快速收款'
    })
    ElMessage.success(`已收款 ¥${money(remain)}`)

    // 收款后状态会变，刷新当前页与汇总
    await Promise.all([loadBills(), loadSummary()])
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '收款失败'))
  } finally {
    quickPayingId.value = null
  }
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

  reloadAll()
  loadUnits()
})
</script>

<style scoped>
.page {
  padding: 0;
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
  font-size: var(--title-page-size);
  font-weight: var(--title-page-weight);
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

.selection-bar {
  display: flex;
  align-items: center;
  gap: 8px;
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
