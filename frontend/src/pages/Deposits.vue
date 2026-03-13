<template>
  <div style="padding: 20px">
    <h2>押金管理</h2>

    <el-row style="margin-top: 16px" :gutter="10">
      <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 150px" @change="loadDeposits">
        <el-option label="全部" value="" />
        <el-option label="收取" value="RECEIVE" />
        <el-option label="退还" value="REFUND" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" @change="loadDeposits" />
    </el-row>

    <el-table :data="deposits" style="width: 100%; margin-top: 16px" v-loading="loading">
      <el-table-column prop="contractNo" label="合同编号" width="150">
        <template #default="{ row }">
          <el-link type="primary" @click="viewContract(row)">{{ row.contractNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="tenantName" label="租户姓名" />
      <el-table-column prop="roomNumber" label="房间号" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 'RECEIVE' ? 'success' : 'warning'">
            {{ row.type === 'RECEIVE' ? '收取' : '退还' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额" width="120">
        <template #default="{ row }">¥{{ row.amount?.toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="date" label="日期" width="120" />
      <el-table-column prop="remark" label="备注" />
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 16px"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadDeposits"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDeposits } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const deposits = ref([])
const loading = ref(false)
const typeFilter = ref('')
const dateRange = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadDeposits() {
  loading.value = true
  try {
    const params = {
      type: typeFilter.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value - 1,
      size: pageSize.value
    }
    const res = await getDeposits(params)
    deposits.value = res.data.content || res.data || []
    total.value = res.data.totalElements || deposits.value.length
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function viewContract(row) {
  router.push(`/contracts/${row.contractId}`)
}

onMounted(loadDeposits)
</script>
