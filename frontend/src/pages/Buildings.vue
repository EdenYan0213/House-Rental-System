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
            <el-carousel
              v-if="imagesOf(building).length > 0"
              :height="'160px'"
              :interval="4000"
              indicator-position="inside"
              arrow="hover"
              @click.stop
            >
              <el-carousel-item v-for="(img, i) in imagesOf(building)" :key="i">
                <img :src="resolveImageUrl(img)" alt="楼栋图片" class="cover-img" />
              </el-carousel-item>
            </el-carousel>
            <div v-else class="building-placeholder">
              <el-icon size="28"><OfficeBuilding /></el-icon>
              <span>暂无图片</span>
            </div>
            <div v-if="imagesOf(building).length > 1" class="img-count">
              {{ imagesOf(building).length }} 张
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
              action="/api/files/upload"
              :show-file-list="false"
              :on-error="handleUploadError"
              :before-upload="beforeUpload"
              :http-request="customUpload"
            >
              <div class="add-image-tile">
                <el-icon><Plus /></el-icon>
                <span>添加图片</span>
              </div>
            </el-upload>
            <div class="image-grid">
              <div v-for="(img, i) in formImages" :key="img + i" class="image-item">
                <img :src="resolveImageUrl(img)" />
                <div class="image-mask">
                  <el-tag v-if="i === 0" size="small" type="success" effect="dark">封面</el-tag>
                  <el-button
                    v-else
                    size="small"
                    link
                    class="mask-btn"
                    @click="setAsCover(i)"
                  >设为封面</el-button>
                </div>
                <el-button
                  class="remove-btn"
                  circle
                  size="small"
                  type="danger"
                  @click="removeImage(i)"
                >×</el-button>
              </div>
            </div>
            <div class="image-hint">第一张为封面，最多 9 张；卡片上会自动轮播</div>
          </div>
          <div v-else class="url-upload">
            <el-input v-model="imageUrlInput" placeholder="请输入图片URL" />
            <el-button type="primary" :loading="urlUploading" @click="handleUrlUpload">添加</el-button>
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
  uploadFileByUrl,
  getErrorMessage
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
  imageUrls: '',
  totalFloors: 1,
  waterUnitPrice: 0,
  electricUnitPrice: 0
})

// 表单中的图片列表（本地状态），保存时序列化到 imageUrls
const formImages = ref([])

/** 卡片展示用：优先 imageList（后端合并后的列表），回退单图 */
function imagesOf(building) {
  if (Array.isArray(building?.imageList) && building.imageList.length) {
    return building.imageList
  }
  return building?.imageUrl ? [building.imageUrl] : []
}

/** 把当前 formImages 同步到表单字段 */
function syncFormImages() {
  form.value.imageUrls = formImages.value.length
    ? JSON.stringify(formImages.value)
    : ''
  // 封面始终取第一张，保持与旧字段兼容
  form.value.imageUrl = formImages.value[0] || ''
}

function addImage(url) {
  if (!url) return
  if (formImages.value.includes(url)) {
    return ElMessage.warning('该图片已添加')
  }
  if (formImages.value.length >= 9) {
    return ElMessage.warning('最多上传 9 张图片')
  }
  formImages.value.push(url)
  syncFormImages()
}

function removeImage(index) {
  formImages.value.splice(index, 1)
  syncFormImages()
}

function setAsCover(index) {
  if (index <= 0 || index >= formImages.value.length) return
  const [picked] = formImages.value.splice(index, 1)
  formImages.value.unshift(picked)
  syncFormImages()
}

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
    imageUrls: '',
    totalFloors: 1,
    waterUnitPrice: 0,
    electricUnitPrice: 0
  }
  formImages.value = []
  uploadType.value = 'local'
  imageUrlInput.value = ''
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = {
    ...row,
    imageUrl: row.imageUrl || '',
    imageUrls: row.imageUrls || '',
    totalFloors: row.totalFloors || 1,
    waterUnitPrice: row.waterUnitPrice ?? 0,
    electricUnitPrice: row.electricUnitPrice ?? 0
  }
  // 回填已有图片（后端 imageList 优先，回退单图）
  formImages.value = imagesOf(row)
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
    addImage(response.url)
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
    const url = res.data?.url || res.data?.data?.url
    if (url) {
      addImage(url)
      ElMessage.success('图片上传成功')
    } else {
      ElMessage.error(res.data?.message || '图片上传失败')
    }
    options.onSuccess(res.data)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '图片上传失败')
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
      addImage(res.data.url)
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
  // 以本地 formImages 为准重新序列化，避免图片列表被清空
  syncFormImages()
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
  background: var(--bg-main);
  /* 角标与轮播需要相对定位的容器，否则会相对页面定位 */
  position: relative;
  overflow: hidden;
  cursor: pointer;
}

.building-cover img,
.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 轮播图片数量角标 */
.img-count {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  z-index: 3;
}

/* 表单多图上传 */
.add-image-tile {
  width: 92px;
  height: 92px;
  border: 1px dashed var(--border-color);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.add-image-tile:hover {
  border-color: var(--accent-color);
  color: var(--accent-color);
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.image-item {
  position: relative;
  width: 92px;
  height: 92px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-mask {
  position: absolute;
  left: 0;
  bottom: 0;
  right: 0;
  padding: 3px 5px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.6));
  display: flex;
  justify-content: center;
}

.mask-btn {
  color: #fff !important;
  font-size: 11px;
}

.remove-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  min-height: 18px;
  padding: 0;
  font-size: 12px;
  line-height: 1;
}

.image-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.building-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
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
  color: var(--text-muted);
  font-size: 13px;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.cover-uploader {
  border: 1px dashed var(--border-color);
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
  color: var(--text-muted);
}

.url-upload {
  display: flex;
  gap: 10px;
}
</style>
