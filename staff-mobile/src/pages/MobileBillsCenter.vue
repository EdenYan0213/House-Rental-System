<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <div class="title">账单收款</div>
      <el-button text @click="refresh">刷新</el-button>
    </div>

    <el-card class="panel" shadow="never">
      <el-input v-model="query.q" clearable placeholder="搜索租户姓名/房间号" @keyup.enter="reload" />
      <div class="filters">
        <el-input v-model="query.month" placeholder="月份 YYYY-MM（不填=全部）" clearable />
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="全部" value="" />
          <el-option label="待支付" value="PENDING" />
          <el-option label="部分支付" value="PARTIAL" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已作废" value="VOID" />
        </el-select>
      </div>
      <el-button type="primary" style="width: 100%" @click="reload">查询</el-button>
    </el-card>

    <el-empty v-if="!loading && groups.length === 0" description="暂无账单" />

    <el-card v-for="group in groups" :key="group.key" class="panel" shadow="never">
      <div class="row top">
        <div>{{ group.tenantName || '-' }} · {{ group.roomNumber || '-' }}</div>
        <el-tag v-if="group.unpaidAmount > 0" type="danger">
          待支付 {{ money(group.unpaidAmount) }}
        </el-tag>
        <el-tag v-else type="success">已结清</el-tag>
      </div>
      <div v-if="group.maxOverdueDays > 0" class="overdue">
        已逾期 {{ group.maxOverdueDays }} 天
      </div>

      <div v-for="bill in group.bills" :key="bill.id" class="bill-row" @click="openDetail(bill)">
        <div>{{ bill.billMonth || '-' }}</div>
        <div class="right">
          <span>{{ money(bill.totalAmount) }}</span>
          <el-tag size="small" :type="bill.status === 'PAID' ? 'success' : bill.status === 'PARTIAL' ? 'warning' : 'danger'">
            {{ statusText(bill.status) }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-button v-if="hasMore" style="width: 100%" @click="loadMore" :loading="loadingMore">加载更多</el-button>

    <el-drawer v-model="showDetail" title="账单详情" size="100%" :destroy-on-close="false">
      <template v-if="detailBill">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="账单月份">{{ detailBill.billMonth || '-' }}</el-descriptions-item>
          <el-descriptions-item label="租户">{{ detailBill.tenantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="房间">{{ detailBill.roomNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生成日期">{{ detailBill.generatedDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detailBill.status) }}</el-descriptions-item>
        </el-descriptions>

        <el-card class="section" shadow="never">
          <template #header>费用明细</template>
          <el-empty v-if="detailItems.length === 0" description="暂无明细" />
          <div v-for="item in detailItems" :key="item.id" class="line">
            <span>{{ item.itemName || '-' }}</span>
            <span>{{ money(item.amount) }}</span>
          </div>
        </el-card>

        <el-card class="section" shadow="never">
          <template #header>收款记录</template>
          <el-empty v-if="paymentItems.length === 0" description="暂无收款记录" />
          <div v-for="item in paymentItems" :key="item.id" class="line">
            <span>{{ payMethodText(item.paymentMethod) }} {{ money(item.paidAmount) }}</span>
            <span class="muted">{{ item.paidAt || '-' }}</span>
          </div>
        </el-card>

        <div class="footer-action">
          <el-alert v-if="!isOnline" type="warning" :closable="false" title="当前离线，仅可查看账单，收款需联网" />
          <el-button
            v-if="isPayable(detailBill.status)"
            type="primary"
            style="width: 100%; margin-top: 8px"
            :disabled="!isOnline"
            @click="openPay"
          >收款</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="showPay" title="账单收款" width="92%">
      <el-form :model="payForm" label-width="90px">
        <el-form-item label="应收金额">
          <el-input :model-value="money(remainAmount)" disabled />
        </el-form-item>
        <el-form-item label="实收金额">
          <el-input-number v-model="payForm.paidAmount" :min="0" :max="remainAmount" style="width: 100%" />
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="payForm.paymentMethod" style="width: 100%">
            <el-option label="现金" value="CASH" />
            <el-option label="微信" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="银行卡" value="BANK" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="payForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPay = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="submitPay">提交收款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBills, getBill, recordPayment } from '@/api'
import { unwrapList, unwrapObject } from '@/utils/mobileApi'
import { getMobileCache, setMobileCache } from '@/utils/mobileCache'

const BILL_CACHE_KEY = 'bills_center_v2'
const BILL_CACHE_TTL = 30 * 24 * 60 * 60 * 1000

const loading = ref(false)
const loadingMore = ref(false)
const showDetail = ref(false)
const showPay = ref(false)
const paying = ref(false)
const rows = ref([])
const page = ref(0)
const size = ref(40)
const total = ref(0)
const detailBill = ref(null)
const detailItems = ref([])
const paymentItems = ref([])
const isOnline = ref(navigator.onLine)
const route = useRoute()

const query = ref({
  month: '',
  status: '',
  q: ''
})

const payForm = ref({
  paidAmount: 0,
  paymentMethod: 'CASH',
  remark: ''
})

const remainAmount = computed(() => {
  if (!detailBill.value) return 0
  return Math.max(0, Number(detailBill.value.totalAmount || 0) - Number(detailBill.value.paidAmount || 0))
})

const hasMore = computed(() => rows.value.length < total.value)

const groups = computed(() => {
  const keyword = String(query.value.q || '').trim().toLowerCase()
  const selectedStatus = String(query.value.status || '').toUpperCase()
  const filteredRows = rows.value.filter((bill) => {
    const status = normalizeBillStatus(bill.status)
    if (selectedStatus && status !== selectedStatus) return false
    if (!keyword) return true
    return [bill.tenantName, bill.roomNumber, bill.billMonth, bill.id]
      .some(field => String(field || '').toLowerCase().includes(keyword))
  })

  const map = new Map()
  filteredRows.forEach((bill) => {
    const key = `${bill.tenantId || 0}_${bill.roomId || 0}`
    if (!map.has(key)) {
      map.set(key, {
        key,
        tenantName: bill.tenantName,
        roomNumber: bill.roomNumber,
        unpaidAmount: 0,
        maxOverdueDays: 0,
        bills: []
      })
    }
    const group = map.get(key)
    group.bills.push(bill)
    if (bill.status !== 'PAID' && bill.status !== 'VOID') {
      const remain = Number(bill.totalAmount || 0) - Number(bill.paidAmount || 0)
      group.unpaidAmount += Math.max(0, remain)
    }
    group.maxOverdueDays = Math.max(group.maxOverdueDays, Number(bill.overdueDays || 0))
  })
  return Array.from(map.values())
})

function money(val) {
  return `￥${Number(val || 0).toFixed(2)}`
}

function normalizeBillStatus(status) {
  const raw = String(status || '').trim().toUpperCase()
  if (raw === '待支付' || raw === 'UNPAID') return 'PENDING'
  if (raw === '部分支付') return 'PARTIAL'
  if (raw === '已支付' || raw === 'PAID_FULL') return 'PAID'
  if (raw === '已作废' || raw === 'CANCELLED') return 'VOID'
  return raw
}

function statusText(status) {
  const normalized = normalizeBillStatus(status)
  const map = { PENDING: '待支付', PARTIAL: '部分支付', PAID: '已支付', VOID: '已作废' }
  return map[normalized] || status || '-'
}

function payMethodText(method) {
  const map = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', BANK: '银行卡' }
  return map[method] || method || '-'
}

function isPayable(status) {
  return status === 'PENDING' || status === 'PARTIAL'
}

function bindOnlineEvents() {
  window.addEventListener('online', () => { isOnline.value = true })
  window.addEventListener('offline', () => { isOnline.value = false })
}

function readCacheFirst() {
  const cached = getMobileCache(BILL_CACHE_KEY)
  if (!cached) return
  rows.value = cached.rows || []
  total.value = cached.total || rows.value.length
  page.value = cached.page || 0
}

async function fetchPage(targetPage, append = false) {
  const params = {
    page: targetPage,
    size: size.value,
    month: query.value.month || undefined,
    q: query.value.q || undefined
  }

  const res = await getBills(params)
  const list = unwrapList(res.data)
  const obj = unwrapObject(res.data)
  const nextRows = append ? [...rows.value, ...list] : list
  rows.value = nextRows
  total.value = Number(obj.totalElements || nextRows.length)
  page.value = targetPage

  setMobileCache(BILL_CACHE_KEY, { rows: rows.value, total: total.value, page: page.value }, BILL_CACHE_TTL)
}

async function load() {
  loading.value = true
  try {
    await fetchPage(0, false)
  } catch (_) {
    if (rows.value.length === 0) {
      ElMessage.error('加载账单失败')
    } else {
      ElMessage.warning('网络异常，已显示缓存账单')
    }
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 0
  load()
}

function refresh() {
  load()
}

async function loadMore() {
  if (!hasMore.value) return
  loadingMore.value = true
  try {
    await fetchPage(page.value + 1, true)
  } catch (_) {
    ElMessage.error('加载更多失败')
  } finally {
    loadingMore.value = false
  }
}

async function openDetail(bill) {
  showDetail.value = true
  try {
    const res = await getBill(bill.id)
    const payload = unwrapObject(res.data)
    detailBill.value = payload.bill || bill
    detailItems.value = payload.details || []
    paymentItems.value = payload.payments || []
  } catch (_) {
    detailBill.value = bill
    detailItems.value = []
    paymentItems.value = []
    ElMessage.warning('账单详情加载失败，已显示列表信息')
  }
}

function openPay() {
  payForm.value = {
    paidAmount: remainAmount.value,
    paymentMethod: 'CASH',
    remark: ''
  }
  showPay.value = true
}

async function submitPay() {
  if (!detailBill.value) return
  if (!isOnline.value) {
    ElMessage.warning('当前离线，无法提交收款')
    return
  }
  paying.value = true
  try {
    await recordPayment(detailBill.value.id, { ...payForm.value })
    ElMessage.success('收款成功')
    showPay.value = false
    await openDetail(detailBill.value)
    await load()
  } catch (_) {
    ElMessage.error('收款失败')
  } finally {
    paying.value = false
  }
}

onMounted(() => {
  if (route.query.q) {
    query.value.q = String(route.query.q)
  }
  bindOnlineEvents()
  readCacheFirst()
  load()
})
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
  color: var(--text-main);
}

.panel {
  margin-bottom: 10px;
  border: 1px solid #e8efff;
}

.filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 8px 0;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.top {
  margin-bottom: 6px;
}

.muted {
  color: var(--text-muted);
  font-size: 13px;
}

.bill-row {
  padding: 10px 0;
  border-top: 1px solid #edf1fb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section {
  margin-top: 12px;
}

.line {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #edf1fb;
}

.line:last-child {
  border-bottom: none;
}

.footer-action {
  position: sticky;
  bottom: 0;
  background: var(--bg-main);
  padding: 8px 0;
}
</style>
