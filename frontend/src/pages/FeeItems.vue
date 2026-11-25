<template>
  <div class="page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div>
            <span class="page-title">费用项配置</span>
            <span class="page-subtitle">共 {{ feeItems.length }} 项</span>
          </div>
          <div class="page-actions">
            <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增费用项</el-button>
          </div>
        </div>
      </template>

      <!-- 层级说明：解决「费用项」和「楼栋单价」「房间费用」的关系困惑 -->
      <el-alert type="info" :closable="false" show-icon class="guide-alert">
        <template #title>
          <div class="guide-title">水/电单价怎么取值？</div>
        </template>
        <div class="guide-body">
          <div class="guide-step">
            <el-tag size="small" type="success">优先级 1</el-tag>
            <span>楼栋单价：在「房屋管理 → 楼栋」中配置，不同楼栋可以不同价。</span>
          </div>
          <div class="guide-step">
            <el-tag size="small" type="warning">优先级 2</el-tag>
            <span>本页的水费/电费费用项：楼栋未配置时作为全局兜底价。</span>
          </div>
          <div class="guide-step">
            <el-tag size="small" type="info">全局</el-tag>
            <span>其他费用（如垃圾清运费）：本页配置，对所有房间生效，账单生成时计入。</span>
          </div>
        </div>
      </el-alert>

      <el-tabs v-model="activeTab" class="fee-tabs">
        <el-tab-pane label="费用项" name="items">
          <el-table :data="feeItems" v-loading="loading" style="width: 100%" empty-text="还没有费用项，点击「新增费用项」开始">
            <el-table-column prop="name" label="费用名称" min-width="150">
              <template #default="{ row }">
                <span class="fee-name">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="130" align="right">
              <template #default="{ row }">
                <span class="fee-price">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</span>
                <span class="fee-unit">{{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类别" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="categoryTagType(row.category)" effect="light">
                  {{ getCategoryText(row.category) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="作用范围" width="140">
              <template #default="{ row }">
                <span class="scope-hint">{{ scopeHint(row.category) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="90" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="toggleEnabled(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="楼栋单价一览" name="buildings">
          <div class="tab-hint">
            这里只读展示各楼栋的水电单价。修改请前往「房屋管理 → 楼栋 → 编辑」。
          </div>
          <el-table :data="buildings" v-loading="loadingBuildings" style="width: 100%" empty-text="还没有楼栋">
            <el-table-column prop="name" label="楼栋" min-width="160" />
            <el-table-column label="水费单价" width="150" align="right">
              <template #default="{ row }">
                <span v-if="row.waterUnitPrice != null">¥{{ Number(row.waterUnitPrice).toFixed(2) }} / 吨</span>
                <el-tag v-else size="small" type="info" effect="plain">未配置</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="电费单价" width="150" align="right">
              <template #default="{ row }">
                <span v-if="row.electricUnitPrice != null">¥{{ Number(row.electricUnitPrice).toFixed(2) }} / 度</span>
                <el-tag v-else size="small" type="info" effect="plain">未配置</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="生效价格" min-width="220">
              <template #default="{ row }">
                <div class="effective-price">
                  <span>水 ¥{{ effectivePrice(row, 'water').toFixed(2) }}</span>
                  <span>电 ¥{{ effectivePrice(row, 'electric').toFixed(2) }}</span>
                  <el-tag v-if="needsFallback(row)" size="small" type="warning" effect="plain">
                    部分取全局兜底
                  </el-tag>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog :title="editId ? '编辑费用项' : '新增费用项'" v-model="showDialog" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="费用名称" required>
          <el-input v-model.trim="form.name" placeholder="如：水费、垃圾清运费" />
        </el-form-item>
        <el-form-item label="单价" required>
          <el-input-number v-model="form.unitPrice" :min="0" :step="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="form.unit" style="width: 100%" allow-create filterable>
            <el-option label="元/月" value="元/月" />
            <el-option label="元/度" value="元/度" />
            <el-option label="元/吨" value="元/吨" />
            <el-option label="元/次" value="元/次" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="水费（全局兜底）" value="WATER" />
            <el-option label="电费（全局兜底）" value="ELECTRIC" />
            <el-option label="其他（全局生效）" value="OTHER" />
            <el-option label="基础费用" value="BASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getFeeItems,
  createFeeItem,
  updateFeeItem,
  deleteFeeItem,
  getBuildings,
  getErrorMessage
} from '@/api'

const feeItems = ref([])
const buildings = ref([])
const loading = ref(false)
const loadingBuildings = ref(false)
const activeTab = ref('items')
const showDialog = ref(false)
const editId = ref(null)
const saving = ref(false)
const form = ref({
  name: '',
  unitPrice: 0,
  unit: '元/月',
  category: 'OTHER',
  enabled: true
})

const CATEGORY_TEXT = { BASE: '基础费用', WATER: '水费', ELECTRIC: '电费', OTHER: '其他' }

function getCategoryText(cat) {
  return CATEGORY_TEXT[cat] || cat || '其他'
}

function categoryTagType(cat) {
  if (cat === 'WATER') return 'primary'
  if (cat === 'ELECTRIC') return 'warning'
  if (cat === 'BASE') return 'success'
  return 'info'
}

// 说明每个类别的作用范围，避免管理员误配
function scopeHint(cat) {
  if (cat === 'WATER') return '楼栋未配置时兜底'
  if (cat === 'ELECTRIC') return '楼栋未配置时兜底'
  if (cat === 'BASE') return '月租金基数'
  return '全部房间生效'
}

// 取全局兜底价
const globalWater = computed(() => {
  const item = feeItems.value.find(f => f.category === 'WATER' || (f.name || '').includes('水'))
  return item ? Number(item.unitPrice) : null
})
const globalElectric = computed(() => {
  const item = feeItems.value.find(f => f.category === 'ELECTRIC' || (f.name || '').includes('电'))
  return item ? Number(item.unitPrice) : null
})

function effectivePrice(building, type) {
  const own = type === 'water' ? building.waterUnitPrice : building.electricUnitPrice
  if (own != null) return Number(own)
  const fallback = type === 'water' ? globalWater.value : globalElectric.value
  return fallback == null ? 0 : fallback
}

function needsFallback(building) {
  return building.waterUnitPrice == null || building.electricUnitPrice == null
}

async function loadFeeItems() {
  loading.value = true
  try {
    const res = await getFeeItems()
    feeItems.value = res.data || []
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '费用项加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadBuildings() {
  loadingBuildings.value = true
  try {
    const res = await getBuildings()
    buildings.value = res.data || []
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '楼栋加载失败'))
  } finally {
    loadingBuildings.value = false
  }
}

async function loadAll() {
  await Promise.all([loadFeeItems(), loadBuildings()])
}

function openCreate() {
  editId.value = null
  form.value = { name: '', unitPrice: 0, unit: '元/月', category: 'OTHER', enabled: true }
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = { ...row }
  showDialog.value = true
}

async function save() {
  if (!form.value.name || !form.value.name.trim()) {
    return ElMessage.warning('请填写费用名称')
  }
  if (form.value.unitPrice == null || Number(form.value.unitPrice) < 0) {
    return ElMessage.warning('单价不能为负数')
  }
  saving.value = true
  try {
    if (editId.value) {
      await updateFeeItem(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createFeeItem(form.value)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    await loadFeeItems()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row) {
  try {
    await updateFeeItem(row.id, { enabled: row.enabled })
    ElMessage.success('状态已更新')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error(getErrorMessage(e, '更新失败'))
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除费用项「${row.name}」吗？已生成的账单不受影响。`,
      '确认删除',
      { type: 'warning' }
    )
    await deleteFeeItem(row.id)
    ElMessage.success('删除成功')
    await loadFeeItems()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(getErrorMessage(e, '删除失败'))
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page {
  padding: 0;
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
  font-size: var(--title-page-size);
  font-weight: var(--title-page-weight);
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
}

.guide-alert {
  margin-bottom: 8px;
}

.guide-title {
  font-weight: 600;
  font-size: 13px;
}

.guide-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}

.guide-step {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-muted);
}

.fee-tabs {
  margin-top: 8px;
}

.fee-name {
  font-weight: 500;
  color: var(--text-main);
}

.fee-price {
  font-weight: 600;
  color: var(--text-main);
}

.fee-unit {
  margin-left: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.scope-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.tab-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.effective-price {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
</style>
