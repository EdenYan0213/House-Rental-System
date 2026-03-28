<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">投诉详情</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>
      <p>投诉单号：{{ detail.complaintNo || '-' }}</p>
      <p>房间：{{ detail.roomNumber || '-' }}</p>
      <p>类型：{{ detail.type || '-' }}</p>
      <p>标题：{{ detail.title || '-' }}</p>
      <p>状态：<span :class="statusClass(detail.status)">{{ statusText(detail.status) }}</span></p>
      <p>提交时间：{{ formatTime(detail.createdAt) }}</p>
      <p>内容：{{ detail.content || '-' }}</p>
      <div class="thumb-list" v-if="detail.images?.length">
        <img v-for="(img, idx) in detail.images" :key="idx" :src="img" class="thumb" alt="投诉图片" />
      </div>
    </section>

    <section class="panel" style="margin-bottom:10px" v-if="timeline.length">
      <div class="card-title">处理记录</div>
      <div class="list-item" v-for="(item, idx) in timeline" :key="idx">
        <p style="margin:0 0 6px"><strong>{{ item.action || '-' }}</strong></p>
        <p style="margin:0 0 6px">{{ item.content || '-' }}</p>
        <p class="muted" style="margin:0">{{ formatTime(item.time) }}</p>
      </div>
    </section>

    <section class="panel" v-if="canConfirm">
      <div class="card-title">确认处理完成</div>
      <label class="field-label">处理评分（1-5）</label>
      <input class="input" type="number" min="1" max="5" v-model.number="confirmForm.rating" />
      <label class="field-label" style="margin-top:10px">评价内容（可选）</label>
      <textarea class="textarea" maxlength="200" v-model.trim="confirmForm.comment" placeholder="请输入评价内容"></textarea>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submitConfirm">
        {{ submitting ? '提交中...' : '确认完成' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirmComplaint, getComplaintDetail } from '../api'

const route = useRoute()
const router = useRouter()
const detail = ref({})
const timeline = ref([])
const submitting = ref(false)
const confirmForm = reactive({
  rating: 5,
  comment: ''
})

const canConfirm = computed(() => String(detail.value.status || '').toLowerCase() === 'pending_confirm')

onMounted(load)

async function load() {
  const res = await getComplaintDetail(route.params.id)
  detail.value = res.data || {}
  timeline.value = res.data?.timeline || []
}

async function submitConfirm() {
  if (!confirmForm.rating || confirmForm.rating < 1 || confirmForm.rating > 5) {
    alert('评分需在1到5之间')
    return
  }
  submitting.value = true
  try {
    await confirmComplaint(route.params.id, {
      rating: confirmForm.rating,
      comment: confirmForm.comment
    })
    alert('确认完成成功')
    router.replace('/complaints')
  } catch (error) {
    alert(error?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function statusText(value) {
  const key = String(value || '').toLowerCase()
  if (['pending', 'processing', 'pending_confirm'].includes(key)) return '处理中'
  if (['completed', 'closed'].includes(key)) return '已完成'
  return value || '-'
}

function statusClass(value) {
  const key = String(value || '').toLowerCase()
  if (['pending', 'processing', 'pending_confirm'].includes(key)) return 'tag tag-pending'
  if (['completed', 'closed'].includes(key)) return 'tag tag-paid'
  return 'tag tag-void'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
