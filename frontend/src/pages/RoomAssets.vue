<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <div>
        <el-button @click="goBack" style="margin-right: 10px">← 返回</el-button>
        <span style="font-size: 20px; font-weight: bold">房间 {{ roomInfo.roomNumber || roomId }} - 器具与维修档案</span>
      </div>
      <div>
        <el-button type="primary" @click="openCreate">新增器具</el-button>
        <el-button @click="openBatchByTemplate">从模板批量添加</el-button>
        <el-button
          type="primary"
          plain
          :disabled="!isRentedRoom"
          @click="generateRoomBill"
        >
          生成该房间账单
        </el-button>
        <el-button type="success" @click="exportChecklist('move_in')">导出入住核对单PDF</el-button>
        <el-button type="warning" @click="exportChecklist('move_out')">导出退租核对单PDF</el-button>
      </div>
    </el-row>

    <el-tabs style="margin-top: 16px">
      <el-tab-pane label="器具清单">
        <el-table :data="appliances" border>
          <el-table-column prop="applianceName" label="器具名称" />
          <el-table-column prop="brand" label="品牌" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ applianceStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" />
          <el-table-column label="照片" width="170">
            <template #default="{ row }">
              <div style="display:flex;gap:6px;flex-wrap:wrap">
                <img v-for="(img, idx) in row.photos || []" :key="idx" :src="resolveImageUrl(img)" style="width: 36px; height: 36px; object-fit: cover; border-radius: 4px" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280">
            <template #default="{ row }">
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="warning" @click="quickReport(row)">发起报修</el-button>
              <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="维修记录">
        <el-table :data="repairs" border>
          <el-table-column prop="orderNo" label="工单号" width="170" />
          <el-table-column prop="faultType" label="故障类型" width="100" />
          <el-table-column prop="applianceName" label="关联器具" width="120" />
          <el-table-column prop="workerName" label="维修工" width="120" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }"><el-tag>{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="totalFee" label="费用" width="100" />
          <el-table-column prop="createdAt" label="报修时间" width="170" />
          <el-table-column prop="repairResult" label="维修结果" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="editId ? '编辑器具' : '新增器具'" v-model="showDialog" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="器具名称" required><el-input v-model="form.applianceName" /></el-form-item>
        <el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="form.quantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="损坏" value="DAMAGED" />
            <el-option label="丢失" value="LOST" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="照片">
          <el-upload :http-request="uploadPhoto" :show-file-list="false" :before-upload="beforeUpload">
            <el-button>上传照片</el-button>
          </el-upload>
          <div style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px">
            <img v-for="(img, idx) in form.photos" :key="idx" :src="resolveImageUrl(img)" style="width: 56px; height: 56px; object-fit: cover" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="模板批量添加" v-model="showTemplateDialog" width="520px">
      <el-checkbox-group v-model="selectedTemplateIds" style="display: flex; flex-direction: column; gap: 10px">
        <el-checkbox v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} {{ t.brand ? `(${t.brand})` : '' }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="showTemplateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitTemplateBatch">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRoom,
  getRoomAppliances,
  createRoomAppliance,
  updateRoomAppliance,
  deleteRoomAppliance,
  getApplianceTemplates,
  batchRoomAppliancesFromTemplate,
  uploadFile,
  getRoomRepairOrders,
  exportRoomApplianceChecklistPdf,
  generateBillForRoom,
  getErrorMessage,
} from '../api'
import { applianceStatusText, repairStatusText } from '../utils/labels'

const route = useRoute()
const router = useRouter()
const roomId = Number(route.params.roomId)
const buildingId = Number(route.params.buildingId)

const roomInfo = ref({})
const appliances = ref([])
const repairs = ref([])
const templates = ref([])

const showDialog = ref(false)
const editId = ref(null)
const form = ref({ roomId, applianceName: '', brand: '', quantity: 1, unit: '个', status: 'NORMAL', remark: '', photos: [] })

const showTemplateDialog = ref(false)
const selectedTemplateIds = ref([])
const isRentedRoom = computed(() => {
  const status = String(roomInfo.value?.status || '')
  return status === '已租' || status.toUpperCase() === 'RENTED'
})

function statusTag(status) {
  if (status === 'NORMAL') return 'success'
  if (status === 'DAMAGED') return 'danger'
  return 'warning'
}

function statusText(s) {
  return repairStatusText(s)
}

function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) return url
  return `/api/files/${url}`
}

function beforeUpload(file) {
  const okType = file.type?.startsWith('image/')
  const okSize = file.size / 1024 / 1024 < 10
  if (!okType) ElMessage.error('请上传图片文件')
  if (!okSize) ElMessage.error('图片不能超过10MB')
  return okType && okSize
}

async function uploadPhoto(options) {
  try {
    const res = await uploadFile(options.file)
    const url = res.data?.url
    if (url) {
      form.value.photos.push(url)
      options.onSuccess(res.data)
      ElMessage.success('上传成功')
    } else {
      options.onError(new Error('上传失败'))
    }
  } catch (e) {
    options.onError(e)
    ElMessage.error('上传失败')
  }
}

async function load() {
  const [r, a, t, h] = await Promise.all([
    getRoom(roomId),
    getRoomAppliances(roomId),
    getApplianceTemplates(),
    getRoomRepairOrders(roomId)
  ])
  roomInfo.value = r.data || {}
  appliances.value = a.data || []
  templates.value = t.data || []
  repairs.value = h.data || []
}

function openCreate() {
  editId.value = null
  form.value = { roomId, applianceName: '', brand: '', quantity: 1, unit: '个', status: 'NORMAL', remark: '', photos: [] }
  showDialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = { ...row, roomId, photos: row.photos || [] }
  showDialog.value = true
}

async function save() {
  if (!form.value.applianceName) {
    ElMessage.warning('请输入器具名称')
    return
  }
  if (editId.value) {
    await updateRoomAppliance(editId.value, form.value)
  } else {
    await createRoomAppliance(form.value)
  }
  showDialog.value = false
  ElMessage.success('保存成功')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除器具 ${row.applianceName} 吗？`, '提示', { type: 'warning' })
  await deleteRoomAppliance(row.id)
  ElMessage.success('删除成功')
  await load()
}

function quickReport(row) {
  router.push(`/repairs/orders?roomId=${roomId}&applianceId=${row.id}`)
}

function openBatchByTemplate() {
  selectedTemplateIds.value = []
  showTemplateDialog.value = true
}

async function submitTemplateBatch() {
  if (selectedTemplateIds.value.length === 0) {
    ElMessage.warning('请至少选择一个模板')
    return
  }
  await batchRoomAppliancesFromTemplate({ roomId, templateIds: selectedTemplateIds.value })
  showTemplateDialog.value = false
  ElMessage.success('批量添加成功')
  await load()
}

async function exportChecklist(type) {
  try {
    const res = await exportRoomApplianceChecklistPdf(roomId, type)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const roomNo = roomInfo.value?.roomNumber || roomId
    a.download = `room_${roomNo}_${type}_checklist.pdf`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('PDF导出成功')
  } catch (e) {
    ElMessage.error(getErrorMessage(e, 'PDF导出失败'))
  }
}

async function generateRoomBill() {
  if (!isRentedRoom.value) {
    ElMessage.warning('该房间未出租，不能生成账单')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入账单月份（格式 yyyy-MM，可留空默认上月）',
      '生成房间账单',
      {
        confirmButtonText: '生成',
        cancelButtonText: '取消',
        inputPlaceholder: '例如 2026-03',
        inputPattern: /^$|^\d{4}-(0[1-9]|1[0-2])$/,
        inputErrorMessage: '月份格式应为 yyyy-MM'
      }
    )

    await generateBillForRoom(roomId, String(value || '').trim())
    ElMessage.success('房间账单生成成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data || e?.message || '生成账单失败')
    }
  }
}

function goBack() {
  router.push(`/buildings/${buildingId}/rooms`)
}

onMounted(load)
</script>
