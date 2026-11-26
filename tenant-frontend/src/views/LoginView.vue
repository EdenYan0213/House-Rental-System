<template>
  <div class="page login-page">
    <div class="panel login-panel">
      <h1 style="margin:0 0 4px">租客登录</h1>
      <p class="muted" style="margin:0 0 16px">查询账单、在线报修与投诉、签署合同</p>

      <div class="segmented" style="margin-bottom:12px">
        <button :class="{ active: mode === 'sms' }" @click="switchMode('sms')">验证码登录</button>
        <button :class="{ active: mode === 'password' }" @click="switchMode('password')">密码登录</button>
      </div>

      <input
        v-model.trim="phone"
        class="input"
        placeholder="请输入手机号"
        type="tel"
        maxlength="11"
      />

      <template v-if="mode === 'sms'">
        <div class="code-row">
          <input v-model.trim="code" class="input" placeholder="6 位验证码" maxlength="6" inputmode="numeric" />
          <button class="btn btn-light" :disabled="sending || countdown > 0" @click="onSendCode">
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </button>
        </div>
      </template>
      <template v-else>
        <input
          v-model.trim="password"
          style="margin-top:10px"
          class="input"
          placeholder="请输入密码"
          type="password"
          maxlength="50"
          @keyup.enter="onLogin"
        />
      </template>

      <button class="btn btn-primary" style="margin-top:14px" :disabled="loading" @click="onLogin">
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <p class="muted" style="margin-top:12px;text-align:center">
        没有账号？<span class="link" @click="router.push('/register')">立即注册</span>
      </p>

      <p v-if="errorText" class="error">{{ errorText }}</p>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  sendSmsCode,
  tenantLogin,
  tenantPasswordLogin,
  setTenantToken,
  setTenantInfo,
  setCurrentRoomCache,
  getErrorMessage
} from '../api'

const router = useRouter()
const route = useRoute()
const phone = ref('')
const code = ref('')
const password = ref('')
const mode = ref('sms')
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const errorText = ref('')
let timer = null

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  errorText.value = ''
}

function validPhone() {
  if (!/^1\d{10}$/.test(phone.value)) {
    errorText.value = '请输入 11 位有效手机号'
    return false
  }
  return true
}

async function onSendCode() {
  errorText.value = ''
  if (!validPhone()) return

  sending.value = true
  try {
    await sendSmsCode(phone.value)
    startCountdown()
  } catch (error) {
    errorText.value = getErrorMessage(error, '验证码发送失败')
  } finally {
    sending.value = false
  }
}

async function onLogin() {
  errorText.value = ''
  if (!validPhone()) return

  if (mode.value === 'sms' && !code.value) {
    errorText.value = '请输入验证码'
    return
  }
  if (mode.value === 'password' && !password.value) {
    errorText.value = '请输入密码'
    return
  }

  loading.value = true
  try {
    const res = mode.value === 'sms'
      ? await tenantLogin(phone.value, code.value)
      : await tenantPasswordLogin(phone.value, password.value)

    const data = res.data || {}
    if (!data.token) {
      throw new Error('登录返回异常，请稍后重试')
    }

    setTenantToken(data.token)
    setTenantInfo(data.tenantInfo || {})

    const room = (data.roomList || []).find(item => item.roomId === data.currentRoomId)
      || (data.roomList || [])[0]
      || {}
    setCurrentRoomCache(room)

    // 登录前被拦截的页面优先（例如从消息链接跳来），否则按是否待租分流
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect && redirect.startsWith('/')) {
      router.replace(redirect)
      return
    }
    router.replace(data.isProspect ? '/available-rooms' : '/home')
  } catch (error) {
    errorText.value = getErrorMessage(error, '登录失败')
  } finally {
    loading.value = false
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
</script>
