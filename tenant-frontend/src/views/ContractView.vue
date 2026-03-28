<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">电子合同</div>
        <button class="btn btn-primary" style="width:96px" :disabled="!current.id" @click="goRenew">申请续租</button>
      </div>
      <template v-if="current.id">
        <p>合同号：{{ current.contractNo || '-' }}</p>
        <p>房间：{{ current.roomNumber || '-' }}</p>
        <p>租期：{{ formatDate(current.startDate) }} 至 {{ formatDate(current.endDate) }}</p>
        <p>月租：￥{{ Number(current.monthlyRent || 0).toFixed(2) }}</p>
        <p>押金：￥{{ Number(current.deposit || 0).toFixed(2) }}</p>
        <p>支付方式：{{ current.paymentMethod || '-' }}</p>
        <p>状态：{{ current.status || '-' }}</p>
        <p class="muted">{{ current.canApplyRenew ? '当前合同已进入可续租期' : '距离到期90天内可申请续租' }}</p>
        <button class="btn btn-light" style="width:100%" @click="download(current)">下载合同附件</button>
      </template>
      <p v-else class="muted">暂无当前合同</p>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">续租记录</div>
      <div v-for="item in renews" :key="item.id" class="list-item">
        <div class="row-between">
          <strong>续租 {{ item.expectedDuration }} 个月</strong>
          <span :class="renewStatusClass(item.status)">{{ renewStatusText(item.status) }}</span>
        </div>
        <p class="muted" style="margin:8px 0 4px">提交时间：{{ formatTime(item.createdAt) }}</p>
        <p style="margin:0">备注：{{ item.remark || '-' }}</p>
      </div>
      <p class="muted" v-if="!renews.length">暂无续租记录</p>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('sign_title') }}</div>
      <template v-if="current.id">
        <div class="grid-two" style="margin-bottom:8px">
          <div>
            <label class="field-label">{{ t('sign_verify_name') }}</label>
            <input class="input" v-model="verifyForm.name" />
          </div>
          <div>
            <label class="field-label">{{ t('sign_verify_id') }}</label>
            <input class="input" v-model="verifyForm.idCard" />
          </div>
        </div>
        <p>{{ t('sign_verify_status') }}：{{ signPreview.verifyStatus || '-' }}</p>
        <p>{{ t('sign_status') }}：{{ signPreview.signStatus || '-' }}</p>
        <div class="grid-two">
          <button class="btn btn-light" @click="doVerify">{{ t('sign_verify') }}</button>
          <button class="btn btn-primary" @click="doSign">{{ t('sign_submit') }}</button>
          <button class="btn btn-light" @click="doRetry">{{ t('sign_retry') }}</button>
          <button class="btn btn-light" @click="openAttachment">{{ t('sign_open_contract') }}</button>
        </div>
      </template>
      <p v-else class="muted">{{ t('sign_need_contract') }}</p>

      <div style="margin-top:10px">
        <div class="field-label">{{ t('sign_history') }}</div>
        <div v-for="item in signHistory" :key="item.id" class="list-item">
          <div class="row-between">
            <strong>{{ item.contractNo || '-' }}</strong>
            <span>{{ item.signStatus || '-' }}</span>
          </div>
          <p class="muted" style="margin:8px 0 0">{{ formatTime(item.updatedAt) }}</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="card-title">历史合同</div>
      <div v-for="item in history" :key="item.id" class="list-item">
        <div class="row-between">
          <strong>{{ item.contractNo || '-' }}</strong>
          <span>{{ item.status || '-' }}</span>
        </div>
        <p style="margin:8px 0 4px">{{ formatDate(item.startDate) }} 至 {{ formatDate(item.endDate) }}</p>
        <p class="muted" style="margin:0 0 8px">房间：{{ item.roomNumber || '-' }} | 月租：￥{{ Number(item.monthlyRent || 0).toFixed(2) }}</p>
        <button class="btn btn-light" style="width:100%" @click="download(item)">下载附件</button>
      </div>
      <p class="muted" v-if="!history.length">暂无历史合同</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  downloadContract,
  getContractHistory,
  getCurrentContract,
  getRenewRecords,
  getSignHistory,
  getSignPreview,
  retrySignContract,
  submitSignContract,
  verifySignIdentity
} from '../api'
import { t } from '../utils/i18n'

const router = useRouter()
const current = ref({})
const history = ref([])
const renews = ref([])
const signPreview = ref({})
const signHistory = ref([])
const verifyForm = ref({ name: '', idCard: '' })

onMounted(load)

async function load() {
  const [currentRes, historyRes, renewRes] = await Promise.all([
    getCurrentContract(),
    getContractHistory(),
    getRenewRecords()
  ])
  current.value = currentRes.data || {}
  history.value = historyRes.data || []
  renews.value = renewRes.data || []
  if (current.value?.id) {
    const previewRes = await getSignPreview(current.value.id)
    signPreview.value = previewRes.data || {}
  }
  const signRes = await getSignHistory()
  signHistory.value = signRes.data || []
}

async function doVerify() {
  if (!current.value?.id) return
  await verifySignIdentity({
    contractId: current.value.id,
    name: verifyForm.value.name,
    idCard: verifyForm.value.idCard
  })
  alert('实名认证成功')
  await load()
}

async function doSign() {
  if (!current.value?.id) return
  await submitSignContract({ contractId: current.value.id })
  alert('签署成功')
  await load()
}

async function doRetry() {
  if (!current.value?.id) return
  await retrySignContract(current.value.id)
  alert('已发起重试')
  await load()
}

function openAttachment() {
  if (!current.value?.id) return
  download(current.value)
}

function goRenew() {
  if (!current.value?.id) {
    alert('暂无可续租合同')
    return
  }
  router.push({ path: '/renew', query: { contractId: current.value.id } })
}

async function download(item) {
  try {
    const res = await downloadContract(item.id)
    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${item.contractNo || 'contract'}.pdf`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    alert(error?.response?.data?.message || '下载失败')
  }
}

function renewStatusText(status) {
  const value = Number(status)
  if (value === 0) return '待处理'
  if (value === 1) return '已通过'
  if (value === 2) return '已驳回'
  return '-'
}

function renewStatusClass(status) {
  const value = Number(status)
  if (value === 0) return 'tag tag-pending'
  if (value === 1) return 'tag tag-paid'
  return 'tag tag-void'
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleDateString('zh-CN')
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
