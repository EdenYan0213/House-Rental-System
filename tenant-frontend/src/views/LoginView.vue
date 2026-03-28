<template>
  <div class="page login-page">
    <div class="panel login-panel">
      <h1>租客登录</h1>
      <div class="grid-two" style="margin-bottom:10px">
        <button class="btn" :class="mode === 'sms' ? 'btn-primary' : 'btn-light'" @click="mode='sms'">验证码登录</button>
        <button class="btn" :class="mode === 'password' ? 'btn-primary' : 'btn-light'" @click="mode='password'">密码登录</button>
      </div>
      <input v-model.trim="phone" class="input" placeholder="请输入手机号" maxlength="20" />

      <template v-if="mode === 'sms'">
        <div class="code-row">
          <input v-model.trim="code" class="input" placeholder="请输入6位验证码" maxlength="6" />
          <button class="btn btn-light" :disabled="sending || countdown > 0" @click="onSendCode">
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </button>
        </div>
      </template>
      <template v-else>
        <input v-model.trim="password" style="margin-top:10px" class="input" placeholder="请输入密码" type="password" maxlength="50" />
      </template>

      <button class="btn btn-primary" :disabled="loading" @click="onLogin">登录</button>
      <p class="muted" style="margin-top:10px">没有账号？<span class="link" @click="router.push('/register')">立即注册</span></p>

      <p v-if="errorText" class="error">{{ errorText }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  sendSmsCode,
  tenantLogin,
  tenantPasswordLogin,
  setTenantToken,
  setTenantInfo,
  setCurrentRoomCache
} from '../api'

const router = useRouter()
const phone = ref('')
const code = ref('')
const password = ref('')
const mode = ref('sms')
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const errorText = ref('')
let timer = null

async function onSendCode() {
  errorText.value = ''
  if (!phone.value) {
    errorText.value = '请输入手机号'
    return
  }
  sending.value = true
  try {
    await sendSmsCode(phone.value)
    startCountdown()
  } catch (error) {
    errorText.value = error?.response?.data?.message || '验证码发送失败'
  } finally {
    sending.value = false
  }
}

async function onLogin() {
  errorText.value = ''
  if (!phone.value || (mode.value === 'sms' && !code.value) || (mode.value === 'password' && !password.value)) {
    errorText.value = mode.value === 'sms' ? '请填写手机号和验证码' : '请填写手机号和密码'
    return
  }

  loading.value = true
  try {
    const res = mode.value === 'sms'
      ? await tenantLogin(phone.value, code.value)
      : await tenantPasswordLogin(phone.value, password.value)
    const data = res.data || {}
    setTenantToken(data.token)
    setTenantInfo(data.tenantInfo || {})

    const room = (data.roomList || []).find(item => item.roomId === data.currentRoomId) || (data.roomList || [])[0] || {}
    setCurrentRoomCache(room)

    router.replace(data.isProspect ? '/available-rooms' : '/home')
  } catch (error) {
    errorText.value = error?.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  if (timer) {
    clearInterval(timer)
  }
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}
</script>
