<template>
  <div class="page">
    <section class="panel">
      <div class="row-between">
        <div class="card-title" style="margin:0">修改手机号</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>

      <label class="field-label" style="margin-top:10px">原手机号</label>
      <input class="input" v-model.trim="form.oldPhone" placeholder="请输入当前手机号" />

      <label class="field-label" style="margin-top:10px">新手机号</label>
      <input class="input" v-model.trim="form.newPhone" placeholder="请输入新手机号" />

      <label class="field-label" style="margin-top:10px">短信验证码</label>
      <div class="code-row">
        <input class="input" v-model.trim="form.code" placeholder="请输入验证码" />
        <button class="btn btn-light" :disabled="sending || cooldown > 0" @click="sendCode">
          {{ cooldown > 0 ? `${cooldown}s` : '发送验证码' }}
        </button>
      </div>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '确认修改' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { setTenantToken, sendUpdatePhoneCode, updateTenantPhone } from '../api'

const router = useRouter()
const sending = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
const cooldown = ref(0)
const form = reactive({
  oldPhone: '',
  newPhone: '',
  code: ''
})

let timer = null

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

async function sendCode() {
  errorMsg.value = ''
  if (!form.newPhone) {
    errorMsg.value = '请先输入新手机号'
    return
  }
  sending.value = true
  try {
    await sendUpdatePhoneCode(form.newPhone)
    cooldown.value = 60
    timer = setInterval(() => {
      cooldown.value -= 1
      if (cooldown.value <= 0) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
    alert('验证码已发送')
  } catch (error) {
    errorMsg.value = error?.response?.data?.message || '发送失败，请稍后再试'
  } finally {
    sending.value = false
  }
}

async function submit() {
  errorMsg.value = ''
  if (!form.oldPhone || !form.newPhone || !form.code) {
    errorMsg.value = '请完整填写信息'
    return
  }
  submitting.value = true
  try {
    const res = await updateTenantPhone({
      oldPhone: form.oldPhone,
      newPhone: form.newPhone,
      code: form.code
    })
    if (res.data?.token) {
      setTenantToken(res.data.token)
    }
    alert('手机号修改成功')
    router.replace('/me')
  } catch (error) {
    errorMsg.value = error?.response?.data?.message || '修改失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>
