<template>
  <div class="bills-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>账单管理</span>
          <div style="display:flex;align-items:center;gap:12px">
            <div>
              <small>水费单价: </small>
              <strong v-if="waterUnit != null">¥{{ Number(waterUnit).toFixed(2) }}</strong>
              <span v-else>-</span>
            </div>
            <div>
              <small>电费单价: </small>
              <strong v-if="electricUnit != null">¥{{ Number(electricUnit).toFixed(2) }}</strong>
              <span v-else>-</span>
            </div>
            <el-button type="primary" @click="handleGenerate" v-if="canGenerate">生成账单</el-button>
          </div>
        </div>
      </template>
      
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="账单月份">
          <el-input v-model="queryForm.month" placeholder="2024-01" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待付款" value="PENDING" />
            <el-option label="部分付款" value="PARTIAL" />
            <el-option label="已付清" value="PAID" />
            <el-option label="已作废" value="VOID" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadBills">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="bills" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="billMonth" label="账单月份" width="120" />
        <el-table-column label="房间" width="120">
          <template #default="{ row }">
            {{ row.roomNumber || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="楼栋" width="120">
          <template #default="{ row }">
            {{ row.buildingName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" width="120" />
        <el-table-column prop="totalAmount" label="总金额" width="100">
          <template #default="{ row }">
            ¥{{ row.totalAmount?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已付" width="100">
          <template #default="{ row }">
            ¥{{ row.paidAmount?.toFixed(2) || '0.00' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="水量" width="100">
          <template #default="{ row }">
            {{ row.waterUsage != null ? row.waterUsage : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="电量" width="100">
          <template #default="{ row }">
            {{ row.electricUsage != null ? row.electricUsage : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column label="操作" fixed="right" width="280">
          <template #default="{ row }">
            <div class="bill-actions">
              <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
              <el-button type="info" link @click="handleSendSms(row)">发短信</el-button>
              <el-button type="warning" link @click="openRegenerate(row)" v-if="canGenerate && !isBillFinal(row)">重新生成</el-button>
              <el-button type="success" link @click="openPayment(row)" v-if="canPay && !isBillFinal(row)">收款</el-button>
              <el-button type="danger" link @click="handleVoid(row)" v-if="canVoid && !isBillFinal(row)">作废</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBills,
  generateBill,
  regenerateBill,
  voidBill,
  recordPayment,
  sendBillSms,
  getFeeItems,
  canGenerateBill,
  canRecordPayment,
  canVoidBill
} from '@/api'
import { showUndoToast } from '../utils/undo'

const router = useRouter()

const canGenerate = computed(() => canGenerateBill())
const canPay = computed(() => canRecordPayment())
const canVoid = computed(() => canVoidBill())

const loading = ref(false)
const paying = ref(false)
const regenerating = ref(false)
const bills = ref([])
const queryForm = reactive({ month: '', status: '' })
const paymentVisible = ref(false)
const paymentRef = ref(null)
const currentBill = ref(null)
const regenerateVisible = ref(false)
const regenerateBillId = ref(null)

const paymentForm = reactive({
  paidAmount: 0,
  paymentMethod: 'CASH',
  remark: ''
})

const waterUnit = ref(null)
const electricUnit = ref(null)

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

function getStatusType(status) {
  const map = { PENDING: 'danger', PARTIAL: 'warning', PAID: 'success', VOID: 'info', OBSOLETE: 'info' }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = { PENDING: '待付款', PARTIAL: '部分付款', PAID: '已付清', VOID: '已作废', OBSOLETE: '已作废' }
  return map[status] || status
}

function isBillFinal(row) {
  const s = String(row?.status || '').toUpperCase()
  return s === 'PAID' || s === 'VOID' || s === 'OBSOLETE'
}

async function loadBills() {
  loading.value = true
  try {
    const params = {}
    if (queryForm.month) params.month = queryForm.month
    if (queryForm.status) params.status = queryForm.status
    const res = await getBills(params)
    bills.value = res.data || []
  } catch (err) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryForm.month = ''
  queryForm.status = ''
  loadBills()
}

async function handleGenerate() {
  try {
    await generateBill(queryForm.month)
    ElMessage.success('账单生成成功')
    loadBills()
  } catch (err) {
    ElMessage.error('生成失败')
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
    ElMessage.error(err.response?.data || '重新生成失败')
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
      ElMessage.error(err?.response?.data || err?.message || '作废失败')
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
      ElMessage.error(err.response?.data || '收款失败')
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
  loadBills()
  loadUnits()
})
</script>

<style scoped>
.bills-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.amount {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
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
