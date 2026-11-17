import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '@/api'

/**
 * 员工移动端路由（独立服务，端口 5180）。
 * 与管理端（../frontend，端口 5173）完全分离。
 */
const routes = [
  { path: '/', redirect: '/mobile/home' },
  { path: '/login', component: () => import('@/pages/Login.vue') },

  {
    path: '/mobile',
    component: () => import('@/pages/MobileLayout.vue'),
    children: [
      { path: '', redirect: '/mobile/home' },
      { path: 'home', component: () => import('@/pages/MobileHome.vue'), meta: { title: '工作台' } },
      { path: 'meter-tasks', component: () => import('@/pages/MobileMeterTasks.vue'), meta: { title: '抄表任务' } },
      { path: 'repairs', component: () => import('@/pages/MobileRepairCenter.vue'), meta: { title: '报修中心' } },
      { path: 'bills', component: () => import('@/pages/MobileBillsCenter.vue'), meta: { title: '账单' } },
      { path: 'messages', component: () => import('@/pages/MobileMessagesCenter.vue'), meta: { title: '消息' } },
      { path: 'buildings', component: () => import('@/pages/MobileBuildings.vue'), meta: { title: '楼栋' } },
      { path: 'buildings/:id/rooms', component: () => import('@/pages/MobileBuildingRooms.vue'), meta: { title: '房间' } },
      { path: 'tenants', component: () => import('@/pages/MobileTenantLookup.vue'), meta: { title: '租户查询' } },
      { path: 'scan', component: () => import('@/pages/MobileScan.vue'), meta: { title: '扫码' } },
      { path: 'ai-assistant', component: () => import('@/pages/MobileAiAssistant.vue'), meta: { title: 'AI 助手' } },
      { path: 'me', component: () => import('@/pages/MobileMe.vue'), meta: { title: '我的' } },
      { path: 'settings', component: () => import('@/pages/MobileSettings.vue'), meta: { title: '设置' } }
    ]
  },

  // 扫码直达房间
  { path: '/qr/room/:id', redirect: to => `/mobile/room/${to.params.id}` },
  { path: '/mobile/room/:id', component: () => import('@/pages/MobileRoom.vue'), meta: { title: '房间详情' } },
  { path: '/mobile/meter/:roomId', component: () => import('@/pages/MobileMeter.vue'), meta: { title: '水电录入' } },
  { path: '/mobile/repair/:roomId', component: () => import('@/pages/MobileRepair.vue'), meta: { title: '发起报修' } },
  { path: '/mobile/bills/:roomId', component: () => import('@/pages/MobileBills.vue'), meta: { title: '房间账单' } },

  { path: '/:pathMatch(.*)*', redirect: '/mobile/home' }
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
    return next('/mobile/home')
  }

  // 普通租户账号只能看房间总览，其余快捷操作禁用
  if (
    ['/mobile/meter/', '/mobile/repair/', '/mobile/bills/'].some(p => to.path.startsWith(p)) &&
    role === 'USER'
  ) {
    return next(`/mobile/room/${to.params.roomId}`)
  }
  if (['/mobile/meter-tasks', '/mobile/repairs'].some(p => to.path.startsWith(p)) && role === 'USER') {
    return next('/mobile/home')
  }

  next()
})

router.afterEach(to => {
  document.title = to.meta?.title ? `${to.meta.title} - 房屋租赁工作台` : '房屋租赁工作台'
})

export default router
