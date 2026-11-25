<template>
  <div>
    <el-container class="app-shell" v-if="!isLoginPage">
      <el-aside
        class="sidebar"
        :class="{ 'is-collapsed': sidebarCollapsed }"
      >
        <div class="logo" :class="{ collapsed: sidebarCollapsed }">
          <span class="logo-mark">租</span>
          <span v-if="!sidebarCollapsed" class="logo-text">房屋租赁系统</span>
        </div>
        <el-menu
          :default-active="currentPath"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          router
          class="sidebar-menu"
        >
        <el-menu-item-group>
          <template #title><span class="menu-group-title">概览</span></template>
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title><span class="menu-group-title">房源与租约</span></template>
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
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title><span class="menu-group-title">计费与收款</span></template>
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
          <el-menu-item index="/finance" v-if="canAccessFinance">
            <el-icon><DataAnalysis /></el-icon>
            <span>财务报表</span>
          </el-menu-item>
          <el-menu-item index="/deposits">
            <el-icon><Money /></el-icon>
            <span>押金管理</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title><span class="menu-group-title">租客服务</span></template>
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
        </el-menu-item-group>

        <el-menu-item-group v-if="canAccessSettings">
          <template #title><span class="menu-group-title">系统</span></template>
          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <span>个人设置</span>
          </el-menu-item>
          <el-menu-item index="/users" v-if="canManageUsers">
            <el-icon><UserFilled /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/logs" v-if="canAccessLogs">
            <el-icon><Tickets /></el-icon>
            <span>操作日志</span>
          </el-menu-item>
          <el-menu-item index="/alert-rules" v-if="canAccessLogs">
            <el-icon><WarningFilled /></el-icon>
            <span>操作预警</span>
          </el-menu-item>
          <el-menu-item index="/operation-analysis" v-if="canAccessLogs">
            <el-icon><TrendCharts /></el-icon>
            <span>操作分析</span>
          </el-menu-item>
          <el-menu-item index="/backup" v-if="canManageBackup">
            <el-icon><FolderOpened /></el-icon>
            <span>数据备份与恢复</span>
          </el-menu-item>
        </el-menu-item-group>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="header">
          <div class="header-left">
            <el-button text class="collapse-trigger" @click="toggleSidebar">
              <el-icon>
                <Expand v-if="sidebarCollapsed" />
                <Fold v-else />
              </el-icon>
            </el-button>
            <!-- 二级页面用面包屑提供回退路径，一级页面直接显示标题 -->
            <el-breadcrumb v-if="breadcrumbs.length > 1" separator="/" class="breadcrumb">
              <el-breadcrumb-item v-for="(item, idx) in breadcrumbs" :key="idx" :to="item.to">
                {{ item.label }}
              </el-breadcrumb-item>
            </el-breadcrumb>
            <div v-else class="header-title">{{ pageTitle }}</div>
          </div>
          <div class="header-right">
          <el-button text class="search-trigger" @click="openQuickSearch">
            <el-icon><Search /></el-icon>
            <span class="search-label">搜索</span>
            <kbd class="search-kbd">{{ isMac ? '⌘' : 'Ctrl' }}K</kbd>
          </el-button>
          <el-button
            v-if="canUseAi"
            text
            class="ai-trigger"
            title="AI 助手（⌘/Ctrl + J）"
            @click="openAiAssistant"
          >
            <el-icon><MagicStick /></el-icon>
            <span class="search-label">AI 助手</span>
          </el-button>
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
    <router-view v-if="isLoginPage" :key="'bare'" />
    <QuickSearch ref="quickSearchRef" />
    <AiAssistantDrawer v-if="canUseAi" v-model="showAiAssistant" />
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
  QuestionFilled,
  Expand,
  Fold,
  Search,
  DataAnalysis,
  MagicStick,
  UserFilled,
  Tickets,
  WarningFilled,
  FolderOpened
} from '@element-plus/icons-vue'
import QuickSearch from '@/components/QuickSearch.vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'
import {
  getUser,
  removeToken,
  updateProfile,
  setUser,
  getThemePreference,
  setThemePreference,
  getCurrentUser,
  dismissGuide,
  hasRole
} from '@/api'
import { applyThemePreference } from './utils/theme'

const route = useRoute()
const router = useRouter()

const currentUser = ref(getUser())
const user = computed(() => currentUser.value)
const currentPath = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')
const themePreference = ref(getThemePreference())
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
const canAccessFinance = computed(() => hasRole('ADMIN') || hasRole('SUPER_ADMIN'))
const canManageBackup = computed(() => hasAnyRole(['ADMIN', 'SUPER_ADMIN']))

const pageTitleMap = {
  '/dashboard': '仪表盘',
  '/buildings': '房屋管理',
  '/tenants': '租户管理',
  '/contracts': '合同管理',
  '/fee-items': '费用项配置',
  '/meter-readings': '水电录入',
  '/bills': '账单管理',
  '/deposits': '押金管理',
  '/finance': '财务报表',
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
  if (path.startsWith('/buildings/') && path.endsWith('/meter')) return '水电录入'
  if (path.startsWith('/buildings/')) return '房间管理'
  if (path === '/contracts/new') return '新建合同'
  if (path.startsWith('/bills/')) return '账单详情'
  return pageTitleMap[path] || route.meta?.title || '房屋租赁系统'
})

/**
 * 二级页面面包屑：给出「模块 → 当前页」的回退路径。
 * 只有 meta.breadcrumb 为真的路由才展开，一级页面保持简洁。
 */
const breadcrumbs = computed(() => {
  const path = route.path
  const trail = [{ label: '首页', to: '/dashboard' }]
  if (!route.meta?.breadcrumb) {
    return trail
  }
  if (path.startsWith('/buildings/')) {
    trail.push({ label: '房屋管理', to: '/buildings' })
    trail.push({ label: pageTitle.value })
  } else if (path.startsWith('/contracts/')) {
    trail.push({ label: '合同管理', to: '/contracts' })
    trail.push({ label: pageTitle.value })
  } else if (path.startsWith('/bills/')) {
    trail.push({ label: '账单管理', to: '/bills' })
    trail.push({ label: pageTitle.value })
  } else {
    trail.push({ label: pageTitle.value })
  }
  return trail
})

// 全局快速搜索：⌘K / Ctrl+K 唤起
const quickSearchRef = ref(null)
const isMac = computed(() => /Mac|iPhone|iPad/.test(navigator.platform || ''))

function openQuickSearch() {
  quickSearchRef.value?.open()
}

// AI 助手：仅 SUPER_ADMIN / ADMIN 可见（与后端 @PreAuthorize 保持一致）
const showAiAssistant = ref(false)
const canUseAi = computed(() => hasRole('SUPER_ADMIN') || hasRole('ADMIN'))

function openAiAssistant() {
  showAiAssistant.value = true
}

function handleGlobalKeydown(e) {
  const key = e.key.toLowerCase()
  const withMod = e.metaKey || e.ctrlKey
  if (withMod && key === 'k') {
    e.preventDefault()
    openQuickSearch()
    return
  }
  if (withMod && key === 'j' && canUseAi.value) {
    e.preventDefault()
    openAiAssistant()
  }
}

const SIDEBAR_KEY = 'sidebarCollapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === '1')

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed.value ? '1' : '0')
}

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


  window.addEventListener('storage', refreshCurrentUser)
  window.addEventListener('keydown', handleGlobalKeydown)
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

/* 应用外壳：占满视口，侧边栏背景才能一直铺到屏幕底部。
   之前没有高度限制，容器高度等于内容高度（666px < 720px 视口），
   于是侧边栏下方露出一块背景色，看起来像「断板」。 */
.app-shell {
  height: 100vh;
  overflow: hidden;
}

/* 侧边栏：深墨绿，与浅色内容区形成明确分区。
   菜单文字用浅色，激活项用主色块 + 左侧高亮条。 */
.sidebar {
  background: var(--bg-sidebar);
  border-right: none;
  display: flex;
  flex-direction: column;
  /* 菜单项多于一屏时要能纵向滚动，否则底部分组点不到 */
  overflow-x: hidden;
  overflow-y: auto;
  /* 唯一必须保留的是 min-width: 0 —— flex 子项默认 min-width:auto
     会取内容最小宽度（展开时约 220px），让收起后的 64px 失效。
     注意：这里不要写 transition: width —— 宽度由 CSS 变量驱动，
     加过渡反而会让浏览器缓存上一帧宽度、导致收/展切换后宽度不更新
     （实测：带 transition 时宽度恒为 220px，去掉后 64↔220 正常）。 */
  min-width: 0;
  transition: background-color 0.2s ease;
  /* 收起/展开时会瞬时出现滚动条，隐藏掉避免跳一下 */
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.18) transparent;
}

/* 宽度：唯一来源。
   el-aside 自带 `.el-aside { width: var(--el-aside-width, 300px) }`，
   与 `.sidebar.is-collapsed`（同为单类选择器）优先级相同，胜负取决于样式表顺序，
   而 Element Plus 的 theme-chalk 常常后加载 —— 实测直接写 width 会被它盖掉。
   因此这里改用 `.el-aside.sidebar...` 双类前缀提高优先级，变量和 width 一起给。 */
.el-aside.sidebar:not(.is-collapsed) {
  --el-aside-width: 220px;
  width: 220px;
}

.el-aside.sidebar.is-collapsed {
  --el-aside-width: 64px;
  width: 64px;
}

.sidebar::-webkit-scrollbar {
  width: 6px;
}

.sidebar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 3px;
}

.sidebar::-webkit-scrollbar-track {
  background: transparent;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-inverse);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  /* 不随菜单滚动，始终钉在顶上 */
  flex: 0 0 auto;
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--bg-sidebar);
}

/* 品牌标识块：比裸文字更像「产品」而不是「一个 div」 */
.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-inverse);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  flex: 0 0 auto;
}

.logo-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.logo.collapsed {
  padding: 0;
  gap: 0;
}

/* 用 Element Plus 自身变量控制配色，避免被组件库默认样式覆盖 */
.sidebar-menu {
  border-right: none;
  background: var(--bg-sidebar);
  --el-menu-bg-color: var(--bg-sidebar);
  --el-menu-text-color: rgba(247, 250, 249, 0.72);
  --el-menu-hover-bg-color: var(--bg-sidebar-hover);
  --el-menu-active-color: var(--text-inverse);
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 分组标题：小字、全大写感、弱化，用来切分菜单而不是抢视觉 */
.sidebar-menu .menu-group-title {
  display: block;
  padding: 0 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: rgba(247, 250, 249, 0.38);
}

.sidebar-menu .el-menu-item-group {
  padding: 6px 0;
}

.sidebar-menu .el-menu-item-group:not(:first-child) {
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  margin-top: 4px;
}

.sidebar-menu .el-menu-item-group__title {
  padding: 10px 16px 4px;
  line-height: 1.4;
}

/* 收起状态：分组标题退化成一条细分隔线，保留分组语义 */
.sidebar-menu.el-menu--collapse .el-menu-item-group__title {
  padding: 8px 12px;
  height: 1px;
  overflow: hidden;
}

.sidebar-menu.el-menu--collapse .menu-group-title {
  display: none;
}

.sidebar-menu.el-menu--collapse .el-menu-item-group:not(:first-child) {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

/* Element Plus 自带的折叠隐藏规则只匹配 .el-menu--collapse > .el-menu-item，
   而分组里的菜单结构是 .el-menu-item-group > ul > .el-menu-item，多了一层 ul，
   于是文字不会被隐藏、还会把宽度撑到 157px 并产生横向滚动。
   这里用带 .sidebar 前缀的高优先级选择器补齐。 */
.sidebar .sidebar-menu.el-menu--collapse .el-menu-item-group .el-menu-item > span,
.sidebar .sidebar-menu.el-menu--collapse .el-menu-item-group .el-sub-menu__title > span {
  display: none !important;
}

/* 分组容器在收起态不要左右留白，否则图标不居中 */
.sidebar .sidebar-menu.el-menu--collapse .el-menu-item-group {
  padding: 6px 0;
}

.sidebar .sidebar-menu.el-menu--collapse .el-menu-item,
.sidebar .sidebar-menu.el-menu--collapse .el-sub-menu__title {
  margin: 2px 6px;
  padding-left: 0 !important;
  padding-right: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 收起态图标居中，不再依赖 padding-left */
.sidebar .sidebar-menu.el-menu--collapse .el-menu-item .el-icon,
.sidebar .sidebar-menu.el-menu--collapse .el-sub-menu__title .el-icon {
  margin: 0;
  width: auto;
}

.sidebar-menu .el-menu-item,
.sidebar-menu .el-sub-menu__title {
  color: rgba(247, 250, 249, 0.72);
  height: 42px;
  line-height: 42px;
  margin: 2px 10px;
  border-radius: 8px;
  transition: background 0.15s ease, color 0.15s ease;
}

.sidebar-menu .el-menu-item .el-icon,
.sidebar-menu .el-sub-menu__title .el-icon {
  color: rgba(247, 250, 249, 0.6);
  transition: color 0.15s ease;
}

.sidebar-menu .el-menu-item:hover,
.sidebar-menu .el-sub-menu__title:hover {
  background: var(--bg-sidebar-hover);
  color: var(--text-inverse);
}

.sidebar-menu .el-menu-item:hover .el-icon,
.sidebar-menu .el-sub-menu__title:hover .el-icon {
  color: var(--text-inverse);
}

/* 激活项：实色块 + 文字提亮加粗。
   底色用 ::after 铺底而不是 background-color ——
   Element Plus 会在 .el-menu-item 上用内联/高优先级规则重置背景，
   而伪元素不受影响，这是更稳的做法。
   不再额外画左侧竖条：深色底上的实心色块本身已经足够醒目，
   再叠一条白条会戳出圆角，显得脏。 */
.sidebar-menu .el-menu-item.is-active {
  color: var(--text-inverse) !important;
  font-weight: 600;
  position: relative;
  isolation: isolate;
}

.sidebar-menu .el-menu-item.is-active::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 8px;
  /* 用品牌墨绿而不是半透明白：在深色底上更「实」，
     与 hover 的浅灰绿有明确区分，不像悬浮态 */
  background: var(--bg-sidebar-active);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.22);
  z-index: -1;
  pointer-events: none;
}

.sidebar-menu .el-menu-item.is-active .el-icon {
  color: var(--text-inverse);
}

.el-main {
  background: var(--bg-main);
  /* 所有页面共用同一套内边距；页面根容器不要再自带 padding，
     否则会出现「有的页面 20px、有的 40px」的左边缘错位。 */
  padding: var(--space-5);
}

.header {
  background: var(--header-bg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid var(--border-color);
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.collapse-trigger {
  color: var(--text-main);
  padding: 4px;
}

.breadcrumb {
  font-size: 14px;
}

.breadcrumb .el-breadcrumb__inner {
  color: var(--text-muted);
}

.breadcrumb .el-breadcrumb__item:last-child .el-breadcrumb__inner {
  color: var(--text-main);
  font-weight: 600;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-shrink: 0;
}

.username {
  color: var(--text-main);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.theme-trigger {
  color: var(--text-main);
}

.search-trigger {
  gap: 6px;
  color: var(--text-muted);
}

.ai-trigger {
  gap: 6px;
  color: var(--accent-color);
}

.search-label {
  font-size: 13px;
}

.search-kbd {
  padding: 1px 5px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-size: 11px;
  background: var(--el-fill-color-light);
}

.el-dropdown-menu__item.active {
  color: var(--accent-color);
  font-weight: 600;
}

/* 上下文提示条 */
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


.help-list {
  padding-left: 18px;
  color: var(--text-main);
  line-height: 1.9;
}

/* 窄屏下隐藏用户名，避免头部拥挤 */
@media (max-width: 900px) {
  .header .username {
    display: none;
  }
}
</style>
