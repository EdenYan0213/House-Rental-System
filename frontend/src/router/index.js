import { createRouter, createWebHistory } from 'vue-router'
import Login from '../pages/Login.vue'
import Dashboard from '../pages/Dashboard.vue'
import Buildings from '../pages/Buildings.vue'
import Rooms from '../pages/Rooms.vue'
import RoomMeters from '../pages/RoomMeters.vue'
import RoomDetail from '../pages/RoomDetail.vue'
import Tenants from '../pages/Tenants.vue'
import Contracts from '../pages/Contracts.vue'
import ContractCreate from '../pages/ContractCreate.vue'
import Bills from '../pages/Bills.vue'
import BillDetail from '../pages/BillDetail.vue'
import MeterReadings from '../pages/MeterReadings.vue'
import FeeItems from '../pages/FeeItems.vue'
import Deposits from '../pages/Deposits.vue'
import Settings from '../pages/Settings.vue'
import Messages from '../pages/Messages.vue'
import Blacklist from '../pages/Blacklist.vue'
import Users from '../pages/Users.vue'
import Logs from '../pages/Logs.vue'
import AlertRules from '../pages/AlertRules.vue'
import OperationAnalysis from '../pages/OperationAnalysis.vue'
import Remind from '../pages/Remind.vue'
import RepairOrders from '../pages/RepairOrders.vue'
import RepairWorkers from '../pages/RepairWorkers.vue'
import Complaints from '../pages/Complaints.vue'
import RoomAssets from '../pages/RoomAssets.vue'
import Backup from '../pages/Backup.vue'
import MobileRoom from '../pages/mobile/MobileRoom.vue'
import MobileMeter from '../pages/mobile/MobileMeter.vue'
import MobileRepair from '../pages/mobile/MobileRepair.vue'
import MobileBills from '../pages/mobile/MobileBills.vue'
import MobileLayout from '../pages/mobile/MobileLayout.vue'
import MobileHome from '../pages/mobile/MobileHome.vue'
import MobileMeterTasks from '../pages/mobile/MobileMeterTasks.vue'
import MobileRepairCenter from '../pages/mobile/MobileRepairCenter.vue'
import MobileTenantLookup from '../pages/mobile/MobileTenantLookup.vue'
import MobileMe from '../pages/mobile/MobileMe.vue'
import MobileSettings from '../pages/mobile/MobileSettings.vue'
import MobileAiAssistant from '../pages/mobile/MobileAiAssistant.vue'
import MobileScan from '../pages/mobile/MobileScan.vue'
import MobileBillsCenter from '../pages/mobile/MobileBillsCenter.vue'
import MobileMessagesCenter from '../pages/mobile/MobileMessagesCenter.vue'
import MobileBuildings from '../pages/mobile/MobileBuildings.vue'
import MobileBuildingRooms from '../pages/mobile/MobileBuildingRooms.vue'
import { getToken, getUser } from '../api'
import { Capacitor } from '@capacitor/core'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard },
  { path: '/buildings', component: Buildings },
  { path: '/buildings/:id/rooms', component: Rooms },
  { path: '/buildings/:buildingId/rooms/:roomId/detail', component: RoomDetail },
  { path: '/buildings/:buildingId/rooms/:roomId/meter', component: RoomMeters },
  { path: '/tenants', component: Tenants },
  { path: '/tenants/blacklist', component: Blacklist },
  { path: '/contracts', component: Contracts },
  { path: '/contracts/new', component: ContractCreate },
  { path: '/meter-readings', component: MeterReadings },
  { path: '/fee-items', component: FeeItems },
  { path: '/bills', component: Bills },
  { path: '/bills/:id', component: BillDetail },
  { path: '/deposits', component: Deposits },
  { path: '/settings', component: Settings },
  { path: '/messages', component: Messages },
  { path: '/users', component: Users },
  { path: '/logs', component: Logs },
  { path: '/alert-rules', component: AlertRules },
  { path: '/operation-analysis', component: OperationAnalysis },
  { path: '/remind', component: Remind },
  { path: '/repairs/orders', component: RepairOrders },
  { path: '/repairs/workers', component: RepairWorkers },
  { path: '/complaints', component: Complaints },
  { path: '/buildings/:buildingId/rooms/:roomId/assets', component: RoomAssets },
  { path: '/backup', component: Backup },
  {
    path: '/mobile',
    component: MobileLayout,
    redirect: '/mobile/home',
    children: [
      { path: 'home', component: MobileHome },
      { path: 'meter-tasks', component: MobileMeterTasks },
      { path: 'repairs', component: MobileRepairCenter },
      { path: 'bills', component: MobileBillsCenter },
      { path: 'messages', component: MobileMessagesCenter },
      { path: 'buildings', component: MobileBuildings },
      { path: 'buildings/:id/rooms', component: MobileBuildingRooms },
      { path: 'tenants', component: MobileTenantLookup },
      { path: 'scan', component: MobileScan },
      { path: 'ai-assistant', component: MobileAiAssistant },
      { path: 'me', component: MobileMe },
      { path: 'settings', component: MobileSettings }
    ]
  },
  { path: '/qr/room/:id', redirect: to => `/mobile/room/${to.params.id}` },
  { path: '/mobile/room/:id', component: MobileRoom },
  { path: '/mobile/meter/:roomId', component: MobileMeter },
  { path: '/mobile/repair/:roomId', component: MobileRepair },
  { path: '/mobile/bills/:roomId', component: MobileBills }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = getToken()
  const user = getUser()

  if (to.path !== '/login' && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    if (Capacitor.isNativePlatform()) {
      next('/mobile/home')
    } else {
      next(to.query.redirect || '/dashboard')
    }
  } else if (
    ['/mobile/meter/', '/mobile/repair/', '/mobile/bills/'].some(prefix => to.path.startsWith(prefix)) &&
    user?.role?.toUpperCase() === 'USER'
  ) {
    next(`/mobile/room/${to.params.roomId}`)
  } else if (
    ['/mobile/meter-tasks', '/mobile/repairs'].some(prefix => to.path.startsWith(prefix)) &&
    user?.role?.toUpperCase() === 'USER'
  ) {
    next('/mobile/home')
  } else if (
    ['/mobile/bills', '/mobile/messages'].some(prefix => to.path.startsWith(prefix)) &&
    user?.role?.toUpperCase() === 'USER'
  ) {
    next('/mobile/home')
  } else if (to.path === '/logs' && user?.role?.toUpperCase() !== 'SUPER_ADMIN') {
    next('/dashboard')
  } else if (['/alert-rules', '/operation-analysis'].includes(to.path) && user?.role?.toUpperCase() !== 'SUPER_ADMIN') {
    next('/dashboard')
  } else if (to.path === '/remind' && !['SUPER_ADMIN', 'ADMIN'].includes(user?.role?.toUpperCase())) {
    next('/dashboard')
  } else if (to.path.startsWith('/repairs') && !['SUPER_ADMIN', 'ADMIN'].includes(user?.role?.toUpperCase())) {
    next('/dashboard')
  } else if (to.path.startsWith('/complaints') && !['SUPER_ADMIN', 'ADMIN'].includes(user?.role?.toUpperCase())) {
    next('/dashboard')
  } else if (to.path === '/backup' && !['SUPER_ADMIN', 'ADMIN'].includes(user?.role?.toUpperCase())) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
