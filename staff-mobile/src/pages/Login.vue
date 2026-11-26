<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="title">房屋租赁工作台</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input type="password" v-model="form.password" placeholder="请输入密码" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">连接设置</el-divider>
      <el-input v-model="apiBaseDraft" placeholder="后端地址，如 http://10.0.2.2:8081/api" />
      <div class="api-row">
        <el-button size="small" type="primary" plain @click="saveApiBase">保存</el-button>
        <el-button size="small" @click="resetApiBase">恢复默认</el-button>
      </div>
      <p class="api-tip">当前后端：{{ currentApiBase }}</p>

    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Capacitor } from '@capacitor/core'
import {
  login,
  setToken,
  setUser,
  setThemePreference,
  getApiBaseUrl,
  setApiBaseUrl,
  getErrorMessage
} from '@/api'
import { applyThemePreference } from '@/utils/theme'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const loading = ref(false)
const apiBaseDraft = ref('')
const currentApiBase = ref(getApiBaseUrl())

async function doLoginRequest() {
  return login(form)
}

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      let res
      try {
        res = await doLoginRequest()
      } catch (err) {
        const isNative = Capacitor.isNativePlatform()
        const isNetworkError = !err.response
        const base = getApiBaseUrl()

        // Browser simulation may keep native API base (e.g. 10.0.2.2) in localStorage.
        if (!isNative && isNetworkError && base && base !== '/api') {
          setApiBaseUrl('')
          currentApiBase.value = getApiBaseUrl()
          apiBaseDraft.value = ''
          ElMessage.warning('检测到浏览器环境地址不兼容，已自动切回默认后端并重试')
          res = await doLoginRequest()
        } else {
          throw err
        }
      }

      if (res.data.token) {
        setToken(res.data.token)
        setUser({
          userId: res.data.userId,
          username: res.data.username,
          realName: res.data.realName,
          role: res.data.role,
          email: res.data.email,
          themePreference: res.data.themePreference || 'system',
          firstLogin: res.data.firstLogin !== false,
          showContextTips: res.data.showContextTips !== false,
          showTaskCard: res.data.showTaskCard !== false,
          dismissedGuideKeys: []
        })
        setThemePreference(res.data.themePreference || 'system')
        applyThemePreference(res.data.themePreference || 'system')
        ElMessage.success('登录成功')

        const isNative = Capacitor.isNativePlatform()
        if (isNative) {
          // Native app should always enter mobile pages after login.
          router.push('/mobile/home')
          return
        }

        const redirect = route.query.redirect
        if (redirect) {
          router.push(redirect)
          return
        }

        if (window.location.pathname.startsWith('/mobile')) {
          router.push('/mobile/home')
        } else {
          router.push('/mobile/home')
        }
      }
    } catch (err) {
      // 移动端保留当前后端地址提示，便于排查连不上服务的问题
      const hint = Capacitor.isNativePlatform() ? `（当前后端：${getApiBaseUrl()}）` : ''
      ElMessage.error(getErrorMessage(err, '登录失败') + hint)
    } finally {
      loading.value = false
    }
  })
}

function saveApiBase() {
  setApiBaseUrl(apiBaseDraft.value)
  currentApiBase.value = getApiBaseUrl()
  ElMessage.success('后端地址已保存')
}

function resetApiBase() {
  setApiBaseUrl('')
  apiBaseDraft.value = ''
  currentApiBase.value = getApiBaseUrl()
  ElMessage.success('已恢复默认后端地址')
}
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 20px;
}

.title {
  text-align: center;
  margin-bottom: 30px;
  /* 跟随主题，深色下不能继续用近黑 #333 */
  color: var(--text-main);
}

.tips {
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 10px;
}

.api-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.api-tip {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
