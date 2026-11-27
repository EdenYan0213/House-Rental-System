<template>
  <el-dialog
    :model-value="modelValue"
    title="拍照识别抄表"
    width="960px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <!-- 第一步：上传识别 -->
    <div v-if="!result" class="ocr-upload">
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept="image/*"
        :on-change="onFileChange"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖入图片，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 jpg / png / webp，单张不超过 10MB。可拍整页抄表本（多行），也可单条记录。
          </div>
        </template>
      </el-upload>

      <el-alert
        v-if="errorText"
        :title="errorText"
        type="error"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />

      <div v-if="recognizing" class="ocr-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在识别，请稍候…
      </div>
    </div>

    <!-- 第二步：确认表格 -->
    <template v-else>
      <el-alert
        v-if="result.sample"
        title="当前为示例数据（未配置视觉模型），仅用于验证流程"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />

      <div class="ocr-summary">
        共识别 <strong>{{ result.total }}</strong> 行，
        已匹配 <strong class="ok">{{ result.matchedCount }}</strong>，
        待确认 <strong class="warn">{{ result.unmatchedCount }}</strong>
      </div>

      <el-table :data="editableRows" size="small" max-height="420" style="width: 100%">
        <el-table-column label="行" width="50">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>

        <el-table-column label="房间" min-width="200">
          <template #default="{ row }">
            <!-- 已匹配：直接展示 -->
            <span v-if="row.matchStatus === 'MATCHED'" class="room-ok">
              {{ row.buildingName ? row.buildingName + ' / ' : '' }}{{ row.roomLabel }}
            </span>
            <!-- 多候选：让用户选 -->
            <el-select
              v-else-if="row.matchStatus === 'AMBIGUOUS'"
              v-model="row.roomId"
              filterable
              placeholder="多个同名房号，请选择"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="c in row.candidates"
                :key="c.roomId"
                :label="c.roomLabel"
                :value="c.roomId"
              />
            </el-select>
            <!-- 没匹配到：手动选 -->
            <el-select
              v-else
              v-model="row.roomId"
              filterable
              placeholder="未匹配，请手动选择房间"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="r in allRooms"
                :key="r.id"
                :label="r.label"
                :value="r.id"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="日期" width="150">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.recordDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>

        <el-table-column label="水表读数" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.waterReading"
              :min="0"
              :step="0.1"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>

        <el-table-column label="电表读数" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.electricReading"
              :min="0"
              :step="0.1"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>

        <el-table-column label="识别置信度" width="110">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="confidenceType(row.confidence)"
            >
              {{ row.confidence == null ? '-' : Math.round(row.confidence * 100) + '%' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <p class="ocr-hint">
        请逐行核对后再导入。低置信度（橙色/红色）的行请重点确认，避免错误读数生成账单。
      </p>

      <el-alert
        v-if="saveError"
        :title="saveError"
        type="error"
        :closable="false"
        show-icon
        style="margin-top: 8px"
      />
    </template>

    <template #footer>
      <template v-if="!result">
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button
          type="primary"
          :loading="recognizing"
          :disabled="!selectedFile"
          @click="doRecognize"
        >
          开始识别
        </el-button>
      </template>
      <template v-else>
        <el-button @click="reset">重新上传</el-button>
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="validRows.length === 0"
          @click="doSave"
        >
          确认导入 {{ validRows.length }} 条
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, UploadFilled } from '@element-plus/icons-vue'
import {
  getRooms,
  getErrorMessage,
  recognizeMeterImage,
  saveMeterReadings
} from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const selectedFile = ref(null)
const recognizing = ref(false)
const saving = ref(false)
const errorText = ref('')
const saveError = ref('')
const result = ref(null)
const editableRows = ref([])
const allRooms = ref([])

watch(() => props.modelValue, async visible => {
  if (visible) {
    reset()
    await loadRooms()
  }
})

async function loadRooms() {
  try {
    const { data } = await getRooms()
    const list = Array.isArray(data) ? data : (data.content || [])
    allRooms.value = list.map(r => ({
      id: r.id,
      label: `${r.buildingName || ''}${r.roomNumber || ''} ${r.roomNumber || ''}`.trim()
    }))
  } catch (error) {
    // 下拉只是辅助，失败不阻塞识别
    allRooms.value = []
  }
}

function onFileChange(uploadFile) {
  selectedFile.value = uploadFile.raw
  errorText.value = ''
}

async function doRecognize() {
  if (!selectedFile.value) return
  errorText.value = ''
  recognizing.value = true
  try {
    const { data } = await recognizeMeterImage(selectedFile.value)
    result.value = data
    editableRows.value = (data.rows || []).map(r => ({ ...r }))
  } catch (error) {
    errorText.value = getErrorMessage(error, '识别失败，请重试或手工录入')
  } finally {
    recognizing.value = false
  }
}

/** 只有房间、日期、两个读数都齐全的行才能导入 */
const validRows = computed(() =>
  editableRows.value.filter(r =>
    r.roomId != null
    && r.recordDate
    && r.waterReading != null
    && r.electricReading != null
  )
)

function confidenceType(value) {
  if (value == null) return 'info'
  if (value >= 0.85) return 'success'
  if (value >= 0.6) return 'warning'
  return 'danger'
}

function removeRow(index) {
  editableRows.value.splice(index, 1)
}

function reset() {
  selectedFile.value = null
  result.value = null
  editableRows.value = []
  errorText.value = ''
  saveError.value = ''
}

async function doSave() {
  saveError.value = ''
  const rows = validRows.value

  try {
    await ElMessageBox.confirm(
      `将写入 ${rows.length} 条抄表记录，覆盖同房间同日期的已有数据。确认继续？`,
      '确认导入',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  saving.value = true
  try {
    await saveMeterReadings(rows.map(r => ({
      roomId: r.roomId,
      recordDate: r.recordDate,
      waterReading: r.waterReading,
      electricReading: r.electricReading
    })))
    ElMessage.success(`已导入 ${rows.length} 条`)
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    saveError.value = getErrorMessage(error, '导入失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ocr-upload {
  padding: 8px 0;
}

.ocr-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  color: var(--text-muted, #6c7a96);
}

.ocr-summary {
  margin-bottom: 10px;
  font-size: 14px;
}

.ocr-summary .ok {
  color: var(--success-color, #2ea043);
}

.ocr-summary .warn {
  color: var(--danger-color, #e5484d);
}

.ocr-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--text-muted, #6c7a96);
  line-height: 1.6;
}

.room-ok {
  color: var(--text-main, #18243d);
}
</style>
