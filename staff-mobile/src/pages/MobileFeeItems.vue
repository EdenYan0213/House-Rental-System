<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">费率配置</h1>
            <p class="m-head-sub">
              共 <span class="m-num count">{{ items.length }}</span> 项
              <template v-if="enabledCount !== items.length">· 启用 {{ enabledCount }}</template>
            </p>
          </div>
          <el-button
            v-if="canManage"
            type="primary"
            :icon="Plus"
            class="head-btn"
            @click="openCreate"
          >新建</el-button>
        </div>
      </header>

      <div v-if="!loading && items.length === 0" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><Money /></el-icon>
        <p class="empty-title">暂无费率项</p>
        <p class="empty-desc">{{ canManage ? '点击右上角「新建」添加水费、电费等。' : '请联系管理员配置费率。' }}</p>
      </div>

      <div v-else class="fee-list">
        <article v-for="item in items" :key="item.id" class="m-card fee-card" :class="{ 'is-off': !item.enabled }">
          <div class="fee-main">
            <div class="fee-top">
              <span class="fee-name">{{ item.name }}</span>
              <span class="fee-state" :class="item.enabled ? 'is-on' : 'is-off-tag'">
                {{ item.enabled ? '启用' : '停用' }}
              </span>
            </div>
            <div class="fee-price m-num">
              ￥{{ Number(item.unitPrice || 0).toFixed(2) }}
              <span class="fee-unit">/ {{ item.unit || '单位' }}</span>
            </div>
            <div v-if="item.category" class="fee-cat">{{ categoryText(item.category) }}</div>
          </div>

          <div v-if="canManage" class="fee-ops">
            <button type="button" class="op-btn" @click.stop="openEdit(item)">编辑</button>
            <button
              type="button"
              class="op-btn"
              :class="{ 'op-btn--danger': item.enabled }"
              @click.stop="toggleEnabled(item)"
            >{{ item.enabled ? '停用' : '启用' }}</button>
            <button type="button" class="op-btn op-btn--danger" @click.stop="confirmDelete(item)">删除</button>
          </div>
        </article>
      </div>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editId ? '编辑费率项' : '新建费率项'"
      width="92%"
      align-center
    >
      <el-form :model="form" label-position="top" class="create-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：水费 / 电费 / 物业费" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="单价（元）">
              <el-input-number v-model="form.unitPrice" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="form.unit" placeholder="如：元/吨" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：水电 / 固定费用" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ editId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </PullRefresh>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Money } from '@element-plus/icons-vue'
import PullRefresh from '@/components/PullRefresh.vue'
import {
  getFeeItems, createFeeItem, updateFeeItem, deleteFeeItem,
  canManageFeeItems, getErrorMessage
} from '@/api'

const loading = ref(false)
const items = ref([])
const canManage = canManageFeeItems()

const enabledCount = computed(() => items.value.filter(i => i.enabled).length)

/** 后端 category 存的是 WATER/ELECTRIC/OTHER 之类的英文枚举。 */
function categoryText(cat) {
  const raw = String(cat || '').trim().toUpperCase()
  return {
    WATER: '水费',
    ELECTRIC: '电费',
    ELECTRICITY: '电费',
    GAS: '燃气费',
    OTHER: '其他',
    FIXED: '固定费用'
  }[raw] || cat || ''
}

/* ---------- 新建 / 编辑 ---------- */
const formVisible = ref(false)
const submitting = ref(false)
const editId = ref(null)
const emptyForm = () => ({ name: '', unitPrice: 0, unit: '', category: '', enabled: true })
const form = ref(emptyForm())

function openCreate() {
  editId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEdit(item) {
  editId.value = item.id
  form.value = {
    name: item.name || '',
    unitPrice: Number(item.unitPrice || 0),
    unit: item.unit || '',
    category: item.category || '',
    enabled: item.enabled !== false
  }
  formVisible.value = true
}

async function submitForm() {
  const name = form.value.name.trim()
  if (!name) {
    ElMessage.warning('请填写名称')
    return
  }
  submitting.value = true
  try {
    if (editId.value) {
      await updateFeeItem(editId.value, { ...form.value, name })
      ElMessage.success('已保存')
    } else {
      await createFeeItem({ ...form.value, name })
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '保存失败'))
  } finally {
    submitting.value = false
  }
}

/* ---------- 启用/停用 ---------- */
async function toggleEnabled(item) {
  const next = !item.enabled
  try {
    // 只提交 enabled：后端 update 是部分更新，不会清掉其它字段
    await updateFeeItem(item.id, { enabled: next })
    ElMessage.success(next ? '已启用' : '已停用')
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  }
}

async function confirmDelete(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.name}」吗？已生成的历史账单不受影响。`,
      '删除费率项',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteFeeItem(item.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '删除失败，可能已被账单引用'))
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getFeeItems()
    const data = res.data
    items.value = Array.isArray(data) ? data : (data?.content || [])
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载费率失败'))
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

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.fee-list {
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
  padding-bottom: calc(56px + var(--m-safe-bottom));
}

.fee-card {
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 0;
  overflow: hidden;
}

.fee-card.is-off {
  opacity: 0.68;
}

.fee-main {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
}

.fee-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fee-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.fee-state {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  line-height: 1.6;
}

.fee-state.is-on {
  color: var(--accent-3);
  background: var(--accent-1);
  border: 1px solid var(--accent-2);
}

.fee-state.is-off-tag {
  color: var(--text-muted);
  background: var(--bg-subtle);
  border: 1px solid var(--border-color);
}

.fee-price {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.fee-unit {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-muted);
}

.fee-cat {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-muted);
}

.fee-ops {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 10px 10px;
  border-left: 1px solid var(--border-color);
  flex: 0 0 auto;
}

.op-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-main);
  border-radius: 8px;
  padding: 7px 11px;
  font-size: 12px;
  font-family: inherit;
  min-height: 32px;
  cursor: pointer;
  white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
}

.op-btn:active {
  background: var(--accent-1);
  border-color: var(--accent-2);
}

.op-btn--danger {
  color: var(--danger-color);
  border-color: color-mix(in srgb, var(--danger-color) 30%, transparent);
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

.create-form :deep(.el-input-number) {
  width: 100%;
}

.full-width {
  width: 100%;
}

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
