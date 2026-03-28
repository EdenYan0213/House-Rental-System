<template>
  <div class="buildings-page">
    <el-row justify="space-between" align="middle" style="margin-bottom: 16px">
      <h2 style="margin: 0">楼栋管理</h2>
      <el-button v-if="canAdd" type="primary" @click="openCreate">新建楼栋</el-button>
    </el-row>

    <el-row v-if="buildings.length > 0" :gutter="20">
      <el-col v-for="building in buildings" :key="building.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="building-card" shadow="hover">
          <div class="building-cover" @click="goRooms(building)">
            <img v-if="building.imageUrl" :src="resolveImageUrl(building.imageUrl)" alt="楼栋图片" />
            <div v-else class="building-placeholder">
              <el-icon size="28"><OfficeBuilding /></el-icon>
              <span>暂无图片</span>
            </div>
          </div>
          <div class="building-body">
            <h3>{{ building.name }}</h3>
            <p>编号：{{ building.code || '-' }}</p>
            <p>地址：{{ building.address || '-' }}</p>
            <p>楼层数：{{ building.totalFloors || 0 }} 层</p>
            <p>水费：{{ formatPrice(building.waterUnitPrice) }} 元/吨</p>
            <p>电费：{{ formatPrice(building.electricUnitPrice) }} 元/度</p>
          </div>
          <div class="card-actions">
            <el-button size="small" @click="goRooms(building)">查看房间</el-button>
            <el-button v-if="canEdit" size="small" type="primary" plain @click="openEdit(building)">编辑</el-button>
            <el-button v-if="canDelete" size="small" type="danger" plain @click="remove(building)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else description="暂无楼栋数据" />

    <el-dialog :title="editId ? '编辑楼栋' : '新建楼栋'" v-model="showDialog" width="620px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="楼栋图片">
          <el-radio-group v-model="uploadType" style="margin-bottom: 10px">
            <el-radio label="local">本地上传</el-radio>
            <el-radio label="url">URL上传</el-radio>
          </el-radio-group>
          <div v-if="uploadType === 'local'">
            <el-upload
              class="cover-uploader"
              action="/api/files/upload"
              :show-file-list="false"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :before-upload="beforeUpload"
              :http-request="customUpload"
            >
              <img v-if="form.imageUrl" :src="resolveImageUrl(form.imageUrl)" class="cover-preview" />
              <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
            </el-upload>
          </div>
          <div v-else class="url-upload">
            <el-input v-model="imageUrlInput" placeholder="请输入图片URL" />
            <el-button type="primary" :loading="urlUploading" @click="handleUrlUpload">确认</el-button>
          </div>
          <div v-if="form.imageUrl" style="margin-top: 8px">
            <el-button type="danger" size="small" @click="form.imageUrl = ''">删除图片</el-button>
          </div>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="编号">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="水费单价 (元/吨)">
          <el-input-number v-model="form.waterUnitPrice" :min="0" :step="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="电费单价 (元/度)">
          <el-input-number v-model="form.electricUnitPrice" :min="0" :step="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="楼层数">
          <el-input-number v-model="form.totalFloors" :min="1" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, Plus } from '@element-plus/icons-vue'
import {
  canAddBuilding,
  canDeleteBuilding,
  canEditBuilding,
  createBuilding,
  deleteBuilding,
  getBuildings,
  uploadFile,
  updateBuilding,
  uploadFileByUrl
} from '../api'
import { showUndoToast } from '../utils/undo'

const router = useRouter()
const buildings = ref([])
const showDialog = ref(false)
const editId = ref(null)
const uploadType = ref('local')
const imageUrlInput = ref('')
const urlUploading = ref(false)
const canAdd = canAddBuilding()
const canEdit = canEditBuilding()
const canDelete = canDeleteBuilding()

const form = ref({
  name: '',
  code: '',
  address: '',
  imageUrl: '',
  totalFloors: 1,
  waterUnitPrice: 0,
  electricUnitPrice: 0
})

async function load() {
  const res = await getBuildings()
  buildings.value = res.data || []
}

function openCreate() {
  editId.value = null
  form.value = {
    name: '',
    code: '',
    address: '',
    imageUrl: '',
    totalFloors: 1,
    waterUnitPrice: 0,
    electricUnitPrice: 0
  }
  uploadType.value = 'local'
  imageUrlInput.value = ''
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = {
    ...row,
    imageUrl: row.imageUrl || '',
    totalFloors: row.totalFloors || 1,
    waterUnitPrice: row.waterUnitPrice ?? 0,
    electricUnitPrice: row.electricUnitPrice ?? 0
  }
  uploadType.value = 'local'
  imageUrlInput.value = ''
  showDialog.value = true
}

function beforeUpload(file) {
  const ext = (file.name || '').split('.').pop()?.toLowerCase() || ''
  const allowedExt = ['jpg', 'jpeg', 'png', 'gif', 'webp']
  const byMime = file.type ? file.type.startsWith('image/') : false
  const isImage = byMime || allowedExt.includes(ext)
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isImage) {
    ElMessage.error('请上传图片文件')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}

function handleUploadSuccess(response) {
  if (response?.success && response?.url) {
    form.value.imageUrl = response.url
    ElMessage.success('图片上传成功')
    return
  }
  ElMessage.error(response?.message || '图片上传失败')
}

function handleUploadError(error) {
  const message = error?.response?.data?.message || error?.message || '图片上传失败'
  ElMessage.error(message)
}

async function customUpload(options) {
  try {
    const res = await uploadFile(options.file)
    options.onSuccess(res.data)
  } catch (error) {
    options.onError(error)
  }
}

async function handleUrlUpload() {
  if (!imageUrlInput.value) {
    ElMessage.warning('请输入图片URL')
    return
  }
  urlUploading.value = true
  try {
    const res = await uploadFileByUrl(imageUrlInput.value)
    if (res.data?.success) {
      form.value.imageUrl = res.data.url
      imageUrlInput.value = ''
      ElMessage.success('图片上传成功')
    } else {
      ElMessage.error(res.data?.message || '图片上传失败')
    }
  } catch (error) {
    ElMessage.error('图片上传失败: ' + (error.response?.data?.message || error.message))
  } finally {
    urlUploading.value = false
  }
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请填写楼栋名称')
    return
  }
  if (editId.value) {
    await updateBuilding(editId.value, form.value)
  } else {
    await createBuilding(form.value)
  }
  showDialog.value = false
  await load()
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除楼栋 ${row.name} 吗？`, '提示', { type: 'warning' })
    const res = await deleteBuilding(row.id)
    showUndoToast(res?.data, load)
    await load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') {
      return
    }
    ElMessage.error(e?.response?.data || e?.message || '删除失败')
  }
}

function formatPrice(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(2)
}

function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) {
    return url
  }
  return `/api/files/${url}`
}

function goRooms(row) {
  router.push(`/buildings/${row.id}/rooms`)
}

onMounted(load)
</script>

<style scoped>
.buildings-page {
  padding: 20px;
}

.building-card {
  margin-bottom: 20px;
  border-radius: 10px;
  overflow: hidden;
}

.building-cover {
  height: 160px;
  background: #f4f6f8;
  cursor: pointer;
}

.building-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.building-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  gap: 8px;
}

.building-body {
  padding: 12px 2px 8px 2px;
}

.building-body h3 {
  margin: 0 0 10px 0;
  font-size: 18px;
}

.building-body p {
  margin: 0 0 6px 0;
  color: #606266;
  font-size: 13px;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.cover-uploader {
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  width: 220px;
  height: 140px;
  overflow: hidden;
}

.cover-preview {
  width: 220px;
  height: 140px;
  object-fit: cover;
}

.cover-uploader-icon {
  width: 220px;
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.url-upload {
  display: flex;
  gap: 10px;
}
</style>
