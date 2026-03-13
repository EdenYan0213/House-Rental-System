<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="title">房屋租赁管理系统</h2>
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
      <div class="tips">
        <p>默认管理员: admin / admin123</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, setToken, setUser, setThemePreference } from '@/api'
import { applyThemePreference } from '@/utils/theme'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const loading = ref(false)

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
      const res = await login(form)
      if (res.data.token) {
        setToken(res.data.token)
        setUser({
          userId: res.data.userId,
          username: res.data.username,
          realName: res.data.realName,
          role: res.data.role,
          email: res.data.email,
          themePreference: res.data.themePreference || 'system'
        })
        setThemePreference(res.data.themePreference || 'system')
        applyThemePreference(res.data.themePreference || 'system')
        ElMessage.success('登录成功')
        router.push(route.query.redirect || '/dashboard')
      }
    } catch (err) {
      ElMessage.error(err.response?.data?.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
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
  color: #333;
}

.tips {
  text-align: center;
  color: #999;
  font-size: 12px;
  margin-top: 10px;
}
</style>
