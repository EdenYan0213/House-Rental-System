<template>
  <div class="page">
    <PageHeader title="我的合同" to="/me" />

    <LoadingBlock v-if="loading" />

    <template v-else>
      <!-- 当前合同 -->
      <section class="panel" style="margin-bottom:10px">
        <div class="row-between">
          <div class="card-title" style="margin:0">当前合同</div>
          <button
            class="btn btn-primary"
            style="width:96px"
            :disabled="!current.id || !current.canApplyRenew"
            @click="goRenew"
          >
            申请续租
          </button>
        </div>

        <template v-if="current.id">
          <div class="row-between" style="margin-top:12px">
            <strong>{{ current.contractNo || '-' }}</strong>
            <span :class="contractTagClass">{{ contractStatusText(current.status) }}</span>
          </div>

          <p style="margin:12px 0 6px">房间：{{ current.roomNumber || '-' }}</p>
          <p style="margin:6px 0">租期：{{ formatDate(current.startDate) }} 至 {{ formatDate(current.endDate) }}</p>
          <p style="margin:6px 0">月租：￥{{ money(current.monthlyRent) }}</p>
          <p style="margin:6px 0">押金：￥{{ money(current.deposit) }}</p>
          <p style="margin:6px 0">支付方式：{{ paymentMethodText(current.paymentMethod) }}</p>

          <p class="muted" style="margin:10px 0 0">
            {{ current.canApplyRenew ? '当前合同已进入可续租期' : '距离到期 90 天内可申请续租' }}
          </p>

          <div class="grid-two" style="margin-top:12px">
            <button class="btn btn-light" @click="$router.push('/deposit')">查看押金明细</button>
            <button class="btn btn-light" @click="download(current)">下载合同附件</button>
          </div>
        </template>
        <EmptyState v-else text="暂无生效中的合同" />
      </section>

      <!-- 电子签署 -->
      <section class="panel" style="margin-bottom:10px" v-if="current.id">
        <div class="card-title">电子签署</div>

        <!-- 状态机：实名认证 → 签署 → 完成 -->
        <div class="sign-steps">
          <div class="sign-step" :class="{ done: verifyPassed }">
            <span class="sign-step-no">1</span>
            <span>实名认证</span>
          </div>
          <div class="sign-step" :class="{ done: verified, active: verifyPassed && !verified }">
            <span class="sign-step-no">2</span>
            <span>签署合同</span>
          </div>
          <div class="sign-step" :class="{ done: verified }">
            <span class="sign-step-no">3</span>
            <span>签署完成</span>
          </div>
        </div>

        <p class="muted" style="margin:8px 0 12px">
          实名状态：{{ verifyStatusText }} · 签署状态：{{ signStatusText }}
        </p>

        <!-- 未实名才展示表单 -->
        <template v-if="!verifyPassed">
          <label class="field-label">姓名</label>
          <input class="input" v-model.trim="verifyForm.name" placeholder="与身份证一致" />

          <label class="field-label" style="margin-top:10px">身份证号</label>
          <input class="input" v-model.trim="verifyForm.idCard" placeholder="18 位身份证号" maxlength="18" />

          <button
            class="btn btn-primary"
            style="width:100%;margin-top:12px"
            :disabled="signBusy"
            @click="doVerify"
          >
            提交实名认证
          </button>
        </template>

        <template v-else-if="!verified">
          <p class="muted" style="margin:0 0 12px">实名认证已通过，请签署合同</p>
          <button class="btn btn-primary" style="width:100%" :disabled="signBusy" @click="doSign">
            {{ signBusy ? '签署中...' : '确认签署' }}
          </button>
        </template>

        <template v-else>
          <p class="muted" style="margin:0">合同已签署完成</p>
        </template>

        <!-- 仅失败时出现重试 -->
        <button
          v-if="canRetry"
          class="btn btn-light"
          style="width:100%;margin-top:10px"
          :disabled="signBusy"
          @click="doRetry"
        >
          签署失败，重试
        </button>

        <p v-if="signError" class="error">{{ signError }}</p>
      </section>

      <!-- 续租记录 -->
      <section class="panel" style="margin-bottom:10px">
        <div class="card-title">续租记录</div>
        <div v-for="item in renews" :key="item.id" class="list-item">
          <div class="row-between">
            <strong>续租 {{ item.expectedDuration || '-' }} 个月</strong>
            <span :class="renewStatusClass(item.status)">{{ renewStatusText(item.status) }}</span>
          </div>
          <p class="muted" style="margin:8px 0 4px">提交时间：{{ formatTime(item.createdAt) }}</p>
          <p style="margin:0">备注：{{ item.remark || '-' }}</p>
          <p v-if="item.resultRemark" class="muted" style="margin:6px 0 0">
            处理意见：{{ item.resultRemark }}
          </p>
        </div>
        <EmptyState v-if="!renews.length" text="暂无续租记录" />
      </section>

      <!-- 历史合同 -->
      <section class="panel" style="margin-bottom:10px" v-if="history.length">
        <div class="card-title">历史合同</div>
        <div v-for="item in history" :key="item.id" class="list-item">
          <div class="row-between">
            <strong>{{ item.contractNo || '-' }}</strong>
            <span class="tag tag-void">{{ contractStatusText(item.status) }}</span>
          </div>
          <p class="muted" style="margin:8px 0 0">
            {{ formatDate(item.startDate) }} 至 {{ formatDate(item.endDate) }} · 月租 ￥{{ money(item.monthlyRent) }}
          </p>
          <button class="btn btn-light" style="margin-top:10px" @click="download(item)">下载附件</button>
        </div>
      </section>

      <!-- 签署历史 -->
      <section class="panel" v-if="signHistory.length">
        <div class="card-title">签署记录</div>
        <div v-for="item in signHistory" :key="item.id" class="list-item">
          <div class="row-between">
            <strong>{{ item.contractNo || '-' }}</strong>
            <span class="tag tag-partial">{{ signStatusTextOf(item.signStatus) }}</span>
          </div>
          <p class="muted" style="margin:8px 0 0">{{ formatTime(item.updatedAt) }}</p>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  downloadContract,
  getContractHistory,
  getCurrentContract,
  getErrorMessage,
  getRenewRecords,
  getSignHistory,
  getSignPreview,
  retrySignContract,
  submitSignContract,
  verifySignIdentity
} from '../api'
import {
  contractStatusText,
  formatDate,
  formatTime,
  money,
  renewStatusClass,
  renewStatusText
} from '../utils/format'
import { confirmDialog, showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const router = useRouter()
const current = ref({})
const history = ref([])
const renews = ref([])
const signPreview = ref({})
const signHistory = ref([])
const verifyForm = reactive({ name: '', idCard: '' })
const loading = ref(true)
const signBusy = ref(false)
const signError = ref('')

const VERIFY_TEXT = { PENDING: '未认证', PASSED: '已认证', FAILED: '认证失败' }
const SIGN_TEXT = { UNSIGNED: '未签署', SIGNED: '已签署', FAILED: '签署失败', SIGNING: '签署中' }

const verifyPassed = computed(() => String(signPreview.value.verifyStatus || '').toUpperCase() === 'PASSED')
const verified = computed(() => String(signPreview.value.signStatus || '').toUpperCase() === 'SIGNED')
const canRetry = computed(() => String(signPreview.value.signStatus || '').toUpperCase() === 'FAILED')
const verifyStatusText = computed(() =>
  VERIFY_TEXT[String(signPreview.value.verifyStatus || '').toUpperCase()] || signPreview.value.verifyStatus || '-'
)
const signStatusText = computed(() =>
  SIGN_TEXT[String(signPreview.value.signStatus || '').toUpperCase()] || signPreview.value.signStatus || '-'
)
const contractTagClass = computed(() => {
  const key = String(current.value.status || '').toUpperCase()
  return key === 'ACTIVE' || current.value.status === '进行中' ? 'tag tag-paid' : 'tag tag-void'
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    // 三块数据独立：任一失败不应整页空白
    const [currentRes, historyRes, renewRes] = await Promise.allSettled([
      getCurrentContract(),
      getContractHistory(),
      getRenewRecords()
    ])

    if (currentRes.status === 'fulfilled') {
      current.value = currentRes.value.data || {}
    } else {
      showError(getErrorMessage(currentRes.reason, '当前合同加载失败'))
    }
    if (historyRes.status === 'fulfilled') {
      history.value = historyRes.value.data || []
    }
    if (renewRes.status === 'fulfilled') {
      renews.value = renewRes.value.data || []
    }

    if (current.value?.id) {
      try {
        const previewRes = await getSignPreview(current.value.id)
        signPreview.value = previewRes.data || {}
      } catch (error) {
        showError(getErrorMessage(error, '签署状态加载失败'))
      }
    }

    try {
      const signRes = await getSignHistory()
      signHistory.value = signRes.data || []
    } catch (_) {
      // 历史记录失败不影响主流程
      signHistory.value = []
    }
  } finally {
    loading.value = false
  }
}

async function doVerify() {
  signError.value = ''
  if (!current.value?.id) return

  if (!verifyForm.name) {
    signError.value = '请填写姓名'
    return
  }
  if (!/^\d{17}[\dXx]$/.test(verifyForm.idCard)) {
    signError.value = '请输入 18 位有效身份证号'
    return
  }

  signBusy.value = true
  try {
    await verifySignIdentity({
      contractId: current.value.id,
      name: verifyForm.name,
      idCard: verifyForm.idCard
    })
    showSuccess('实名认证成功')
    await load()
  } catch (error) {
    signError.value = getErrorMessage(error, '实名认证失败')
  } finally {
    signBusy.value = false
  }
}

async function doSign() {
  signError.value = ''
  const ok = await confirmDialog('签署后将产生法律效力，确认签署该合同？', {
    title: '确认签署',
    okText: '签署'
  })
  if (!ok) return

  signBusy.value = true
  try {
    await submitSignContract({ contractId: current.value.id })
    showSuccess('签署成功')
    await load()
  } catch (error) {
    signError.value = getErrorMessage(error, '签署失败')
  } finally {
    signBusy.value = false
  }
}

async function doRetry() {
  signError.value = ''
  signBusy.value = true
  try {
    await retrySignContract(current.value.id)
    showSuccess('已发起重试')
    await load()
  } catch (error) {
    signError.value = getErrorMessage(error, '重试失败')
  } finally {
    signBusy.value = false
  }
}

function goRenew() {
  if (!current.value?.id) {
    showError('暂无可续租合同')
    return
  }
  router.push({ path: '/renew', query: { contractId: current.value.id } })
}

async function download(item) {
  if (!item?.id) return
  try {
    const res = await downloadContract(item.id)
    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${item.contractNo || 'contract'}.pdf`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    showError(getErrorMessage(error, '下载失败'))
  }
}

function paymentMethodText(method) {
  const key = String(method || '').toUpperCase()
  if (key === 'MONTHLY') return '月付'
  if (key === 'QUARTERLY') return '季付'
  if (key === 'YEARLY') return '年付'
  return method || '-'
}

function signStatusTextOf(value) {
  return SIGN_TEXT[String(value || '').toUpperCase()] || value || '-'
}
</script>

<style scoped>
.sign-steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin: 4px 0;
}

.sign-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
  padding: 8px 4px;
  border-radius: 10px;
  background: #f4f8ff;
}

.sign-step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #dfe8fb;
  color: #6c7a96;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.sign-step.done {
  color: var(--success);
  background: #e9f8ee;
}

.sign-step.done .sign-step-no {
  background: var(--success);
  color: #fff;
}

.sign-step.active {
  color: var(--brand);
  background: #eaf1ff;
}
</style>
