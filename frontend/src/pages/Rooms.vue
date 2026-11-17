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
        <el-button :disabled="rooms.length === 0" @click="toggleSelectAll">{{ allSelected ? '取消本页全选' : '选中本页' }}</el-button>
        <el-button type="danger" plain :disabled="selectedRoomIds.length === 0" @click="batchDelete">批量删除</el-button>
        <el-button type="warning" :disabled="selectedRoomIds.length === 0" @click="openBatchRentDialog">批量调整租金</el-button>
      </div>
    </el-row>

    <div class="room-toolbar">
      <el-select v-model="filterFloor" placeholder="全部楼层" clearable style="width: 130px" @change="onFilterChange">
        <el-option v-for="f in floorList" :key="f" :label="`${f}层`" :value="f" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 130px" @change="onFilterChange">
        <el-option label="空闲" value="IDLE" />
        <el-option label="已租" value="RENTED" />
        <el-option label="维修中" value="MAINTENANCE" />
      </el-select>
      <el-tag v-if="selectedRoomIds.length > 0" type="primary" effect="light" round>
        已选 {{ selectedRoomIds.length }} 个房间
      </el-tag>
      <span class="total-hint">共 {{ totalRooms }} 个房间</span>
    </div>

    <el-empty
      v-if="rooms.length === 0"
      description="该楼栋还没有房间"
      style="margin-top: 40px"
    >
      <el-button type="primary" @click="openCreate">新建房间</el-button>
      <el-button @click="openBatch">批量生成</el-button>
    </el-empty>

    <el-row v-else :gutter="20" style="margin-top: 20px">
      <el-col v-for="room in rooms" :key="room.id" :span="8">
        <el-card class="room-card" shadow="hover">
          <div
            style="
              position: absolute;
              z-index: 2;
              left: 10px;
              top: 10px;
              background: var(--bg-surface);
              border-radius: 4px;
              padding: 2px 6px;
            "
          >
            <el-checkbox
              :model-value="selectedRoomIds.includes(room.id)"
              @change="(v) => toggleRoomSelection(room.id, v)"
            />
          </div>
          <div class="room-image">
            <el-carousel
              v-if="imagesOf(room).length > 0"
              height="220px"
              :interval="4500"
              indicator-position="none"
              arrow="hover"
            >
              <el-carousel-item v-for="id in imagesOf(room)" :key="id">
                <img :src="imageSrc(id)" alt="房间图片" />
              </el-carousel-item>
            </el-carousel>
            <img v-else :src="getRoomImage(room)" alt="房间图片" />
            <div class="room-status" :class="room.status">
              {{ roomStatusText(room.status) }}
            </div>
            <div v-if="imagesOf(room).length > 1" class="room-img-count">
              {{ imagesOf(room).length }} 张
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

    <el-dialog :title="editId ? '编辑房间' : '新建房间'" v-model="showDialog" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="图片">
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
          </div>
          <div v-else style="display: flex; gap: 10px; align-items: center;">
            <el-input v-model="imageUrlInput" placeholder="请输入图片URL" style="flex: 1" />
            <el-button type="primary" @click="handleUrlUpload" :loading="urlUploading">添加</el-button>
          </div>
          <div class="image-grid">
            <div v-for="(id, i) in formImages" :key="id" class="image-item">
              <img :src="imageSrc(id)" />
              <div class="image-mask">
                <el-tag v-if="i === 0" size="small" type="success" effect="dark">封面</el-tag>
                <el-button v-else size="small" link class="mask-btn" @click="setAsCover(i)">设为封面</el-button>
              </div>
              <el-button class="remove-btn" circle size="small" type="danger" @click="removeImage(i)">×</el-button>
            </div>
          </div>
          <div class="image-hint">第一张为封面，最多 9 张；房间卡片上会自动轮播</div>
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
            <el-option label="空闲" value="IDLE" />
            <el-option label="已租" value="RENTED" />
            <el-option label="维修中" value="MAINTENANCE" />
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
          <el-select v-model="batchForm.status" style="width: 100%">
            <el-option label="空闲" value="IDLE" />
            <el-option label="已租" value="RENTED" />
            <el-option label="维修中" value="MAINTENANCE" />
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
    <div class="room-pager" v-if="totalRooms > pageSize">
      <el-pagination
        :current-page="currentPage"
        :page-size="pageSize"
        :total="totalRooms"
        layout="total, prev, pager, next"
        background
        @current-change="onPageChange"
      />
    </div>
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
  uploadImage,
  batchAdjustRoomRent,
  getRoomQrPng,
  exportRoomQrsPdf,
  getErrorMessage
} from '../api'
import { showUndoToast } from '../utils/undo'
import { roomStatusText } from '../utils/labels'

const route = useRoute()
const router = useRouter()
const buildingId = Number(route.params.id)
const rooms = ref([])
const buildingName = ref('')
const showDialog = ref(false)
const editId = ref(null)
const form = ref({
  buildingId: buildingId,
  floor: 1,
  roomNumber: '',
  layout: '',
  area: 0,
  status: 'IDLE',
  imageUrl: ''
})
const uploadType = ref('local')
const imageUrlInput = ref('')
// 房间图片列表（本地状态），保存时序列化到 imageUrls
const formImages = ref([])

/** 图片 URL：存的是图片 ID，拼成接口地址 */
function imageSrc(id) {
  return `/api/images/${id}`
}

/** 卡片展示用：后端返回 imageIdList（图片ID数组） */
function imagesOf(room) {
  return Array.isArray(room?.imageIdList) ? room.imageIdList : []
}

function syncFormImages() {
  form.value.imageIds = formImages.value.length ? JSON.stringify(formImages.value) : ''
}

function addImage(imageId) {
  if (imageId == null) return
  if (formImages.value.includes(imageId)) return ElMessage.warning('该图片已添加')
  if (formImages.value.length >= 9) return ElMessage.warning('最多上传 9 张图片')
  formImages.value.push(imageId)
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
const urlUploading = ref(false)
const showBatchDialog = ref(false)
const batchForm = ref({
  buildingId: buildingId,
  floor: 1,
  startNumber: 1,
  endNumber: 10,
  layout: '',
  area: 0,
  status: 'IDLE'
})

const showConflictDialog = ref(false)
const conflicts = ref([])
const pendingBatchForm = ref(null)
const showResultDialog = ref(false)
const batchResult = ref({ successCount: 0, conflictCount: 0 })
const selectedRoomIds = ref([])
const showBatchRentDialog = ref(false)
const batchRentLoading = ref(false)
const batchRentForm = ref({ mode: 'FIXED', fixedAmount: 0, percent: 0, syncActiveContracts: true })
// 分页后「全选」只作用于当前页，避免误以为选中了全部房间
const allSelected = computed(() =>
  rooms.value.length > 0 &&
  rooms.value.every(r => selectedRoomIds.value.includes(r.id))
)

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

// 服务端分页 + 楼层/状态筛选（房间数量大时避免一次全量返回）
const currentPage = ref(1)
const pageSize = ref(24)
const totalRooms = ref(0)
const filterFloor = ref(null)
const filterStatus = ref(null)
const floorList = ref([])

async function load() {
  const params = {
    buildingId,
    page: currentPage.value - 1,
    size: pageSize.value
  }
  if (filterFloor.value != null) params.floor = filterFloor.value
  if (filterStatus.value) params.status = filterStatus.value

  const res = await getRooms(params)
  const data = res.data
  // 兼容服务端分页对象与旧的纯数组返回
  if (Array.isArray(data)) {
    rooms.value = data
    totalRooms.value = data.length
  } else {
    rooms.value = data?.content || []
    totalRooms.value = data?.totalElements ?? rooms.value.length
  }

  const validIds = new Set((rooms.value || []).map(r => r.id))
  selectedRoomIds.value = selectedRoomIds.value.filter(id => validIds.has(id))

  try {
    const b = await getBuilding(buildingId)
    buildingName.value = b.data.name
    // 楼层下拉按楼栋总层数生成
    const floors = []
    for (let i = 1; i <= (b.data.totalFloors || 1); i++) floors.push(i)
    floorList.value = floors
  } catch (e) {
    buildingName.value = ''
  }
}

function onFilterChange() {
  currentPage.value = 1
  load()
}

function onPageChange(page) {
  currentPage.value = page
  load()
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
    ElMessage.error(getErrorMessage(e, '操作失败'))
  } finally {
    batchRentLoading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.value = {
    buildingId: buildingId,
    floor: 1,
    roomNumber: '',
    layout: '',
    area: 0,
    status: 'IDLE',
    imageIds: ''
  }
  formImages.value = []
  uploadType.value = 'local'
  imageUrlInput.value = ''
  showDialog.value = true
}

function openBatch() {
  showBatchDialog.value = true
  batchForm.value = {
    buildingId: buildingId,
    floor: 1,
    startNumber: 1,
    endNumber: 10,
    layout: '',
    area: 0,
    status: 'IDLE'
  }
}

function openQrBatchDialog() {
  qrBatchForm.value.useSelectedOnly = selectedRoomIds.value.length > 0;
  showQrBatchDialog.value = true;
}

async function showRoomQr(room) {
  currentQrRoom.value = room;
  try {
    const res = await getRoomQrPng(room.id, { showLogo: false });
    if (qrImageUrl.value) {
      URL.revokeObjectURL(qrImageUrl.value);
    }
    qrImageUrl.value = URL.createObjectURL(res.data);
    showQrDialog.value = true;
  } catch (_) {
    ElMessage.error("二维码生成失败");
  }
}

async function downloadQrPng() {
  if (!currentQrRoom.value) return;
  try {
    const res = await getRoomQrPng(currentQrRoom.value.id, { showLogo: true });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(res.data);
    link.download = `${buildingName.value}_${currentQrRoom.value.roomNumber}_二维码.png`;
    link.click();
    URL.revokeObjectURL(link.href);
  } catch (_) {
    ElMessage.error("下载失败");
  }
}

function printQr() {
  if (!qrImageUrl.value) return;
  const win = window.open("", "_blank");
  if (!win) return;
  win.document.write(`<img src="${qrImageUrl.value}" style="width:320px;display:block;margin:20px auto;"/>`);
  win.document.close();
  win.print();
}

async function exportQrBatchPdf() {
  qrBatchLoading.value = true;
  try {
    let targetRooms = qrBatchForm.value.useSelectedOnly
      ? rooms.value.filter((room) => selectedRoomIds.value.includes(room.id))
      : rooms.value;

    if (qrBatchForm.value.floor !== null && qrBatchForm.value.floor !== undefined) {
      targetRooms = targetRooms.filter((r) => Number(r.floor) === Number(qrBatchForm.value.floor));
    }
    if (targetRooms.length === 0) {
      ElMessage.warning("没有可导出的房间");
      return;
    }
    const roomIds = targetRooms.map((r) => r.id);
    const res = await exportRoomQrsPdf({
      roomIds,
      paperSize: qrBatchForm.value.paperSize,
      qrPerPage: qrBatchForm.value.qrPerPage,
      showLogo: qrBatchForm.value.showLogo,
    });

    const fileName = `${buildingName.value || "房间"}二维码_${new Date().toISOString().slice(0, 10).replace(/-/g, "")}.pdf`;
    const link = document.createElement("a");
    link.href = URL.createObjectURL(res.data);
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(link.href);
    ElMessage.success("导出成功");
    showQrBatchDialog.value = false;
  } catch (_) {
    ElMessage.error("导出失败");
  } finally {
    qrBatchLoading.value = false;
  }
}

function editRoom(r) {
  editId.value = r.id
  form.value = { ...r }
  // 回填已有图片（后端 imageList 优先，回退单图）
  formImages.value = imagesOf(r)
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
      addImage(res.data.url)
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
  const payload = response?.data?.url ? response.data : response
  if (payload?.success && payload?.url) {
    addImage(payload.url)
    ElMessage.success('图片上传成功')
    return
  }
  ElMessage.error(payload?.message || '图片上传失败')
}

function handleUploadError(error) {
  const message = error?.response?.data?.message || error?.message || '图片上传失败'
  ElMessage.error(message)
}

async function customUpload(options) {
  try {
    const res = await uploadImage(options.file)
    const id = res.data?.id
    if (id != null) {
      addImage(id)
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

async function save() {
  // 后端用 @JsonIgnore 隐藏了 imageUrls 原字段，编辑回填时拿不到，
  // 这里以本地 formImages 为准重新序列化，避免保存后图片列表被清空。
  syncFormImages()
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
    ElMessage.error(getErrorMessage(e, '批量生成失败'))
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
  if (status === 'RENTED') return 'danger'
  if (status === 'MAINTENANCE') return 'warning'
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
.room-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.total-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.room-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

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
  background: var(--bg-main);
  position: relative;
}

.room-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 图片数量角标 */
.room-img-count {
  position: absolute;
  top: 10px;
  left: 10px;
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
  right: 0;
  bottom: 0;
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

.room-status {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
}

.room-status.RENTED {
  background: #f56c6c;
}

.room-status.IDLE {
  background: #67c23a;
}

.room-status.MAINTENANCE {
  background: #e6a23c;
}

.room-info {
  padding: 12px;
}

.room-info h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: var(--text-main);
}

.room-info .detail {
  color: var(--text-muted);
  font-size: 14px;
  margin: 0 0 12px 0;
}

.room-info .actions {
  display: flex;
  gap: 8px;
}
</style>
