<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <div>
        <el-button @click="goBack" style="margin-right: 10px">← 返回</el-button>
        <span style="font-size: 20px; font-weight: bold">房间详情 - {{ room?.roomNumber || roomId }}</span>
      </div>
      <div style="display:flex; gap:8px; align-items:center;">
        <el-tag :type="isRented ? 'danger' : 'success'">{{ isRented ? '已租' : '未出租' }}</el-tag>
        <el-button type="primary" plain @click="goAssets">器具档案</el-button>
        <el-button type="primary" plain @click="goMeter">水电记录</el-button>
        <el-button type="primary" :disabled="!isRented" @click="generateBill">生成该房间账单</el-button>
      </div>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>房间基础信息</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="楼栋">{{ building?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="房间号">{{ room?.roomNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="楼层">{{ room?.floor ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="户型">{{ room?.layout || '-' }}</el-descriptions-item>
            <el-descriptions-item label="面积">{{ room?.area != null ? `${room.area} m²` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ room?.status || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>出租与租户信息</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="是否已出租">{{ isRented ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="合同编号">
              <el-button v-if="activeContract?.id" type="primary" link @click="goContractDetail">
                {{ activeContract?.contractNo || `合同#${activeContract?.id}` }}
              </el-button>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="租约状态">{{ activeContract?.status || '-' }}</el-descriptions-item>
            <el-descriptions-item label="租户姓名">
              <el-button v-if="tenant?.id" type="primary" link @click="goTenantDetail">
                {{ tenant?.name || `租户#${tenant?.id}` }}
              </el-button>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="租户电话">{{ tenant?.phone || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>费用配置</template>
          <el-form label-width="120px">
            <el-form-item label="水费单价">
              <el-input :model-value="waterUnitPriceText" disabled />
            </el-form-item>
            <el-form-item label="电费单价">
              <el-input :model-value="electricUnitPriceText" disabled />
            </el-form-item>
          </el-form>

          <div style="font-weight: 600; margin: 8px 0">其他费用（可自行设置）</div>
          <el-table :data="otherFees" border size="small" max-height="300">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="unitPrice" label="单价" width="120">
              <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openFeeEdit(row)">编辑</el-button>
                <el-button size="small" link type="danger" @click="removeFee(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 10px">
            <el-button type="primary" plain size="small" @click="openFeeCreate">新增其他费用</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>器具配置</template>
          <el-table :data="appliances" border size="small" max-height="360">
            <el-table-column prop="applianceName" label="器具" min-width="140" />
            <el-table-column prop="brand" label="品牌" min-width="120" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="feeDialogVisible" :title="editingFeeId ? '编辑其他费用' : '新增其他费用'" width="460px">
      <el-form :model="feeForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model.trim="feeForm.name" placeholder="例如：垃圾清运费" />
        </el-form-item>
        <el-form-item label="单价" required>
          <el-input-number v-model="feeForm.unitPrice" :min="0" :step="10" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model.trim="feeForm.unit" placeholder="例如：元/月" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="feeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="feeSaving" @click="saveFee">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRoomDetail,
  generateBillForRoom,
  createFeeItem,
  updateFeeItem,
  deleteFeeItem
} from '../api'

const route = useRoute()
const router = useRouter()

const buildingId = Number(route.params.buildingId)
const roomId = Number(route.params.roomId)

const room = ref(null)
const building = ref(null)
const activeContract = ref(null)
const tenant = ref(null)
const appliances = ref([])
const otherFees = ref([])
const waterUnitPrice = ref(null)
const electricUnitPrice = ref(null)
const isRented = ref(false)

const feeDialogVisible = ref(false)
const feeSaving = ref(false)
const editingFeeId = ref(null)
const feeForm = ref({ name: '', unitPrice: 0, unit: '元/月', enabled: true })

const waterUnitPriceText = computed(() => {
  if (waterUnitPrice.value == null) return '-'
  return `¥${Number(waterUnitPrice.value).toFixed(2)}`
})

const electricUnitPriceText = computed(() => {
  if (electricUnitPrice.value == null) return '-'
  return `¥${Number(electricUnitPrice.value).toFixed(2)}`
})

async function loadDetail() {
  try {
    const { data } = await getRoomDetail(roomId)
    room.value = data?.room || null
    building.value = data?.building || null
    activeContract.value = data?.activeContract || null
    tenant.value = data?.tenant || null
    appliances.value = data?.appliances || []
    otherFees.value = data?.otherFees || []
    waterUnitPrice.value = data?.waterUnitPrice ?? null
    electricUnitPrice.value = data?.electricUnitPrice ?? null
    isRented.value = !!data?.isRented
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '加载房间详情失败')
  }
}

async function generateBill() {
  if (!isRented.value) {
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
    ElMessage.success('账单生成成功')

    try {
      await ElMessageBox.confirm('账单已生成，是否前往账单页面查看？', '操作完成', {
        confirmButtonText: '前往查看',
        cancelButtonText: '留在当前页',
        type: 'success'
      })
      const month = String(value || '').trim()
      router.push({
        path: '/bills',
        query: {
          roomId: String(roomId),
          ...(month ? { month } : {})
        }
      })
    } catch (jumpChoice) {
      // 用户选择留在当前页
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data || e?.message || '生成账单失败')
    }
  }
}

function openFeeCreate() {
  editingFeeId.value = null
  feeForm.value = { name: '', unitPrice: 0, unit: '元/月', enabled: true }
  feeDialogVisible.value = true
}

function openFeeEdit(row) {
  editingFeeId.value = row.id
  feeForm.value = {
    name: row.name || '',
    unitPrice: row.unitPrice || 0,
    unit: row.unit || '元/月',
    enabled: row.enabled !== false
  }
  feeDialogVisible.value = true
}

async function saveFee() {
  if (!feeForm.value.name) {
    ElMessage.warning('请输入费用名称')
    return
  }
  feeSaving.value = true
  try {
    if (editingFeeId.value) {
      await updateFeeItem(editingFeeId.value, feeForm.value)
    } else {
      await createFeeItem(feeForm.value)
    }
    feeDialogVisible.value = false
    ElMessage.success('保存成功')
    await loadDetail()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '保存失败')
  } finally {
    feeSaving.value = false
  }
}

async function removeFee(row) {
  try {
    await ElMessageBox.confirm(`确定删除其他费用“${row.name}”吗？`, '提示', { type: 'warning' })
    await deleteFeeItem(row.id)
    ElMessage.success('删除成功')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data || e?.message || '删除失败')
    }
  }
}

function goAssets() {
  router.push(`/buildings/${buildingId}/rooms/${roomId}/assets`)
}

function goMeter() {
  router.push(`/buildings/${buildingId}/rooms/${roomId}/meter`)
}

function goTenantDetail() {
  if (!tenant.value?.id) return
  router.push({ path: '/tenants', query: { detailId: String(tenant.value.id) } })
}

function goContractDetail() {
  if (!activeContract.value?.id) return
  router.push({ path: '/contracts', query: { detailId: String(activeContract.value.id) } })
}

function goBack() {
  router.push(`/buildings/${buildingId}/rooms`)
}

onMounted(loadDetail)
</script>
