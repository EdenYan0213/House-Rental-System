import { createRouter, createWebHistory } from 'vue-router'
import { getTenantToken } from '../api'
import TenantLayout from '../views/TenantLayout.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import HomeView from '../views/HomeView.vue'
import BillsView from '../views/BillsView.vue'
import BillDetailView from '../views/BillDetailView.vue'
import NoticesView from '../views/NoticesView.vue'
import NoticeDetailView from '../views/NoticeDetailView.vue'
import RepairsView from '../views/RepairsView.vue'
import RepairCreateView from '../views/RepairCreateView.vue'
import RepairDetailView from '../views/RepairDetailView.vue'
import ComplaintsView from '../views/ComplaintsView.vue'
import ComplaintCreateView from '../views/ComplaintCreateView.vue'
import ComplaintDetailView from '../views/ComplaintDetailView.vue'
import ContractView from '../views/ContractView.vue'
import RenewApplyView from '../views/RenewApplyView.vue'
import UpdatePhoneView from '../views/UpdatePhoneView.vue'
import FeedbackView from '../views/FeedbackView.vue'
import MeView from '../views/MeView.vue'
import MessagesView from '../views/MessagesView.vue'
import MessageDetailView from '../views/MessageDetailView.vue'
import DashboardView from '../views/DashboardView.vue'
import PreferencesView from '../views/PreferencesView.vue'
import AvailableRoomsView from '../views/AvailableRoomsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/register', component: RegisterView },
    {
      path: '/',
      component: TenantLayout,
      redirect: '/home',
      children: [
        { path: 'home', component: HomeView },
        { path: 'repairs', component: RepairsView },
        { path: 'complaints', component: ComplaintsView },
        { path: 'bills', component: BillsView },
        { path: 'notices', component: NoticesView },
        { path: 'me', component: MeView }
      ]
    },
    { path: '/repairs/create', component: RepairCreateView },
    { path: '/repairs/:id', component: RepairDetailView },
    { path: '/complaints/create', component: ComplaintCreateView },
    { path: '/complaints/:id', component: ComplaintDetailView },
    { path: '/bills/:id', component: BillDetailView },
    { path: '/notices/:id', component: NoticeDetailView },
    { path: '/contract', component: ContractView },
    { path: '/renew', component: RenewApplyView },
    { path: '/update-phone', component: UpdatePhoneView },
    { path: '/feedback', component: FeedbackView },
    { path: '/messages', component: MessagesView },
    { path: '/messages/:id', component: MessageDetailView },
    { path: '/dashboard', component: DashboardView },
    { path: '/preferences', component: PreferencesView },
    { path: '/available-rooms', component: AvailableRoomsView }
  ]
})

router.beforeEach((to, from, next) => {
  const token = getTenantToken()
  if (!['/login', '/register'].includes(to.path) && !token) {
    next('/login')
    return
  }
  if (['/login', '/register'].includes(to.path) && token) {
    next('/home')
    return
  }
  next()
})

export default router
