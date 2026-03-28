<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">{{ t('msg_title') }}</div>
        <span class="muted">{{ t('msg_unread') }}: {{ unreadCount }}</span>
      </div>
      <div class="grid-two" style="margin-top:8px">
        <button class="btn" :class="filter === 'all' ? 'btn-primary' : 'btn-light'" @click="changeFilter('all')">{{ t('msg_filter_all') }}</button>
        <button class="btn" :class="filter === 'TODO' ? 'btn-primary' : 'btn-light'" @click="changeFilter('TODO')">{{ t('msg_filter_todo') }}</button>
        <button class="btn" :class="filter === 'NOTICE' ? 'btn-primary' : 'btn-light'" @click="changeFilter('NOTICE')">{{ t('msg_filter_notice') }}</button>
      </div>
    </section>

    <section class="panel">
      <div v-for="item in rows" :key="item.id" class="list-item">
        <div class="row-between">
          <strong>{{ item.title }}</strong>
          <span v-if="Number(item.isRead || 0) === 0" class="tag tag-pending">{{ t('msg_unread') }}</span>
        </div>
        <p class="muted" style="margin:8px 0">{{ formatTime(item.createdAt) }}</p>
        <div class="grid-two">
          <button class="btn btn-light" @click="$router.push(`/messages/${item.id}`)">详情</button>
          <button class="btn btn-light" @click="onRead(item.id)">{{ t('msg_read') }}</button>
          <button class="btn btn-light" @click="onDelete(item.id)">{{ t('msg_delete') }}</button>
          <button v-if="item.needAction === 1 && item.actionStatus !== 'DONE'" class="btn btn-primary" @click="onProcess(item.id)">{{ t('msg_process') }}</button>
        </div>
      </div>
      <p v-if="!rows.length" class="muted">{{ t('msg_empty') }}</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  deleteTenantMessage,
  getTenantMessages,
  processTenantMessage,
  readTenantMessage
} from '../api'
import { t } from '../utils/i18n'

const rows = ref([])
const unreadCount = ref(0)
const filter = ref('all')

onMounted(load)

async function load() {
  const params = filter.value === 'all' ? {} : { category: filter.value }
  const res = await getTenantMessages(params)
  const data = res.data || {}
  rows.value = data.content || []
  unreadCount.value = Number(data.unreadCount || 0)
}

async function onRead(id) {
  await readTenantMessage(id)
  await load()
}

async function onDelete(id) {
  await deleteTenantMessage(id)
  await load()
}

async function onProcess(id) {
  await processTenantMessage(id)
  await load()
}

async function changeFilter(value) {
  filter.value = value
  await load()
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}
</script>
