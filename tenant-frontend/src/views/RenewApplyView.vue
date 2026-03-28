<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">申请续租</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>

      <p class="muted" style="margin-top:8px">合同ID：{{ form.contractId || '-' }}</p>

      <label class="field-label" style="margin-top:10px">续租时长（月）</label>
      <select class="select" v-model.number="form.duration">
        <option :value="6">6 个月</option>
        <option :value="12">12 个月</option>
        <option :value="24">24 个月</option>
      </select>

      <label class="field-label" style="margin-top:10px">备注（可选）</label>
      <textarea class="textarea" v-model.trim="form.remark" maxlength="200" placeholder="可填写预算、入住时间等信息"></textarea>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交续租申请' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { applyRenew, getCurrentContract } from '../api'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const errorMsg = ref('')
const form = reactive({
  contractId: null,
  duration: 12,
  remark: ''
})

onMounted(init)

async function init() {
  const queryId = Number(route.query.contractId || 0)
  if (queryId) {
    form.contractId = queryId
    return
  }
  try {
    const res = await getCurrentContract()
    const current = res.data || {}
    form.contractId = current.id || null
  } catch (_) {
    // ignore
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
    alert('续租申请提交成功')
    router.replace('/contract')
  } catch (error) {
    errorMsg.value = error?.response?.data?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>
