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
import DepositView from '../views/DepositView.vue'
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
    { path: '/login', component: LoginView, meta: { title: '登录', public: true } },
    { path: '/register', component: RegisterView, meta: { title: '注册', public: true } },
    {
      path: '/',
      component: TenantLayout,
      redirect: '/home',
      children: [
        { path: 'home', component: HomeView, meta: { title: '首页' } },
        { path: 'repairs', component: RepairsView, meta: { title: '在线报修' } },
        { path: 'complaints', component: ComplaintsView, meta: { title: '在线投诉' } },
        { path: 'bills', component: BillsView, meta: { title: '我的账单' } },
        { path: 'notices', component: NoticesView, meta: { title: '公告通知' } },
        { path: 'me', component: MeView, meta: { title: '个人中心' } }
      ]
    },
    { path: '/repairs/create', component: RepairCreateView, meta: { title: '发起报修' } },
    { path: '/repairs/:id', component: RepairDetailView, meta: { title: '报修详情' } },
    { path: '/complaints/create', component: ComplaintCreateView, meta: { title: '发起投诉' } },
    { path: '/complaints/:id', component: ComplaintDetailView, meta: { title: '投诉详情' } },
    { path: '/bills/:id', component: BillDetailView, meta: { title: '账单详情' } },
    { path: '/notices/:id', component: NoticeDetailView, meta: { title: '公告详情' } },
    { path: '/contract', component: ContractView, meta: { title: '我的合同' } },
    { path: '/deposit', component: DepositView, meta: { title: '我的押金' } },
    { path: '/renew', component: RenewApplyView, meta: { title: '申请续租' } },
    { path: '/update-phone', component: UpdatePhoneView, meta: { title: '修改手机号' } },
    { path: '/feedback', component: FeedbackView, meta: { title: '意见反馈' } },
    { path: '/messages', component: MessagesView, meta: { title: '消息中心' } },
    { path: '/messages/:id', component: MessageDetailView, meta: { title: '消息详情' } },
    { path: '/dashboard', component: DashboardView, meta: { title: '租客看板' } },
    { path: '/preferences', component: PreferencesView, meta: { title: '语言与提醒' } },
    { path: '/available-rooms', component: AvailableRoomsView, meta: { title: '可租房源' } },
    { path: '/:pathMatch(.*)*', redirect: '/home' }
  ]
})

router.beforeEach((to, from, next) => {
  const token = getTenantToken()
  // meta.public 显式标记免登录页，避免登录/注册新增页面时漏改白名单
  if (!to.meta?.public && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.meta?.public && token) {
    next('/home')
    return
  }
  next()
})

router.afterEach(to => {
  const title = to.meta?.title
  document.title = title ? `${title} · 租客端` : '租客端'
})

export default router
