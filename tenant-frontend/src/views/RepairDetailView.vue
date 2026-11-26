<template>
  <div class="page">
    <PageHeader title="报修详情" to="/repairs" />

    <LoadingBlock v-if="loading" />

    <template v-else>
      <section class="panel" style="margin-bottom:10px">
        <div class="row-between">
          <strong>{{ detail.orderNo || '-' }}</strong>
          <span :class="repairStatusClass(detail.status)">{{ repairStatusText(detail.status) }}</span>
        </div>
        <p class="muted" style="margin:8px 0 0">
          {{ detail.roomNumber || '-' }} · 提交于 {{ formatTime(detail.createdAt) }}
        </p>

        <div style="margin-top:12px">
          <p style="margin:6px 0">故障类型：{{ detail.faultType || '-' }}</p>
          <p style="margin:6px 0">联系电话：{{ detail.phone || '-' }}</p>
          <p style="margin:6px 0">期望上门：{{ formatTime(detail.expectedTime) }}</p>
          <p style="margin:6px 0">故障描述：{{ detail.description || '-' }}</p>
        </div>

        <div class="thumb-list" v-if="images.length">
          <img v-for="(img, idx) in images" :key="idx" :src="img" class="thumb" alt="报修图片" />
        </div>
      </section>

      <!-- 费用信息：维修可能产生材料费/人工费 -->
      <section class="panel" style="margin-bottom:10px" v-if="hasFee">
        <div class="card-title">费用信息</div>
        <div class="stat-row">
          <div class="stat-card">
            <div class="stat-value">￥{{ money(detail.materialFee) }}</div>
            <div class="stat-label">材料费</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">￥{{ money(detail.laborFee) }}</div>
            <div class="stat-label">人工费</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" :style="{ color: detail.isTenantCharge ? 'var(--danger)' : 'var(--success)' }">
              ￥{{ money(detail.totalFee) }}
            </div>
            <div class="stat-label">{{ detail.isTenantCharge ? '需租客承担' : '房东承担' }}</div>
          </div>
        </div>
        <p v-if="detail.feeRemark" class="muted" style="margin:10px 0 0">
          说明：{{ detail.feeRemark }}
        </p>
      </section>

      <section class="panel" style="margin-bottom:10px" v-if="detail.worker">
        <div class="card-title">维修人员</div>
        <p style="margin:6px 0">姓名：{{ detail.worker.name || '-' }}</p>
        <p style="margin:6px 0">
          电话：
          <a v-if="detail.worker.phone" :href="`tel:${detail.worker.phone}`" class="link">
            {{ detail.worker.phone }}
          </a>
          <span v-else>-</span>
        </p>
      </section>

      <section class="panel" style="margin-bottom:10px" v-if="timeline.length">
        <div class="card-title">处理进度</div>
        <div class="list-item" v-for="(item, idx) in timeline" :key="idx">
          <p style="margin:0 0 6px"><strong>{{ item.content || '-' }}</strong></p>
          <p class="muted" style="margin:0">
            {{ item.operator || '-' }} · {{ formatTime(item.time) }}
          </p>
        </div>
      </section>

      <!-- 已完成的工单展示评价结果 -->
      <section class="panel" style="margin-bottom:10px" v-if="showRating">
        <div class="card-title">我的评价</div>
        <div class="star-row">
          <span v-for="n in 5" :key="n" class="star" :class="{ on: n <= Number(detail.rating || 0) }">★</span>
        </div>
        <p class="muted" style="margin:8px 0 0">{{ detail.comment || '未填写评价内容' }}</p>
      </section>

      <section class="panel" v-if="canConfirm">
        <div class="card-title">确认完成并评价</div>
        <p class="muted" style="margin:0 0 10px">维修已完成，请确认并为本此服务打分</p>

        <label class="field-label">服务评分</label>
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
          placeholder="说说维修是否及时、服务态度如何"
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
import { confirmRepair, getRepairDetail, getErrorMessage } from '../api'
import { formatTime, money, repairStatusClass, repairStatusText } from '../utils/format'
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

const statusKey = computed(() => String(detail.value.status || '').toLowerCase())
const canConfirm = computed(() => statusKey.value === 'pending_accept')
// 已完成且打过分才展示评价
const showRating = computed(() => statusKey.value === 'completed' && Number(detail.value.rating || 0) > 0)
// 后端返回的 images 是数组；历史数据可能是 JSON 字符串
const images = computed(() => {
  const raw = detail.value.images
  return Array.isArray(raw) ? raw : []
})
const hasFee = computed(() =>
  Number(detail.value.materialFee || 0) > 0
  || Number(detail.value.laborFee || 0) > 0
  || Number(detail.value.totalFee || 0) > 0
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getRepairDetail(route.params.id)
    detail.value = res.data || {}
    timeline.value = res.data?.timeline || []
  } catch (error) {
    showError(getErrorMessage(error, '报修详情加载失败'))
  } finally {
    loading.value = false
  }
}

async function submitConfirm() {
  if (!confirmForm.rating || confirmForm.rating < 1 || confirmForm.rating > 5) {
    showError('请先选择 1-5 星评分')
    return
  }

  const ok = await confirmDialog('确认维修已完成？提交后不可修改评价。', {
    title: '确认完成',
    okText: '确认'
  })
  if (!ok) return

  submitting.value = true
  try {
    await confirmRepair(route.params.id, {
      rating: confirmForm.rating,
      comment: confirmForm.comment
    })
    showSuccess('已确认完成，感谢您的评价')
    router.replace('/repairs')
  } catch (error) {
    showError(getErrorMessage(error, '提交失败，请重试'))
  } finally {
    submitting.value = false
  }
}
</script>
