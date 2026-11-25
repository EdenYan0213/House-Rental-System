<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">申请续租</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>

      <!-- 无合同：直接说明，不展示无法使用的表单 -->
      <template v-if="loaded && !form.contractId">
        <div class="empty-block">
          <p class="muted">当前没有可续租的生效合同。</p>
          <p class="muted">如果你已签署合同但这里为空，请在「电子合同」确认合同状态。</p>
        </div>
        <button class="btn" style="width:100%;margin-top:10px" @click="$router.replace('/contract')">
          查看电子合同
        </button>
      </template>

      <template v-else>
        <p class="muted" style="margin-top:8px">合同编号：{{ contractLabel }}</p>

        <label class="field-label" style="margin-top:10px">续租时长（月）</label>
        <select class="select" v-model.number="form.duration">
          <option :value="6">6 个月</option>
          <option :value="12">12 个月</option>
          <option :value="24">24 个月</option>
        </select>

        <label class="field-label" style="margin-top:10px">备注（可选）</label>
        <textarea class="textarea" v-model.trim="form.remark" maxlength="200"
          placeholder="可填写预算、入住时间等信息"></textarea>

        <p class="error" v-if="errorMsg">{{ errorMsg }}</p>
        <button class="btn btn-primary" style="width:100%;margin-top:10px"
          :disabled="submitting || !loaded" @click="submit">
          {{ submitting ? '提交中...' : (loaded ? '提交续租申请' : '加载中...') }}
        </button>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { applyRenew, getCurrentContract } from '../api'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const loaded = ref(false)
const errorMsg = ref('')
const contractNo = ref('')

const form = reactive({
  contractId: null,
  duration: 12,
  remark: ''
})

/** 优先显示合同编号，缺失时退回 ID。 */
const contractLabel = computed(() => contractNo.value || form.contractId || '-')

onMounted(init)

async function init() {
  const queryId = Number(route.query.contractId || 0)
  if (queryId) {
    form.contractId = queryId
    loaded.value = true
    return
  }
  try {
    const res = await getCurrentContract()
    const current = res.data || {}
    form.contractId = current.id || null
    contractNo.value = current.contractNo || ''
  } catch (e) {
    // 加载失败与「确实没有合同」要区分，否则用户不知道该重试还是放弃
    errorMsg.value = e?.response?.data?.message || '合同信息加载失败，请稍后重试'
  } finally {
    loaded.value = true
  }
}

async function submit() {
  errorMsg.value = ''
  if (!form.contractId) {
    errorMsg.value = '未找到可续租合同'
    return
  }
  submitting.value = true
  try {
    await applyRenew({
      contractId: form.contractId,
      duration: form.duration,
      remark: form.remark
    })
    router.replace({ path: '/contract', query: { renewSubmitted: '1' } })
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.empty-block {
  margin-top: 16px;
  padding: 20px 16px;
  border: 1px dashed var(--border-color, #dcdfe6);
  border-radius: 8px;
  text-align: center;
}

.empty-block p {
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.6;
}
</style>
