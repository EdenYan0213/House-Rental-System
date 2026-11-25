<template>
  <PullRefresh :on-refresh="refresh">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <h1 class="m-head-title">账单收款</h1>
        <p class="m-head-sub">
          <span class="m-num count">{{ groups.length }}</span> 户
          <template v-if="totalUnpaid > 0">
            · 待收 <span class="m-num unpaid">{{ money(totalUnpaid) }}</span>
          </template>
        </p>
      </header>

      <el-input
        v-model="query.q"
        clearable
        placeholder="搜索租户姓名 / 房间号"
        :prefix-icon="Search"
        class="search-input"
        @keyup.enter="reload"
        @clear="reload"
      />

      <!-- 状态 chips：待收排第一，因为这是唯一需要动手的 -->
      <div class="status-scroll">
        <div class="status-chips">
          <button
            v-for="opt in statusChips"
            :key="opt.value || 'all'"
            type="button"
            class="chip"
            :class="{ active: (query.status || '') === opt.value }"
            @click="filterByStatus(opt.value)"
          >{{ opt.label }}</button>
        </div>
      </div>

      <div class="month-row">
        <el-input
          v-model="query.month"
          placeholder="月份（如 2026-03，留空为全部）"
          clearable
          class="month-input"
          @keyup.enter="reload"
        />
        <el-button :icon="Refresh" class="refresh-btn" @click="reload" />
      </div>

      <!-- 账单分组卡 -->
      <div v-if="groups.length > 0" class="group-list">
        <article
          v-for="group in groups"
          :key="group.key"
          class="m-card group-card"
          :class="{ 'is-settled': group.openBills.length === 0 }"
        >
          <!-- 房间 + 租户 + 应收 -->
          <div class="group-top">
            <div class="group-main">
              <div class="group-title">
                <span class="room-no m-num">{{ group.roomNumber || '-' }}</span>
                <span class="tenant-name">{{ group.tenantName || '-' }}</span>
              </div>
              <div class="group-meta">
                <span v-if="group.buildingName" class="building">{{ group.buildingName }}</span>
                <span v-if="group.overdueDays > 0" class="overdue-pill">
                  逾期 <span class="m-num">{{ group.overdueDays }}</span> 天
                </span>
              </div>
            </div>
            <div class="group-amount">
              <template v-if="group.unpaidAmount > 0">
                <span class="amount-value m-num danger">{{ money(group.unpaidAmount) }}</span>
                <span class="amount-label">待收</span>
              </template>
              <span v-else class="settled-badge">
                <el-icon :size="13"><CircleCheck /></el-icon>
                已结清
              </span>
            </div>
          </div>

          <!-- 待收账单：只列需要处理的行 -->
          <div v-if="group.openBills.length > 0" class="bill-rows">
            <div
              v-for="bill in group.openBills"
              :key="bill.id"
              class="bill-row"
              @click="openDetail(bill)"
            >
              <div class="bill-info">
                <span class="bill-month m-num">{{ bill.billMonth || '-' }}</span>
                <span v-if="isPartial(bill.status)" class="bill-partial">
                  已收 <span class="m-num">{{ money(bill.paidAmount) }}</span>
                  · 还差 <span class="m-num">{{ money(remainOf(bill)) }}</span>
                </span>
              </div>
              <span class="bill-amount m-num">{{ money(remainOf(bill)) }}</span>
              <el-icon class="bill-arrow"><ArrowRight /></el-icon>
            </div>
          </div>

          <!-- 已结清：折叠成一行，避免历史账单淹没待办 -->
          <button
            v-if="group.settledBills.length > 0"
            type="button"
            class="settled-toggle"
            @click="toggleSettled(group.key)"
          >
            <el-icon
              :size="13"
              class="settled-caret"
              :class="{ open: isExpanded(group.key) }"
            ><ArrowRight /></el-icon>
            <span>已结清 {{ group.settledBills.length }} 笔</span>
            <span class="settled-sum m-num">{{ money(group.settledAmount) }}</span>
          </button>

          <div v-if="isExpanded(group.key)" class="settled-rows">
            <div
              v-for="bill in group.settledBills"
              :key="bill.id"
              class="settled-row"
              @click="openDetail(bill)"
            >
              <span class="m-num">{{ bill.billMonth || '-' }}</span>
              <span class="settled-status">{{ statusText(bill.status) }}</span>
              <span class="m-num">{{ money(bill.totalAmount) }}</span>
            </div>
          </div>
        </article>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><Wallet /></el-icon>
        <p class="empty-title">{{ emptyTitle }}</p>
        <p class="empty-desc">{{ emptyDesc }}</p>
      </div>

      <el-button v-if="hasMore" class="load-more" :loading="loadingMore" @click="loadMore">
        加载更多
      </el-button>
    </div>
  </PullRefresh>

  <!-- 账单详情 -->
  <el-drawer v-model="showDetail" size="100%" :destroy-on-close="false" class="bill-drawer">
    <template #header>
      <div class="drawer-head">
        <div class="drawer-title">
          <span class="m-num">{{ detailBill?.billMonth || '-' }}</span>
          <span class="drawer-room">{{ detailBill?.roomNumber || '-' }} · {{ detailBill?.tenantName || '-' }}</span>
        </div>
      </div>
    </template>

    <template v-if="detailBill">
      <!-- 金额主视觉 -->
      <div class="drawer-amount">
        <span class="drawer-amount-label">
          {{ remainAmount > 0 ? '待收金额' : '已结清' }}
        </span>
        <span class="drawer-amount-value m-num" :class="{ paid: remainAmount <= 0 }">
          {{ money(remainAmount > 0 ? remainAmount : detailBill.totalAmount) }}
        </span>
        <div class="drawer-meta">
          <span>应收 {{ money(detailBill.totalAmount) }}</span>
          <span v-if="Number(detailBill.paidAmount) > 0">已收 {{ money(detailBill.paidAmount) }}</span>
          <span>出账 {{ formatDate(detailBill.generatedDate) }}</span>
        </div>
      </div>

      <section class="drawer-section">
        <h3 class="m-section-title">费用明细</h3>
        <p v-if="detailItems.length === 0" class="section-empty">暂无明细</p>
        <div v-for="item in detailItems" :key="item.id" class="line">
          <span class="line-label">{{ item.itemName || '费用项' }}</span>
          <span class="line-value m-num">{{ money(item.amount) }}</span>
        </div>
      </section>

      <section class="drawer-section">
        <h3 class="m-section-title">收款记录</h3>
        <p v-if="paymentItems.length === 0" class="section-empty">暂无收款记录</p>
        <div v-for="item in paymentItems" :key="item.id" class="line">
          <div class="line-label">
            <span>{{ payMethodText(item.paymentMethod) }}</span>
            <span class="line-time">{{ formatDate(item.paidAt) }}</span>
          </div>
          <span class="line-value m-num accent">{{ money(item.paidAmount) }}</span>
        </div>
      </section>
    </template>

    <!-- 底部操作固定，避免要滚到底才能收款 -->
    <template #footer>
      <div class="drawer-footer">
        <el-alert
          v-if="!isOnline"
          type="warning"
          :closable="false"
          title="当前离线，仅可查看账单，收款需联网"
        />
        <el-button
          v-if="detailBill && isPayable(detailBill.status)"
          type="primary"
          class="pay-btn"
          :disabled="!isOnline"
          @click="openPay"
        >收款 {{ money(remainAmount) }}</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- 收款弹窗 -->
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
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, ArrowRight, Wallet, CircleCheck } from '@element-plus/icons-vue'
import { getBills, getBill, recordPayment } from '@/api'
import { unwrapList, unwrapObject } from '@/utils/mobileApi'
import { getMobileCache, setMobileCache } from '@/utils/mobileCache'
import PullRefresh from '../components/PullRefresh.vue'

const BILL_CACHE_KEY = 'bills_center_v3'
const BILL_CACHE_TTL = 30 * 24 * 60 * 60 * 1000

const loading = ref(false)
const loadingMore = ref(false)
const showDetail = ref(false)
const showPay = ref(false)
const paying = ref(false)
const rows = ref([])
const page = ref(0)
const size = ref(40)
/** 待收模式一次取满：待收是待办清单，分批加载反而让人以为「就这么多」。 */
const OUTSTANDING_SIZE = 200
const total = ref(0)
const detailBill = ref(null)
const detailItems = ref([])
const paymentItems = ref([])
const isOnline = ref(navigator.onLine)
const route = useRoute()

const query = ref({
  month: '',
  status: 'OUTSTANDING',
  q: ''
})

const payForm = ref({
  paidAmount: 0,
  paymentMethod: 'CASH',
  remark: ''
})

/** 展开「已结清」的分组 key。默认全收起。 */
const expanded = reactive(new Set())

const remainAmount = computed(() => {
  if (!detailBill.value) return 0
  return Math.max(0, Number(detailBill.value.totalAmount || 0) - Number(detailBill.value.paidAmount || 0))
})

const hasMore = computed(() => rows.value.length < total.value)

/**
 * 状态筛选项。
 * 「待收」放第一位作为默认：这是唯一需要动手的一类，
 * 而「全部」会把历史已付账单全部铺开，一屏里大半是无需处理的行。
 */
const statusChips = [
  { label: '待收', value: 'OUTSTANDING' },
  { label: '已支付', value: 'PAID' },
  { label: '已作废', value: 'VOID' },
  { label: '全部', value: '' }
]

const totalUnpaid = computed(() =>
  groups.value.reduce((sum, g) => sum + (Number(g.unpaidAmount) || 0), 0)
)

const emptyTitle = computed(() => {
  if (query.value.status === 'OUTSTANDING') return '没有待收账单'
  return '暂无账单'
})

const emptyDesc = computed(() => {
  if (query.value.status === 'OUTSTANDING') return '当前筛选范围内所有账单都已结清。'
  return '当前筛选条件下没有账单记录。'
})

function toggleSettled(key) {
  if (expanded.has(key)) expanded.delete(key)
  else expanded.add(key)
}

function isExpanded(key) {
  return expanded.has(key)
}

function filterByStatus(value) {
  query.value.status = value
  expanded.clear()
  reload()
}

/** 状态归一：后端可能给英文也可能给中文。 */
function normalizeBillStatus(status) {
  const raw = String(status || '').trim().toUpperCase()
  if (raw === '待支付' || raw === 'UNPAID' || raw === 'PENDING') return 'PENDING'
  if (raw === '部分支付' || raw === 'PARTIAL') return 'PARTIAL'
  if (raw === '已支付' || raw === 'PAID_FULL' || raw === 'PAID') return 'PAID'
  if (raw === '已作废' || raw === '作废' || raw === 'CANCELLED' || raw === 'VOID') return 'VOID'
  return raw
}

function isPartial(status) {
  return normalizeBillStatus(status) === 'PARTIAL'
}

function isPayable(status) {
  const s = normalizeBillStatus(status)
  return s === 'PENDING' || s === 'PARTIAL'
}

/** 该账单还差多少。待收行显示这个而不是总额，收钱时更直接。 */
function remainOf(bill) {
  const total = Number(bill.totalAmount || 0)
  const paid = Number(bill.paidAmount || 0)
  return Math.max(0, total - paid)
}

/**
 * 是否还需要收款。
 * 不只看状态：历史上有「已收全额但状态仍是 PARTIAL」的脏数据，
 * 这类行如果当待收处理会显示「待收 ¥0.00」，既无用又误导。
 */
function needsCollect(bill) {
  if (!isPayable(bill.status)) return false
  return remainOf(bill) > 0.005
}

/** 逾期天数：由 dueDate 现算。后端不返回 overdueDays，原来那套取值恒为 0。 */
function overdueDaysOf(bill) {
  if (!needsCollect(bill)) return 0
  const raw = String(bill.dueDate || '').slice(0, 10)
  if (!raw) return 0
  const due = new Date(`${raw}T00:00:00`)
  if (Number.isNaN(due.getTime())) return 0
  const diff = Date.now() - due.getTime()
  return diff > 0 ? Math.floor(diff / 86400000) : 0
}

const groups = computed(() => {
  const keyword = String(query.value.q || '').trim().toLowerCase()
  const filter = String(query.value.status || '').toUpperCase()

  const matchesStatus = (bill) => {
    const s = normalizeBillStatus(bill.status)
    if (!filter) return true
    if (filter === 'OUTSTANDING') return needsCollect(bill)
    return s === filter
  }

  const map = new Map()
  rows.value.forEach((bill) => {
    if (!matchesStatus(bill)) return
    if (keyword) {
      const hay = [bill.tenantName, bill.roomNumber, bill.billMonth, bill.id]
        .map(v => String(v || '').toLowerCase())
      if (!hay.some(f => f.includes(keyword))) return
    }

    const key = `${bill.tenantId || 0}_${bill.roomId || 0}`
    if (!map.has(key)) {
      map.set(key, {
        key,
        tenantName: bill.tenantName,
        roomNumber: bill.roomNumber,
        buildingName: bill.buildingName,
        unpaidAmount: 0,
        overdueDays: 0,
        openBills: [],
        settledBills: [],
        settledAmount: 0
      })
    }
    const group = map.get(key)
    if (needsCollect(bill)) {
      group.openBills.push(bill)
      group.unpaidAmount += remainOf(bill)
      group.overdueDays = Math.max(group.overdueDays, overdueDaysOf(bill))
    } else {
      group.settledBills.push(bill)
      group.settledAmount += Number(bill.totalAmount || 0)
    }
  })

  const list = Array.from(map.values())
  list.forEach((g) => {
    // 待收按月份升序：先处理最早欠的
    g.openBills.sort((a, b) => String(a.billMonth || '').localeCompare(String(b.billMonth || '')))
    // 已结清按月份降序：最近结的更常回看
    g.settledBills.sort((a, b) => String(b.billMonth || '').localeCompare(String(a.billMonth || '')))
  })

  // 有待收的排前面，欠得久/欠得多的更靠前；已结清的沉底
  return list.sort((a, b) => {
    if ((a.openBills.length === 0) !== (b.openBills.length === 0)) {
      return a.openBills.length === 0 ? 1 : -1
    }
    if (b.overdueDays !== a.overdueDays) return b.overdueDays - a.overdueDays
    return b.unpaidAmount - a.unpaidAmount
  })
})

function money(val) {
  return `￥${Number(val || 0).toFixed(2)}`
}

function formatDate(val) {
  if (!val) return '-'
  return String(val).replace('T', ' ').slice(0, 16)
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
  // 状态筛选交给后端：在客户端过滤只会筛掉「已拉到的这一页」，
  // 数据量一大（如 100 户 600+ 笔）就会出现「待收 1 户」而实际有几十户的假象，
  // 页头的待收合计也会严重低估。
  const st = String(query.value.status || '').toUpperCase()
  if (st === 'OUTSTANDING') {
    // 待收 = 待支付 + 部分支付。后端只认单一状态，这里分开拉再合并。
    const [pendingRes, partialRes] = await Promise.all([
      getBills({ ...params, page: 0, size: OUTSTANDING_SIZE, status: 'PENDING' }),
      getBills({ ...params, page: 0, size: OUTSTANDING_SIZE, status: 'PARTIAL' })
    ])
    const pendingList = unwrapList(pendingRes.data)
    const partialList = unwrapList(partialRes.data)
    const merged = [...pendingList, ...partialList]
    rows.value = append ? [...rows.value, ...merged] : merged
    total.value = merged.length
    page.value = targetPage
    setMobileCache(BILL_CACHE_KEY, { rows: rows.value, total: total.value, page: page.value }, BILL_CACHE_TTL)
    return
  }
  if (st) {
    params.status = st
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
  expanded.clear()
  return load()
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
.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.unpaid {
  color: var(--danger-color);
  font-weight: 600;
}

.search-input :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

/* ---------- 状态 chips ---------- */
.status-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 0 calc(var(--m-gutter) * -1);
  padding: 0 var(--m-gutter);
}

.status-scroll::-webkit-scrollbar {
  display: none;
}

.status-chips {
  display: inline-flex;
  gap: 8px;
}

.chip {
  flex: 0 0 auto;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-muted);
  border-radius: 999px;
  padding: 8px 15px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}

.chip.active {
  background: var(--accent-3);
  border-color: var(--accent-3);
  color: #fff;
  font-weight: 600;
}

/* ---------- 月份筛选 ---------- */
.month-row {
  display: flex;
  gap: 8px;
}

.month-input {
  flex: 1;
  min-width: 0;
}

.month-input :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.refresh-btn {
  min-height: var(--m-tap);
  width: 46px;
  border-radius: 10px;
  flex: 0 0 auto;
}

/* ---------- 分组卡 ---------- */
.group-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.group-card {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

/* 已结清的分组整体降饱和，视觉上退到待办之后 */
.group-card.is-settled {
  opacity: 0.85;
}

.group-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: var(--m-gutter);
}

.group-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.group-title {
  display: flex;
  align-items: baseline;
  gap: 7px;
  min-width: 0;
}

.room-no {
  font-size: 19px;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.tenant-name {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 16px;
}

.building {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.overdue-pill {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--danger-color);
  background: color-mix(in srgb, var(--danger-color) 12%, transparent);
  padding: 2px 7px;
  border-radius: 6px;
}

.group-amount {
  text-align: right;
  flex: 0 0 auto;
}

.amount-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
}

.amount-value.danger { color: var(--danger-color); }

.amount-label {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.settled-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-3);
  line-height: 1.2;
}

/* ---------- 待收账单行 ---------- */
.bill-rows {
  border-top: 1px solid var(--border-color);
  padding: 0 var(--m-gutter);
}

.bill-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: var(--m-tap);
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.bill-row:last-child {
  border-bottom: none;
}

.bill-row:active {
  background: var(--accent-1);
}

.bill-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bill-month {
  font-size: 14px;
  color: var(--text-main);
  font-weight: 500;
}

/* 部分支付：补齐「已收多少 / 还差多少」，避免只看到一个数就以为是全额 */
.bill-partial {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bill-amount {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
  margin-left: auto;
  letter-spacing: -0.01em;
}

.bill-arrow {
  color: var(--text-muted);
  opacity: 0.45;
  flex: 0 0 auto;
}

/* ---------- 已结清折叠 ---------- */
.settled-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 40px;
  padding: 0 var(--m-gutter);
  border: none;
  border-top: 1px solid var(--border-color);
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.settled-toggle:active {
  background: var(--accent-1);
}

.settled-caret {
  transition: transform 0.18s ease;
}

.settled-caret.open {
  transform: rotate(90deg);
}

.settled-sum {
  margin-left: auto;
  font-weight: 600;
  color: var(--text-muted);
}

.settled-rows {
  padding: 0 var(--m-gutter);
  background: var(--bg-subtle);
}

.settled-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
}

.settled-row:last-child {
  border-bottom: none;
}

.settled-row:active {
  background: var(--accent-1);
}

.settled-status {
  font-size: 11px;
  opacity: 0.8;
}

.settled-row > .m-num:last-child {
  margin-left: auto;
  font-weight: 600;
}

/* ---------- 详情抽屉 ---------- */
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-main);
}

.drawer-room {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.drawer-amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 20px var(--m-gutter);
  margin-bottom: var(--m-gap);
  border-radius: var(--m-radius);
  background: color-mix(in srgb, var(--danger-color) 7%, var(--bg-surface));
  border: 1px solid color-mix(in srgb, var(--danger-color) 20%, transparent);
  text-align: center;
}

.drawer-amount-label {
  font-size: 12px;
  color: var(--text-muted);
}

.drawer-amount-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--danger-color);
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.drawer-amount-value.paid {
  color: var(--accent-3);
}

.drawer-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.drawer-section {
  padding: 0 var(--m-gutter);
  margin-bottom: var(--m-gap);
}

.drawer-section .m-section-title {
  margin-bottom: 4px;
}

.section-empty {
  margin: 0;
  padding: 10px 0;
  font-size: 12px;
  color: var(--text-muted);
}

.line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
}

.line:last-child {
  border-bottom: none;
}

.line-label {
  display: flex;
  flex-direction: column;
  gap: 1px;
  color: var(--text-main);
  min-width: 0;
}

.line-time {
  font-size: 11px;
  color: var(--text-muted);
}

.line-value {
  margin-left: auto;
  font-weight: 600;
  color: var(--text-main);
}

.line-value.accent {
  color: var(--accent-3);
}

.drawer-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pay-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
  font-weight: 600;
  /* 用品牌墨绿，与系统主色一致（Element Plus 默认是蓝色） */
  --el-button-bg-color: var(--accent-3);
  --el-button-border-color: var(--accent-3);
  --el-button-hover-bg-color: var(--accent-4);
  --el-button-hover-border-color: var(--accent-4);
  --el-button-active-bg-color: var(--accent-5);
  --el-button-active-border-color: var(--accent-5);
}

/* ---------- 空态 / 加载更多 ---------- */
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

.load-more {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
}
</style>
