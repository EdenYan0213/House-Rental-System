<template>
  <div class="page">
    <PageHeader title="公告详情" to="/notices" />

    <LoadingBlock v-if="loading" />

    <section class="panel" v-else>
      <h2 style="margin:0 0 6px">{{ detail.title || '-' }}</h2>
      <p class="muted">{{ formatTime(detail.createdAt) }}</p>
      <img v-if="detail.image" :src="detail.image" alt="公告图片" style="width:100%;border-radius:10px;margin:10px 0" />
      <div class="notice-content" v-html="detail.content || '暂无内容'"></div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getErrorMessage, getNoticeDetail } from '../api'
import { formatTime } from '../utils/format'
import { showError } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const route = useRoute()
const detail = ref({})
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await getNoticeDetail(route.params.id)
    detail.value = res.data || {}
  } catch (error) {
    showError(getErrorMessage(error, '公告加载失败'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.notice-content {
  line-height: 1.7;
}
</style>
