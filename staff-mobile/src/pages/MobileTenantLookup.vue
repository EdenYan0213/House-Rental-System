<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">租户查询</h1>
            <p class="m-head-sub">共 <span class="m-num count">{{ tenants.length }}</span> 位租户</p>
          </div>
          <el-button
            v-if="canAdd"
            type="primary"
            :icon="Plus"
            class="head-btn"
            @click="openCreate"
          >新建</el-button>
        </div>
      </header>

      <!-- 搜索 -->
      <el-input
        v-model="keyword"
        clearable
        placeholder="姓名 / 手机号 / 房间号"
        :prefix-icon="Search"
        class="search-input"
        @keyup.enter="load"
        @clear="load"
      />

      <!-- 租户卡 -->
      <div v-if="pagedTenants.length > 0" class="tenant-list">
        <article
          v-for="item in pagedTenants"
          :key="item.id"
          class="m-card tenant-card"
          @click="openTenant(item)"
        >
          <div class="tenant-top">
            <div class="tenant-name-row">
              <span class="tenant-avatar">{{ (item.name || '?').slice(0, 1) }}</span>
              <span class="tenant-name">{{ item.name || '-' }}</span>
            </div>
            <el-icon class="tenant-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="tenant-info">
            <div class="info-row">
              <el-icon class="info-icon"><Iphone /></el-icon>
              <span class="m-num">{{ item.phone || '-' }}</span>
            </div>
            <div class="info-row">
              <el-icon class="info-icon"><Postcard /></el-icon>
              <span class="m-num">{{ mask(item.idCard) || '-' }}</span>
            </div>
            <div class="info-row" v-if="item.emergencyContact">
              <el-icon class="info-icon"><User /></el-icon>
              <span>{{ item.emergencyContact }}</span>
            </div>
          </div>
        </article>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><Search /></el-icon>
        <p class="empty-title">未找到租户</p>
        <p class="empty-desc">{{ keyword ? '换个关键词试试' : '当前还没有租户记录' }}</p>
      </div>

    <!-- 新建租户 -->
    <el-dialog v-model="createVisible" title="新建租户" width="92%" align-center>
      <el-form :model="createForm" label-position="top" class="create-form">
        <el-form-item label="姓名" required>
          <el-input v-model="createForm.name" placeholder="请输入租户姓名" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="createForm.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="createForm.idCard" placeholder="可选，用于实名" maxlength="18" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="紧急联系人">
              <el-input v-model="createForm.emergencyContact" placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急电话">
              <el-input v-model="createForm.emergencyPhone" placeholder="可选" maxlength="11" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

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

      <div v-if="hasMoreTenants" class="load-more">
        <el-button text type="primary" @click="showMoreTenants">
          加载更多（还有 {{ remainingTenants }} 条）
        </el-button>
      </div>
  </PullRefresh>
</template>

<script setup>
import { useIncrementalList } from '@/utils/incrementalList'
import PullRefresh from '../components/PullRefresh.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight, Iphone, Postcard, User, Plus } from '@element-plus/icons-vue'
import { getTenants, getContracts, createTenant, canAddTenant, getErrorMessage } from '@/api'
import { unwrapList } from '@/utils/mobileApi'
import { getMobileCache, setMobileCache } from '@/utils/mobileCache'

const TENANT_CACHE_TTL = 60 * 60 * 1000

const router = useRouter()
const loading = ref(false)

// ---- 新建租户 ----
const canAdd = canAddTenant()
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({
  name: '', phone: '', idCard: '',
  emergencyContact: '', emergencyPhone: '', remark: ''
})

function openCreate() {
  createForm.value = {
    name: '', phone: '', idCard: '',
    emergencyContact: '', emergencyPhone: '', remark: ''
  }
  createVisible.value = true
}

async function submitCreate() {
  const f = createForm.value
  if (!f.name.trim()) {
    ElMessage.warning('请填写租户姓名')
    return
  }
  if (!/^1\d{10}$/.test(f.phone.trim())) {
    ElMessage.warning('请填写 11 位手机号')
    return
  }
  creating.value = true
  try {
    await createTenant({
      name: f.name.trim(),
      phone: f.phone.trim(),
      idCard: f.idCard.trim() || null,
      emergencyContact: f.emergencyContact.trim() || null,
      emergencyPhone: f.emergencyPhone.trim() || null,
      remark: f.remark.trim() || null
    })
    ElMessage.success('租户已创建')
    createVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '创建失败'))
  } finally {
    creating.value = false
  }
}
const keyword = ref('')
const tenants = ref([])
const {
  visible: pagedTenants,
  hasMore: hasMoreTenants,
  remaining: remainingTenants,
  showMore: showMoreTenants,
  reset: resetTenants
} =
  useIncrementalList(tenants, 20)
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
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-btn {
  flex: 0 0 auto;
  min-height: 36px;
  border-radius: 9px;
}

.create-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 4px;
}

.create-form :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.search-input :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.tenant-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.tenant-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: border-color 0.15s ease, transform 0.12s ease;
}

.tenant-card:active {
  transform: scale(0.985);
  border-color: var(--accent-2);
}

.tenant-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tenant-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 姓名首字头像 */
.tenant-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--accent-1);
  color: var(--accent-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  flex: 0 0 auto;
}

.tenant-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tenant-arrow {
  color: var(--text-muted);
  opacity: 0.45;
  flex: 0 0 auto;
}

.tenant-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.info-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.info-icon {
  flex: 0 0 auto;
  opacity: 0.6;
}

/* 空态 */
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
</style>
