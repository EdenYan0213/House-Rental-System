import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '../api'

/**
 * 管理端（桌面）路由。
 *
 * 移动端（staff-mobile）已拆为独立前端包 `staff-mobile/`，它有自己的 router。
 * 这里只保留桌面后台页面。
 */
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
    path: '/buildings/:buildingId/rooms/:roomId/detail',
    component: () => import('../pages/RoomDetail.vue'),
    meta: { title: '房间详情', breadcrumb: true }
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
    meta: { title: '用户管理', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/logs',
    component: () => import('../pages/Logs.vue'),
    meta: { title: '操作日志', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/alert-rules',
    component: () => import('../pages/AlertRules.vue'),
    meta: { title: '操作预警', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/operation-analysis',
    component: () => import('../pages/OperationAnalysis.vue'),
    meta: { title: '操作分析', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: '/remind',
    component: () => import('../pages/Remind.vue'),
    meta: { title: '提醒设置', roles: ['SUPER_ADMIN', 'ADMIN'] }
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
  {
    path: '/complaints',
    component: () => import('../pages/Complaints.vue'),
    meta: { title: '投诉管理', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },

  {
    path: '/backup',
    component: () => import('../pages/Backup.vue'),
    meta: { title: '数据备份与恢复', roles: ['SUPER_ADMIN', 'ADMIN'] }
  },

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
