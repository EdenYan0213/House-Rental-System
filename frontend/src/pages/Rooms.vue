<template>
  <div>
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
        <el-button :disabled="rooms.length === 0" @click="openQrBatchDialog">导出二维码</el-button>
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
        <el-card class="room-card room-card--clickable" shadow="hover" @click="goDetail(room)">
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
            <span @click.stop>
              <el-checkbox
                :model-value="selectedRoomIds.includes(room.id)"
                @change="(v) => toggleRoomSelection(room.id, v)"
              />
            </span>
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
            <div v-else class="room-image-placeholder">
              <el-icon :size="32"><Picture /></el-icon>
              <span>暂无照片</span>
            </div>
            <div class="room-status" :class="room.status">
              {{ roomStatusText(room.status) }}
            </div>
            <div v-if="imagesOf(room).length > 1" class="room-img-count">
              {{ imagesOf(room).length }} 张
            </div>
          </div>
          <div class="room-info" :class="`floor-band-${floorBand(room.floor)}`">
            <!-- 房号是本系统的主键，也是管理员扫视时最先找的东西：
                 用等宽大字号 + 左侧楼层色带，让「同一层的房间」在视觉上成组。 -->
            <div class="room-title-row">
              <span class="room-no">{{ room.roomNumber }}</span>
              <span v-if="room.floor != null" class="floor-tag">{{ room.floor }}F</span>
            </div>
            <p class="detail">
              <span v-if="room.layout">{{ room.layout }}</span>
              <span v-if="room.layout && room.area != null" class="sep">·</span>
              <span v-if="room.area != null">{{ room.area }}m²</span>
            </p>

            <!-- 常用操作用文字按钮，低频操作收进「更多」，避免一排按钮被挤出卡片 -->
            <div class="actions" @click.stop>
              <el-button type="primary" size="small" @click="goDetail(room)">详情</el-button>
              <el-button size="small" @click="editRoom(room)">编辑</el-button>
              <el-button size="small" :loading="qrLoading" @click="showRoomQr(room)">二维码</el-button>
              <el-dropdown trigger="click" @command="cmd => handleRoomCommand(cmd, room)">
                <el-button size="small" class="more-btn">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="assets">器具档案</el-dropdown-item>
                    <el-dropdown-item command="meter">水电记录</el-dropdown-item>
                    <el-dropdown-item command="complaint">投诉记录</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span class="danger-item">删除房间</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
              multiple
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

    <!-- 单房间二维码 -->
    <el-dialog
      v-model="showQrDialog"
      :title="currentQrRoom ? `${currentQrRoom.roomNumber} 二维码` : '房间二维码'"
      width="420px"
      @closed="releaseQrUrl"
    >
      <div v-loading="qrLoading" class="qr-preview">
        <img v-if="qrImageUrl" :src="qrImageUrl" alt="房间二维码" />
        <p class="qr-hint">扫码进入房间详情，可录入水电、查看账单</p>
      </div>
      <template #footer>
        <el-button @click="showQrDialog = false">关闭</el-button>
        <el-button @click="printQr">打印</el-button>
        <el-button type="primary" @click="downloadQrPng">下载 PNG</el-button>
      </template>
    </el-dialog>

    <!-- 批量导出二维码 PDF -->
    <el-dialog v-model="showQrBatchDialog" title="批量导出二维码" width="520px">
      <el-form :model="qrBatchForm" label-width="110px">
        <el-form-item label="范围">
          <el-radio-group v-model="qrBatchForm.useSelectedOnly">
            <el-radio :label="false">本页全部房间</el-radio>
            <el-radio :label="true">仅已选房间（{{ selectedRoomIds.length }}）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="qrBatchForm.floor" placeholder="全部楼层" clearable style="width:100%">
            <el-option v-for="f in floorList" :key="f" :label="`${f}层`" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="纸张">
          <el-select v-model="qrBatchForm.paperSize" style="width:100%">
            <el-option label="A4" value="A4" />
            <el-option label="A5" value="A5" />
          </el-select>
        </el-form-item>
        <el-form-item label="每页数量">
          <el-input-number v-model="qrBatchForm.qrPerPage" :min="1" :max="24" style="width:100%" />
        </el-form-item>
        <el-form-item label="显示 Logo">
          <el-switch v-model="qrBatchForm.showLogo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showQrBatchDialog = false">取消</el-button>
        <el-button type="primary" :loading="qrBatchLoading" @click="exportQrBatchPdf">导出 PDF</el-button>
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
import { computed, onBeforeUnmount, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowDown, Picture } from '@element-plus/icons-vue'
import {
  getRooms,
  createRoom,
  updateRoom,
  deleteRoom,
  batchDeleteRooms,
  getBuilding,
  createRoomsBatch,
  createRoomsBatchOverride,
  uploadFileByUrl,
  uploadImage,
  batchAdjustRoomRent,
  getErrorMessage
} from '../api'
import { showUndoToast } from '../utils/undo'
import { roomStatusText } from '../utils/labels'
import { useRoomQr } from '@/composables/useRoomQr'

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

// 二维码：状态与逻辑在 composable 内，这里只负责模板绑定
const {
  showQrDialog,
  currentQrRoom,
  qrImageUrl,
  qrLoading,
  showQrBatchDialog,
  qrBatchLoading,
  qrBatchForm,
  showRoomQr,
  downloadQrPng,
  printQr,
  openQrBatchDialog,
  exportQrBatchPdf,
  releaseQrUrl
} = useRoomQr({ rooms, selectedRoomIds, buildingName })
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
  const message = getErrorMessage(error, '图片上传失败')
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
    ElMessage.error(getErrorMessage(error, '图片上传失败'))
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
      const message = getErrorMessage(e, '删除失败')
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

/** 卡片「更多」菜单命令分发。 */
function handleRoomCommand(cmd, room) {
  if (cmd === 'assets') return goAssets(room)
  if (cmd === 'meter') return goMeter(room)
  if (cmd === 'complaint') return goComplaint(room)
  if (cmd === 'delete') return del(room)
}

/**
 * 楼层 → 色带层级（1-5）。
 *
 * <p>同一层的房间共用同一条色带，管理员扫视时能立刻看出
 * 「这几个房间在同一层」。楼层越高颜色越深，形成垂直方向的地图感。
 */
function floorBand(floor) {
  const f = Number(floor)
  if (!Number.isFinite(f) || f <= 0) return 1
  return Math.min(5, Math.max(1, f))
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

/**
 * 房间封面地址。
 *
 * <p>只使用 imageIdList（图片存 image_blob）；没有图片时返回空字符串，
 * 由模板渲染占位图。不再回退到已废弃的 imageUrl，
 * 也不再使用随机网络图片——那会让人误以为房间有照片。
 */
function getRoomImage(room) {
  const ids = imagesOf(room)
  return ids.length > 0 ? imageSrc(ids[0]) : ''
}

onMounted(load)

// 预览图是 objectURL，离开页面释放
onBeforeUnmount(releaseQrUrl)
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

.qr-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  min-height: 240px;
}

.qr-preview img {
  width: 260px;
  height: 260px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
}

.qr-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}

/* ---------- 卡片：整卡可点击 ---------- */
.room-card--clickable {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.room-card--clickable:hover {
  transform: translateY(-2px);
}

/* 操作区固定不换行，避免 7 个按钮把「删除」挤出卡片 */
.room-card .actions {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
  align-items: center;
  margin-top: 10px;
}

.room-card .actions :deep(.el-button) {
  margin-left: 0;
  flex: 0 0 auto;
}

.room-card .actions .more-btn {
  flex: 0 0 auto;
}

.danger-item {
  color: var(--danger-color, #f56c6c);
}

.room-card .detail .sep {
  margin: 0 5px;
  opacity: 0.5;
}

/* 无图时的占位（替代原来的随机网络图） */
.room-image-placeholder {
  height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-muted);
  background: var(--el-fill-color-light);
  font-size: 13px;
}

/* ==========================================================================
   房间卡片 —— 本系统的签名元素
   --------------------------------------------------------------------------
   房号是管理员扫视时最先找的信息，所以给它最大字号和等宽数字。
   左侧色带按楼层分 5 级深浅，同层房间成组，形成"垂直楼层地图"。
   ========================================================================== */
.room-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--card-radius, 10px);
  border: 1px solid var(--border-color);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.room-card:hover {
  border-color: var(--accent-2);
  box-shadow: var(--shadow-md);
}

.room-card .el-card__body {
  padding: 0;
}

/* 楼层色带：贴在卡片左缘，颜色深浅编码楼层 */
.room-card .room-info {
  position: relative;
  padding: var(--space-3) var(--space-4) var(--space-4);
  padding-left: calc(var(--space-4) + 6px);
  border-top: 1px solid var(--border-color);
}

.room-card .room-info::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--band-color, var(--accent-2));
}

/* 楼层色带：5 级递进。
   浅色下用实色阶梯；深色下 accent-1 在卡片底色上几乎不可见，
   因此深色改用「同一个主色 + 不同透明度」，保证 5 级都能分辨。 */
.room-info.floor-band-1::before { --band-color: var(--accent-2); --band-alpha: 0.28; }
.room-info.floor-band-2::before { --band-color: var(--accent-2); --band-alpha: 0.48; }
.room-info.floor-band-3::before { --band-color: var(--accent-3); --band-alpha: 0.72; }
.room-info.floor-band-4::before { --band-color: var(--accent-4); --band-alpha: 0.9; }
.room-info.floor-band-5::before { --band-color: var(--accent-5); --band-alpha: 1; }

.room-card .room-info::before {
  opacity: var(--band-alpha, 1);
}

/* 深色主题：整体提亮一档，否则低层色带与卡片底色混在一起 */
:root.theme-dark .room-info.floor-band-1::before { --band-color: var(--accent-3); --band-alpha: 0.35; }
:root.theme-dark .room-info.floor-band-2::before { --band-color: var(--accent-3); --band-alpha: 0.55; }
:root.theme-dark .room-info.floor-band-3::before { --band-color: var(--accent-4); --band-alpha: 0.75; }
:root.theme-dark .room-info.floor-band-4::before { --band-color: var(--accent-4); --band-alpha: 0.95; }
:root.theme-dark .room-info.floor-band-5::before { --band-color: var(--accent-5); --band-alpha: 1; }

/* 房号：等宽 + 大字号 + 紧字距，像门牌号 */
.room-card .room-title-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.room-card .room-no {
  font-family: var(--font-num);
  font-size: 26px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: var(--text-main);
  font-variant-numeric: tabular-nums;
}

/* 楼层小标：等宽、低对比，作为房号的"单位" */
.room-card .floor-tag {
  font-family: var(--font-num);
  font-size: var(--fs-xs);
  font-weight: 500;
  color: var(--accent-3);
  background: var(--accent-1);
  padding: 1px 6px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.room-card .detail {
  margin: 0 0 var(--space-3);
  font-size: var(--fs-sm);
  color: var(--text-muted);
  line-height: var(--lh-base);
}

.room-card .detail .sep {
  margin: 0 5px;
  opacity: 0.45;
}

/* 图片区：统一裁切，避免不同原图比例撑破卡片 */
.room-card .room-image {
  position: relative;
  height: 200px;
  overflow: hidden;
  background: var(--bg-subtle);
}

.room-card .room-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 状态角标：右上，圆角药丸 */
.room-card .room-status {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  padding: 3px 10px;
  border-radius: 999px;
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.01em;
  backdrop-filter: blur(6px);
}

/* 图片张数：右下，比状态角标更弱 */
.room-card .room-img-count {
  position: absolute;
  bottom: var(--space-2);
  right: var(--space-2);
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(6px);
  font-variant-numeric: tabular-nums;
}
</style>
