<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">公告详情</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>
      <h2 style="margin-bottom:6px">{{ detail.title || '-' }}</h2>
      <p class="muted">{{ formatTime(detail.createdAt) }}</p>
      <img v-if="detail.image" :src="detail.image" alt="公告图片" style="width:100%;border-radius:10px;margin:10px 0" />
      <div class="notice-content" v-html="detail.content || '暂无内容'"></div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getNoticeDetail } from '../api'

const route = useRoute()
const detail = ref({})

onMounted(async () => {
  const res = await getNoticeDetail(route.params.id)
  detail.value = res.data || {}
})

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.notice-content {
  line-height: 1.7;
}
</style>
