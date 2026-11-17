<template>
  <div class="bill-detail-container">
    <el-page-header @back="goBack" content="账单详情">
      <template #extra>
        <el-button type="primary" @click="handlePrint">
          <el-icon><Printer /></el-icon>
          打印
        </el-button>
        <el-button type="success" @click="handleExport">
          <el-icon><Download /></el-icon>
          导出PDF
        </el-button>
        <el-button type="warning" @click="openPayment" v-if="canPayBill">
          <el-icon><Wallet /></el-icon>
          收款
        </el-button>
      </template>
    </el-page-header>

    <div ref="printContent" class="bill-content">
      <el-card class="bill-card">
        <div class="bill-header">
          <h2>房屋租赁账单</h2>
          <div class="bill-info">
            <p><strong>账单月份：</strong>{{ bill?.billMonth }}</p>
            <p><strong>生成时间：</strong>{{ bill?.createTime }}</p>
          </div>
        </div>

        <el-descriptions :column="2" border class="info-section">
          <el-descriptions-item label="楼栋">{{ bill?.buildingName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="房间号">{{ bill?.roomNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="租户">{{ bill?.tenantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ bill?.tenantPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到期日">{{ bill?.dueDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(bill?.status)">{{ getStatusText(bill?.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="section-title">账单明细</h3>
        <el-table :data="details" border stripe size="small">
          <el-table-column prop="feeType" label="费用类型" />
          <el-table-column prop="prevReading" label="上期读数">
            <template #default="{ row }">
              {{ row.prevReading != null ? row.prevReading : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="currReading" label="本期读数">
            <template #default="{ row }">
              {{ row.currReading != null ? row.currReading : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="description" label="本期用量">
            <template #default="{ row }">
              {{ row.description != null ? row.description : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="单价">
            <template #default="{ row }">
              <span v-if="row.unitPrice != null">¥{{ Number(row.unitPrice).toFixed(2) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额">
            <template #default="{ row }">
              ¥{{ row.amount?.toFixed(2) }}
            </template>
          </el-table-column>
        </el-table>

        <div class="amount-summary">
          <div class="summary-item">
            <span>总金额：</span>
            <span class="amount">¥{{ bill?.totalAmount?.toFixed(2) }}</span>
          </div>
          <div class="summary-item">
            <span>已付金额：</span>
            <span class="paid">¥{{ bill?.paidAmount?.toFixed(2) || '0.00' }}</span>
          </div>
          <div class="summary-item">
            <span>待付金额：</span>
            <span class="unpaid">¥{{ (bill?.totalAmount - bill?.paidAmount)?.toFixed(2) }}</span>
          </div>
        </div>

        <h3 class="section-title">收款信息</h3>
        <el-descriptions :column="2" border class="info-section">
          <el-descriptions-item label="房东/收款人">
            {{ bill?.landlordName || bill?.ownerName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            {{ bill?.landlordPhone || bill?.ownerPhone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="收款账户（开户行）">
            {{ bill?.bankName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="收款账户（账号）">
            {{ bill?.bankAccount || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="收款二维码 / 其他方式">
            {{ bill?.paymentCodeDesc || '可通过支付宝 / 微信等方式付款' }}
          </el-descriptions-item>
          <el-descriptions-item label="开票 / 出单日期">
            {{ bill?.createTime || issueDate }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="sign-section">
          <div class="sign-item">
            <span>收款人签字：</span>
            <div class="sign-line"></div>
          </div>
          <div class="sign-item">
            <span>日期：</span>
            <div class="sign-line"></div>
          </div>
        </div>

        <h3 class="section-title">付款记录</h3>
        <el-table :data="payments" border stripe size="small" v-if="payments.length">
          <el-table-column prop="paidDate" label="付款日期" />
          <el-table-column prop="paidAmount" label="付款金额">
            <template #default="{ row }">
              ¥{{ row.paidAmount?.toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column prop="paymentMethod" label="付款方式">
            <template #default="{ row }">
              {{ getPaymentMethodText(row.paymentMethod) }}
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" />
        </el-table>
        <el-empty v-else description="暂无付款记录" :image-size="60" />
      </el-card>
    </div>

    <!-- 收款弹窗 -->
    <el-dialog v-model="paymentVisible" title="收款" width="500px">
      <el-form :model="paymentForm" :rules="paymentRules" ref="paymentRef" label-width="100px">
        <el-form-item label="账单金额">
          <span class="amount-large">¥{{ bill?.totalAmount?.toFixed(2) }}</span>
        </el-form-item>
        <el-form-item label="已付金额">
          <span>¥{{ bill?.paidAmount?.toFixed(2) || '0.00' }}</span>
        </el-form-item>
        <el-form-item label="应付金额">
          <span class="amount-large">¥{{ (bill?.totalAmount - bill?.paidAmount)?.toFixed(2) }}</span>
        </el-form-item>
        <el-form-item label="收款金额" prop="paidAmount">
          <el-input-number v-model="paymentForm.paidAmount" :min="0" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="收款方式" prop="paymentMethod">
          <el-select v-model="paymentForm.paymentMethod" style="width: 200px">
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
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Printer, Download, Wallet } from '@element-plus/icons-vue'
import { getBill, recordPayment, getErrorMessage } from '@/api'
import { billStatusText, billStatusTag, paymentMethodText } from '@/utils/labels'
import html2pdf from 'html2pdf.js'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const paying = ref(false)
const bill = ref(null)
const details = ref([])
const payments = ref([])
const issueDate = ref('')
const paymentVisible = ref(false)
const paymentRef = ref(null)
const printContent = ref(null)

const paymentForm = reactive({
  paidAmount: 0,
  paymentMethod: 'CASH',
  remark: ''
})

const paymentRules = {
  paidAmount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择收款方式', trigger: 'change' }]
}

function getStatusType(status) {
  return billStatusTag(status)
}

function getStatusText(status) {
  return billStatusText(status)
}

// 已付清或已作废的账单不可再收款
const canPayBill = computed(() => {
  const s = String(bill.value?.status || '').trim().toUpperCase()
  return !['PAID', 'VOID', 'OBSOLETE'].includes(s)
})

function getPaymentMethodText(method) {
  return paymentMethodText(method)
}

function goBack() {
  router.push('/bills')
}

async function loadBill() {
  loading.value = true
  try {
    const res = await getBill(route.params.id)
    const data = res.data?.data
    bill.value = data.bill
    details.value = data.details || []
    payments.value = data.payments || []
  } catch (err) {
    ElMessage.error(getErrorMessage(err, '账单加载失败'))
  } finally {
    loading.value = false
  }
}

function openPayment() {
  paymentForm.paidAmount = bill.value.totalAmount - bill.value.paidAmount
  paymentForm.paymentMethod = 'CASH'
  paymentForm.remark = ''
  paymentVisible.value = true
}

async function submitPayment() {
  if (!paymentRef.value) return
  
  await paymentRef.value.validate(async (valid) => {
    if (!valid) return
    
    paying.value = true
    try {
      await recordPayment(bill.value.id, paymentForm)
      ElMessage.success('收款成功')
      paymentVisible.value = false
      loadBill()
    } catch (err) {
      ElMessage.error(getErrorMessage(err, '收款失败'))
    } finally {
      paying.value = false
    }
  })
}

function handlePrint() {
  const content = printContent.value.innerHTML
  const printWindow = window.open('', '_blank')
  printWindow.document.write(`
    <html>
      <head>
        <title>账单打印 - ${bill.value?.billMonth}</title>
        <style>
          body { font-family: Arial, sans-serif; padding: 20px; }
          .bill-header { text-align: center; margin-bottom: 20px; }
          .bill-header h2 { margin: 0 0 10px 0; }
          .info-section { margin-bottom: 20px; }
          .section-title { margin: 20px 0 10px 0; }
          .amount-summary { margin-top: 20px; text-align: right; }
          .summary-item { margin: 5px 0; }
          .amount { font-size: 20px; font-weight: bold; color: #409eff; }
          .paid { color: #67c23a; }
          .unpaid { font-size: 20px; font-weight: bold; color: #f56c6c; }
          table { width: 100%; border-collapse: collapse; }
          th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
          th { background: #f5f5f5; }
        </style>
      </head>
      <body>${content}</body>
    </html>
  `)
  printWindow.document.close()
  printWindow.print()
}

function handleExport() {
  if (!printContent.value) return

  const element = printContent.value
  const filename = `房屋租赁账单_${bill.value?.billMonth || ''}_${bill.value?.tenantName || ''}.pdf`

  const opt = {
    margin: [10, 10, 10, 10],
    filename,
    image: { type: 'jpeg', quality: 0.98 },
    html2canvas: { scale: 2, useCORS: true },
    jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
    pagebreak: { mode: ['css', 'legacy'] }
  }

  html2pdf().set(opt).from(element).save()
}

onMounted(() => {
  loadBill()
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  issueDate.value = `${y}-${m}-${d}`
})
</script>

<style scoped>
.bill-detail-container {
  padding: 20px;
}

.bill-content {
  margin-top: 20px;
}

.bill-card {
  max-width: 900px;
}

.bill-header {
  text-align: center;
  margin-bottom: 20px;
}

.bill-header h2 {
  margin: 0 0 10px 0;
}

.bill-info {
  display: flex;
  justify-content: center;
  gap: 30px;
}

.bill-info p {
  margin: 5px 0;
}

.info-section {
  margin: 20px 0;
}

.section-title {
  margin: 20px 0 10px 0;
  font-size: 16px;
  font-weight: bold;
}

.amount-summary {
  margin-top: 20px;
  text-align: right;
  padding: 15px;
  background: var(--bg-main);
  border-radius: 4px;
}

.summary-item {
  margin: 8px 0;
  font-size: 14px;
}

.amount {
  font-size: 20px;
  font-weight: bold;
  color: var(--el-color-primary);
}

.paid {
  color: var(--success-color);
}

.unpaid {
  font-size: 20px;
  font-weight: bold;
  color: var(--danger-color);
}

.amount-large {
  font-size: 24px;
  font-weight: bold;
  color: var(--el-color-primary);
}

.sign-section {
  margin-top: 40px;
  display: flex;
  justify-content: flex-end;
  gap: 80px;
}

.sign-item {
  font-size: 14px;
  display: flex;
  align-items: center;
}

.sign-line {
  width: 160px;
  border-bottom: 1px solid var(--border-color);
  margin-left: 8px;
}

@media print {
  .el-page-header,
  .el-button {
    display: none !important;
  }
}
</style>
