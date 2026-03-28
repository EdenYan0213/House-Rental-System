<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('dash_title') }}</div>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('dash_bill') }}</div>
      <div v-for="item in data.billTrend || []" :key="item.month" class="list-item">
        <div class="row-between">
          <span>{{ item.month }}</span>
          <strong>￥{{ Number(item.amount || 0).toFixed(2) }}</strong>
        </div>
      </div>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('dash_repair') }}</div>
      <p>Pending: {{ data.repair?.pending || 0 }}</p>
      <p>Completed: {{ data.repair?.completed || 0 }}</p>
      <div class="card-title">{{ t('dash_complaint') }}</div>
      <p>Pending: {{ data.complaint?.pending || 0 }}</p>
      <p>Total: {{ data.complaint?.total || 0 }}</p>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('dash_renew') }}</div>
      <p>Pending: {{ data.renew?.pendingCount || 0 }}</p>
      <p v-if="data.renew?.hint?.daysLeft !== null">Days Left: {{ data.renew?.hint?.daysLeft }}</p>
      <button v-if="data.renew?.hint?.canApplyRenew" class="btn btn-primary" style="width:100%" @click="goRenew">去续租</button>
    </section>

    <section class="panel">
      <div class="card-title">{{ t('dash_stay') }}</div>
      <p>{{ data.stay?.days || 0 }} {{ t('dash_days') }}</p>
      <p>{{ data.stay?.months || 0 }} {{ t('dash_months') }}</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getTenantDashboard } from '../api'
import { t } from '../utils/i18n'

const router = useRouter()
const data = ref({})

onMounted(load)

async function load() {
  const res = await getTenantDashboard({ months: 12 })
  data.value = res.data || {}
}

function goRenew() {
  const contractId = data.value?.renew?.hint?.contractId
  if (!contractId) return
  router.push({ path: '/renew', query: { contractId } })
}
</script>
