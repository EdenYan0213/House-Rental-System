<template>
  <div>
    <el-container style="height: 100vh" v-if="!isLoginPage">
      <el-aside width="220px" class="sidebar">
        <div class="logo">房屋租赁系统</div>
        <el-menu :default-active="currentPath" router class="sidebar-menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/buildings" v-if="canAccessBuilding">
          <el-icon><OfficeBuilding /></el-icon>
          <span>房屋管理</span>
        </el-menu-item>
        <el-menu-item index="/tenants">
          <el-icon><User /></el-icon>
          <span>租户管理</span>
        </el-menu-item>
        <el-menu-item index="/contracts">
          <el-icon><Document /></el-icon>
          <span>合同管理</span>
        </el-menu-item>
        <el-menu-item index="/fee-items" v-if="canAccessFeeItems">
          <el-icon><Coin /></el-icon>
          <span>费用项配置</span>
        </el-menu-item>
        <el-menu-item index="/meter-readings" v-if="canAccessMeterReadings">
          <el-icon><TrendCharts /></el-icon>
          <span>水电录入</span>
        </el-menu-item>
        <el-menu-item index="/bills">
          <el-icon><Wallet /></el-icon>
          <span>账单管理</span>
        </el-menu-item>
        <el-menu-item index="/deposits">
          <el-icon><Money /></el-icon>
          <span>押金管理</span>
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Bell /></el-icon>
          <span>消息中心</span>
        </el-menu-item>
        <el-sub-menu index="repair" v-if="canAccessRepairs">
          <template #title>
            <el-icon><Tools /></el-icon>
            <span>维修管理</span>
          </template>
          <el-menu-item index="/repairs/orders">工单列表</el-menu-item>
          <el-menu-item index="/repairs/workers">维修工与模板</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/complaints" v-if="canAccessRepairs">
          <el-icon><ChatDotRound /></el-icon>
          <span>投诉管理</span>
        </el-menu-item>
        <el-sub-menu index="settings" v-if="canAccessSettings">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/settings">个人设置</el-menu-item>
          <el-menu-item index="/users" v-if="canManageUsers">用户管理</el-menu-item>
          <el-menu-item index="/logs" v-if="canAccessLogs">操作日志</el-menu-item>
          <el-menu-item index="/alert-rules" v-if="canAccessLogs">操作预警</el-menu-item>
          <el-menu-item index="/operation-analysis" v-if="canAccessLogs">操作分析</el-menu-item>
          <el-menu-item index="/backup" v-if="canManageBackup">数据备份与恢复</el-menu-item>
        </el-sub-menu>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="header">
          <div class="header-title">{{ pageTitle }}</div>
          <div class="header-right">
          <el-dropdown @command="handleThemeCommand" trigger="click">
            <el-button text class="theme-trigger">
              <el-icon v-if="themePreference === 'dark'"><Sunny /></el-icon>
              <el-icon v-else-if="themePreference === 'light'"><MoonNight /></el-icon>
              <el-icon v-else><Monitor /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light" :class="{ active: themePreference === 'light' }">浅色</el-dropdown-item>
                <el-dropdown-item command="dark" :class="{ active: themePreference === 'dark' }">深色</el-dropdown-item>
                <el-dropdown-item command="system" :class="{ active: themePreference === 'system' }">跟随系统</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button text class="theme-trigger" @click="openHelpCenter">
            <el-icon><QuestionFilled /></el-icon>
          </el-button>
          <el-button v-if="canUseAiAssistant" text class="theme-trigger" @click="showAiDrawer = true">
            <el-icon><ChatDotRound /></el-icon>
          </el-button>
          <el-tag size="small" :type="roleTagType">{{ roleName }}</el-tag>
          <span class="username">{{ user?.realName || user?.username }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
          </div>
        </el-header>
        <el-main>
          <el-alert
            v-if="showContextTip"
            :title="activeContextTip.title"
            type="info"
            show-icon
            :closable="false"
            class="context-tip"
          >
            <template #default>
              <div class="context-tip-content">{{ activeContextTip.content }}</div>
              <div class="context-tip-actions">
                <el-button link type="primary" @click="dismissCurrentTip">不再提示</el-button>
                <el-button link @click="closeCurrentTip">本次关闭</el-button>
              </div>
            </template>
          </el-alert>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
    <router-view v-else />
    <AiAssistantDrawer v-if="!isLoginPage && canUseAiAssistant" v-model="showAiDrawer" />
    <el-dialog v-model="showWelcomeDialog" title="欢迎使用" width="520px" :close-on-click-modal="false">
      <p class="welcome-text">这是你的首次登录。建议按顺序完成关键功能，系统会在页面中提供上下文提示。</p>
      <template #footer>
        <el-button @click="skipWelcomeGuide">稍后再看</el-button>
        <el-button type="primary" @click="startWelcomeGuide">开始引导</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="showHelpCenter" title="帮助中心" size="520px" :with-header="true">
      <el-tabs v-model="helpTab">
        <el-tab-pane label="图文教程" name="article">
          <ul class="help-list">
            <li>1. 先录入楼栋和房间，再进行租户与合同管理。</li>
            <li>2. 每月先录水电，再生成账单并确认收款。</li>
            <li>3. 在“消息中心”查看提醒，减少合同到期漏处理。</li>
          </ul>
        </el-tab-pane>
        <el-tab-pane label="视频教程" name="video">
          <ul class="help-list">
            <li>新手总览: https://example.com/onboarding-overview</li>
            <li>合同与账单流程: https://example.com/contract-billing</li>
            <li>维修与投诉协同: https://example.com/repair-complaint</li>
          </ul>
        </el-tab-pane>
        <el-tab-pane label="常见问题" name="faq">
          <ul class="help-list">
            <li>Q: 账单金额不对怎么办? A: 检查费用项配置和当月水电录入。</li>
            <li>Q: 为什么看不到某些菜单? A: 不同角色有不同权限范围。</li>
            <li>Q: 如何重新打开新手引导? A: 系统设置 -> 新手引导 -> 重置。</li>
          </ul>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Odometer,
  OfficeBuilding,
  User,
  Document,
  Coin,
  Wallet,
  Money,
  Setting,
  Bell,
  TrendCharts,
  Tools,
  ChatDotRound,
  MoonNight,
  Sunny,
  Monitor,
  QuestionFilled
} from '@element-plus/icons-vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'
import {
  getUser,
  removeToken,
  updateProfile,
  setUser,
  getThemePreference,
  setThemePreference,
  getCurrentUser,
  dismissGuide
} from '@/api'
import { applyThemePreference } from './utils/theme'

const route = useRoute()
const router = useRouter()

const currentUser = ref(getUser())
const user = computed(() => currentUser.value)
const currentPath = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login' || route.path.startsWith('/mobile') || route.path.startsWith('/qr/'))
const themePreference = ref(getThemePreference())
const showAiDrawer = ref(false)
const showWelcomeDialog = ref(false)
const showHelpCenter = ref(false)
const helpTab = ref('article')
const routeTipHidden = ref(false)

const contextTipMap = {
  '/dashboard': {
    key: 'tip:dashboard',
    title: '仪表盘提示',
    content: '优先查看待办与合同到期提醒，再进入对应模块处理。'
  },
  '/buildings': {
    key: 'tip:buildings',
    title: '房屋管理提示',
    content: '先维护楼栋，再批量创建房间，可显著减少后续录入工作量。'
  },
  '/contracts': {
    key: 'tip:contracts',
    title: '合同管理提示',
    content: '创建合同前先确认租户与房间状态，避免重复签约。'
  },
  '/meter-readings': {
    key: 'tip:meter',
    title: '水电录入提示',
    content: '建议每月固定日期抄表，并在生成账单前完成校验。'
  },
  '/bills': {
    key: 'tip:bills',
    title: '账单管理提示',
    content: '账单生成后先抽样核对，再执行短信催缴和收款登记。'
  }
}

function refreshCurrentUser() {
  currentUser.value = getUser()
}

const activeContextTip = computed(() => {
  const path = route.path
  return contextTipMap[path] || null
})

const showContextTip = computed(() => {
  if (isLoginPage.value) return false
  if (routeTipHidden.value) return false
  if (!activeContextTip.value) return false
  if (currentUser.value?.showContextTips === false) return false
  const dismissed = currentUser.value?.dismissedGuideKeys || []
  return !dismissed.includes(activeContextTip.value.key)
})

function hasAnyRole(roles = []) {
  const role = currentUser.value?.role?.toUpperCase()
  if (!role) return false
  if (role === 'SUPER_ADMIN') return true
  return roles.map(item => String(item).toUpperCase()).includes(role)
}

const roleName = computed(() => {
  const role = user.value?.role?.toUpperCase()
  if (role === 'SUPER_ADMIN') return '超级管理员'
  if (role === 'ADMIN') return '管理员'
  return '普通用户'
})

const roleTagType = computed(() => {
  const role = user.value?.role?.toUpperCase()
  if (role === 'SUPER_ADMIN') return 'danger'
  if (role === 'ADMIN') return 'warning'
  return 'info'
})

const canManageUsers = computed(() => {
  const u = user.value
  return u && (u.role === 'ADMIN' || u.role === 'SUPER_ADMIN')
})
const canAccessBuilding = computed(() => true)
const canAccessFeeItems = computed(() => hasAnyRole(['ADMIN', 'SUPER_ADMIN']))
const canAccessMeterReadings = computed(() => hasAnyRole(['ADMIN', 'SUPER_ADMIN']))
const canAccessLogs = computed(() => hasAnyRole(['SUPER_ADMIN']))
const canAccessRepairs = computed(() => hasAnyRole(['ADMIN', 'SUPER_ADMIN']))
const canAccessSettings = computed(() => true)
const canManageBackup = computed(() => hasAnyRole(['ADMIN', 'SUPER_ADMIN']))
const canUseAiAssistant = computed(() => hasAnyRole(['ADMIN', 'SUPER_ADMIN']))

const pageTitleMap = {
  '/dashboard': '仪表盘',
  '/buildings': '房屋管理',
  '/tenants': '租户管理',
  '/contracts': '合同管理',
  '/fee-items': '费用项配置',
  '/meter-readings': '水电录入',
  '/bills': '账单管理',
  '/deposits': '押金管理',
  '/messages': '消息中心',
  '/repairs/orders': '维修工单',
  '/repairs/workers': '维修管理',
  '/complaints': '投诉管理',
  '/settings': '系统设置',
  '/backup': '数据备份与恢复',
  '/users': '用户管理',
  '/logs': '操作日志',
  '/alert-rules': '操作预警',
  '/operation-analysis': '操作分析'
}

const pageTitle = computed(() => {
  const path = route.path
  if (path.startsWith('/buildings/') && path.endsWith('/assets')) return '房间器具档案'
  if (path.startsWith('/buildings/')) return '房间管理'
  if (path.startsWith('/contracts/')) return '合同详情'
  if (path.startsWith('/bills/')) return '账单详情'
  return pageTitleMap[path] || '房屋租赁系统'
})

function handleLogout() {
  removeToken()
  router.push('/login')
}

async function handleThemeCommand(command) {
  themePreference.value = command
  applyThemePreference(command)
  setThemePreference(command)

  const currentUser = getUser()
  if (!currentUser) return

  try {
    await updateProfile({ themePreference: command })
    setUser({ ...currentUser, themePreference: command })
  } catch (_) {
    // Keep local preference available even if server sync fails.
  }
}

function markGuideKeyDismissed(key) {
  const user = getUser()
  if (!user) return
  const existing = Array.isArray(user.dismissedGuideKeys) ? user.dismissedGuideKeys : []
  if (existing.includes(key)) return
  setUser({ ...user, dismissedGuideKeys: [...existing, key] })
  refreshCurrentUser()
}

async function dismissCurrentTip() {
  if (!activeContextTip.value) return
  try {
    await dismissGuide(activeContextTip.value.key)
    markGuideKeyDismissed(activeContextTip.value.key)
  } catch (_) {
    // Keep page available even if dismiss API fails.
  }
  routeTipHidden.value = true
}

function closeCurrentTip() {
  routeTipHidden.value = true
}

async function setFirstLoginFlag(value) {
  const payload = { firstLogin: value ? 'true' : 'false' }
  await updateProfile(payload)
  const user = getUser()
  if (user) {
    setUser({ ...user, firstLogin: value })
    refreshCurrentUser()
  }
}

async function skipWelcomeGuide() {
  showWelcomeDialog.value = false
  try {
    await setFirstLoginFlag(false)
  } catch (_) {
    // Ignore sync errors and keep local UX state.
  }
}

async function startWelcomeGuide() {
  showWelcomeDialog.value = false
  if (route.path !== '/dashboard') {
    router.push('/dashboard')
  }
  try {
    await dismissGuide('tour:dashboard')
    markGuideKeyDismissed('tour:dashboard')
    await setFirstLoginFlag(false)
  } catch (_) {
    // Ignore sync errors and keep local UX state.
  }
}

async function openHelpCenter() {
  showHelpCenter.value = true
  try {
    await dismissGuide('help:center')
    markGuideKeyDismissed('help:center')
  } catch (_) {
    // Ignore sync errors and keep local UX state.
  }
}

async function syncCurrentUserFromServer() {
  try {
    const res = await getCurrentUser()
    const localUser = getUser()
    if (!localUser) return
    setUser({
      ...localUser,
      realName: res.data.realName,
      role: res.data.role,
      phone: res.data.phone,
      email: res.data.email,
      themePreference: res.data.themePreference || 'system',
      firstLogin: res.data.firstLogin !== false,
      showContextTips: res.data.showContextTips !== false,
      showTaskCard: res.data.showTaskCard !== false,
      dismissedGuideKeys: localUser.dismissedGuideKeys || []
    })
    refreshCurrentUser()
  } catch (_) {
    // Keep local auth state when current user API is unavailable.
  }
}

onMounted(() => {
  refreshCurrentUser()
  syncCurrentUserFromServer()
  const currentUser = getUser()
  const pref = currentUser?.themePreference || getThemePreference() || 'system'
  themePreference.value = pref
  applyThemePreference(pref)
  setThemePreference(pref)

  if (!isLoginPage.value && currentUser?.firstLogin !== false) {
    showWelcomeDialog.value = true
  }

  window.addEventListener('storage', refreshCurrentUser)
})

watch(() => route.fullPath, () => {
  refreshCurrentUser()
  routeTipHidden.value = false
})

onBeforeUnmount(() => {
  window.removeEventListener('storage', refreshCurrentUser)
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
}

.sidebar {
  background: var(--bg-sidebar);
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: var(--text-main);
  font-size: 18px;
  font-weight: 700;
}

.sidebar-menu {
  border-right: none;
  background: var(--bg-sidebar);
}

.sidebar-menu .el-menu-item,
.sidebar-menu .el-sub-menu__title {
  color: var(--text-main);
}

.sidebar-menu .el-menu-item:hover,
.sidebar-menu .el-menu-item.is-active {
  background: var(--bg-sidebar-active);
  color: #fff;
}

.el-main {
  background: var(--bg-main);
}

.header {
  background: var(--header-bg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid var(--border-color);
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.username {
  color: var(--text-main);
}

.theme-trigger {
  color: var(--text-main);
}

.el-dropdown-menu__item.active {
  color: var(--accent-color);
  font-weight: 600;
}

.context-tip {
  margin-bottom: 12px;
}

.context-tip-content {
  font-size: 13px;
  line-height: 1.6;
}

.context-tip-actions {
  margin-top: 6px;
  display: flex;
  gap: 10px;
}

.welcome-text {
  color: #4b5563;
  line-height: 1.7;
}

.help-list {
  padding-left: 18px;
  color: #374151;
  line-height: 1.9;
}
</style>
