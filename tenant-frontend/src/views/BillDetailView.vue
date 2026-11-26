<template>
  <div class="page">
    <PageHeader title="账单详情" to="/bills" />

    <LoadingBlock v-if="loading" />

    <template v-else>
      <section class="panel" style="margin-bottom:10px">
        <div class="row-between">
          <span class="muted">{{ bill.billMonth || '-' }}</span>
          <span :class="billStatusClass(bill.status)">{{ billStatusText(bill.status) }}</span>
        </div>
        <h2 style="margin:12px 0 4px">￥{{ money(bill.totalAmount) }}</h2>
        <p class="muted" style="margin:0">生成时间：{{ formatTime(bill.generatedDate) }}</p>

        <div class="stat-row" style="margin-top:14px">
          <div class="stat-card">
            <div class="stat-value">￥{{ money(bill.totalAmount) }}</div>
            <div class="stat-label">应缴</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: var(--success)">￥{{ money(bill.paidAmount) }}</div>
            <div class="stat-label">已缴</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: var(--danger)">￥{{ money(bill.unpaidAmount) }}</div>
            <div class="stat-label">待缴</div>
          </div>
        </div>
      </section>

      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">费用明细</div>
        <div v-for="(item, idx) in details" :key="idx" class="list-item">
          <div class="row-between">
            <strong>{{ item.name || '-' }}</strong>
            <span>￥{{ money(item.amount) }}</span>
          </div>
          <p class="muted" style="margin:8px 0 0">用量：{{ item.usage ?? '-' }}</p>
        </div>
        <EmptyState v-if="!details.length" text="暂无明细" />
      </section>

      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">支付记录</div>
        <div v-for="(item, idx) in payments" :key="idx" class="list-item">
          <div class="row-between">
            <strong>￥{{ money(item.paidAmount) }}</strong>
            <span class="muted">{{ payMethodText(item.paymentMethod) }}</span>
          </div>
          <p class="muted" style="margin:8px 0 0">{{ formatTime(item.paidDate) }}</p>
        </div>
        <EmptyState v-if="!payments.length" text="暂无支付记录" />
      </section>

      <section class="panel" v-if="canPay">
        <div class="card-title">在线支付</div>
        <p class="muted" style="margin:0 0 12px">
          待缴金额 <strong style="color: var(--danger)">￥{{ money(bill.unpaidAmount) }}</strong>
        </p>
        <div class="grid-two">
          <button class="btn btn-light" :disabled="paying" @click="pay('wx')">微信支付</button>
          <button class="btn btn-light" :disabled="paying" @click="pay('alipay')">支付宝支付</button>
        </div>
        <p class="muted" style="margin:10px 0 0;text-align:center">
          {{ paying ? '支付处理中，请勿关闭页面…' : '当前为演示支付，不会产生真实扣款' }}
        </p>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  createPaymentOrder,
  getBillDetail,
  getErrorMessage,
  getPaymentOrder,
  paymentCallback
} from '../api'
import { billStatusClass, billStatusText, formatTime, money } from '../utils/format'
import { confirmDialog, showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const route = useRoute()
const bill = ref({})
const details = ref([])
const payments = ref([])
const paying = ref(false)
const loading = ref(true)

const canPay = computed(() =>
  ['PENDING', 'PARTIAL'].includes(String(bill.value.status || '').toUpperCase())
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getBillDetail(route.params.id)
    bill.value = res.data?.bill || {}
    details.value = res.data?.details || []
    payments.value = res.data?.payments || []
  } catch (error) {
    showError(getErrorMessage(error, '账单加载失败'))
  } finally {
    loading.value = false
  }
}

async function pay(payType) {
  const label = payType === 'wx' ? '微信' : '支付宝'
  const ok = await confirmDialog(
    `确认使用${label}支付 ￥${money(bill.value.unpaidAmount)}？`,
    { title: '确认支付', okText: '去支付' }
  )
  if (!ok) return

  paying.value = true
  try {
    const createRes = await createPaymentOrder(Number(route.params.id), payType)
    const outTradeNo = createRes.data?.outTradeNo
    if (!outTradeNo) {
      throw new Error('创建支付单失败，请稍后重试')
    }

    // 演示阶段由前端触发模拟回调；真实接入后应改为只轮询订单状态
    await paymentCallback(outTradeNo, 'success', `${payType.toUpperCase()}_${Date.now()}`)

    // 回调后再确认订单状态，避免"回调成功但账单未更新"的假成功
    const statusRes = await getPaymentOrder(outTradeNo)
    if (statusRes.data?.paid) {
      showSuccess('支付成功')
      await load()
    } else {
      showError('支付状态确认失败，请稍后在支付记录中查看')
    }
  } catch (error) {
    showError(getErrorMessage(error, '支付失败，请重试'))
  } finally {
    paying.value = false
  }
}

function payMethodText(method) {
  const key = String(method || '').toUpperCase()
  if (key === 'WECHAT' || key === 'WX') return '微信支付'
  if (key === 'ALIPAY') return '支付宝'
  if (key === 'CASH') return '现金'
  if (key === 'TRANSFER' || key === 'BANK') return '银行转账'
  return method || '-'
}
</script>
