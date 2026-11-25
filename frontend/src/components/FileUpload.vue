<template>
  <div class="file-upload">
    <el-upload
      ref="uploadRef"
      :action="uploadUrl"
      :headers="headers"
      :multiple="multiple"
      :limit="limit"
      :file-list="fileList"
      :before-upload="handleBeforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-exceed="handleExceed"
      :accept="accept"
      :auto-upload="autoUpload"
      list-type="picture-card"
      :http-request="customUpload"
    >
      <el-icon class="upload-icon">
        <Plus />
      </el-icon>
    </el-upload>
    <div v-if="!listType" class="upload-button">
      <el-button type="primary" @click="submitUpload">
        <el-icon><Upload /></el-icon>
        选择文件
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage} from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { uploadFile, deleteFile } from '@/api'

const props = defineProps({
  modelValue: {
    type: [String, Array],
    default: () => []
  },
  multiple: {
    type: Boolean,
    default: false
  },
  limit: {
    type: Number,
    default: 10
  },
  accept: {
    type: String,
    default: '.jpg,.jpeg,.png,.pdf,.doc,.docx,.xls,.xlsx'
  },
  autoUpload: {
    type: Boolean,
    default: true
  },
  listType: {
    type: String,
    default: 'picture-card'
  }
})

const emit = defineEmits(['update:modelValue', 'success', 'error', 'remove'])

const uploadRef = ref(null)
const fileList = ref([])
const uploadUrl = ref('/api/files/upload')
const headers = ref({})

const handleBeforeUpload = (file) => {
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  const acceptTypes = props.accept.split(',').map(t => t.trim().toLowerCase())
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  if (!acceptTypes.includes(ext) && !acceptTypes.includes(ext.replace('.', ''))) {
    ElMessage.error('不支持的文件类型')
    return false
  }
  return true
}

const handleSuccess = (response, file) => {
  if (response.success) {
    const fileInfo = {
      name: file.name,
      filename: response.filename,
      url: response.url,
      size: response.size
    }
    updateFileList(fileInfo)
    emit('success', fileInfo)
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
    emit('error', response.message)
  }
}

const handleError = (err) => {
  ElMessage.error('上传失败: ' + (err.message || '未知错误'))
  emit('error', err)
}

const handleRemove = (file) => {
  const filename = file.response?.filename || file.filename
  if (filename) {
    deleteFile(filename).catch(() => {})
    removeFromFileList(filename)
    emit('remove', filename)
  }
}

const handleExceed = (files) => {
  ElMessage.warning(`最多只能上传 ${props.limit} 个文件`)
}

const customUpload = async (options) => {
  const { file } = options
  try {
    const response = await uploadFile(file)
    if (response.data.success) {
      options.onSuccess(response.data, file)
    } else {
      options.onError(new Error(response.data.message))
    }
  } catch (error) {
    options.onError(error)
  }
}

const updateFileList = (fileInfo) => {
  if (props.multiple) {
    const current = Array.isArray(props.modelValue) ? props.modelValue : []
    emit('update:modelValue', [...current, fileInfo])
  } else {
    emit('update:modelValue', fileInfo)
  }
}

const removeFromFileList = (filename) => {
  if (props.multiple) {
    const current = Array.isArray(props.modelValue) ? props.modelValue : []
    emit('update:modelValue', current.filter(f => f.filename !== filename))
  } else {
    emit('update:modelValue', null)
  }
}

const submitUpload = () => {
  uploadRef.value?.submit()
}

const clearFiles = () => {
  uploadRef.value?.clearFiles()
}

defineExpose({
  submitUpload,
  clearFiles
})
</script>

<style scoped>
.file-upload {
  width: 100%;
}

.upload-icon {
  font-size: 28px;
  color: #8c939d;
}

.upload-button {
  margin-top: 10px;
}
</style>
