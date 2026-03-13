<template>
  <div style="padding: 20px">
    <el-page-header @back="goBack" content="新建合同" />
    
    <el-steps :active="step" finish-status="success" style="margin: 30px 0">
      <el-step title="选择房间" />
      <el-step title="选择租户" />
      <el-step title="填写合同信息" />
      <el-step title="确认提交" />
    </el-steps>

    <el-card v-show="step === 0">
      <div slot="header">步骤1: 选择空闲房间</div>
      <el-row :gutter="10" style="margin-bottom: 15px">
        <el-select v-model="buildingFilter" placeholder="选择楼栋" clearable @change="loadRooms">
          <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
        <el-select v-model="floorFilter" placeholder="选择楼层" clearable @change="loadRooms">
          <el-option v-for="f in floors" :key="f" :label="f + '层'" :value="f" />
        </el-select>
      </el-row>
      <el-table :data="rooms" @row-click="selectRoom" :row-class-name="getRoomRowClass" v-loading="loadingRooms">
        <el-table-column prop="roomNumber" label="房间号" />
        <el-table-column prop="buildingName" label="楼栋" />
        <el-table-column prop="floor" label="楼层" />
        <el-table-column prop="area" label="面积(㎡)" />
        <el-table-column prop="layout" label="户型" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === '空闲' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-show="step === 1">
      <div slot="header">步骤2: 选择/新增租户</div>
      <el-row style="margin-bottom: 15px">
        <el-input v-model="tenantSearch" placeholder="搜索租户姓名或手机号" clearable style="width: 300px" @change="searchTenants">
          <el-button slot="append" icon="Search" @click="searchTenants" />
        </el-input>
        <el-button type="primary" style="margin-left: 10px" @click="showNewTenant = true">新增租户</el-button>
      </el-row>
      <el-table :data="tenants" @row-click="selectTenant" :row-class-name="getTenantRowClass" v-loading="loadingTenants">
        <el-table-column prop="name" label="姓名">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.isBlacklisted" type="danger" size="small" style="margin-left: 5px">黑名单</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="idCard" label="身份证号" />
      </el-table>
      <el-alert v-if="selectedTenant?.isBlacklisted" type="error" :closable="false" style="margin-top: 16px">
        <template #title>
          <span>⚠️ 该租户已被加入黑名单！原因：{{ selectedTenant.blacklistReason }}</span>
        </template>
      </el-alert>
      <div v-if="selectedTenant" style="margin-top: 16px; text-align: center">
        <el-button type="primary" @click="goToStep2">下一步</el-button>
      </div>
    </el-card>

    <el-card v-show="step === 2">
      <div slot="header">步骤3: 填写合同信息</div>
      <el-form :model="contractForm" label-width="120px" style="max-width: 600px">
        <el-form-item label="起租日期" required>
          <el-date-picker v-model="contractForm.startDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="contractForm.endDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="月租金(元)" required>
          <el-input-number v-model="contractForm.monthlyRent" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="押金(元)" required>
          <el-input-number v-model="contractForm.deposit" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="contractForm.paymentMethod" style="width: 100%">
            <el-option label="月付" value="MONTHLY" />
            <el-option label="季付" value="QUARTERLY" />
            <el-option label="半年付" value="HALF_YEAR" />
            <el-option label="年付" value="YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="contractForm.remark" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="上传附件">
          <el-upload :auto-upload="false" :on-change="handleFileChange" :limit="1">
            <el-button size="small" type="primary">选择文件</el-button>
            <span v-if="contractForm.attachment" style="margin-left: 10px">{{ contractForm.attachment.name }}</span>
          </el-upload>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-show="step === 3">
      <div slot="header">步骤4: 确认提交</div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="房间">{{ selectedRoom?.buildingName }} - {{ selectedRoom?.roomNumber }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ selectedTenant?.name }} ({{ selectedTenant?.phone }})</el-descriptions-item>
        <el-descriptions-item label="起租日期">{{ contractForm.startDate }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{ contractForm.endDate }}</el-descriptions-item>
        <el-descriptions-item label="月租金">¥{{ contractForm.monthlyRent?.toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="押金">¥{{ contractForm.deposit?.toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ getPaymentMethodText(contractForm.paymentMethod) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ contractForm.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <div style="margin-top: 20px; text-align: center">
      <el-button v-if="step > 0" @click="step--">上一步</el-button>
      <el-button v-if="step < 3" type="primary" @click="nextStep" :disabled="!canProceed">下一步</el-button>
      <el-button v-if="step === 3" type="success" @click="submit" :loading="submitting">确认提交</el-button>
    </div>

    <el-dialog title="新增租户" v-model="showNewTenant" width="500px">
      <el-form :model="newTenantForm" label-width="100px">
        <el-form-item label="姓名" required>
          <el-input v-model="newTenantForm.name" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="newTenantForm.phone" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="newTenantForm.idCard" />
        </el-form-item>
        <el-form-item label="紧急联系人">
          <el-input v-model="newTenantForm.emergencyContact" />
        </el-form-item>
        <el-form-item label="紧急联系电话">
          <el-input v-model="newTenantForm.emergencyPhone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewTenant = false">取消</el-button>
        <el-button type="primary" @click="createNewTenant" :loading="creatingTenant">保存并选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getBuildings, getRooms, getTenants, createTenant, createContract, uploadFile } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()

const step = ref(0)
const buildings = ref([])
const buildingFilter = ref(null)
const floors = ref([])
const floorFilter = ref(null)
const rooms = ref([])
const loadingRooms = ref(false)
const selectedRoom = ref(null)
const tenants = ref([])
const loadingTenants = ref(false)
const selectedTenant = ref(null)
const tenantSearch = ref('')
const showNewTenant = ref(false)
const creatingTenant = ref(false)
const newTenantForm = ref({ name: '', phone: '', idCard: '', emergencyContact: '', emergencyPhone: '' })
const submitting = ref(false)

const contractForm = ref({
  startDate: '',
  endDate: '',
  monthlyRent: 0,
  deposit: 0,
  paymentMethod: 'MONTHLY',
  remark: '',
  attachment: null
})

const canProceed = computed(() => {
  if (step.value === 0) return selectedRoom.value
  if (step.value === 1) return selectedTenant.value
  if (step.value === 2) return contractForm.value.startDate && contractForm.value.endDate && contractForm.value.monthlyRent >= 0
  return true
})

function getPaymentMethodText(method) {
  const map = { MONTHLY: '月付', QUARTERLY: '季付', HALF_YEAR: '半年付', YEARLY: '年付' }
  return map[method] || method
}

function goBack() {
  router.push('/contracts')
}

async function loadBuildings() {
  const res = await getBuildings()
  buildings.value = res.data
  if (buildings.value.length > 0) {
    buildingFilter.value = buildings.value[0].id
  }
}

async function loadRooms() {
  if (!buildingFilter.value) return
  loadingRooms.value = true
  try {
    const building = buildings.value.find(b => b.id === buildingFilter.value)
    floors.value = []
    for (let i = 1; i <= building.totalFloors; i++) floors.value.push(i)
    
    const res = await getRooms({ buildingId: buildingFilter.value, floor: floorFilter.value, status: '空闲' })
    rooms.value = (res.data.content || res.data).map(r => ({ ...r, buildingName: building.name }))
  } finally {
    loadingRooms.value = false
  }
}

function selectRoom(row) {
  selectedRoom.value = row
}

function getRoomRowClass({ row }) {
  return selectedRoom.value?.id === row.id ? 'selected-row' : ''
}

function getTenantRowClass({ row }) {
  return selectedTenant.value?.id === row.id ? 'selected-row' : ''
}

async function searchTenants() {
  loadingTenants.value = true
  try {
    const res = await getTenants({ q: tenantSearch.value })
    tenants.value = res.data.content || res.data || []
  } finally {
    loadingTenants.value = false
  }
}

function selectTenant(row) {
  selectedTenant.value = row
}

async function goToStep2() {
  if (!selectedTenant.value) {
    return ElMessage.warning('请先选择一个租户')
  }
  if (selectedTenant.value.isBlacklisted) {
    try {
      await ElMessageBox.confirm('该租户在黑名单中，是否确定继续签约？', '警告', { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' })
    } catch (e) {
      return
    }
  }
  step.value++
}

async function createNewTenant() {
  if (!newTenantForm.value.name || !newTenantForm.value.phone) {
    return ElMessage.warning('请填写姓名和手机号')
  }
  creatingTenant.value = true
  try {
    const res = await createTenant(newTenantForm.value)
    selectedTenant.value = res.data
    showNewTenant.value = false
    ElMessage.success('租户创建成功')
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    creatingTenant.value = false
  }
}

function handleFileChange(file) {
  contractForm.value.attachment = file.raw
}

function nextStep() {
  if (step.value < 3) step.value++
}

async function submit() {
  submitting.value = true
  try {
    let attachmentUrl = ''
    if (contractForm.value.attachment) {
      const uploadRes = await uploadFile(contractForm.value.attachment)
      attachmentUrl = uploadRes.data.url
    }

    await createContract({
      roomId: selectedRoom.value.id,
      tenantId: selectedTenant.value.id,
      startDate: contractForm.value.startDate,
      endDate: contractForm.value.endDate,
      monthlyRent: contractForm.value.monthlyRent,
      deposit: contractForm.value.deposit,
      paymentMethod: contractForm.value.paymentMethod,
      remark: contractForm.value.remark,
      attachmentUrl
    })

    ElMessage.success('合同创建成功')
    router.push('/contracts')
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadBuildings()
  await loadRooms()
  await searchTenants()
})
</script>

<style>
.selected-row {
  background-color: #e6f7ff !important;
}
.clickable-row {
  cursor: pointer;
}
.clickable-row:hover {
  background-color: #f5f7fa;
}
</style>
