<template>
  <div>
    <el-row style="margin-bottom: 15px" :gutter="10">
      <el-input v-model="operatorFilter" placeholder="操作人" clearable style="width: 150px" />
      <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-button icon="Search" @click="loadLogs">查询</el-button>
      <el-button @click="exportLogs">导出</el-button>
    </el-row>

    <el-table :data="logs" style="width: 100%" v-loading="loading">
      <el-table-column prop="operationTime" label="操作时间" width="180" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="action" label="操作内容" />
      <el-table-column prop="ip" label="IP地址" width="150" />
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 16px"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadLogs"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getOperationLogs, exportLogs as apiExportLogs } from '../api'
import { ElMessage } from 'element-plus'

const logs = ref([])
const loading = ref(false)
const operatorFilter = ref('')
const dateRange = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadLogs() {
  loading.value = true
  try {
    const params = {
      operator: operatorFilter.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value - 1,
      size: pageSize.value
    }
    const res = await getOperationLogs(params)
    logs.value = res.data.content || res.data || []
    total.value = res.data.totalElements || logs.value.length
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function exportLogs() {
  try {
    const params = {
      operator: operatorFilter.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    }
    const res = await apiExportLogs(params)
    const blob = new Blob([res.data], { type: 'application/vnd.ms-excel' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `操作日志_${new Date().toISOString().split('T')[0]}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(loadLogs)
</script>
