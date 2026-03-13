import { createRouter, createWebHistory } from 'vue-router'
import Login from '../pages/Login.vue'
import Dashboard from '../pages/Dashboard.vue'
import Buildings from '../pages/Buildings.vue'
import Rooms from '../pages/Rooms.vue'
import RoomMeters from '../pages/RoomMeters.vue'
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
import RepairOrders from '../pages/RepairOrders.vue'
import RepairWorkers from '../pages/RepairWorkers.vue'
import RoomAssets from '../pages/RoomAssets.vue'
import MobileRoom from '../pages/mobile/MobileRoom.vue'
import MobileMeter from '../pages/mobile/MobileMeter.vue'
import MobileRepair from '../pages/mobile/MobileRepair.vue'
import MobileBills from '../pages/mobile/MobileBills.vue'
import { getToken, getUser } from '../api'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard },
  { path: '/buildings', component: Buildings },
  { path: '/buildings/:id/rooms', component: Rooms },
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
  { path: '/repairs/orders', component: RepairOrders },
  { path: '/repairs/workers', component: RepairWorkers },
  { path: '/buildings/:buildingId/rooms/:roomId/assets', component: RoomAssets },
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
    next(to.query.redirect || '/dashboard')
  } else if (
    ['/mobile/meter/', '/mobile/repair/', '/mobile/bills/'].some(prefix => to.path.startsWith(prefix)) &&
    user?.role?.toUpperCase() === 'USER'
  ) {
    next(`/mobile/room/${to.params.roomId}`)
  } else if (to.path === '/logs' && !['SUPER_ADMIN', 'ADMIN'].includes(user?.role?.toUpperCase())) {
    next('/dashboard')
  } else if (to.path.startsWith('/repairs') && !['SUPER_ADMIN', 'ADMIN'].includes(user?.role?.toUpperCase())) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
