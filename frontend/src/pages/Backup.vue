<template>
  <div class="page">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2>数据备份与恢复</h2>
        <p class="page-sub">
          自动备份按设定周期执行；手动备份不占用保留名额，可随时创建。
        </p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="reloadAll">刷新</el-button>
      </div>
    </div>

    <!-- 状态概览：三块关键信息放在最上面 -->
    <div class="stat-grid">
      <div class="stat-box">
        <div class="stat-box-label">自动备份</div>
        <div class="stat-box-value" :class="config.enabled ? 'is-on' : 'is-off'">
          {{ config.enabled ? '已开启' : '已关闭' }}
        </div>
        <div class="stat-box-hint">
          {{ config.enabled
            ? `${cycleText} ${config.backupTime}`
            : '数据不会自动备份，建议开启' }}
        </div>
      </div>

      <div class="stat-box">
        <div class="stat-box-label">最近备份</div>
        <div class="stat-box-value">
          {{ latestBackup ? formatDateTime(latestBackup.backupTime) : '暂无' }}
        </div>
        <div class="stat-box-hint">
          {{ latestBackup
            ? `${backupTypeText(latestBackup.type)} · ${formatFileSize(latestBackup.fileSize)}`
            : '还没有可用的备份文件' }}
        </div>
      </div>

      <div class="stat-box">
        <div class="stat-box-label">保留份数</div>
        <div class="stat-box-value">{{ config.retentionCount }} 份</div>
        <div class="stat-box-hint">
          当前共 {{ backups.length }} 份记录，超出后自动删除最旧的
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
    <el-card class="config-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>自动备份设置</span>
          <el-tag v-if="config.enabled" type="success" size="small" effect="plain">运行中</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">已停用</el-tag>
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

      </el-col>

      <el-col :xs="24" :lg="10">
    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>手动操作</span>
        </div>
      </template>

      <div class="action-block">
        <div class="action-text">
          <div class="action-title">立即备份</div>
          <div class="action-desc">把当前数据库与上传文件打包成一个 zip，不占用自动备份保留名额。</div>
        </div>
        <el-button type="primary" :icon="Download" :loading="backupLoading" @click="handleManualBackup">
          立即备份
        </el-button>
      </div>

      <el-divider style="margin: var(--space-4) 0" />

      <div class="action-block">
        <div class="action-text">
          <div class="action-title">从备份恢复</div>
          <div class="action-desc">上传之前导出的 zip 文件，系统会用其中的数据覆盖当前库，请谨慎操作。</div>
        </div>
        <el-upload
          v-if="canRestore"
          :show-file-list="false"
          accept=".zip,application/zip"
          :before-upload="beforeUploadRestoreFile"
          :http-request="handleUploadAndRestore"
        >
          <el-button type="danger" plain :icon="Upload" :loading="uploadRestoreLoading">
            上传并恢复
          </el-button>
        </el-upload>
        <span v-else class="action-disabled">当前账号无恢复权限</span>
      </div>
    </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>备份记录</span>
          <el-button size="small" :icon="Refresh" @click="loadBackups">刷新</el-button>
        </div>
      </template>
      <el-table :data="backups" stripe>
              <template #empty>
                <EmptyState icon="document" title="还没有备份记录" description="建议先创建一次手动备份，确保数据安全" compact />
              </template>
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
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { Refresh, Download, Upload } from '@element-plus/icons-vue'
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
const loading = ref(false)

/** 最近一次成功备份，用于顶部概览。 */
const latestBackup = computed(() => {
  const ok = backups.value.filter(b => b.status === 'success')
  if (!ok.length) return null
  return [...ok].sort((a, b) => new Date(b.backupTime) - new Date(a.backupTime))[0]
})

/** 备份周期的中文描述，概览卡用。 */
const cycleText = computed(() => {
  if (config.value.backupType === 'weekly') {
    const days = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
    return `每周${(days[config.value.backupDay] || '一').slice(1)}`
  }
  return '每日'
})

/** 一次性刷新配置与记录（页头「刷新」用）。 */
async function reloadAll() {
  loading.value = true
  try {
    await Promise.all([loadConfig(), loadBackups()])
  } finally {
    loading.value = false
  }
}
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
  padding: 0;
}

/* ---------- 顶部状态概览 ----------
 * 三块关键信息（是否自动备份 / 最近一次 / 保留份数）优先展示，
 * 管理员进来第一眼就能判断「数据是否安全」。
 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-4);
}

.stat-box {
  padding: var(--space-4);
  border: 1px solid var(--border-color);
  border-radius: var(--card-radius);
  background: var(--bg-surface);
}

.stat-box-label {
  font-size: var(--fs-xs);
  color: var(--text-muted);
  letter-spacing: 0.02em;
  margin-bottom: var(--space-2);
}

.stat-box-value {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.3;
  margin-bottom: var(--space-1);
}

.stat-box-value.is-on { color: var(--accent-3); }
.stat-box-value.is-off { color: var(--warning-color); }

.stat-box-hint {
  font-size: var(--fs-xs);
  color: var(--text-muted);
  line-height: 1.5;
}

/* ---------- 手动操作区 ---------- */
.action-block {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.action-text {
  flex: 1 1 200px;
  min-width: 0;
}

.action-title {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: var(--space-1);
}

.action-desc {
  font-size: var(--fs-xs);
  color: var(--text-muted);
  line-height: 1.6;
}

.action-disabled {
  font-size: var(--fs-xs);
  color: var(--text-muted);
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
