<template>
  <div class="mobile-page room-page" v-loading="loading">
    <!-- 顶栏 -->
    <header class="room-topbar">
      <el-button link :icon="ArrowLeft" class="back-link" @click="goBack">返回</el-button>
    </header>

    <!-- 房间横幅 -->
    <section class="room-hero" v-if="summary.room">
      <div class="hero-top">
        <div class="hero-room m-num">{{ summary.room.roomNumber }}</div>
        <el-tag
          size="small"
          :type="roomStatusTag(summary.room.status)"
          effect="dark"
          class="hero-status"
        >{{ roomStatusText(summary.room.status) }}</el-tag>
      </div>
      <div class="hero-building">{{ summary.room.buildingName }}</div>
      <div class="hero-meta">
        <span v-if="summary.room.layout">{{ summary.room.layout }}</span>
        <span v-if="summary.room.area" class="sep">·</span>
        <span v-if="summary.room.area">{{ summary.room.area }}m²</span>
        <span v-if="summary.room.floor != null" class="sep">·</span>
        <span v-if="summary.room.floor != null">{{ summary.room.floor }} 层</span>
      </div>
    </section>

    <!-- 欠费提醒：有欠费才显示，红色突出 -->
    <section
      v-if="(summary.unpaidAmount || 0) > 0"
      class="arrears-banner"
      @click="goUnpaid"
    >
      <div class="arrears-left">
        <el-icon :size="18"><Warning /></el-icon>
        <span>欠费</span>
      </div>
      <div class="arrears-right">
        <span class="m-num arrears-amount">{{ formatMoney(summary.unpaidAmount) }}</span>
        <el-icon class="arrears-arrow"><ArrowRight /></el-icon>
      </div>
    </section>

    <!-- 快捷操作：四个大按钮 -->
    <section class="quick-ops">
      <button
        type="button"
        class="op-btn op-primary"
        :disabled="!summary.canOperate"
        @click="goMeter"
      >
        <el-icon :size="22"><Edit /></el-icon>
        <span>录入水电</span>
      </button>
      <button
        type="button"
        class="op-btn"
        :disabled="!summary.canOperate"
        @click="goRepair"
      >
        <el-icon :size="22"><Tools /></el-icon>
        <span>发起报修</span>
      </button>
      <button
        type="button"
        class="op-btn"
        :disabled="!summary.canOperate"
        @click="goUnpaid"
      >
        <el-icon :size="22"><Wallet /></el-icon>
        <span>查看欠费</span>
      </button>
      <button type="button" class="op-btn" @click="openRepairHistory">
        <el-icon :size="22"><Document /></el-icon>
        <span>维修历史</span>
      </button>
    </section>

    <!-- 只读提示 -->
    <div v-if="!summary.canOperate" class="readonly-tip">
      <el-icon><InfoFilled /></el-icon>
      <span>当前账号为只读权限，快捷操作已禁用</span>
    </div>

    <!-- 租户 / 合同 -->
    <section v-if="summary.tenant || summary.contract" class="info-block">
      <div class="m-section-title">租户与合同</div>
      <div class="m-card">
        <div v-if="summary.tenant" class="info-row">
          <span class="info-label">租户</span>
          <span class="info-value">{{ summary.tenant.name }}</span>
        </div>
        <div v-if="summary.tenant" class="info-row">
          <span class="info-label">手机</span>
          <a class="info-value link m-num" :href="`tel:${summary.tenant.phone}`">{{ summary.tenant.phone }}</a>
        </div>
        <div v-if="summary.contract" class="info-row">
          <span class="info-label">租期</span>
          <span class="info-value m-num">{{ summary.contract.startDate }} ~ {{ summary.contract.endDate }}</span>
        </div>
        <div v-if="summary.contract" class="info-row">
          <span class="info-label">月租</span>
          <span class="info-value m-num">{{ formatMoney(summary.contract.monthlyRent) }}</span>
        </div>
      </div>
    </section>

    <section v-else class="vacant-block">
      <el-icon :size="28" class="vacant-icon"><House /></el-icon>
      <p class="vacant-title">当前房间空闲</p>
      <p class="vacant-desc">可在管理端为该房间创建合同。</p>
    </section>

    <!-- 最近维修 -->
    <section>
      <div class="m-section-title">
        <span>最近维修</span>
        <span
          v-if="(summary.repairHistory || []).length > 0"
          class="m-section-more"
          @click="openRepairHistory"
        >查看全部 ›</span>
      </div>

      <div v-if="(summary.repairHistory || []).length === 0" class="m-card empty-mini">
        暂无维修记录
      </div>
      <div v-else class="m-list">
        <div
          v-for="item in (summary.repairHistory || [])"
          :key="item.id"
          class="m-list-item"
        >
          <div class="m-list-main">
            <div class="m-list-title">{{ item.faultType || '-' }}</div>
            <div class="m-list-desc m-num">{{ item.createdAt || '-' }}</div>
          </div>
          <el-tag size="small" :type="repairStatusTag(item.status)" effect="light">
            {{ repairStatusText(item.status) }}
          </el-tag>
        </div>
      </div>
    </section>

    <el-drawer v-model="showAllRepair" title="维修历史" size="100%">
      <el-table :data="allRepairHistory" style="width: 100%">
        <el-table-column prop="faultType" label="故障类型" />
        <el-table-column label="状态">
          <template #default="{ row }">{{ repairStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowRight, Warning, Edit, Tools, Wallet, Document, InfoFilled, House
} from '@element-plus/icons-vue'
import { getMobileRoomSummary, getRoomRepairOrders } from '@/api'
import { roomStatusText, roomStatusTag, repairStatusText, repairStatusTag } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const showAllRepair = ref(false)
const loadingHistory = ref(false)
const allRepairHistory = ref([])
const summary = ref({
  room: null,
  tenant: null,
  contract: null,
  unpaidAmount: 0,
  unpaidBills: [],
  repairHistory: [],
  canOperate: false
})

const roomId = computed(() => Number(route.params.id || route.params.roomId))
const roomTitle = computed(() => summary.value.room ? `${summary.value.room.roomNumber}` : '房间详情')
const canBack = computed(() => window.history.length > 1)

function formatMoney(v) {
  return `￥${Number(v || 0).toFixed(2)}`
}

async function load() {
  loading.value = true
  try {
    const res = await getMobileRoomSummary(roomId.value)
    summary.value = res.data
  } catch (_) {
    ElMessage.error('加载房间信息失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

function goMeter() {
  if (!summary.value.canOperate) return
  router.push({
    path: `/mobile/meter/${roomId.value}`,
    query: { from: 'room' }
  })
}

function goRepair() {
  if (!summary.value.canOperate) return
  router.push(`/mobile/repair/${roomId.value}`)
}

function goUnpaid() {
  if (!summary.value.canOperate) return
  router.push(`/mobile/bills/${roomId.value}`)
}

async function openRepairHistory() {
  showAllRepair.value = true
  if (loadingHistory.value) return
  loadingHistory.value = true
  try {
    const res = await getRoomRepairOrders(roomId.value)
    allRepairHistory.value = res.data || []
  } catch (_) {
    allRepairHistory.value = summary.value.repairHistory || []
  } finally {
    loadingHistory.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.room-page {
  gap: 14px;
}

/* 顶栏 */
.room-topbar {
  display: flex;
  align-items: center;
  min-height: 36px;
}

.back-link {
  color: var(--accent-3);
  padding: 0;
}

/* ---------- 房间横幅 ---------- */
.room-hero {
  padding: 20px var(--m-gutter);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  border-radius: var(--m-radius);
  color: var(--text-inverse);
}

.hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.hero-room {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.hero-status {
  flex: 0 0 auto;
  margin-top: 4px;
}

.hero-building {
  font-size: 14px;
  opacity: 0.9;
  margin-top: 6px;
}

.hero-meta {
  font-size: 12px;
  opacity: 0.8;
  margin-top: 4px;
}

.hero-meta .sep {
  margin: 0 5px;
  opacity: 0.6;
}

/* ---------- 欠费横幅 ---------- */
.arrears-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 13px var(--m-gutter);
  border-radius: var(--m-radius);
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger-color) 30%, transparent);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.arrears-banner:active {
  opacity: 0.85;
}

.arrears-left {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--danger-color);
  font-weight: 600;
}

.arrears-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.arrears-amount {
  font-size: 16px;
  font-weight: 700;
  color: var(--danger-color);
}

.arrears-arrow {
  color: var(--danger-color);
  opacity: 0.6;
}

/* ---------- 快捷操作：2x2 大按钮 ---------- */
.quick-ops {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--m-gap);
}

.op-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 78px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--m-radius);
  background: var(--bg-surface);
  color: var(--text-main);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: all 0.15s ease;
}

.op-btn .el-icon {
  color: var(--accent-3);
}

.op-btn:active:not(:disabled) {
  transform: scale(0.97);
  border-color: var(--accent-2);
}

.op-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* 主操作用实心色强调 */
.op-primary {
  background: var(--accent-3);
  border-color: var(--accent-3);
  color: #fff;
}

.op-primary .el-icon {
  color: #fff;
}

/* ---------- 只读提示 ---------- */
.readonly-tip {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px var(--m-gutter);
  border-radius: 10px;
  font-size: 12px;
  color: var(--text-muted);
  background: var(--el-fill-color-light, #f2f3f5);
  line-height: 1.5;
}

/* ---------- 租户与合同 ---------- */
.info-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
}

.info-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-row:first-child {
  padding-top: 0;
}

.info-label {
  color: var(--text-muted);
  flex: 0 0 auto;
  font-size: 12px;
}

.info-value {
  color: var(--text-main);
  text-align: right;
  word-break: break-all;
}

.info-value.link {
  color: var(--accent-3);
  text-decoration: none;
}

/* ---------- 空闲房间 ---------- */
.vacant-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 20px;
  border: 1px dashed var(--border-color);
  border-radius: var(--m-radius);
  text-align: center;
}

.vacant-icon {
  color: var(--text-muted);
  opacity: 0.4;
}

.vacant-title {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.vacant-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}

/* 空记录 */
.empty-mini {
  padding: 22px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}
</style>
