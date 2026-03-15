<template>
  <div class="mobile-page">
    <el-card shadow="never" class="panel">
      <div class="name">{{ user?.realName || user?.username || '-' }}</div>
      <div class="role">角色：{{ roleText }}</div>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>主题设置</template>
      <el-radio-group v-model="themePref" @change="changeTheme" size="small">
        <el-radio-button label="light">浅色</el-radio-button>
        <el-radio-button label="dark">深色</el-radio-button>
        <el-radio-button label="system">跟随系统</el-radio-button>
      </el-radio-group>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>消息提醒</template>
      <div class="switch-row">
        <span>总开关</span>
        <el-switch v-model="notifySettings.enabled" @change="saveNotifySettings" />
      </div>
      <div class="switch-row">
        <span>工单提醒</span>
        <el-switch v-model="notifySettings.repair" :disabled="!notifySettings.enabled" @change="saveNotifySettings" />
      </div>
      <div class="switch-row">
        <span>账单提醒</span>
        <el-switch v-model="notifySettings.bill" :disabled="!notifySettings.enabled" @change="saveNotifySettings" />
      </div>
      <div class="switch-row">
        <span>合同提醒</span>
        <el-switch v-model="notifySettings.contract" :disabled="!notifySettings.enabled" @change="saveNotifySettings" />
      </div>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>连接设置</template>
      <el-input v-model="apiBaseUrl" placeholder="后端地址，如 http://192.168.1.100:8080/api" />
      <div class="ops">
        <el-button type="primary" plain @click="saveApiBase">保存地址</el-button>
        <el-button @click="resetApiBase">恢复默认</el-button>
      </div>
      <div class="muted">当前：{{ currentApiBase }}</div>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>系统操作</template>
      <div class="action-list">
        <el-button type="primary" plain class="action-btn" @click="goSettings">个人设置</el-button>
        <el-button plain class="action-btn" @click="clearCache">清理缓存</el-button>
        <el-button type="danger" class="action-btn" @click="logout">退出登录</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>帮助</template>
      <el-link href="/README.md" target="_blank" type="primary">查看帮助文档</el-link>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getUser,
  removeToken,
  setThemePreference,
  updateProfile,
  setUser,
  getApiBaseUrl,
  setApiBaseUrl
} from '@/api'
import { savePushSettings } from '@/api'
import { applyThemePreference, getStoredThemePreference } from '@/utils/theme'
import { clearMobileCache } from '@/utils/mobileCache'

const router = useRouter()
const user = getUser()
const NOTIFY_SETTING_KEY = 'mobile_notify_settings'

const themePref = ref(getStoredThemePreference())
const apiBaseUrl = ref('')
const currentApiBase = ref(getApiBaseUrl())
const notifySettings = ref(readNotifySettings())

const roleText = computed(() => {
  const role = user?.role?.toUpperCase()
  if (role === 'SUPER_ADMIN') return '超级管理员'
  if (role === 'ADMIN') return '管理员'
  return '普通用户'
})

function goSettings() {
  router.push('/mobile/settings')
}

function readNotifySettings() {
  try {
    const raw = localStorage.getItem(NOTIFY_SETTING_KEY)
    if (!raw) {
      return { enabled: true, repair: true, bill: true, contract: true }
    }
    return { ...{ enabled: true, repair: true, bill: true, contract: true }, ...JSON.parse(raw) }
  } catch (_) {
    return { enabled: true, repair: true, bill: true, contract: true }
  }
}

async function saveNotifySettings() {
  localStorage.setItem(NOTIFY_SETTING_KEY, JSON.stringify(notifySettings.value))
  try {
    await savePushSettings({ ...notifySettings.value })
  } catch (_) {
    // Backend push setting endpoint may not be enabled yet.
  }
  ElMessage.success('提醒设置已保存')
}

async function changeTheme(value) {
  applyThemePreference(value)
  setThemePreference(value)
  try {
    await updateProfile({ themePreference: value })
    setUser({ ...(user || {}), themePreference: value })
  } catch (_) {
    // Keep local preference even if sync fails.
  }
}

function clearCache() {
  clearMobileCache()
  ElMessage.success('缓存已清理（离线待上传记录已保留）')
}

function saveApiBase() {
  setApiBaseUrl(apiBaseUrl.value)
  currentApiBase.value = getApiBaseUrl()
  ElMessage.success('后端地址已保存')
}

function resetApiBase() {
  setApiBaseUrl('')
  apiBaseUrl.value = ''
  currentApiBase.value = getApiBaseUrl()
  ElMessage.success('已恢复默认后端地址')
}

function logout() {
  removeToken()
  router.push('/login')
}

apiBaseUrl.value = ''
</script>

<style scoped>
.mobile-page {
  padding: 14px;
  max-width: 760px;
  margin: 0 auto;
}

.panel {
  margin-bottom: 12px;
}

.name {
  font-size: 20px;
  font-weight: 700;
  color: #1d3266;
}

.role {
  margin-top: 6px;
  color: #6b7896;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 0;
}

.ops {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.muted {
  margin-top: 8px;
  color: #7182a2;
  font-size: 12px;
}

.action-list {
  display: grid;
  gap: 10px;
}

.action-btn {
  width: 100%;
  height: 42px;
  border-radius: 10px;
}
</style>
