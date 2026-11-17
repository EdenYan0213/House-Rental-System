<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <div class="title">租户与合同</div>
      <el-button text @click="load">刷新</el-button>
    </div>

    <el-input v-model="keyword" clearable placeholder="输入姓名/手机号/房间号" @keyup.enter="load" />

    <el-card v-for="item in tenants" :key="item.id" class="panel" shadow="never" @click="openTenant(item)">
      <div class="line top">
        <div>{{ item.name || '-' }}</div>
        <el-tag size="small">{{ item.phone || '-' }}</el-tag>
      </div>
      <div class="line muted">身份证：{{ mask(item.idCard) }}</div>
      <div class="line muted">紧急联系人：{{ item.emergencyContact || '-' }}</div>
    </el-card>

    <el-empty v-if="!loading && tenants.length === 0" description="未找到租户" />

    <el-drawer v-model="showDetail" title="租户详情" size="100%">
      <template v-if="currentTenant">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="姓名">{{ currentTenant.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ currentTenant.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="身份证">{{ currentTenant.idCard || '-' }}</el-descriptions-item>
          <el-descriptions-item label="紧急联系人">{{ currentTenant.emergencyContact || '-' }}</el-descriptions-item>
          <el-descriptions-item label="紧急电话">{{ currentTenant.emergencyPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ currentTenant.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-card class="section" shadow="never">
          <template #header>当前合同</template>
          <div v-if="activeContract">
            <div class="line">房间：{{ activeContract.roomNumber || '-' }}</div>
            <div class="line">合同号：{{ activeContract.contractNo || '-' }}</div>
            <div class="line">周期：{{ activeContract.startDate }} ~ {{ activeContract.endDate }}</div>
            <div class="line">月租：￥{{ Number(activeContract.monthlyRent || 0).toFixed(2) }}</div>
            <el-button type="primary" plain style="width: 100%; margin-top: 8px" @click="goRoom(activeContract.roomId)">进入房间页</el-button>
            <el-button style="width: 100%; margin-top: 8px" @click="goBills(currentTenant)">查看账单</el-button>
          </div>
          <el-empty v-else description="暂无进行中合同" />
        </el-card>

        <el-card class="section" shadow="never">
          <template #header>历史合同</template>
          <el-empty v-if="contractHistory.length === 0" description="暂无记录" />
          <div v-for="contract in contractHistory" :key="contract.id" class="history-row">
            <div>{{ contract.roomNumber || '-' }} · {{ contract.contractNo || '-' }}</div>
            <div class="muted">{{ contract.startDate }} ~ {{ contract.endDate }} · {{ contract.status }}</div>
          </div>
        </el-card>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTenants, getContracts } from '@/api'
import { unwrapList } from '@/utils/mobileApi'
import { getMobileCache, setMobileCache } from '@/utils/mobileCache'

const TENANT_CACHE_TTL = 60 * 60 * 1000

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const tenants = ref([])
const showDetail = ref(false)
const currentTenant = ref(null)
const contractHistory = ref([])

const activeContract = computed(() => contractHistory.value.find(item => item.status === 'ACTIVE'))

function mask(idCard) {
  if (!idCard || idCard.length < 8) return idCard || '-'
  return `${idCard.slice(0, 4)}********${idCard.slice(-4)}`
}

async function load() {
  loading.value = true
  try {
    const res = await getTenants({ q: keyword.value || '' })
    tenants.value = unwrapList(res.data)
    setMobileCache(`tenants_${keyword.value || 'all'}`, tenants.value, TENANT_CACHE_TTL)
  } catch (_) {
    const cached = getMobileCache(`tenants_${keyword.value || 'all'}`)
    if (Array.isArray(cached)) {
      tenants.value = cached
      ElMessage.warning('网络异常，已显示缓存租户')
    } else {
      ElMessage.error('租户加载失败')
    }
  } finally {
    loading.value = false
  }
}

async function openTenant(item) {
  currentTenant.value = item
  showDetail.value = true
  try {
    const contractRes = await getContracts({ page: 0, size: 500 })
    const allContracts = unwrapList(contractRes.data).filter(contract => Number(contract.tenantId) === Number(item.id))
    contractHistory.value = allContracts
    setMobileCache(`tenant_contract_${item.id}`, allContracts, TENANT_CACHE_TTL)
  } catch (_) {
    contractHistory.value = getMobileCache(`tenant_contract_${item.id}`) || []
  }
}

function goRoom(roomId) {
  if (!roomId) return
  router.push(`/mobile/room/${roomId}`)
}

function goBills(tenant) {
  if (!tenant) return
  router.push({ path: '/mobile/bills', query: { q: tenant.name || tenant.phone || '' } })
}

onMounted(load)
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
  color: #1d3266;
}

.panel {
  margin-top: 10px;
  border: 1px solid #e8efff;
}

.line {
  margin-bottom: 6px;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted {
  color: #7382a1;
  font-size: 13px;
}

.section {
  margin-top: 12px;
}

.history-row {
  padding: 8px 0;
  border-bottom: 1px solid #ecf0fb;
}

.history-row:last-child {
  border-bottom: none;
}
</style>
