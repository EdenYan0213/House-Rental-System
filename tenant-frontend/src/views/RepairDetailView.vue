<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">报修详情</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>
      <p>工单号：{{ detail.orderNo || '-' }}</p>
      <p>房间：{{ detail.roomNumber || '-' }}</p>
      <p>故障类型：{{ detail.faultType || '-' }}</p>
      <p>联系电话：{{ detail.phone || '-' }}</p>
      <p>状态：<span :class="statusClass(detail.status)">{{ statusText(detail.status) }}</span></p>
      <p>期望上门：{{ formatTime(detail.expectedTime) }}</p>
      <p>提交时间：{{ formatTime(detail.createdAt) }}</p>
      <p>描述：{{ detail.description || '-' }}</p>
      <div class="thumb-list" v-if="detail.images?.length">
        <img v-for="(img, idx) in detail.images" :key="idx" :src="img" class="thumb" alt="报修图片" />
      </div>
    </section>

    <section class="panel" style="margin-bottom:10px" v-if="detail.worker">
      <div class="card-title">维修人员</div>
      <p>姓名：{{ detail.worker.name || '-' }}</p>
      <p>电话：{{ detail.worker.phone || '-' }}</p>
    </section>

    <section class="panel" style="margin-bottom:10px" v-if="timeline.length">
      <div class="card-title">处理进度</div>
      <div class="list-item" v-for="(item, idx) in timeline" :key="idx">
        <p style="margin:0 0 6px"><strong>{{ item.content || '-' }}</strong></p>
        <p class="muted" style="margin:0">{{ item.operator || '-' }} | {{ formatTime(item.time) }}</p>
      </div>
    </section>

    <section class="panel" v-if="canConfirm">
      <div class="card-title">确认完成</div>
      <label class="field-label">服务评分（1-5）</label>
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
import { confirmRepair, getRepairDetail } from '../api'

const route = useRoute()
const router = useRouter()
const detail = ref({})
const timeline = ref([])
const submitting = ref(false)
const confirmForm = reactive({
  rating: 5,
  comment: ''
})

const canConfirm = computed(() => String(detail.value.status || '').toLowerCase() === 'pending_accept')

onMounted(load)

async function load() {
  const res = await getRepairDetail(route.params.id)
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
    await confirmRepair(route.params.id, {
      rating: confirmForm.rating,
      comment: confirmForm.comment
    })
    alert('确认完成成功')
    router.replace('/repairs')
  } catch (error) {
    alert(error?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function statusText(value) {
  const key = String(value || '').toLowerCase()
  if (['pending', 'processing', 'pending_accept'].includes(key)) return '处理中'
  if (['completed'].includes(key)) return '已完成'
  if (['cancelled'].includes(key)) return '已取消'
  return value || '-'
}

function statusClass(value) {
  const key = String(value || '').toLowerCase()
  if (['pending', 'processing', 'pending_accept'].includes(key)) return 'tag tag-pending'
  if (['completed'].includes(key)) return 'tag tag-paid'
  return 'tag tag-void'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>
