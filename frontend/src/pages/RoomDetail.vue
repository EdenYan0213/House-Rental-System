<template>
  <div class="room-detail">
    <!-- 顶部标题栏 -->
    <div class="detail-head">
      <div class="head-left">
        <el-button :icon="ArrowLeft" text @click="goBack">返回</el-button>
        <div class="title-block">
          <h2>
            {{ building?.name || '' }}{{ room?.roomNumber || `房间#${roomId}` }}
            <el-tag :type="roomStatusTag(room?.status)" size="small" effect="dark" class="status-tag">
              {{ roomStatusText(room?.status) }}
            </el-tag>
          </h2>
          <p class="subtitle">
            <span v-if="room?.layout">{{ room.layout }}</span>
            <span v-if="room?.area != null" class="dot">·</span>
            <span v-if="room?.area != null">{{ room.area }} m²</span>
            <span v-if="room?.floor != null" class="dot">·</span>
            <span v-if="room?.floor != null">{{ room.floor }} 层</span>
          </p>
        </div>
      </div>
      <div class="head-actions">
        <el-button :icon="Box" @click="goAssets">器具档案</el-button>
        <el-button :icon="Odometer" @click="goMeter">水电记录</el-button>
        <el-button type="primary" :icon="DocumentAdd" :disabled="!isRented" @click="generateBill">
          生成账单
        </el-button>
      </div>
    </div>

    <!-- 图片画廊 + 关键信息 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="gallery-card">
          <template #header>
            <div class="card-head">
              <span>房间照片</span>
              <el-button
                v-if="room"
                link
                type="primary"
                size="small"
                @click="goEditRoom"
              >管理图片</el-button>
            </div>
          </template>

          <!-- 有有效图片时展示大图 + 缩略图条 -->
          <div v-if="roomImages.length > 0" class="gallery">
            <div class="gallery-main">
              <el-image
                :src="roomImages[activeImageIndex]"
                :preview-src-list="roomImages"
                :initial-index="activeImageIndex"
                fit="cover"
                preview-teleported
                hide-on-click-modal
                class="gallery-main-img"
              >
                <template #error>
                  <div class="image-fallback">
                    <el-icon :size="32"><Picture /></el-icon>
                    <span>图片加载失败</span>
                  </div>
                </template>
              </el-image>
              <div v-if="roomImages.length > 1" class="gallery-counter">
                {{ activeImageIndex + 1 }} / {{ roomImages.length }}
              </div>
            </div>
            <div v-if="roomImages.length > 1" class="gallery-thumbs">
              <div
                v-for="(src, i) in roomImages"
                :key="src"
                class="thumb"
                :class="{ active: i === activeImageIndex }"
                @click="activeImageIndex = i"
              >
                <img :src="src" alt="" @error="markBroken(i)" />
              </div>
            </div>
          </div>

          <!-- 无图 / 全部失效 -->
          <div v-else class="gallery-empty">
            <el-icon :size="40"><Picture /></el-icon>
            <p>{{ hasBrokenImages ? '原图片已失效，请在「管理图片」重新上传' : '这个房间还没有照片' }}</p>
            <el-button v-if="room" size="small" type="primary" plain @click="goEditRoom">
              去上传
            </el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-head">
              <span>{{ isRented ? '在租信息' : '房间概况' }}</span>
              <el-tag :type="isRented ? 'success' : 'info'" size="small" effect="plain">
                {{ isRented ? '已出租' : '空置中' }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="1" size="small" class="info-list">
            <el-descriptions-item label="楼栋">{{ building?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="房间号">{{ room?.roomNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="楼层">{{ room?.floor ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="户型">{{ room?.layout || '-' }}</el-descriptions-item>
            <el-descriptions-item label="面积">
              {{ room?.area != null ? `${room.area} m²` : '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider style="margin: 12px 0" />

          <template v-if="isRented">
            <div class="tenant-row" v-if="tenant">
              <el-avatar :size="36" class="tenant-avatar">
                {{ (tenant.name || '租').slice(0, 1) }}
              </el-avatar>
              <div class="tenant-meta">
                <div class="tenant-name">
                  <el-button v-if="tenant.id" link type="primary" @click="goTenantDetail">
                    {{ tenant.name || `租户#${tenant.id}` }}
                  </el-button>
                  <span v-else>-</span>
                </div>
                <div class="tenant-phone">{{ tenant.phone || '未填写电话' }}</div>
              </div>
            </div>
            <el-descriptions :column="1" size="small" class="info-list" style="margin-top: 10px">
              <el-descriptions-item label="合同编号">
                <el-button v-if="activeContract?.id" link type="primary" @click="goContractDetail">
                  {{ activeContract.contractNo || `合同#${activeContract.id}` }}
                </el-button>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="租约状态">
                <el-tag :type="contractStatusTag(activeContract?.status)" size="small">
                  {{ contractStatusText(activeContract?.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="月租">
                {{ activeContract?.monthlyRent != null ? `¥${Number(activeContract.monthlyRent).toFixed(2)}` : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="租期">
                <span v-if="activeContract?.startDate">
                  {{ activeContract.startDate }} 至 {{ activeContract.endDate || '未定' }}
                </span>
                <span v-else>-</span>
              </el-descriptions-item>
            </el-descriptions>
          </template>
          <div v-else class="vacant-hint">
            <el-icon :size="20"><InfoFilled /></el-icon>
            <span>当前空置，可在「合同管理」为该房间创建合同</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card>
          <template #header>
            <div class="fee-header">
              <span>费用配置</span>
              <el-button link type="primary" size="small" @click="goFeeItems">管理全部费用项</el-button>
            </div>
          </template>

          <!-- 说明水/电单价的实际来源，避免管理员误以为在改本房间专属价格 -->
          <el-alert type="info" :closable="false" show-icon class="fee-alert">
            <template #title>
              水/电单价优先取<b>所在楼栋</b>的配置；楼栋未配置时才用<b>全局费用项</b>兜底。
              下方「其他费用」是全局配置，对所有房间生效。
            </template>
          </el-alert>

          <el-descriptions :column="2" border size="small" style="margin-bottom: 14px">
            <el-descriptions-item label="水费单价">{{ waterUnitPriceText }}</el-descriptions-item>
            <el-descriptions-item label="电费单价">{{ electricUnitPriceText }}</el-descriptions-item>
            <el-descriptions-item label="取值来源" :span="2">
              <el-tag :type="priceSourceFromBuilding ? 'success' : 'warning'" size="small">
                {{ priceSourceFromBuilding ? '楼栋配置' : '全局费用项兜底' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <div class="fee-subtitle">
            <span>其他费用</span>
            <el-tag size="small" type="info" effect="plain">全局生效</el-tag>
          </div>
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
      <el-col :xs="24" :md="10">
        <el-card>
          <template #header>器具配置</template>
          <el-table :data="appliances" border size="small" max-height="360">
            <el-table-column prop="applianceName" label="器具" min-width="140" />
            <el-table-column prop="brand" label="品牌" min-width="120" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">{{ applianceStatusText(row.status) }}</template>
            </el-table-column>
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
import { roomStatusText, roomStatusTag, contractStatusText, contractStatusTag, applianceStatusText } from '../utils/labels'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Box, Odometer, DocumentAdd, Picture, InfoFilled
} from '@element-plus/icons-vue'
import {
  getRoomDetail,
  generateBillForRoom,
  createFeeItem,
  updateFeeItem,
  deleteFeeItem,
  getErrorMessage} from '../api'

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

// 当前展示的大图索引
const activeImageIndex = ref(0)
// 记录加载失败的图片下标（悬空引用会 404）
const brokenIndexes = ref(new Set())

/** 房间图片地址列表：后端存的是图片 ID，拼成 /api/images/{id} */
const allImageSrcs = computed(() => {
  const ids = Array.isArray(room.value?.imageIdList) ? room.value.imageIdList : []
  return ids.map(id => `/api/images/${id}`)
})

/** 剔除加载失败的图片，避免露出破损图标 */
const roomImages = computed(() =>
  allImageSrcs.value.filter((_, i) => !brokenIndexes.value.has(i))
)

const hasBrokenImages = computed(() =>
  allImageSrcs.value.length > 0 && roomImages.value.length === 0
)

function markBroken(index) {
  const next = new Set(brokenIndexes.value)
  next.add(index)
  brokenIndexes.value = next
  if (activeImageIndex.value >= roomImages.value.length) {
    activeImageIndex.value = 0
  }
}

function goEditRoom() {
  router.push(`/buildings/${buildingId}/rooms`)
}

const feeDialogVisible = ref(false)
const feeSaving = ref(false)
const editingFeeId = ref(null)
const feeForm = ref({ name: '', unitPrice: 0, unit: '元/月', enabled: true })

const waterUnitPriceText = computed(() => {
  if (waterUnitPrice.value == null) return '-'
  return `¥${Number(waterUnitPrice.value).toFixed(2)}`
})

// 单价是否来自楼栋配置（否则是全局费用项兜底），用于给管理员明确提示
const priceSourceFromBuilding = computed(() => {
  const b = building.value
  if (!b) return false
  return b.waterUnitPrice != null || b.electricUnitPrice != null
})

function goFeeItems() {
  router.push('/fee-items')
}

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
    // 换房间时重置画廊状态
    activeImageIndex.value = 0
    brokenIndexes.value = new Set()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载房间详情失败'))
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
      ElMessage.error(getErrorMessage(e, '生成账单失败'))
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
    ElMessage.error(getErrorMessage(e, '保存失败'))
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
      ElMessage.error(getErrorMessage(e, '删除失败'))
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

<style scoped>
.fee-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fee-alert {
  margin-bottom: 14px;
  line-height: 1.6;
}

.fee-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin: 4px 0 8px 0;
  color: var(--text-main);
}

/* ---------- 页面骨架 ---------- */
.room-detail {
  /* 内边距由 .el-main 统一提供，这里只负责纵向节奏 */
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.head-left {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.title-block h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.status-tag {
  font-weight: 500;
}

.subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-muted);
}

.subtitle .dot {
  margin: 0 6px;
}

.head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- 卡片头 ---------- */
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

/* ---------- 画廊 ---------- */
/* 同一行卡片等高：col 撑满行高，卡片再 100% 填充，
   避免「左长右短」的参差感。 */
.room-detail .el-row {
  align-items: stretch;
}

.room-detail .el-col {
  display: flex;
}

.room-detail .el-col > .el-card {
  flex: 1;
  width: 100%;
}

.gallery-main {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.gallery-main-img {
  width: 100%;
  height: 100%;
  display: block;
}

.gallery-counter {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
}

.gallery-thumbs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.thumb {
  flex: 0 0 auto;
  width: 72px;
  height: 54px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  opacity: 0.65;
  transition: opacity 0.15s ease, border-color 0.15s ease;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb:hover {
  opacity: 1;
}

.thumb.active {
  opacity: 1;
  border-color: var(--accent-color);
}

.image-fallback,
.gallery-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-muted);
  background: var(--el-fill-color-light);
}

.gallery-empty {
  aspect-ratio: 16 / 10;
  border-radius: 8px;
  border: 1px dashed var(--border-color);
  padding: 20px;
  text-align: center;
}

.gallery-empty p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  max-width: 280px;
}

/* ---------- 租户信息 ---------- */
.info-list :deep(.el-descriptions__label) {
  width: 84px;
  color: var(--text-muted);
}

.tenant-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.tenant-avatar {
  background: var(--accent-color);
  color: #fff;
  font-weight: 600;
  flex: 0 0 auto;
}

.tenant-meta {
  min-width: 0;
}

.tenant-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-main);
}

.tenant-phone {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.vacant-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-muted);
  background: var(--el-fill-color-light);
}
</style>
