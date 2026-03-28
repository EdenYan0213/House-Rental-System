<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">账单详情</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>
      <p>账单月份：{{ bill.billMonth || '-' }}</p>
      <p>生成时间：{{ formatTime(bill.generatedDate) }}</p>
      <p>状态：<span :class="statusClass(bill.status)">{{ statusText(bill.status) }}</span></p>
      <p>总金额：￥{{ Number(bill.totalAmount || 0).toFixed(2) }}</p>
      <p>已付金额：￥{{ Number(bill.paidAmount || 0).toFixed(2) }}</p>
      <p>待付金额：￥{{ Number(bill.unpaidAmount || 0).toFixed(2) }}</p>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">费用明细</div>
      <div v-for="(item, idx) in details" :key="idx" class="list-item">
        <div class="row-between">
          <strong>{{ item.name || '-' }}</strong>
          <span>￥{{ Number(item.amount || 0).toFixed(2) }}</span>
        </div>
        <p class="muted" style="margin:8px 0 0">用量：{{ item.usage ?? '-' }}</p>
      </div>
      <p v-if="!details.length" class="muted">暂无明细</p>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">支付记录</div>
      <div v-for="(item, idx) in payments" :key="idx" class="list-item">
        <div class="row-between">
          <strong>￥{{ Number(item.paidAmount || 0).toFixed(2) }}</strong>
          <span>{{ item.paymentMethod || '-' }}</span>
        </div>
        <p class="muted" style="margin:8px 0 0">{{ formatTime(item.paidDate) }}</p>
      </div>
      <p v-if="!payments.length" class="muted">暂无支付记录</p>
    </section>

    <section class="panel" v-if="canPay">
      <div class="grid-two">
        <button class="btn btn-primary" :disabled="paying" @click="pay('wx')">微信支付</button>
        <button class="btn btn-primary" :disabled="paying" @click="pay('alipay')">支付宝支付</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createPaymentOrder, getBillDetail, paymentCallback } from '../api'

const route = useRoute()
const bill = ref({})
const details = ref([])
const payments = ref([])
const paying = ref(false)

const canPay = computed(() => ['PENDING', 'PARTIAL'].includes(String(bill.value.status || '').toUpperCase()))

onMounted(load)

async function load() {
  const res = await getBillDetail(route.params.id)
  bill.value = res.data?.bill || {}
  details.value = res.data?.details || []
  payments.value = res.data?.payments || []
}

async function pay(payType) {
  paying.value = true
  try {
    const createRes = await createPaymentOrder(Number(route.params.id), payType)
    const outTradeNo = createRes.data?.outTradeNo
    if (!outTradeNo) {
      throw new Error('创建支付单失败')
    }

    // 第一阶段使用模拟回调完成账单状态更新。
    await paymentCallback(outTradeNo, 'success', `${payType.toUpperCase()}_${Date.now()}`)
    await load()
    alert('支付成功')
  } catch (error) {
    alert(error?.response?.data?.message || error.message || '支付失败，请重试')
  } finally {
    paying.value = false
  }
}

function statusText(value) {
  return {
    PENDING: '待支付',
    PARTIAL: '部分支付',
    PAID: '已支付',
    VOID: '已作废'
  }[String(value || '').toUpperCase()] || value
}

function statusClass(value) {
  const statusValue = String(value || '').toUpperCase()
  if (statusValue === 'PENDING') return 'tag tag-pending'
  if (statusValue === 'PAID') return 'tag tag-paid'
  if (statusValue === 'PARTIAL') return 'tag tag-partial'
  return 'tag tag-void'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
