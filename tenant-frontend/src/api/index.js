import axios from 'axios'

const TOKEN_KEY = 'tenant_token'
const TENANT_INFO_KEY = 'tenant_info'
const CURRENT_ROOM_KEY = 'tenant_current_room'

const api = axios.create({
  baseURL: '/api',
  // 图片上传/合同下载耗时较长，默认 10s 不够；单次请求可覆盖
  timeout: 15000
})

api.interceptors.request.use(config => {
  const token = getTenantToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 时跳登录；403/网络异常交给页面用 getErrorMessage 展示。 */
api.interceptors.response.use(
  response => response,
  error => {
    const status = error?.response?.status
    if (status === 401) {
      clearTenantSession()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

/**
 * 把后端各种错误响应体压成一句可展示的文案。
 *
 * <p>后端错误体不统一：有时是裸字符串，有时是 `{message}`，有时是 `{error}`。
 * 直接 `e.response.data` 会渲染成 `[object Object]`。所有页面统一走这里。
 */
export function getErrorMessage(error, fallback = '操作失败') {
  if (!error) return fallback
  if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
  if (!error.response) return '网络异常，请检查网络连接'

  const { data, status } = error.response
  if (typeof data === 'string' && data.trim()) return data
  if (data && typeof data === 'object') {
    if (data.message) return data.message
    if (data.error) return data.error
  }
  if (status === 403) return '无权限执行该操作'
  if (status === 404) return '请求的资源不存在'
  if (status >= 500) return '服务端异常，请稍后重试'
  return error.message || fallback
}

export const sendSmsCode = (phone) => api.post('/tenant/login/sms', { phone })
export const sendRegisterSmsCode = (phone) => api.post('/tenant/register/sms', { phone })
export const tenantLogin = (phone, code) => api.post('/tenant/login', { phone, code })
export const tenantPasswordLogin = (phone, password) => api.post('/tenant/login/password', { phone, password })
export const tenantRegister = (data) => api.post('/tenant/register', data)
export const getTenantCurrent = () => api.get('/tenant/current')
export const switchTenantRoom = (roomId) => api.post('/tenant/switch-room', { roomId })
export const getTenantHome = () => api.get('/tenant/home')
export const getVacantRooms = () => api.get('/tenant/vacant-rooms')
export const submitRentIntent = (data) => api.post('/tenant/rent-intent', data)

export const getBillList = (params) => api.get('/bill/list', { params })
export const getBillDetail = (id) => api.get(`/bill/detail/${id}`)

export const createPaymentOrder = (billId, payType) => api.post('/payment/create', { billId, payType })
export const paymentCallback = (outTradeNo, result = 'success', transactionId = '') => {
  const qs = new URLSearchParams({ outTradeNo, result, transactionId }).toString()
  return api.post(`/payment/callback?${qs}`)
}
export const getPaymentOrder = (outTradeNo) => api.get(`/tenant/payment/order/${outTradeNo}`)

export const getNoticeList = (params) => api.get('/notice/list', { params })
export const getNoticeDetail = (id) => api.get(`/notice/detail/${id}`)
export const getLatestNotice = () => api.get('/notice/latest')

export const getRepairList = (params) => api.get('/repair/list', { params })
export const getRepairDetail = (id) => api.get(`/repair/detail/${id}`)
export const createRepair = (data) => api.post('/repair/create', data)
export const confirmRepair = (id, data) => api.post(`/repair/confirm/${id}`, data)

export const getComplaintList = (params) => api.get('/complaint/list', { params })
export const getComplaintDetail = (id) => api.get(`/complaint/detail/${id}`)
export const createComplaint = (data) => api.post('/complaint/create', data)
export const confirmComplaint = (id, data) => api.post(`/complaint/confirm/${id}`, data)

export const getCurrentContract = () => api.get('/contract/current')
export const getContractHistory = () => api.get('/contract/history')
export const getMyDeposits = () => api.get('/contract/deposits')
export const downloadContract = (id) => api.get(`/contract/download/${id}`, { responseType: 'blob' })
export const getSignPreview = (contractId) => api.post('/tenant/contract/sign/preview', { contractId })
export const verifySignIdentity = (data) => api.post('/tenant/contract/sign/verify', data)
export const submitSignContract = (data) => api.post('/tenant/contract/sign/submit', data)
export const retrySignContract = (contractId) => api.post(`/tenant/contract/sign/retry/${contractId}`)
export const getSignHistory = () => api.get('/tenant/contract/sign/history')

export const applyRenew = (data) => api.post('/contract/renew/apply', data)
export const getRenewRecords = () => api.get('/contract/renew/records')

export const sendUpdatePhoneCode = (phone) => api.post('/tenant/update-phone/sms', { phone })
export const updateTenantPhone = (data) => api.post('/tenant/update-phone', data)

export const createFeedback = (data) => api.post('/feedback/create', data)

export const getTenantPreferences = () => api.get('/tenant/preferences')
export const updateTenantPreferences = (data) => api.put('/tenant/preferences', data)

export const getTenantMessages = (params) => api.get('/tenant/messages', { params })
export const getTenantMessageDetail = (id) => api.get(`/tenant/messages/${id}`)
export const readTenantMessage = (id) => api.post(`/tenant/messages/${id}/read`)
export const readAllTenantMessages = () => api.post('/tenant/messages/read-all')
export const deleteTenantMessage = (id) => api.post(`/tenant/messages/${id}/delete`)
export const processTenantMessage = (id) => api.post(`/tenant/messages/${id}/process`)

export const getTenantDashboard = (params) => api.get('/tenant/dashboard', { params })

export const uploadTenantImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/files/uploadComplaint', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const getTenantToken = () => localStorage.getItem(TOKEN_KEY)
export const setTenantToken = (token) => localStorage.setItem(TOKEN_KEY, token)

export const getTenantInfo = () => {
  const raw = localStorage.getItem(TENANT_INFO_KEY)
  return raw ? JSON.parse(raw) : null
}

export const setTenantInfo = (info) => localStorage.setItem(TENANT_INFO_KEY, JSON.stringify(info || {}))

export const getCurrentRoomCache = () => {
  const raw = localStorage.getItem(CURRENT_ROOM_KEY)
  return raw ? JSON.parse(raw) : null
}

export const setCurrentRoomCache = (room) => localStorage.setItem(CURRENT_ROOM_KEY, JSON.stringify(room || {}))

export const clearTenantSession = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TENANT_INFO_KEY)
  localStorage.removeItem(CURRENT_ROOM_KEY)
}
