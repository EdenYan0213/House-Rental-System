<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div>
            <span class="page-title">财务报表</span>
            <span class="page-subtitle">
              {{ report.period?.startMonth }} ~ {{ report.period?.endMonth }}
            </span>
          </div>
          <div class="page-actions">
            <el-date-picker
              v-model="monthRange"
              type="monthrange"
              value-format="YYYY-MM"
              range-separator="至"
              start-placeholder="开始月"
              end-placeholder="结束月"
              style="width: 240px"
              @change="load"
            />
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="primary" :icon="Download" @click="exportReport">导出报表</el-button>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon class="caliber-tip">
        <template #title>
          <b>统计口径：</b>「应收」按账单月份统计，「实收」按实际收款日期统计，两者可能因跨月收租而不等；
          「未收」= 应收 − 这些账单自身已收金额。
        </template>
      </el-alert>

      <!-- 关键指标 -->
      <div class="kpi-row" v-loading="loading">
        <div class="kpi-item">
          <div class="kpi-label">应收<span class="kpi-tip">按账单月份</span></div>
          <div class="kpi-value">¥{{ money(summary.receivable) }}</div>
          <div class="kpi-sub">{{ summary.billCount || 0 }} 张账单</div>
        </div>
        <div class="kpi-item">
          <div class="kpi-label">实收<span class="kpi-tip">按收款日期</span></div>
          <div class="kpi-value tone-success">¥{{ money(summary.received) }}</div>
          <div class="kpi-sub">现金流口径 · 收租率 {{ summary.collectRate || 0 }}%</div>
        </div>
        <div class="kpi-item">
          <div class="kpi-label">未收<span class="kpi-tip">账单差</span></div>
          <div class="kpi-value tone-warning">¥{{ money(summary.unpaid) }}</div>
          <div class="kpi-sub">{{ (summary.billCount || 0) - (summary.paidBillCount || 0) }} 张未结清</div>
        </div>
        <div class="kpi-item">
          <div class="kpi-label">支出</div>
          <div class="kpi-value tone-danger">¥{{ money(summary.expense) }}</div>
          <div class="kpi-sub">维修费用</div>
        </div>
        <div class="kpi-item">
          <div class="kpi-label">净收入</div>
          <div class="kpi-value" :class="summary.netIncome >= 0 ? 'tone-success' : 'tone-danger'">
            ¥{{ money(summary.netIncome) }}
          </div>
          <div class="kpi-sub">实收 - 支出</div>
        </div>
      </div>

      <el-row :gutter="16" class="section-row">
        <el-col :xs="24" :md="14">
          <el-card shadow="never">
            <template #header>月度收支趋势</template>
            <div v-if="hasTrend" ref="trendChart" class="chart"></div>
            <EmptyState v-else icon="wallet" title="所选区间没有账单数据" compact />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="10">
          <el-card shadow="never">
            <template #header>费用构成</template>
            <div v-if="costBreakdown.length" ref="costChart" class="chart"></div>
            <EmptyState v-else icon="wallet" title="暂无费用明细" compact />
          </el-card>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab" class="section-row">
        <el-tab-pane label="按楼栋" name="building">
          <el-table :data="byBuilding" v-loading="loading" style="width: 100%">
            <el-table-column prop="buildingName" label="楼栋" min-width="150" />
            <el-table-column label="房间数" width="100" align="center">
              <template #default="{ row }">{{ row.roomCount }}</template>
            </el-table-column>
            <el-table-column label="出租率" width="110" align="center">
              <template #default="{ row }">
                <el-progress :percentage="row.occupancyRate" :stroke-width="10" :show-text="false" style="width: 60px; display: inline-block" />
                <span class="rate-text">{{ row.occupancyRate }}%</span>
              </template>
            </el-table-column>
            <el-table-column label="应收" width="130" align="right">
              <template #default="{ row }">¥{{ money(row.receivable) }}</template>
            </el-table-column>
            <el-table-column label="实收" width="130" align="right">
              <template #default="{ row }"><span class="tone-success">¥{{ money(row.received) }}</span></template>
            </el-table-column>
            <el-table-column label="未收" width="130" align="right">
              <template #default="{ row }">
                <span :class="row.unpaid > 0 ? 'tone-warning' : ''">¥{{ money(row.unpaid) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="收缴率" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.receivable > 0 && row.received >= row.receivable ? 'success' : 'warning'">
                  {{ row.receivable > 0 ? Math.round(row.received / row.receivable * 100) : 0 }}%
                </el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState icon="document" title="暂无楼栋收支数据" description="生成账单后即可查看各楼栋的经营情况" compact />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="按房间" name="room">
          <el-table :data="byRoom" v-loading="loading" style="width: 100%" max-height="520">
            <el-table-column label="房间" min-width="140">
              <template #default="{ row }">
                <span class="room-no">{{ row.roomNumber }}</span>
                <span class="room-building">{{ row.buildingName || '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="租户" width="120">
              <template #default="{ row }">{{ row.tenantName || '-' }}</template>
            </el-table-column>
            <el-table-column label="账单数" width="90" align="center">
              <template #default="{ row }">{{ row.billCount }}</template>
            </el-table-column>
            <el-table-column label="应收" width="130" align="right">
              <template #default="{ row }">¥{{ money(row.receivable) }}</template>
            </el-table-column>
            <el-table-column label="实收" width="130" align="right">
              <template #default="{ row }"><span class="tone-success">¥{{ money(row.received) }}</span></template>
            </el-table-column>
            <el-table-column label="未收" width="130" align="right">
              <template #default="{ row }">
                <span :class="row.unpaid > 0 ? 'tone-warning' : ''">¥{{ money(row.unpaid) }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState icon="document" title="暂无房间收支数据" description="所选区间内没有产生账单" compact />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="收款方式" name="method">
          <el-table :data="paymentMethods" v-loading="loading" style="width: 100%">
            <el-table-column label="收款方式" min-width="140">
              <template #default="{ row }">{{ methodText(row.method) }}</template>
            </el-table-column>
            <el-table-column label="笔数" width="110" align="center">
              <template #default="{ row }">{{ row.count }}</template>
            </el-table-column>
            <el-table-column label="金额" width="160" align="right">
              <template #default="{ row }">¥{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="占比" min-width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="receivedTotal > 0 ? Math.round(row.amount / receivedTotal * 100) : 0"
                  :stroke-width="14"
                />
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState icon="money" title="暂无收款记录" description="收款后可按支付方式查看资金分布" compact />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getFinanceReport, getErrorMessage } from '@/api'
import { paymentMethodText } from '@/utils/labels'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(false)
const activeTab = ref('building')
const monthRange = ref([])
const report = reactive({
  period: {},
  summary: {},
  monthlyTrend: {},
  byBuilding: [],
  byRoom: [],
  costBreakdown: [],
  paymentMethods: []
})

const trendChart = ref(null)
const costChart = ref(null)
const chartInstances = []

const summary = computed(() => report.summary || {})
const byBuilding = computed(() => report.byBuilding || [])
const byRoom = computed(() => report.byRoom || [])
const costBreakdown = computed(() => report.costBreakdown || [])
const paymentMethods = computed(() => report.paymentMethods || [])
const receivedTotal = computed(() =>
  paymentMethods.value.reduce((s, m) => s + Number(m.amount || 0), 0)
)
const hasTrend = computed(() => {
  const months = report.monthlyTrend?.months || []
  return months.length > 0 && (report.monthlyTrend?.receivable || []).some(v => Number(v) > 0)
})

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function methodText(m) {
  return paymentMethodText(m) || m
}

function disposeCharts() {
  while (chartInstances.length) {
    chartInstances.pop().dispose()
  }
}

function handleResize() {
  chartInstances.forEach(i => i.resize())
}

function renderCharts() {
  disposeCharts()

  if (hasTrend.value && trendChart.value) {
    const inst = echarts.init(trendChart.value)
    inst.setOption({
      tooltip: { trigger: 'axis', valueFormatter: v => '¥' + Number(v).toLocaleString() },
      legend: { data: ['应收(账单月)', '实收(收款月)', '支出'] },
      grid: { left: 56, right: 16, top: 40, bottom: 28 },
      xAxis: { type: 'category', data: report.monthlyTrend.months },
      yAxis: { type: 'value', axisLabel: { formatter: v => v >= 10000 ? (v / 10000) + '万' : v } },
      series: [
        { name: '应收(账单月)', type: 'bar', barMaxWidth: 22, data: report.monthlyTrend.receivable },
        { name: '实收(收款月)', type: 'bar', barMaxWidth: 22, data: report.monthlyTrend.received },
        { name: '支出', type: 'line', smooth: true, data: report.monthlyTrend.expense }
      ]
    })
    chartInstances.push(inst)
  }

  if (costBreakdown.value.length && costChart.value) {
    const inst = echarts.init(costChart.value)
    inst.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['40%', '62%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        minShowLabelAngle: 5,
        label: {
          formatter: '{b}\n¥{c}',
          fontSize: 11,
          lineHeight: 15,
          alignTo: 'labelLine',
          edgeDistance: 4
        },
        labelLine: { length: 8, length2: 10 },
        data: costBreakdown.value.map(c => ({ name: c.name, value: c.amount }))
      }]
    })
    chartInstances.push(inst)
  }
}

async function load() {
  loading.value = true
  try {
    const params = {}
    if (monthRange.value?.[0]) params.startMonth = monthRange.value[0]
    if (monthRange.value?.[1]) params.endMonth = monthRange.value[1]
    const res = await getFinanceReport(params)
    const data = res.data || {}
    report.period = data.period || {}
    report.summary = data.summary || {}
    report.monthlyTrend = data.monthlyTrend || {}
    report.byBuilding = data.byBuilding || []
    report.byRoom = data.byRoom || []
    report.costBreakdown = data.costBreakdown || []
    report.paymentMethods = data.paymentMethods || []
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '财务报表加载失败'))
  } finally {
    loading.value = false
  }
}

// 导出：把各维度明细拼成 CSV（Excel 可直接打开）
function exportReport() {
  const rows = []
  const p = report.period || {}
  rows.push([`财务报表 ${p.startMonth || ''} ~ ${p.endMonth || ''}`])
  rows.push([])
  rows.push(['关键指标', '金额'])
  rows.push(['应收', summary.value.receivable ?? 0])
  rows.push(['实收', summary.value.received ?? 0])
  rows.push(['未收', summary.value.unpaid ?? 0])
  rows.push(['支出', summary.value.expense ?? 0])
  rows.push(['净收入', summary.value.netIncome ?? 0])
  rows.push(['收租率(%)', summary.value.collectRate ?? 0])
  rows.push([])
  rows.push(['楼栋', '房间数', '出租率(%)', '应收', '实收', '未收'])
  byBuilding.value.forEach(b => rows.push([
    b.buildingName, b.roomCount, b.occupancyRate, b.receivable, b.received, b.unpaid
  ]))
  rows.push([])
  rows.push(['房间', '楼栋', '租户', '账单数', '应收', '实收', '未收'])
  byRoom.value.forEach(r => rows.push([
    r.roomNumber, r.buildingName || '', r.tenantName || '', r.billCount, r.receivable, r.received, r.unpaid
  ]))
  rows.push([])
  rows.push(['费用构成', '金额'])
  costBreakdown.value.forEach(c => rows.push([c.name, c.amount]))

  const csv = '\ufeff' + rows
    .map(r => r.map(v => {
      const s = v == null ? '' : String(v)
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
    }).join(','))
    .join('\n')

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `财务报表_${p.startMonth || ''}_${p.endMonth || ''}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('报表已导出')
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
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

.page-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-main);
}

.page-subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.page-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.caliber-tip {
  margin-bottom: 16px;
}

.kpi-tip {
  margin-left: 6px;
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 400;
}

/* 关键指标 */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.kpi-item {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--border-color);
}

.kpi-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.kpi-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.tone-success {
  color: var(--success-color);
}

.tone-warning {
  color: #d97706;
}

.tone-danger {
  color: var(--danger-color);
}

.section-row {
  margin-top: 16px;
}

.chart {
  height: 300px;
  width: 100%;
}

.rate-text {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.room-no {
  font-weight: 600;
  color: var(--text-main);
  margin-right: 8px;
}

.room-building {
  font-size: 12px;
  color: var(--text-muted);
}

@media (max-width: 1200px) {
  .kpi-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 700px) {
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
