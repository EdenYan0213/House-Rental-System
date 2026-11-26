<template>
  <div class="mobile-page" v-loading="saving">
    <header class="m-head">
      <h1 class="m-head-title">个人设置</h1>
      <p class="m-head-sub">修改个人资料与登录密码。</p>
    </header>

    <section>
      <div class="m-section-title">个人资料</div>
      <div class="m-card">
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-position="top" class="form">
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="profileForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <div class="hint">提示：手机号支持 11 位大陆手机号，邮箱可留空。</div>
      </el-form>
      <el-button type="primary" class="submit-btn" @click="saveProfile">保存资料</el-button>
      </div>
    </section>

    <section>
      <div class="m-section-title">修改密码</div>
      <div class="m-card">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="form">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <div class="hint">提示：新密码长度建议 8-20 位，且不要与原密码相同。</div>
      </el-form>
      <el-button type="danger" plain class="submit-btn" @click="savePassword">更新密码</el-button>
      </div>
    </section>

    <el-button class="back-btn" @click="goBack">返回我的</el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword, getUser, setUser, updateProfile, getErrorMessage } from '@/api'

const router = useRouter()
const saving = ref(false)
const user = getUser() || {}
const profileFormRef = ref()
const passwordFormRef = ref()

const profileForm = ref({
  realName: user.realName || '',
  phone: user.phone || '',
  email: user.email || ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const profileRules = {
  realName: [
    { required: true, message: '姓名不能为空', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度为 2-20 个字符', trigger: 'blur' }
  ],
  phone: [
    {
      validator: (_, value, callback) => {
        const val = String(value || '').trim()
        if (!val) return callback()
        if (!/^1\d{10}$/.test(val)) return callback(new Error('请输入有效的 11 位手机号'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  email: [
    {
      validator: (_, value, callback) => {
        const val = String(value || '').trim()
        if (!val) return callback()
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)) return callback(new Error('邮箱格式不正确'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      realName: (profileForm.value.realName || '').trim(),
      phone: (profileForm.value.phone || '').trim(),
      email: (profileForm.value.email || '').trim()
    }
    const res = await updateProfile(payload)
    const nextUser = { ...(getUser() || {}), ...(res.data || payload) }
    setUser(nextUser)
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '更新资料失败'))
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  const oldPassword = String(passwordForm.value.oldPassword || '')
  const newPassword = String(passwordForm.value.newPassword || '')
  if (newPassword === oldPassword) {
    ElMessage.warning('新密码不能与原密码一致')
    return
  }

  saving.value = true
  try {
    await changePassword({ oldPassword, newPassword })
    passwordForm.value.oldPassword = ''
    passwordForm.value.newPassword = ''
    passwordForm.value.confirmPassword = ''
    ElMessage.success('密码修改成功')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '修改密码失败'))
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/mobile/me')
}
</script>

<style scoped>
.form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 4px;
}

.form :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.hint {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
  margin: 2px 0 12px;
}

.submit-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
}

.back-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
}
</style>
