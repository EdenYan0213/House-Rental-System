<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>费用项配置</h2>
      <el-button type="primary" @click="openCreate">新增费用项</el-button>
    </el-row>

    <el-table :data="feeItems" style="width: 100%; margin-top: 16px" v-loading="loading">
      <el-table-column prop="name" label="费用名称" />
      <el-table-column prop="unitPrice" label="单价">
        <template #default="{ row }">¥{{ row.unitPrice }}</template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" />
      <el-table-column prop="category" label="类别">
        <template #default="{ row }">
          <el-tag>{{ getCategoryText(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" @change="toggleEnabled(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="editId ? '编辑费用项' : '新增费用项'" v-model="showDialog" width="450px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="费用名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="单价" required>
          <el-input-number v-model="form.unitPrice" :min="0" :step="0.01" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="form.unit" style="width: 100%">
            <el-option label="元/月" value="元/月" />
            <el-option label="元/度" value="元/度" />
            <el-option label="元/吨" value="元/吨" />
            <el-option label="元/次" value="元/次" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="基础费用" value="BASE" />
            <el-option label="水费" value="WATER" />
            <el-option label="电费" value="ELECTRIC" />
            <el-option label="其他" value="OTHER" />
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
import { ref, onMounted } from 'vue'
import { getFeeItems, createFeeItem, updateFeeItem, deleteFeeItem } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const feeItems = ref([])
const loading = ref(false)
const showDialog = ref(false)
const editId = ref(null)
const saving = ref(false)
const form = ref({
  name: '',
  unitPrice: 0,
  unit: '元/月',
  category: 'BASE',
  enabled: true
})

function getCategoryText(cat) {
  const map = { BASE: '基础费用', WATER: '水费', ELECTRIC: '电费', OTHER: '其他' }
  return map[cat] || cat
}

async function loadFeeItems() {
  loading.value = true
  try {
    const res = await getFeeItems()
    feeItems.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.value = { name: '', unitPrice: 0, unit: '元/月', category: 'BASE', enabled: true }
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = { ...row }
  showDialog.value = true
}

async function save() {
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
    loadFeeItems()
  } catch (e) {
    ElMessage.error('操作失败')
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
    ElMessage.error('更新失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除该费用项吗？', '提示', { type: 'warning' })
    await deleteFeeItem(row.id)
    ElMessage.success('删除成功')
    loadFeeItems()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(loadFeeItems)
</script>
