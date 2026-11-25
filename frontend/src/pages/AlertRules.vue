<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>操作预警</h2>
        <p class="page-sub">配置异常操作规则，触发后生成记录供跟进处理。</p>
      </div>
    </div>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>预警规则配置</span>
              <el-button type="primary" @click="openCreate">新增规则</el-button>
            </div>
          </template>
          <el-table :data="rules" border>
            <template #empty>
              <EmptyState icon="bell" title="还没有预警规则" description="设置规则后，异常操作会自动触发预警" compact />
            </template>
            <el-table-column prop="title" label="规则" />
            <el-table-column label="类型" width="150">
              <template #default="scope">{{ alertRuleTypeText(scope.row.ruleType) }}</template>
            </el-table-column>
            <el-table-column label="阈值" width="90">
              <template #default="scope">{{ scope.row.thresholdValue ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="窗口(分钟)" width="110">
              <template #default="scope">{{ scope.row.windowMinutes ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="级别" width="90">
              <template #default="scope">{{ levelText(scope.row.level) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="scope">
                <el-button link @click="editRule(scope.row)">编辑</el-button>
                <el-button link @click="toggleRule(scope.row)">{{ scope.row.enabled === 1 ? '停用' : '启用' }}</el-button>
                <el-button link type="danger" @click="removeRule(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>预警记录</span>
              <el-button @click="loadRecords">刷新</el-button>
            </div>
          </template>
          <el-table :data="records" border>
            <template #empty>
              <EmptyState icon="bell" title="还没有预警记录" description="触发规则后会在这里生成记录，可标记处理" compact />
            </template>
            <el-table-column label="时间" width="140">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column label="级别" width="80">
              <template #default="scope">{{ levelText(scope.row.level) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'warning'">{{ scope.row.status === 1 ? '已处理' : '未处理' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="scope">
                <el-button v-if="scope.row.status === 0" link type="primary" @click="handleRecord(scope.row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" title="预警规则" width="520px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="规则名称"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="form.ruleType" style="width:100%">
            <el-option label="短时高频操作" value="high_frequency" />
            <el-option label="连续登录失败" value="login_failures" />
            <el-option label="敏感操作" value="sensitive_operation" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="form.thresholdValue" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="时间窗口(分钟)"><el-input-number v-model="form.windowMinutes" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width:100%">
            <el-option label="低" :value="1" />
            <el-option label="中" :value="2" />
            <el-option label="高" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知方式"><el-input v-model="form.notifyChannels" placeholder="in_app,email,sms,wecom" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { formatDateTime } from '../utils/format'
import { onMounted, ref } from 'vue'
import { alertRuleTypeText } from '../utils/labels'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAlertRule,
  getAlertRecords,
  getAlertRules,
  getErrorMessage,
  handleAlertRecord,
  saveAlertRule
} from '../api'
import EmptyState from '../components/EmptyState.vue'

const rules = ref([])
const records = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({})

function levelText(level) {
  if (level === 1) return '低'
  if (level === 3) return '高'
  return '中'
}

async function loadRules() {
  try {
    const res = await getAlertRules()
    rules.value = res.data || []
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '预警规则加载失败'))
  }
}

async function loadRecords() {
  try {
    const res = await getAlertRecords({ page: 0, size: 20 })
    records.value = res.data?.content || []
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '预警记录加载失败'))
  }
}

function openCreate() {
  form.value = {
    title: '',
    ruleType: 'high_frequency',
    thresholdValue: 5,
    windowMinutes: 1,
    level: 2,
    notifyChannels: 'in_app',
    enabled: 1
  }
  dialogVisible.value = true
}

function editRule(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.title?.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  saving.value = true
  try {
    await saveAlertRule(form.value)
    dialogVisible.value = false
    ElMessage.success('保存成功')
    await loadRules()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function toggleRule(row) {
  try {
    await saveAlertRule({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
    ElMessage.success('状态已更新')
    await loadRules()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '状态更新失败'))
  }
}

async function removeRule(row) {
  try {
    await ElMessageBox.confirm(`确认删除规则「${row.title}」？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  try {
    await deleteAlertRule(row.id)
    ElMessage.success('删除成功')
    await loadRules()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '删除失败'))
  }
}

async function handleRecord(row) {
  try {
    await handleAlertRecord(row.id)
    ElMessage.success('已标记处理')
    await loadRecords()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '处理失败'))
  }
}

onMounted(async () => {
  await Promise.all([loadRules(), loadRecords()])
})
</script>
