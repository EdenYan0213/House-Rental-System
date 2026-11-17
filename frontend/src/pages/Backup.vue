<template>
  <div class="backup-management">
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span>自动备份设置</span>
        </div>
      </template>
      <el-form :model="config" label-width="120px">
        <el-form-item label="启用自动备份">
          <el-switch v-model="config.enabled" @change="handleConfigChange" />
        </el-form-item>
        <el-form-item label="备份周期">
          <el-radio-group v-model="config.backupType" @change="handleConfigChange">
            <el-radio value="daily">每日</el-radio>
            <el-radio value="weekly">每周</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备份时间" v-if="config.backupType === 'weekly'">
          <el-select v-model="config.backupDay" @change="handleConfigChange">
            <el-option :value="1" label="周一" />
            <el-option :value="2" label="周二" />
            <el-option :value="3" label="周三" />
            <el-option :value="4" label="周四" />
            <el-option :value="5" label="周五" />
            <el-option :value="6" label="周六" />
            <el-option :value="7" label="周日" />
          </el-select>
        </el-form-item>
        <el-form-item label="备份时间">
          <el-time-select
            v-model="config.backupTime"
            start="00:00"
            step="00:30"
            end="23:30"
            placeholder="选择时间"
            @change="handleConfigChange"
          />
          <span class="time-hint">每日凌晨执行备份</span>
        </el-form-item>
        <el-form-item label="保留份数">
          <el-input-number v-model="config.retentionCount" :min="1" :max="30" @change="handleConfigChange" />
          <span class="time-hint">自动备份超出此数量将自动删除最旧备份</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="action-card">
      <template #header>
        <div class="card-header">
          <span>手动备份</span>
        </div>
      </template>
      <el-button type="primary" @click="handleManualBackup" :loading="backupLoading">
        立即备份
      </el-button>
      <el-upload
        v-if="canRestore"
        class="upload-restore"
        :show-file-list="false"
        accept=".zip,application/zip"
        :before-upload="beforeUploadRestoreFile"
        :http-request="handleUploadAndRestore"
      >
        <el-button type="danger" :loading="uploadRestoreLoading">
          上传并恢复
        </el-button>
      </el-upload>
      <span class="time-hint">手动备份不占用自动备份保留名额</span>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>备份记录</span>
          <el-button type="primary" @click="loadBackups">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>
      <el-table :data="backups" stripe>
        <el-table-column prop="backupTime" label="备份时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.backupTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'auto' ? 'info' : 'success'">
              {{ backupTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'">
              {{ backupStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleDownload(row)"
              :disabled="row.status !== 'success'"
            >
              下载
            </el-button>
            <el-button 
              type="warning" 
              size="small" 
              @click="handleRestore(row)"
              v-if="canRestore"
              :disabled="row.status !== 'success'"
            >
              恢复
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              @click="handleDelete(row)"
              v-if="canDelete"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="restoreDialogVisible" title="恢复确认" width="500px">
      <el-alert
        title="警告：恢复操作风险提示"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #default>
          恢复操作将用备份数据覆盖当前系统所有数据，且不可逆。
          建议在恢复前手动备份当前数据。
        </template>
      </el-alert>
      <div class="confirm-text">
        <p>您正在恢复备份：<strong>{{ selectedBackup?.fileName }}</strong></p>
        <p>恢复时间：<strong>{{ formatDateTime(selectedBackup?.backupTime) }}</strong></p>
      </div>
      <el-checkbox v-model="restoreConfirmed">
        我已了解恢复操作的风险
      </el-checkbox>
      <template #footer>
        <el-button @click="restoreDialogVisible = false">取消</el-button>
        <el-button 
          type="danger" 
          @click="confirmRestore" 
          :disabled="!restoreConfirmed"
          :loading="restoreLoading"
        >
          确认恢复
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  hasRole,
  getBackupRecords,
  createManualBackup,
  deleteBackup,
  downloadBackup,
  restoreBackup,
  uploadAndRestoreBackup,
  getBackupConfig,
  updateBackupConfig
} from '@/api'
import { backupTypeText, backupStatusText } from '@/utils/labels'

const config = ref({
  enabled: false,
  backupType: 'daily',
  backupDay: 1,
  backupTime: '02:00',
  retentionCount: 7
})

const backups = ref([])
const backupLoading = ref(false)
const restoreDialogVisible = ref(false)
const restoreLoading = ref(false)
const uploadRestoreLoading = ref(false)
const selectedBackup = ref(null)
const restoreConfirmed = ref(false)
const canRestore = hasRole('ADMIN')
const canDelete = hasRole('SUPER_ADMIN')

const loadConfig = async () => {
  try {
    const { data } = await getBackupConfig()
    config.value = data
  } catch (error) {
    console.error('加载配置失败', error)
  }
}

const handleConfigChange = async () => {
  try {
    await updateBackupConfig(config.value)
    ElMessage.success('配置保存成功')
  } catch (error) {
    ElMessage.error('配置保存失败')
  }
}

const loadBackups = async () => {
  try {
    const { data } = await getBackupRecords()
    backups.value = data
  } catch (error) {
    console.error('加载备份记录失败', error)
  }
}

const handleManualBackup = async () => {
  try {
    backupLoading.value = true
    await createManualBackup()
    ElMessage.success('备份创建成功')
    await loadBackups()
  } catch (error) {
    ElMessage.error('备份创建失败: ' + (error.response?.data?.message || error.message))
  } finally {
    backupLoading.value = false
  }
}

const handleDownload = async (row) => {
  try {
    const response = await downloadBackup(row.id)
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', row.fileName)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('下载失败')
  }
}

const handleRestore = (row) => {
  selectedBackup.value = row
  restoreConfirmed.value = false
  restoreDialogVisible.value = true
}

const beforeUploadRestoreFile = (file) => {
  const name = (file.name || '').toLowerCase()
  if (!name.endsWith('.zip')) {
    ElMessage.error('仅支持上传 .zip 备份文件')
    return false
  }
  return true
}

const handleUploadAndRestore = async (options) => {
  const { file } = options
  try {
    await ElMessageBox.confirm(
      `确定使用文件 \"${file.name}\" 执行恢复吗？该操作会覆盖当前数据且不可逆。`,
      '上传恢复确认',
      {
        confirmButtonText: '确认恢复',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    uploadRestoreLoading.value = true
    const { data } = await uploadAndRestoreBackup(file)
    if (data?.status === 'success') {
      ElMessage.success('上传并恢复成功')
      await loadBackups()
      options.onSuccess?.(data)
      return
    }

    const reason = data?.failReason || data?.message || '恢复失败'
    ElMessage.error('上传恢复失败: ' + reason)
    options.onError?.(new Error(reason))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('上传恢复失败: ' + (error.response?.data?.message || error.message))
      options.onError?.(error)
    }
  } finally {
    uploadRestoreLoading.value = false
  }
}

const confirmRestore = async () => {
  try {
    restoreLoading.value = true
    await restoreBackup(selectedBackup.value.id)
    ElMessage.success('恢复成功')
    restoreDialogVisible.value = false
  } catch (error) {
    ElMessage.error('恢复失败: ' + (error.response?.data?.message || error.message))
  } finally {
    restoreLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除备份 "${row.fileName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteBackup(row.id)
    ElMessage.success('删除成功')
    await loadBackups()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  const date = new Date(datetime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

onMounted(() => {
  loadConfig()
  loadBackups()
})
</script>

<style scoped>
.backup-management {
  padding: 20px;
}

.config-card,
.action-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.time-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.upload-restore {
  display: inline-block;
  margin-left: 12px;
}

.confirm-text {
  margin: 20px 0;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.confirm-text p {
  margin: 5px 0;
}
</style>
