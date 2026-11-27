<template>
  <div class="mobile-page">
    <!-- 用户卡片 -->
    <section class="profile-card">
      <div class="avatar">{{ avatarLetter }}</div>
      <div class="profile-meta">
        <div class="profile-name">{{ user?.realName || user?.username || '-' }}</div>
        <div class="profile-role">{{ roleText }}</div>
      </div>
    </section>

    <!-- 主题 -->
    <section>
      <div class="m-section-title">外观</div>
      <div class="m-card theme-card">
        <el-radio-group v-model="themePref" @change="changeTheme" class="theme-group">
          <el-radio-button label="light">浅色</el-radio-button>
          <el-radio-button label="dark">深色</el-radio-button>
          <el-radio-button label="system">跟随系统</el-radio-button>
        </el-radio-group>
      </div>
    </section>

    <!-- 消息提醒 -->
    <section>
      <div class="m-section-title">消息提醒</div>
      <div class="m-card switch-list">
        <div class="switch-row">
          <div class="switch-text">
            <span class="switch-label">总开关</span>
            <span class="switch-desc">关闭后不再接收任何提醒</span>
          </div>
          <el-switch v-model="notifySettings.enabled" @change="saveNotifySettings" />
        </div>
        <div class="switch-row">
          <div class="switch-text">
            <span class="switch-label">工单提醒</span>
            <span class="switch-desc">有新工单或状态变更时通知</span>
          </div>
          <el-switch v-model="notifySettings.repair" :disabled="!notifySettings.enabled" @change="saveNotifySettings" />
        </div>
        <div class="switch-row">
          <div class="switch-text">
            <span class="switch-label">账单提醒</span>
            <span class="switch-desc">账单逾期或生成时通知</span>
          </div>
          <el-switch v-model="notifySettings.bill" :disabled="!notifySettings.enabled" @change="saveNotifySettings" />
        </div>
        <div class="switch-row">
          <div class="switch-text">
            <span class="switch-label">合同提醒</span>
            <span class="switch-desc">合同即将到期时通知</span>
          </div>
          <el-switch v-model="notifySettings.contract" :disabled="!notifySettings.enabled" @change="saveNotifySettings" />
        </div>
      </div>
    </section>

    <!-- 连接设置 -->
    <section>
      <div class="m-section-title">连接设置</div>
      <div class="m-card">
        <el-input v-model="apiBaseUrl" placeholder="http://192.168.1.100:8081/api" />
        <div class="conn-ops">
          <el-button type="primary" plain @click="saveApiBase">保存</el-button>
          <el-button @click="resetApiBase">恢复默认</el-button>
        </div>
        <div class="conn-current">当前：{{ currentApiBase }}</div>
      </div>
    </section>

    <!-- 业务管理：仅管理员可见 -->
    <section v-if="canManageBusiness">
      <div class="m-section-title">业务管理</div>
      <div class="m-list">
        <div class="m-list-item" @click="router.push('/mobile/contracts')">
          <el-icon class="item-icon"><Document /></el-icon>
          <div class="m-list-main"><div class="m-list-title">合同管理</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="m-list-item" @click="router.push('/mobile/tenant-manage')">
          <el-icon class="item-icon"><User /></el-icon>
          <div class="m-list-main"><div class="m-list-title">租户管理</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="m-list-item" @click="router.push('/mobile/deposits')">
          <el-icon class="item-icon"><Coin /></el-icon>
          <div class="m-list-main"><div class="m-list-title">押金管理</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="m-list-item" @click="router.push('/mobile/fee-items')">
          <el-icon class="item-icon"><Money /></el-icon>
          <div class="m-list-main"><div class="m-list-title">费率配置</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </section>

    <!-- 操作 -->
    <section>
      <div class="m-section-title">操作</div>
      <div class="m-list">
        <div v-if="canUseAiAssistant" class="m-list-item" @click="router.push('/mobile/ai-assistant')">
          <el-icon class="item-icon"><MagicStick /></el-icon>
          <div class="m-list-main"><div class="m-list-title">AI 助手</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="m-list-item" @click="goSettings">
          <el-icon class="item-icon"><Setting /></el-icon>
          <div class="m-list-main"><div class="m-list-title">个人设置</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="m-list-item" @click="clearCache">
          <el-icon class="item-icon"><Delete /></el-icon>
          <div class="m-list-main"><div class="m-list-title">清理缓存</div></div>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </section>

    <!-- 退出登录 -->
    <el-button class="logout-btn" @click="logout">退出登录</el-button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Setting, Delete, ArrowRight, Document, User, Coin, Money } from '@element-plus/icons-vue'
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

/** 头像占位字母：取姓名首字。 */
const avatarLetter = computed(() => {
  const name = user?.realName || user?.username || ''
  return name ? name.slice(0, 1).toUpperCase() : '?'
})
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

const canUseAiAssistant = computed(() => {
  const role = user?.role?.toUpperCase()
  return role === 'SUPER_ADMIN' || role === 'ADMIN'
})

/** 业务管理入口：仅管理员，与后端 @PreAuthorize 对齐。 */
const canManageBusiness = computed(() => {
  const role = user?.role?.toUpperCase()
  return role === 'SUPER_ADMIN' || role === 'ADMIN'
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
/* ---------- 用户卡片 ---------- */
.profile-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px var(--m-gutter);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  border-radius: var(--m-radius);
  color: var(--text-inverse);
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex: 0 0 auto;
}

.profile-meta {
  min-width: 0;
}

.profile-name {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-role {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 3px;
}

/* ---------- 主题 ---------- */
.theme-card {
  padding: 12px;
}

.theme-group {
  display: flex;
  width: 100%;
}

.theme-group :deep(.el-radio-button) {
  flex: 1;
}

.theme-group :deep(.el-radio-button__inner) {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---------- 消息提醒 ---------- */
.switch-list {
  padding: 0 var(--m-gutter);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 0;
  min-height: var(--m-tap);
  box-sizing: border-box;
  border-bottom: 1px solid var(--border-color);
}

.switch-row:last-child {
  border-bottom: none;
}

.switch-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.switch-label {
  font-size: 14px;
  color: var(--text-main);
  font-weight: 500;
}

.switch-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}

/* ---------- 连接设置 ---------- */
.conn-ops {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.conn-ops :deep(.el-button) {
  flex: 1;
  min-height: 38px;
  border-radius: 9px;
}

.conn-current {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
  word-break: break-all;
}

/* ---------- 操作列表 ---------- */
.item-icon {
  color: var(--accent-3);
  flex: 0 0 auto;
}

.item-arrow {
  color: var(--text-muted);
  opacity: 0.45;
  flex: 0 0 auto;
}

/* ---------- 退出 ---------- */
.logout-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
  color: var(--danger-color);
  border-color: color-mix(in srgb, var(--danger-color) 35%, transparent);
  background: transparent;
}

.logout-btn:active {
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
}
</style>
