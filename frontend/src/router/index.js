import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '../api'

// 路由懒加载：首屏只加载仪表盘与登录页，其余页面按需加载
const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('../pages/Login.vue'), meta: { title: '登录' } },

  { path: '/dashboard', component: () => import('../pages/Dashboard.vue'), meta: { title: '仪表盘' } },

  { path: '/buildings', component: () => import('../pages/Buildings.vue'), meta: { title: '房屋管理' } },
  {
    path: '/buildings/:id/rooms',
    component: () => import('../pages/Rooms.vue'),
    meta: { title: '房间管理', breadcrumb: true }
  },
  {
    path: '/buildings/:buildingId/rooms/:roomId/meter',
    component: () => import('../pages/RoomMeters.vue'),
    meta: { title: '水电录入', breadcrumb: true }
  },
  {
    path: '/buildings/:buildingId/rooms/:roomId/assets',
    component: () => import('../pages/RoomAssets.vue'),
    meta: { title: '房间器具档案', breadcrumb: true }
  },

  { path: '/tenants', component: () => import('../pages/Tenants.vue'), meta: { title: '租户管理' } },
  {
    path: '/tenants/blacklist',
    component: () => import('../pages/Blacklist.vue'),
    meta: { title: '租户黑名单', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },

  { path: '/contracts', component: () => import('../pages/Contracts.vue'), meta: { title: '合同管理' } },
  {
    path: '/contracts/new',
    component: () => import('../pages/ContractCreate.vue'),
    meta: { title: '新建合同', breadcrumb: true }
  },

  {
    path: '/meter-readings',
    component: () => import('../pages/MeterReadings.vue'),
    meta: { title: '水电录入', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/fee-items',
    component: () => import('../pages/FeeItems.vue'),
    meta: { title: '费用项配置', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },

  { path: '/bills', component: () => import('../pages/Bills.vue'), meta: { title: '账单管理' } },
  {
    path: '/bills/:id',
    component: () => import('../pages/BillDetail.vue'),
    meta: { title: '账单详情', breadcrumb: true }
  },
  { path: '/deposits', component: () => import('../pages/Deposits.vue'), meta: { title: '押金管理' } },

  { path: '/messages', component: () => import('../pages/Messages.vue'), meta: { title: '消息中心' } },
  { path: '/settings', component: () => import('../pages/Settings.vue'), meta: { title: '个人设置' } },
  {
    path: '/users',
    component: () => import('../pages/Users.vue'),
    // GET /api/users 允许 ADMIN 查看列表，写操作由 SecurityConfig 单独限制为 SUPER_ADMIN
    meta: { title: '用户管理', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/logs',
    component: () => import('../pages/Logs.vue'),
    meta: { title: '操作日志', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },

  {
    path: '/repairs/orders',
    component: () => import('../pages/RepairOrders.vue'),
    meta: { title: '维修工单', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/repairs/workers',
    component: () => import('../pages/RepairWorkers.vue'),
    meta: { title: '维修工与模板', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },

  // 扫码入口
  { path: '/qr/room/:id', redirect: to => `/mobile/room/${to.params.id}` },
  { path: '/mobile/room/:id', component: () => import('../pages/mobile/MobileRoom.vue') },
  { path: '/mobile/meter/:roomId', component: () => import('../pages/mobile/MobileMeter.vue') },
  { path: '/mobile/repair/:roomId', component: () => import('../pages/mobile/MobileRepair.vue') },
  { path: '/mobile/bills/:roomId', component: () => import('../pages/mobile/MobileBills.vue') },

  // 兜底：未匹配的地址不再白屏
  { path: '/:pathMatch(.*)*', component: () => import('../pages/NotFound.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  const token = getToken()
  const user = getUser()
  const role = user?.role?.toUpperCase()

  if (to.path !== '/login' && !token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (to.path === '/login' && token) {
    return next(to.query.redirect || '/dashboard')
  }

  // 普通用户在移动端只能看房间总览
  if (
    ['/mobile/meter/', '/mobile/repair/', '/mobile/bills/'].some(prefix => to.path.startsWith(prefix)) &&
    role === 'USER'
  ) {
    return next(`/mobile/room/${to.params.roomId}`)
  }

  // 统一按路由 meta.roles 做权限校验，避免新增页面时遗漏
  const requiredRoles = to.meta?.roles
  if (requiredRoles && !requiredRoles.includes(role)) {
    return next('/dashboard')
  }

  next()
})

router.afterEach(to => {
  const base = '房屋租赁管理系统'
  document.title = to.meta?.title ? `${to.meta.title} - ${base}` : base
})

export default router
