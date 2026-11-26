<template>
  <div class="page">
    <PageHeader title="投诉详情" to="/complaints" />

    <LoadingBlock v-if="loading" />

    <template v-else>
      <section class="panel" style="margin-bottom:10px">
        <div class="row-between">
          <strong>{{ detail.complaintNo || '-' }}</strong>
          <span :class="complaintStatusClass(detail.status)">{{ complaintStatusText(detail.status) }}</span>
        </div>
        <h3 style="margin:12px 0 6px">{{ detail.title || '-' }}</h3>
        <p class="muted" style="margin:0">
          {{ detail.roomNumber || '-' }} · {{ detail.type || '-' }} · {{ formatTime(detail.createdAt) }}
        </p>

        <p style="margin:14px 0 0;white-space:pre-wrap;line-height:1.7">{{ detail.content || '-' }}</p>

        <div class="thumb-list" v-if="images.length">
          <img v-for="(img, idx) in images" :key="idx" :src="img" class="thumb" alt="投诉图片" />
        </div>
      </section>

      <section class="panel" style="margin-bottom:10px" v-if="timeline.length">
        <div class="card-title">处理记录</div>
        <div class="list-item" v-for="(item, idx) in timeline" :key="idx">
          <p style="margin:0 0 6px"><strong>{{ actionText(item.action) }}</strong></p>
          <p style="margin:0 0 6px;white-space:pre-wrap">{{ item.content || '-' }}</p>
          <p class="muted" style="margin:0">{{ formatTime(item.time) }}</p>
        </div>
      </section>

      <section class="panel" style="margin-bottom:10px" v-if="showRating">
        <div class="card-title">我的评价</div>
        <div class="star-row">
          <span v-for="n in 5" :key="n" class="star" :class="{ on: n <= Number(detail.rating || 0) }">★</span>
        </div>
        <p class="muted" style="margin:8px 0 0">{{ detail.comment || '未填写评价内容' }}</p>
      </section>

      <section class="panel" v-if="canConfirm">
        <div class="card-title">确认处理完成</div>
        <p class="muted" style="margin:0 0 10px">处理方已反馈完成，请确认结果并评价</p>

        <label class="field-label">处理评分</label>
        <div class="star-row">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="star"
            :class="{ on: n <= confirmForm.rating }"
            @click="confirmForm.rating = n"
          >
            ★
          </button>
        </div>

        <label class="field-label" style="margin-top:12px">评价内容（可选，200 字以内）</label>
        <textarea
          class="textarea"
          maxlength="200"
          v-model.trim="confirmForm.comment"
          placeholder="说说问题是否解决、处理是否及时"
        ></textarea>
        <p class="muted" style="margin:6px 0 0;text-align:right">{{ confirmForm.comment.length }}/200</p>

        <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submitConfirm">
          {{ submitting ? '提交中...' : '确认完成' }}
        </button>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirmComplaint, getComplaintDetail, getErrorMessage } from '../api'
import { complaintStatusClass, complaintStatusText, formatTime } from '../utils/format'
import { confirmDialog, showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const route = useRoute()
const router = useRouter()
const detail = ref({})
const timeline = ref([])
const loading = ref(true)
const submitting = ref(false)
const confirmForm = reactive({ rating: 5, comment: '' })

const ACTIONS = {
  submit: '提交投诉',
  accept: '受理',
  reply: '回复',
  complete: '处理完成',
  reassign: '转派',
  close: '关闭',
  confirm: '租客确认'
}

const statusKey = computed(() => String(detail.value.status || '').toLowerCase())
const canConfirm = computed(() => statusKey.value === 'pending_confirm')
const showRating = computed(() =>
  ['completed', 'closed'].includes(statusKey.value) && Number(detail.value.rating || 0) > 0
)
const images = computed(() => {
  const raw = detail.value.images
  return Array.isArray(raw) ? raw : []
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getComplaintDetail(route.params.id)
    detail.value = res.data || {}
    timeline.value = res.data?.timeline || []
  } catch (error) {
    showError(getErrorMessage(error, '投诉详情加载失败'))
  } finally {
    loading.value = false
  }
}

async function submitConfirm() {
  if (!confirmForm.rating || confirmForm.rating < 1 || confirmForm.rating > 5) {
    showError('请先选择 1-5 星评分')
    return
  }

  const ok = await confirmDialog('确认投诉已处理完成？提交后不可修改评价。', {
    title: '确认完成',
    okText: '确认'
  })
  if (!ok) return

  submitting.value = true
  try {
    await confirmComplaint(route.params.id, {
      rating: confirmForm.rating,
      comment: confirmForm.comment
    })
    showSuccess('已确认完成，感谢您的评价')
    router.replace('/complaints')
  } catch (error) {
    showError(getErrorMessage(error, '提交失败，请重试'))
  } finally {
    submitting.value = false
  }
}

function actionText(action) {
  return ACTIONS[String(action || '').toLowerCase()] || action || '-'
}
</script>
