import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  // 导入/导出接口耗时较长，默认 5s 不够用；单次请求可通过 config.timeout 覆盖
  timeout: 15000,
})

const TOKEN_KEY = 'token'
const USER_KEY = 'user'
const API_BASE_KEY = 'apiBaseUrl'
const THEME_KEY = 'themePreference'

function normalizeApiBaseUrl(value) {
  const raw = String(value || '').trim()
  if (!raw) return ''

  let url = raw.replace(/\/+$/, '')
  if (url === '/api' || url.endsWith('/api')) return url

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return `${url}/api`
  }

  return url
}

export const getApiBaseUrl = () => {
  const saved = localStorage.getItem(API_BASE_KEY)
  return saved || '/api'
}

export const setApiBaseUrl = (value) => {
  const normalized = normalizeApiBaseUrl(value)
  if (!normalized) {
    localStorage.removeItem(API_BASE_KEY)
    return
  }
  localStorage.setItem(API_BASE_KEY, normalized)
}

export const getThemePreference = () => {
  const value = localStorage.getItem(THEME_KEY)
  if (value === 'light' || value === 'dark' || value === 'system') return value
  return 'system'
}

export const setThemePreference = (value) => {
  const theme = value === 'light' || value === 'dark' || value === 'system' ? value : 'system'
  localStorage.setItem(THEME_KEY, theme)
}

api.interceptors.request.use(config => {
  config.baseURL = getApiBaseUrl()
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 后端错误响应结构不统一：可能是裸字符串、{message} 或 {error}，
 * 这里统一抽取成一句可读文案，避免各页面自行拼装。
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
  if (status === 403) return '无权限执行该操作，请联系管理员'
  if (status === 404) return '请求的资源不存在'
  if (status >= 500) return '服务端异常，请稍后重试'
  return error.message || fallback
}

api.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      // 移动端（Capacitor）下仍用前端路由跳转，保留 redirect 便于登录后回跳
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.href = `/login?redirect=${redirect}`
    } else if (status === 403) {
      // 403 不跳转，只提示原因，避免用户被莫名踢出当前页面
      ElMessage.warning(getErrorMessage(error, '无权限执行该操作'))
    } else if (status === 413) {
      ElMessage.warning('文件过大，请压缩后重新上传')
    }
    return Promise.reject(error)
  }
)

export const login = (data) => api.post('/auth/login', data)
export const register = (data) => api.post('/auth/register', data)
export const getCurrentUser = () => api.get('/auth/current')
export const changePassword = (data) => api.post('/auth/changePassword', data)

export const getBuildings = () => api.get('/buildings')
export const getBuilding = (id) => api.get(`/buildings/${id}`)
export const createBuilding = (data) => api.post('/buildings', data)
export const updateBuilding = (id, data) => api.put(`/buildings/${id}`, data)
export const deleteBuilding = (id) => api.delete(`/buildings/${id}`)

export const getRooms = (params) => api.get('/rooms', { params })
export const getRoom = (id) => api.get(`/rooms/${id}`)
export const getRoomDetail = (id) => api.get(`/rooms/${id}/detail`)
export const createRoom = (data) => api.post('/rooms', data)
export const updateRoom = (id, data) => api.put(`/rooms/${id}`, data)
export const deleteRoom = (id) => api.delete(`/rooms/${id}`)
export const batchDeleteRooms = (roomIds) => api.post('/rooms/batch-delete', { roomIds })
export const createRoomsBatch = (data) => api.post('/rooms/batch', data)
export const createRoomsBatchOverride = (data) => api.post('/rooms/batch/override', data)
export const batchAdjustRoomRent = (data) => api.post('/rooms/batch-adjust-rent', data)
export const getRoomQrLink = (roomId) => api.get(`/qr/rooms/${roomId}/link`)
export const getRoomQrPng = (roomId, params) => api.get(`/qr/rooms/${roomId}/png`, { params, responseType: 'blob', timeout: 60000 })
export const exportRoomQrsPdf = (data) => api.post('/qr/rooms/batch-pdf', data, { responseType: 'blob', timeout: 60000 })

export const getApplianceTemplates = () => api.get('/appliance-templates')
export const createApplianceTemplate = (data) => api.post('/appliance-templates', data)
export const updateApplianceTemplate = (id, data) => api.put(`/appliance-templates/${id}`, data)
export const deleteApplianceTemplate = (id) => api.delete(`/appliance-templates/${id}`)

export const getRoomAppliances = (roomId) => api.get('/room-appliances', { params: { roomId } })
export const createRoomAppliance = (data) => api.post('/room-appliances', data)
export const updateRoomAppliance = (id, data) => api.put(`/room-appliances/${id}`, data)
export const deleteRoomAppliance = (id) => api.delete(`/room-appliances/${id}`)
export const batchRoomAppliancesFromTemplate = (data) => api.post('/room-appliances/batch-from-template', data)

export const getRepairOrders = (params) => api.get('/repair-orders', { params })
export const getRepairOrder = (id) => api.get(`/repair-orders/${id}`)
export const createRepairOrder = (data) => api.post('/repair-orders', data)
export const getMobileRoomSummary = (roomId) => api.get(`/mobile/room/${roomId}`)
export const assignRepairWorker = (id, data) => api.put(`/repair-orders/${id}/assign`, data)
export const startRepairOrder = (id) => api.put(`/repair-orders/${id}/start`)
export const completeRepairOrder = (id, data) => api.put(`/repair-orders/${id}/complete`, data)
export const acceptRepairOrder = (id) => api.put(`/repair-orders/${id}/accept`)
export const rejectRepairOrder = (id, data) => api.put(`/repair-orders/${id}/reject`, data)
export const cancelRepairOrder = (id, data) => api.put(`/repair-orders/${id}/cancel`, data)
export const generateRepairBill = (id) => api.put(`/repair-orders/${id}/generate-bill`)
export const getRoomRepairOrders = (roomId) => api.get(`/repair-orders/room/${roomId}`)
export const getTenantRepairOrders = (tenantId) => api.get(`/repair-orders/tenant/${tenantId}`)

export const getRepairWorkers = (params) => api.get('/repair-workers', { params })
export const createRepairWorker = (data) => api.post('/repair-workers', data)
export const updateRepairWorker = (id, data) => api.put(`/repair-workers/${id}`, data)
export const toggleRepairWorker = (id) => api.put(`/repair-workers/${id}/toggle`)

export const getComplaints = (params) => api.get('/complaints', { params })
export const getComplaint = (id) => api.get(`/complaints/${id}`)
export const createComplaint = (data) => api.post('/complaints', data)
export const acceptComplaint = (id, data) => api.put(`/complaints/${id}/accept`, data)
export const replyComplaint = (id, data) => api.put(`/complaints/${id}/reply`, data)
export const completeComplaint = (id, data) => api.put(`/complaints/${id}/complete`, data)
export const reassignComplaint = (id, data) => api.put(`/complaints/${id}/reassign`, data)
export const closeComplaint = (id, data) => api.put(`/complaints/${id}/close`, data)
export const confirmComplaint = (id) => api.put(`/complaints/${id}/confirm`)
export const getComplaintHandlers = () => api.get('/complaints/handlers')
export const getTenantComplaints = (tenantId) => api.get(`/complaints/tenant/${tenantId}`)
export const getComplaintStats = (params) => api.get('/complaints/stats', { params })

export const uploadFile = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const uploadComplaintFile = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/files/uploadComplaint', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const uploadMultipleFiles = (files) => {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return api.post('/files/uploadMultiple', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deleteFile = (filename) => api.delete(`/files/${filename}`)
export const getFileInfo = (filename) => api.get(`/files/info/${filename}`)
export const uploadFileByUrl = (url) => api.post('/files/uploadByUrl', { url })

export const getTenants = (params) => api.get('/tenants', { params })
export const getTenant = (id) => api.get(`/tenants/${id}`)
export const createTenant = (data) => api.post('/tenants', data)
export const updateTenant = (id, data) => api.put(`/tenants/${id}`, data)
export const deleteTenant = (id) => api.delete(`/tenants/${id}`)
export const checkTenantBlacklist = (id) => api.get(`/tenants/${id}/blacklist-check`)
export const addToBlacklist = (id, data) => api.post(`/tenants/${id}/blacklist`, data)
export const removeFromBlacklist = (id) => api.delete(`/tenants/${id}/blacklist`)
export const getBlacklist = (params) => api.get('/tenants/blacklist', { params })

export const getContracts = (params) => api.get('/contracts', { params })
export const getContract = (id) => api.get(`/contracts/${id}`)
export const createContract = (data) => api.post('/contracts', data)
export const updateContract = (id, data) => api.put(`/contracts/${id}`, data)
export const terminateContract = (id, data) => api.post(`/contracts/${id}/terminate`, data)
export const renewContract = (id, data) => api.post(`/contracts/${id}/renew`, data)
export const batchRenewContracts = (data) => api.post('/contracts/batch-renew', data)
export const batchTerminateContracts = (data) => api.post('/contracts/batch-terminate', data)

export const getFeeItems = () => api.get('/fee-items')
export const createFeeItem = (data) => api.post('/fee-items', data)
export const updateFeeItem = (id, data) => api.put(`/fee-items/${id}`, data)
export const deleteFeeItem = (id) => api.delete(`/fee-items/${id}`)

export const getMeterReadings = (params) => api.get('/meter-readings', { params })

/** 各房间最近一次读数，用于录入页显示「上次读数」并计算本次用量 */
export const getLastMeterReadings = (params) =>
  api.get('/meter-readings/last-by-room', { params })
export const exportMeterReadingsCsv = (params) => api.get('/meter-readings/export-csv', { params, responseType: 'blob' })
export const saveMeterReadings = (data) => api.post('/meter-readings', data)
export const deleteMeterReading = (id) => api.delete(`/meter-readings/${id}`)

/** 拍照识别抄表图片，返回草稿（不写库）。结果需确认后再保存。 */
export const recognizeMeterImage = (file, roomId) => {
  const form = new FormData()
  form.append('file', file)
  const params = roomId ? { roomId } : {}
  return api.post('/meter-readings/ocr', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params,
    timeout: 60000
  })
}
export const downloadMeterReadingImportTemplate = () => api.get('/meter-readings/import-template', { responseType: 'blob', timeout: 60000 })
export const parseMeterReadingImportFile = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/meter-readings/import/parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
export const confirmMeterReadingImport = (records) =>
  api.post('/meter-readings/import/confirm', { records }, { timeout: 60000 })

// ---------- 账单与收款 ----------
export const getBills = (params) => api.get('/bills', { params })
export const exportBillsCsv = (params) => api.get('/bills/export-csv', { params, responseType: 'blob', timeout: 60000 })
export const getBill = (id) => api.get(`/bills/${id}`)
export const generateBill = (month) => {
  if (month) {
    return api.post(`/bills/generate?month=${month}`, null, { timeout: 60000 })
  }
  return api.post('/bills/generate', null, { timeout: 60000 })
}
export const generateBillForRoom = (roomId, month) => {
  if (month) {
    return api.post(`/bills/generate/room/${roomId}?month=${month}`, null, { timeout: 60000 })
  }
  return api.post(`/bills/generate/room/${roomId}`, null, { timeout: 60000 })
}
export const regenerateBill = (id) => api.post(`/bills/regenerate/${id}`, null, { timeout: 60000 })
export const regenerateBillByContract = (contractId) => api.post(`/bills/regenerate/contract/${contractId}`, null, { timeout: 60000 })
export const voidBill = (id) => api.put(`/bills/${id}/void`)
export const sendBillSms = (id) => api.post(`/bills/${id}/send-sms`)
export const recordPayment = (id, data) => api.post(`/bills/${id}/payments`, data)

export const exportModuleData = (module, data) =>
  api.post(`/export/${module}`, data || {}, { responseType: 'blob', timeout: 60000 })

export const exportRoomApplianceChecklistPdf = (roomId, type) =>
  api.get('/export/room-appliance-checklist-pdf', { params: { roomId, type }, responseType: 'blob', timeout: 60000 })

export const getDeposits = (params) => api.get('/deposits', { params })
export const createDeposit = (data) => api.post('/deposits', data)
export const updateDeposit = (id, data) => api.put(`/deposits/${id}`, data)

export const getDashboardStats = () => api.get('/dashboard/stats')
export const getDashboardCharts = () => api.get('/dashboard/charts')
export const getExpiringContracts = (days) => api.get(`/dashboard/expiring-contracts?days=${days}`)

export const getMessages = (params) => api.get('/messages', { params })
export const getUnreadCount = () => api.get('/messages/unread-count')
export const markMessageRead = (id) => api.put(`/messages/${id}/read`)
export const markAllMessagesRead = () => api.put('/messages/read-all')
export const batchSendMessages = (data) => api.post('/messages/batch-send', data)
export const reportPushDeviceToken = (data) => api.post('/messages/push-device-token', data)
export const savePushSettings = (data) => api.post('/messages/push-settings', data)
export const executeAiCommand = (data) => api.post('/ai-assistant/execute', data)

export const getReminderSettings = () => api.get('/reminder-settings')
export const updateReminderSettings = (data) => api.put('/reminder-settings', data)

export const getOperationLogs = (params) => api.get('/logs', { params })
export const exportLogs = (params) => api.get('/logs/export', { params, responseType: 'blob' })
export const getLogFilters = () => api.get('/logs/filters')
export const getLogAnalytics = (params) => api.get('/logs/analytics', { params })
export const runLogLifecycle = () => api.post('/log-lifecycle/run')
export const getAlertRules = () => api.get('/alerts/rules')
export const saveAlertRule = (data) => api.post('/alerts/rules', data)
export const deleteAlertRule = (id) => api.delete(`/alerts/rules/${id}`)
export const getAlertRecords = (params) => api.get('/alerts/records', { params })
export const handleAlertRecord = (id) => api.put(`/alerts/records/${id}/handle`)
export const getOperationSnapshots = (params) => api.get('/operation-snapshots', { params })
export const getOperationSnapshotDetail = (id) => api.get(`/operation-snapshots/${id}`)
export const recoverOperationSnapshot = (id) => api.post(`/operation-snapshots/${id}/recover`)

// ---------- 数据备份与恢复 ----------
export const getBackupRecords = (params) => api.get('/backup/records', { params })
export const createManualBackup = () => api.post('/backup/manual', null, { timeout: 300000 })
export const deleteBackup = (id) => api.delete(`/backup/records/${id}`)
export const downloadBackup = (id) => api.get(`/backup/download/${id}`, { responseType: 'blob', timeout: 300000 })
export const restoreBackup = (id) => api.post(`/backup/restore/${id}`, null, { timeout: 300000 })
export const uploadAndRestoreBackup = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/backup/upload-and-restore', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}
export const getBackupConfig = () => api.get('/backup/config')
export const updateBackupConfig = (data) => api.put('/backup/config', data)
// ---------- 认证态与本地存储 ----------
export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const removeToken = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
export const getUser = () => {
  const userStr = localStorage.getItem(USER_KEY)
  if (!userStr) return null
  try {
    return JSON.parse(userStr)
  } catch {
    return null
  }
}
export const setUser = (user) => localStorage.setItem(USER_KEY, JSON.stringify(user))

// ---------- 用户管理 ----------
export const getUsers = (params) => api.get('/users', { params })
export const getAllUsers = () => api.get('/users/all')
export const getUserById = (id) => api.get(`/users/${id}`)
export const createUser = (data) => api.post('/users', data)
export const updateUser = (id, data) => api.put(`/users/${id}`, data)
export const deleteUser = (id) => api.delete(`/users/${id}`)
export const enableUser = (id) => api.put(`/users/${id}/enable`)
export const disableUser = (id) => api.put(`/users/${id}/disable`)
export const resetUserPassword = (id, newPassword) => api.put(`/users/${id}/reset-password`, { newPassword })
export const updateProfile = (data) => api.put('/users/profile', data)

// ---------- 新手引导 ----------
export const dismissGuide = (key) => api.post('/guide/dismiss', { key })
export const resetGuide = () => api.post('/guide/reset')
export const getGuideTaskProgress = () => api.get('/guide/task-progress')

// ---------- 角色与按钮级权限 ----------
export const hasRole = (role) => {
  const user = getUser()
  if (!user) return false
  const userRole = user.role?.toUpperCase()
  const targetRole = role.toUpperCase()
  if (userRole === 'SUPER_ADMIN') return true
  if (targetRole === 'SUPER_ADMIN') return false
  if (targetRole === 'ADMIN') return userRole === 'ADMIN' || userRole === 'SUPER_ADMIN'
  if (targetRole === 'USER') return userRole === 'USER' || userRole === 'ADMIN' || userRole === 'SUPER_ADMIN'
  return false
}

export const canAddTenant = () => hasRole('USER') || hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canEditTenant = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canDeleteTenant = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')

export const canAddBuilding = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canEditBuilding = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canDeleteBuilding = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')

export const canAddContract = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canEditContract = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canDeleteContract = () => hasRole('SUPER_ADMIN')
export const canRenewContract = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canTerminateContract = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canUploadContract = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')

export const canManageFeeItems = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canInputMeterReadings = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')

export const canGenerateBill = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canRecordPayment = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canVoidBill = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canExportBill = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')

export const canRefundDeposit = () => hasRole('ADMIN') || hasRole('SUPER_ADMIN')
export const canManageUsers = () => hasRole('SUPER_ADMIN')


export default api
