<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <!-- 页头 -->
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">楼栋信息</h1>
            <p class="m-head-sub">共 <span class="m-num count">{{ buildings.length }}</span> 栋</p>
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

      <!-- 空态 -->
      <div v-if="!loading && buildings.length === 0" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><OfficeBuilding /></el-icon>
        <p class="empty-title">暂无楼栋</p>
        <p class="empty-desc">请联系管理员在后台创建楼栋。</p>
      </div>

      <!-- 楼栋卡 -->
      <div v-else class="building-list">
        <article
          v-for="item in pagedBuildings"
          :key="item.id"
          class="m-card building-card"
          @click="openRooms(item)"
        >
          <div class="building-top">
            <div class="building-name">{{ item.name || '-' }}</div>
            <el-icon class="building-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="building-addr">
            <el-icon class="addr-icon"><Location /></el-icon>
            <span>{{ item.address || '未填写地址' }}</span>
          </div>

          <div class="building-prices">
            <div class="price-item">
              <span class="price-label">水价</span>
              <span class="price-value m-num">{{ money(item.waterUnitPrice) }}</span>
            </div>
            <div class="price-item">
              <span class="price-label">电价</span>
              <span class="price-value m-num">{{ money(item.electricUnitPrice) }}</span>
            </div>
            <div class="price-item" v-if="item.totalFloors">
              <span class="price-label">楼层</span>
              <span class="price-value m-num">{{ item.totalFloors }} 层</span>
            </div>
          </div>
        </article>
      </div>
    </div>

      <!-- 新建楼栋 -->
      <el-dialog v-model="createVisible" title="新建楼栋" width="92%" align-center>
        <el-form :model="createForm" label-position="top" class="create-form">
          <el-form-item label="楼栋名称" required>
            <el-input v-model="createForm.name" placeholder="如：阳光花园A栋" />
          </el-form-item>
          <el-form-item label="楼栋编号">
            <el-input v-model="createForm.code" placeholder="如：A" />
          </el-form-item>
          <el-form-item label="地址">
            <el-input v-model="createForm.address" placeholder="如：杭州市西湖区文一西路100号" />
          </el-form-item>
          <el-form-item label="楼层数">
            <el-input-number v-model="createForm.totalFloors" :min="1" :max="99" class="full-width" />
          </el-form-item>
          <el-row :gutter="10">
            <el-col :span="12">
              <el-form-item label="水费单价">
                <el-input-number v-model="createForm.waterUnitPrice" :min="0" :precision="2" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="电费单价">
                <el-input-number v-model="createForm.electricUnitPrice" :min="0" :precision="2" class="full-width" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
        </template>
      </el-dialog>

      <div v-if="hasMoreBuildings" class="load-more">
        <el-button text type="primary" @click="showMoreBuildings">
          加载更多（还有 {{ remainingBuildings }} 条）
        </el-button>
      </div>
  </PullRefresh>

</template>

<script setup>
import { useIncrementalList } from '@/utils/incrementalList'
import PullRefresh from '../components/PullRefresh.vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { OfficeBuilding, ArrowRight, Location, Plus } from '@element-plus/icons-vue'
import { getBuildings, createBuilding, canAddBuilding, getErrorMessage } from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const router = useRouter()
const loading = ref(false)

// ---- 新建楼栋 ----
const canAdd = canAddBuilding()
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({
  name: '',
  code: '',
  address: '',
  totalFloors: 6,
  waterUnitPrice: 0,
  electricUnitPrice: 0
})

function openCreate() {
  createForm.value = { name: '', code: '', address: '', totalFloors: 6, waterUnitPrice: 0, electricUnitPrice: 0 }
  createVisible.value = true
}

async function submitCreate() {
  const name = createForm.value.name.trim()
  if (!name) {
    ElMessage.warning('请填写楼栋名称')
    return
  }
  creating.value = true
  try {
    await createBuilding({ ...createForm.value, name })
    ElMessage.success('楼栋已创建')
    createVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '创建失败'))
  } finally {
    creating.value = false
  }
}
const buildings = ref([])
const {
  visible: pagedBuildings,
  hasMore: hasMoreBuildings,
  remaining: remainingBuildings,
  showMore: showMoreBuildings,
  reset: resetBuildings
} =
  useIncrementalList(buildings, 20)

function money(v) {
  return Number(v || 0).toFixed(2)
}

function openRooms(building) {
  if (!building?.id) return
  router.push(`/mobile/buildings/${building.id}/rooms`)
}

async function load() {
  loading.value = true
  try {
    const res = await getBuildings()
    buildings.value = unwrapList(res.data)
  } catch (_) {
    ElMessage.error('加载楼栋失败')
  } finally {
    loading.value = false
  }
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

/* 弹层表单：输入控件统一 44px 高 */
.create-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 4px;
}

.create-form :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.create-form :deep(.el-input-number) {
  width: 100%;
}

.full-width {
  width: 100%;
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.building-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
}

.building-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: border-color 0.15s ease, transform 0.12s ease;
}

.building-card:active {
  transform: scale(0.985);
  border-color: var(--accent-2);
}

.building-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.building-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.3;
}

.building-arrow {
  color: var(--text-muted);
  opacity: 0.45;
  flex: 0 0 auto;
}

.building-addr {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
}

.addr-icon {
  flex: 0 0 auto;
}

/* 价格行：三等分 */
.building-prices {
  display: flex;
  gap: 16px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
}

.price-item {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.price-label {
  font-size: 11px;
  color: var(--text-muted);
}

.price-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
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
