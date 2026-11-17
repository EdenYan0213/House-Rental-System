<template>
  <div style="padding: 20px">
    <el-tabs>
      <el-tab-pane label="维修工管理">
        <el-row justify="space-between" align="middle" style="margin-bottom: 12px">
          <h3>维修工列表</h3>
          <el-button type="primary" @click="openWorkerCreate">新增维修工</el-button>
        </el-row>
        <el-table :data="workers" v-loading="loading" border>
          <el-table-column prop="name" label="姓名" />
          <el-table-column prop="phone" label="手机号" />
          <el-table-column prop="totalOrders" label="工单数量" width="100" />
          <el-table-column prop="avgCompleteHours" label="平均完成时长(小时)" width="150" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210">
            <template #default="{ row }">
              <el-button size="small" @click="openWorkerEdit(row)">编辑</el-button>
              <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggleWorker(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="器具模板管理">
        <el-row justify="space-between" align="middle" style="margin-bottom: 12px">
          <h3>器具模板</h3>
          <el-button type="primary" @click="openTemplateCreate">新增模板</el-button>
        </el-row>
        <el-table :data="templates" border>
          <el-table-column prop="name" label="器具名称" />
          <el-table-column prop="brand" label="品牌" />
          <el-table-column prop="defaultQuantity" label="默认数量" width="100" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" @click="openTemplateEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="workerEditId ? '编辑维修工' : '新增维修工'" v-model="showWorkerDialog" width="460px">
      <el-form :model="workerForm" label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="workerForm.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="workerForm.phone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="workerForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWorkerDialog = false">取消</el-button>
        <el-button type="primary" @click="saveWorker">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="templateEditId ? '编辑模板' : '新增模板'" v-model="showTemplateDialog" width="460px">
      <el-form :model="templateForm" label-width="90px">
        <el-form-item label="器具名称" required><el-input v-model="templateForm.name" /></el-form-item>
        <el-form-item label="品牌"><el-input v-model="templateForm.brand" /></el-form-item>
        <el-form-item label="默认数量"><el-input-number v-model="templateForm.defaultQuantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="templateForm.unit" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="templateForm.sortOrder" :min="0" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTemplateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRepairWorkers,
  createRepairWorker,
  updateRepairWorker,
  toggleRepairWorker,
  getApplianceTemplates,
  createApplianceTemplate,
  updateApplianceTemplate,
  deleteApplianceTemplate,
  getErrorMessage,
} from '../api'

const workers = ref([])
const templates = ref([])
const loading = ref(false)

const showWorkerDialog = ref(false)
const workerEditId = ref(null)
const workerForm = ref({ name: '', phone: '', status: 1, remark: '' })

const showTemplateDialog = ref(false)
const templateEditId = ref(null)
const templateForm = ref({ name: '', brand: '', defaultQuantity: 1, unit: '个', sortOrder: 0 })

async function load() {
  loading.value = true
  try {
    const [w, t] = await Promise.all([getRepairWorkers(), getApplianceTemplates()])
    workers.value = w.data || []
    templates.value = t.data || []
  } finally {
    loading.value = false
  }
}

function openWorkerCreate() {
  workerEditId.value = null
  workerForm.value = { name: '', phone: '', status: 1, remark: '' }
  showWorkerDialog.value = true
}

function openWorkerEdit(row) {
  workerEditId.value = row.id
  workerForm.value = { ...row }
  showWorkerDialog.value = true
}

async function saveWorker() {
  if (!workerForm.value.name) {
    ElMessage.warning('请输入姓名')
    return
  }
  if (workerEditId.value) {
    await updateRepairWorker(workerEditId.value, workerForm.value)
  } else {
    await createRepairWorker(workerForm.value)
  }
  showWorkerDialog.value = false
  ElMessage.success('保存成功')
  await load()
}

async function toggleWorker(row) {
  await toggleRepairWorker(row.id)
  ElMessage.success('状态已更新')
  await load()
}

function openTemplateCreate() {
  templateEditId.value = null
  templateForm.value = { name: '', brand: '', defaultQuantity: 1, unit: '个', sortOrder: 0 }
  showTemplateDialog.value = true
}

function openTemplateEdit(row) {
  templateEditId.value = row.id
  templateForm.value = { ...row }
  showTemplateDialog.value = true
}

async function saveTemplate() {
  if (!templateForm.value.name) {
    ElMessage.warning('请输入器具名称')
    return
  }
  if (templateEditId.value) {
    await updateApplianceTemplate(templateEditId.value, templateForm.value)
  } else {
    await createApplianceTemplate(templateForm.value)
  }
  showTemplateDialog.value = false
  ElMessage.success('保存成功')
  await load()
}

async function removeTemplate(row) {
  await ElMessageBox.confirm(`确定删除模板 ${row.name} 吗？`, '提示', { type: 'warning' })
  await deleteApplianceTemplate(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>
