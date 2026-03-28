<template>
  <div style="padding:20px">
    <el-row justify="space-between" align="middle">
      <div>
        <el-button @click="goBack" style="margin-right:10px">← 退回</el-button>
        <span style="font-size:20px;font-weight:bold">房间管理 - {{ buildingName }}</span>
      </div>
      <div>
        <el-button type="primary" @click="openCreate">新建房间</el-button>
        <el-button @click="openBatch">批量生成</el-button>
        <el-button :disabled="rooms.length === 0" @click="toggleSelectAll">{{ allSelected ? '取消全选' : '一键全选' }}</el-button>
        <el-button type="danger" plain :disabled="selectedRoomIds.length === 0" @click="batchDelete">批量删除</el-button>
        <el-button type="warning" :disabled="selectedRoomIds.length === 0" @click="openBatchRentDialog">批量调整租金</el-button>
      </div>
    </el-row>

    <el-row v-if="selectedRoomIds.length > 0" style="margin-top: 12px">
      <el-tag type="info">已选择 {{ selectedRoomIds.length }} 个房间</el-tag>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col v-for="room in rooms" :key="room.id" :span="6">
        <el-card class="room-card" shadow="hover">
          <div style="position:absolute;z-index:2;left:10px;top:10px;background:#fff;border-radius:4px;padding:2px 6px">
            <el-checkbox :model-value="selectedRoomIds.includes(room.id)" @change="(v) => toggleRoomSelection(room.id, v)" />
          </div>
          <div class="room-image">
            <img :src="getRoomImage(room)" alt="房间图片" />
            <div class="room-status" :class="room.status">
              {{ room.status }}
            </div>
          </div>
          <div class="room-info">
            <h3>{{ room.roomNumber }}</h3>
            <p class="detail">{{ room.layout }} · {{ room.area }}m² · {{ room.floor }}层</p>
            <div class="actions">
              <el-button size="small" @click="goDetail(room)">详情</el-button>
              <el-button type="primary" size="small" @click="editRoom(room)">编辑</el-button>
              <el-button size="small" @click="goAssets(room)">器具</el-button>
              <el-button size="small" @click="goMeter(room)">水电</el-button>
              <el-button size="small" @click="goComplaint(room)">投诉</el-button>
              <el-button size="small" type="danger" @click="del(room)">删除</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog title="新建房间" v-model="showDialog" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="图片">
          <el-radio-group v-model="uploadType" style="margin-bottom: 10px">
            <el-radio label="local">本地上传</el-radio>
            <el-radio label="url">URL上传</el-radio>
          </el-radio-group>
          <div v-if="uploadType === 'local'">
            <el-upload
              class="avatar-uploader"
              action="/api/files/upload"
              :show-file-list="false"
              :on-success="handleRoomUploadSuccess"
              :on-error="handleUploadError"
              :before-upload="beforeUpload"
              :http-request="customUpload"
            >
              <img v-if="form.imageUrl" :src="resolveImageUrl(form.imageUrl)" class="avatar" />
              <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
            </el-upload>
          </div>
          <div v-else style="display: flex; gap: 10px; align-items: center;">
            <el-input v-model="imageUrlInput" placeholder="请输入图片URL" style="flex: 1" />
            <el-button type="primary" @click="handleUrlUpload" :loading="urlUploading">确认</el-button>
          </div>
          <div v-if="form.imageUrl" style="margin-top: 5px">
            <el-button type="danger" size="small" @click="form.imageUrl = ''">删除图片</el-button>
          </div>
        </el-form-item>
        <el-form-item label="房间号">
          <el-input v-model="form.roomNumber" />
        </el-form-item>
        <el-form-item label="楼层">
          <el-input-number v-model="form.floor" />
        </el-form-item>
        <el-form-item label="户型">
          <el-input v-model="form.layout" />
        </el-form-item>
        <el-form-item label="面积">
          <el-input-number v-model="form.area" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="空闲" value="空闲" />
            <el-option label="已租" value="已租" />
            <el-option label="维修中" value="维修中" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量调整租金" v-model="showBatchRentDialog" width="520px">
      <el-form :model="batchRentForm" label-width="120px">
        <el-form-item label="调整方式">
          <el-radio-group v-model="batchRentForm.mode">
            <el-radio label="FIXED">固定金额</el-radio>
            <el-radio label="PERCENT">百分比</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="batchRentForm.mode === 'FIXED'" label="金额变化">
          <el-input-number v-model="batchRentForm.fixedAmount" :step="100" style="width:100%" />
        </el-form-item>
        <el-form-item v-else label="百分比(%)">
          <el-input-number v-model="batchRentForm.percent" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="batchRentForm.syncActiveContracts">同步更新进行中合同租金</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchRentDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchRentLoading" @click="submitBatchRent">确认调整</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量生成房间" v-model="showBatchDialog" width="600px">
      <el-form :model="batchForm">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="起始房号">
              <el-input-number v-model="batchForm.startNumber" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束房号">
              <el-input-number v-model="batchForm.endNumber" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="楼层">
              <el-input-number v-model="batchForm.floor" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积">
              <el-input-number v-model="batchForm.area" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="户型">
          <el-input v-model="batchForm.layout" placeholder="如：2室1厅" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="batchForm.status" style="width:100%">
            <el-option label="空闲" value="空闲" />
            <el-option label="已租" value="已租" />
            <el-option label="维修中" value="维修中" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchDialog = false">取消</el-button>
        <el-button type="primary" @click="batchCreate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog title="冲突房间" v-model="showConflictDialog" width="700px">
      <div style="margin-bottom: 15px">
        <el-alert type="warning" :closable="false">
          以下房号已存在，您可以选择覆盖（更新信息）或跳过
        </el-alert>
      </div>
      <el-table :data="conflicts" border stripe max-height="400">
        <el-table-column prop="roomNumber" label="房号" width="100" />
        <el-table-column prop="currentStatus" label="当前状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.currentStatus)" size="small">{{ row.currentStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="layout" label="当前户型" />
        <el-table-column prop="area" label="当前面积">
          <template #default="{ row }">{{ row.area }}m²</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ $index }">
            <el-button type="primary" size="small" @click="handleOverride($index)">覆盖</el-button>
            <el-button size="small" @click="handleSkip($index)">跳过</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:15px;text-align:right">
        <el-button @click="skipAll">全部跳过</el-button>
        <el-button type="warning" @click="overrideAll">全部覆盖</el-button>
      </div>
    </el-dialog>

    <el-dialog title="生成结果" v-model="showResultDialog" width="500px">
      <el-result icon="success" v-if="batchResult.successCount > 0">
        <template #sub-title>
          成功创建 {{ batchResult.successCount }} 个房间
        </template>
      </el-result>
      <el-result icon="info" v-else>
        <template #sub-title>
          已全部跳过
        </template>
      </el-result>
      <template #footer>
        <el-button type="primary" @click="showResultDialog = false; showBatchDialog = false; load()">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getRooms,
  createRoom,
  updateRoom,
  deleteRoom,
  batchDeleteRooms,
  getBuilding,
  createRoomsBatch,
  createRoomsBatchOverride,
  uploadFile,
  uploadFileByUrl,
  batchAdjustRoomRent
} from '../api'
import { showUndoToast } from '../utils/undo'

const route = useRoute()
const router = useRouter()
const buildingId = Number(route.params.id)
const rooms = ref([])
const buildingName = ref('')
const showDialog = ref(false)
const editId = ref(null)
const form = ref({ buildingId: buildingId, floor: 1, roomNumber: '', layout: '', area: 0, status: '空闲', imageUrl: '' })
const uploadType = ref('local')
const imageUrlInput = ref('')
const urlUploading = ref(false)
const showBatchDialog = ref(false)
const batchForm = ref({ buildingId: buildingId, floor: 1, startNumber: 1, endNumber: 10, layout: '', area: 0, status: '空闲' })

const showConflictDialog = ref(false)
const conflicts = ref([])
const pendingBatchForm = ref(null)
const showResultDialog = ref(false)
const batchResult = ref({ successCount: 0, conflictCount: 0 })
const selectedRoomIds = ref([])
const showBatchRentDialog = ref(false)
const batchRentLoading = ref(false)
const batchRentForm = ref({ mode: 'FIXED', fixedAmount: 0, percent: 0, syncActiveContracts: true })
const allSelected = computed(() => rooms.value.length > 0 && selectedRoomIds.value.length === rooms.value.length)

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

async function load() {
  const res = await getRooms({ buildingId })
  rooms.value = res.data
  const validIds = new Set((rooms.value || []).map(r => r.id))
  selectedRoomIds.value = selectedRoomIds.value.filter(id => validIds.has(id))
  try {
    const b = await getBuilding(buildingId)
    buildingName.value = b.data.name
  } catch (e) {
    buildingName.value = ''
  }
}

function toggleRoomSelection(roomId, checked) {
  if (checked) {
    if (!selectedRoomIds.value.includes(roomId)) selectedRoomIds.value.push(roomId)
  } else {
    selectedRoomIds.value = selectedRoomIds.value.filter(id => id !== roomId)
  }
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedRoomIds.value = []
    return
  }
  selectedRoomIds.value = (rooms.value || []).map(room => room.id)
}

function openBatchRentDialog() {
  if (selectedRoomIds.value.length === 0) {
    ElMessage.warning('请先选择房间')
    return
  }
  showBatchRentDialog.value = true
}

async function submitBatchRent() {
  batchRentLoading.value = true
  try {
    const payload = {
      roomIds: selectedRoomIds.value,
      mode: batchRentForm.value.mode,
      fixedAmount: batchRentForm.value.fixedAmount,
      percent: batchRentForm.value.percent,
      syncActiveContracts: batchRentForm.value.syncActiveContracts
    }
    const res = await batchAdjustRoomRent(payload)
    ElMessage.success(`调整完成：房间${res.data.affectedRooms}个，合同${res.data.affectedContracts}份`)
    showBatchRentDialog.value = false
  } catch (e) {
    ElMessage.error(e.response?.data || e.message)
  } finally {
    batchRentLoading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.value = { buildingId: buildingId, floor: 1, roomNumber: '', layout: '', area: 0, status: '空闲', imageUrl: '' }
  uploadType.value = 'local'
  imageUrlInput.value = ''
  showDialog.value = true
}

function openBatch() {
  showBatchDialog.value = true
  batchForm.value = { buildingId: buildingId, floor: 1, startNumber: 1, endNumber: 10, layout: '', area: 0, status: '空闲' }
}

function editRoom(r) {
  editId.value = r.id
  form.value = { ...r }
  uploadType.value = 'local'
  imageUrlInput.value = ''
  showDialog.value = true
}

async function handleUrlUpload() {
  if (!imageUrlInput.value) {
    ElMessage.warning('请输入图片URL')
    return
  }
  urlUploading.value = true
  try {
    const res = await uploadFileByUrl(imageUrlInput.value)
    if (res.data.success) {
      form.value.imageUrl = res.data.url
      imageUrlInput.value = ''
      ElMessage.success('图片上传成功')
    } else {
      ElMessage.error(res.data.message || '图片上传失败')
    }
  } catch (error) {
    ElMessage.error('图片上传失败: ' + (error.response?.data?.message || error.message))
  } finally {
    urlUploading.value = false
  }
}

function handleRoomUploadSuccess(response) {
  console.log('Upload response:', response)
  if (response.success && response.url) {
    form.value.imageUrl = response.url
    ElMessage.success('图片上传成功')
  } else if (response.data && response.data.success && response.data.url) {
    form.value.imageUrl = response.data.url
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.error('图片上传失败')
  }
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

async function save() {
  if (editId.value) {
    await updateRoom(editId.value, form.value)
  } else {
    await createRoom(form.value)
  }
  showDialog.value = false
  await load()
}

async function del(r) {
  try {
    await ElMessageBox.confirm(`确定删除房间 ${r.roomNumber} 吗？`, '提示', { type: 'warning' })
    const res = await deleteRoom(r.id)
    showUndoToast(res?.data, load)
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      const message = e?.response?.data || e?.message || '删除失败'
      if (String(message).includes('已出租')) {
        ElMessageBox.alert(message, '删除失败', { type: 'warning' })
      } else {
        ElMessage.error(message)
      }
    }
  }
}

async function batchDelete() {
  if (selectedRoomIds.value.length === 0) {
    ElMessage.warning('请先选择房间')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedRoomIds.value.length} 个房间吗？`, '提示', { type: 'warning' })
    const res = await batchDeleteRooms(selectedRoomIds.value)
    ElMessage.success(`已删除 ${res?.data?.deletedCount || 0} 个房间`)
    showUndoToast(res?.data, load)
    selectedRoomIds.value = []
    await load()
  } catch (e) {
    if (e && e !== 'cancel') {
      ElMessage.error(e.response?.data || e.message || '批量删除失败')
    }
  }
}

async function batchCreate() {
  try {
    const res = await createRoomsBatch(batchForm.value)
    const data = res.data || {}
    const created = data.created || []
    conflicts.value = data.conflicts || []
    
    if (conflicts.value.length > 0) {
      pendingBatchForm.value = { ...batchForm.value }
      showConflictDialog.value = true
    }
    
    if (created.length > 0 || conflicts.value.length === 0) {
      batchResult.value = { successCount: created.length, conflictCount: conflicts.value.length }
      if (conflicts.value.length === 0) {
        showResultDialog.value = true
      }
    }
  } catch (e) {
    ElMessage.error('批量生成失败：' + (e?.response?.data || e.message))
  }
}

async function handleOverride(index) {
  const conflict = conflicts.value[index]
  const room = { ...pendingBatchForm.value }
  room.roomNumber = conflict.roomNumber
  try {
    await createRoomsBatchOverride(room)
    conflicts.value.splice(index, 1)
    if (conflicts.value.length === 0) {
      showConflictDialog.value = false
      await load()
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function handleSkip(index) {
  conflicts.value.splice(index, 1)
  if (conflicts.value.length === 0) {
    showConflictDialog.value = false
  }
}

async function skipAll() {
  showConflictDialog.value = false
  batchResult.value = { successCount: 0, conflictCount: conflicts.value.length }
  showResultDialog.value = true
  conflicts.value = []
}

async function overrideAll() {
  try {
    const res = await createRoomsBatchOverride(pendingBatchForm.value)
    const data = res.data || {}
    batchResult.value = { successCount: data.successCount || 0, conflictCount: 0 }
    showConflictDialog.value = false
    showResultDialog.value = true
    await load()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function tagType(status) {
  if (status === '已租') return 'danger'
  if (status === '维修中') return 'warning'
  return 'success'
}

function goMeter(room) {
  router.push(`/buildings/${buildingId}/rooms/${room.id}/meter`)
}

function goDetail(room) {
  router.push(`/buildings/${buildingId}/rooms/${room.id}/detail`)
}

function goAssets(room) {
  router.push(`/buildings/${buildingId}/rooms/${room.id}/assets`)
}

function goComplaint(room) {
  router.push(`/complaints?roomId=${room.id}`)
}

function goBack() {
  router.push('/buildings')
}

const roomImages = [
  'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=400&h=300&fit=crop',
  'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=400&h=300&fit=crop',
  'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=400&h=300&fit=crop',
  'https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=400&h=300&fit=crop'
]

function getRoomImage(room) {
  if (room.imageUrl) {
    return resolveImageUrl(room.imageUrl)
  }
  const index = room.id ? room.id % roomImages.length : 0
  return roomImages[index]
}

function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) {
    return url
  }
  return `/api/files/${url}`
}

onMounted(load)
</script>

<style scoped>
.room-card {
  margin-bottom: 20px;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.room-image {
  width: 100%;
  height: 160px;
  overflow: hidden;
  background: #f5f5f5;
  position: relative;
}

.room-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.room-status {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
}

.room-status.已租 {
  background: #f56c6c;
}

.room-status.空闲 {
  background: #67c23a;
}

.room-status.维修中 {
  background: #e6a23c;
}

.room-info {
  padding: 12px;
}

.room-info h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #333;
}

.room-info .detail {
  color: #666;
  font-size: 14px;
  margin: 0 0 12px 0;
}

.room-info .actions {
  display: flex;
  gap: 8px;
}
</style>
