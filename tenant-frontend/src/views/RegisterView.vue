<template>
  <div class="page login-page">
    <div class="panel login-panel">
      <h1>租客注册</h1>
      <input v-model.trim="form.name" class="input" placeholder="姓名" maxlength="50" />
      <input v-model.trim="form.phone" style="margin-top:10px" class="input" placeholder="手机号" maxlength="20" />
      <div class="code-row" style="margin-top:10px">
        <input v-model.trim="form.code" class="input" placeholder="请输入6位验证码" maxlength="6" />
        <button class="btn btn-light" :disabled="sending || countdown > 0" @click="onSendCode">
          {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
        </button>
      </div>
      <input v-model.trim="form.password" style="margin-top:10px" class="input" placeholder="密码(至少6位)" type="password" maxlength="50" />
      <input v-model.trim="form.confirmPassword" style="margin-top:10px" class="input" placeholder="确认密码" type="password" maxlength="50" />

      <button class="btn btn-primary" :disabled="registering" @click="onRegister">
        {{ registering ? '注册中...' : '注册并登录' }}
      </button>
      <p class="muted" style="margin-top:10px">已有账号？<span class="link" @click="router.push('/login')">去登录</span></p>

      <p v-if="errorText" class="error">{{ errorText }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  tenantRegister,
  sendRegisterSmsCode,
  setTenantToken,
  setTenantInfo,
  setCurrentRoomCache
} from '../api'

const router = useRouter()
const registering = ref(false)
const sending = ref(false)
const countdown = ref(0)
const errorText = ref('')
let timer = null
const form = ref({
  name: '',
  phone: '',
  code: '',
  password: '',
  confirmPassword: ''
})

async function onSendCode() {
  errorText.value = ''
  if (!form.value.phone) {
    errorText.value = '请先填写手机号'
    return
  }
  sending.value = true
  try {
    await sendRegisterSmsCode(form.value.phone)
    startCountdown()
  } catch (error) {
    errorText.value = error?.response?.data?.message || '验证码发送失败'
  } finally {
    sending.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function onRegister() {
  errorText.value = ''
  if (!form.value.name || !form.value.phone || !form.value.code || !form.value.password) {
    errorText.value = '请完整填写注册信息与验证码'
    return
  }
  if (form.value.password.length < 6) {
    errorText.value = '密码长度不能少于6位'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorText.value = '两次输入密码不一致'
    return
  }

  registering.value = true
  try {
    const res = await tenantRegister({
      name: form.value.name,
      phone: form.value.phone,
      code: form.value.code,
      password: form.value.password
    })
    const data = res.data || {}
    setTenantToken(data.token)
    setTenantInfo(data.tenantInfo || {})
    setCurrentRoomCache({})
    router.replace('/available-rooms')
  } catch (error) {
    errorText.value = error?.response?.data?.message || '注册失败'
  } finally {
    registering.value = false
  }
}
</script>
