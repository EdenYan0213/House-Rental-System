<template>
  <el-container class="app-shell" v-if="!isLoginPage">
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo" :class="{ collapsed: sidebarCollapsed }">
        <span v-if="!sidebarCollapsed">房屋租赁系统</span>
        <span v-else>租</span>
      </div>
      <el-menu
        :default-active="currentPath"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
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
        <el-sub-menu index="settings" v-if="canAccessSettings">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/settings">个人设置</el-menu-item>
          <el-menu-item index="/users" v-if="canManageUsers">用户管理</el-menu-item>
          <el-menu-item index="/logs" v-if="canAccessLogs">操作日志</el-menu-item>
        </el-sub-menu>
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
          <el-tag size="small" :type="roleTagType">{{ roleName }}</el-tag>
          <span class="username">{{ user?.realName || user?.username }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
  <router-view v-else />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
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
  MoonNight,
  Sunny,
  Monitor,
  Expand,
  Fold
} from '@element-plus/icons-vue'
import {
  getUser,
  removeToken,
  hasRole,
  updateProfile,
  setUser,
  getThemePreference,
  setThemePreference
} from '@/api'
import { applyThemePreference } from './utils/theme'

const route = useRoute()
const router = useRouter()

const user = computed(() => getUser())
const currentPath = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login' || route.path.startsWith('/mobile/') || route.path.startsWith('/qr/'))
const themePreference = ref(getThemePreference())

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
  const u = getUser()
  return u && (u.role === 'ADMIN' || u.role === 'SUPER_ADMIN')
})
const canAccessBuilding = computed(() => true)
const canAccessFeeItems = computed(() => hasRole('ADMIN') || hasRole('SUPER_ADMIN'))
const canAccessMeterReadings = computed(() => hasRole('ADMIN') || hasRole('SUPER_ADMIN'))
const canAccessLogs = computed(() => hasRole('ADMIN') || hasRole('SUPER_ADMIN'))
const canAccessRepairs = computed(() => hasRole('ADMIN') || hasRole('SUPER_ADMIN'))
const canAccessSettings = computed(() => true)

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
  '/settings': '系统设置',
  '/users': '用户管理',
  '/logs': '操作日志'
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

onMounted(() => {
  const currentUser = getUser()
  const pref = currentUser?.themePreference || getThemePreference() || 'system'
  themePreference.value = pref
  applyThemePreference(pref)
  setThemePreference(pref)
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
  transition: width 0.2s ease;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: var(--text-main);
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.logo.collapsed {
  font-size: 16px;
}

.sidebar-menu {
  border-right: none;
  background: var(--bg-sidebar);
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
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

.el-dropdown-menu__item.active {
  color: var(--accent-color);
  font-weight: 600;
}

/* 窄屏下侧边栏自动收起，避免挤压内容区 */
@media (max-width: 900px) {
  .header .username {
    display: none;
  }
}
</style>
