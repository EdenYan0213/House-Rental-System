<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-row :gutter="10" align="middle">
        <el-col :span="8">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-col>
        <el-col :span="16" style="text-align:right">
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="exportReport">导出当前日志</el-button>
          <el-button @click="runLifecycle">执行归档清理</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="12"><el-card><template #header>操作量趋势</template><div ref="trendRef" style="height:280px" /></el-card></el-col>
      <el-col :span="12"><el-card><template #header>模块分布</template><div ref="moduleRef" style="height:280px" /></el-card></el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top:12px">
      <el-col :span="12"><el-card><template #header>操作人排行榜</template><div ref="userRef" style="height:280px" /></el-card></el-col>
      <el-col :span="12"><el-card><template #header>失败操作统计</template><div ref="failRef" style="height:280px" /></el-card></el-col>
    </el-row>

    <el-card shadow="never" style="margin-top:12px">
      <template #header>慢操作 Top10</template>
      <el-table :data="slowTop" border>
        <el-table-column prop="module" label="模块" width="140" />
        <el-table-column prop="action" label="类型" width="100" />
        <el-table-column prop="summary" label="摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时(ms)" width="120" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { exportLogs, getLogAnalytics, runLogLifecycle } from '../api'

const dateRange = ref([])
const slowTop = ref([])
const trendRef = ref(null)
const moduleRef = ref(null)
const userRef = ref(null)
const failRef = ref(null)

let trendChart
let moduleChart
let userChart
let failChart

function params() {
  return {
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1]
  }
}

function initCharts() {
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  if (!moduleChart) moduleChart = echarts.init(moduleRef.value)
  if (!userChart) userChart = echarts.init(userRef.value)
  if (!failChart) failChart = echarts.init(failRef.value)
}

function renderCharts(data) {
  initCharts()
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (data.trend || []).map(i => i.date) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', smooth: true, data: (data.trend || []).map(i => i.count) }]
  })

  moduleChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: ['35%', '70%'], data: (data.moduleDistribution || []).map(i => ({ name: i.module, value: i.count })) }]
  })

  userChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (data.topOperators || []).map(i => i.operator) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: (data.topOperators || []).map(i => i.count) }]
  })

  failChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (data.failStats || []).map(i => `${i.module}/${i.action}`) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: (data.failStats || []).map(i => i.count) }]
  })
}

async function loadData() {
  try {
    const res = await getLogAnalytics(params())
    const data = res.data || {}
    slowTop.value = data.slowTop || []
    await nextTick()
    renderCharts(data)
  } catch (_) {
    ElMessage.error('加载分析数据失败')
  }
}

async function exportReport() {
  try {
    const res = await exportLogs(params())
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `operation_analysis_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (_) {
    ElMessage.error('导出失败')
  }
}

async function runLifecycle() {
  try {
    const res = await runLogLifecycle()
    const d = res.data || {}
    ElMessage.success(`归档完成: archived=${d.archived || 0}, onlineDeleted=${d.removedOnline || 0}, archiveDeleted=${d.removedArchive || 0}`)
  } catch (_) {
    ElMessage.error('归档执行失败')
  }
}

onMounted(loadData)
</script>
