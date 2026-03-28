<template>
  <div class="page">
    <section class="panel" v-if="item.id">
      <div class="card-title">{{ item.title }}</div>
      <p class="muted">{{ formatTime(item.createdAt) }}</p>
      <p style="white-space: pre-wrap">{{ item.content || '-' }}</p>
      <div class="grid-two">
        <button class="btn btn-light" @click="back">返回</button>
        <button v-if="item.targetUrl" class="btn btn-primary" @click="goTarget">去处理</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTenantMessageDetail } from '../api'

const route = useRoute()
const router = useRouter()
const item = ref({})

onMounted(load)

async function load() {
  const res = await getTenantMessageDetail(route.params.id)
  item.value = res.data || {}
}

function back() {
  router.back()
}

function goTarget() {
  router.push(item.value.targetUrl)
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}
</script>
