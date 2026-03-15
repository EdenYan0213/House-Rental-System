import axios from 'axios'
import { Capacitor } from '@capacitor/core'

const API_BASE_URL_KEY = 'api_base_url'

const ANDROID_BASE_DEFAULT = 'http://localhost:8080/api'

function isNativeAndroid() {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android'
}

function isAllowedAndroidBase(url) {
  const value = (url || '').trim()
  return value === ANDROID_BASE_DEFAULT || value === 'http://10.0.2.2:8080/api'
}

function resolveApiBaseUrl() {
  const override = (localStorage.getItem(API_BASE_URL_KEY) || '').trim()
  if (override) {
    if (!isNativeAndroid()) return override
    if (isAllowedAndroidBase(override)) return override
    localStorage.removeItem(API_BASE_URL_KEY)
  }

  const envBase = (import.meta.env.VITE_API_BASE_URL || '').trim()
  if (envBase) return envBase

  if (isNativeAndroid()) {
    // For real devices we use adb reverse with localhost.
    return ANDROID_BASE_DEFAULT
  }

  return '/api'
}

const api = axios.create({
  baseURL: resolveApiBaseUrl(),
  timeout: 5000,
})

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export const getApiBaseUrl = () => api.defaults.baseURL

export const setApiBaseUrl = (url) => {
  const value = (url || '').trim()
  if (!value) {
    localStorage.removeItem(API_BASE_URL_KEY)
    api.defaults.baseURL = resolveApiBaseUrl()
    return
  }
  localStorage.setItem(API_BASE_URL_KEY, value)
  api.defaults.baseURL = value
}

api.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.href = `/login?redirect=${redirect}`
    }
    return Promise.reject(error)
  }
)

export const login = (data) => api.post('/auth/login', data)
export const register = (data) => api.post('/auth/register', data)
export const getCurrentUser = () => api.get('/auth/current')
export const changePassword = (data) => api.put('/users/change-password', data)

export const getBuildings = () => api.get('/buildings')
export const getBuilding = (id) => api.get(`/buildings/${id}`)
export const createBuilding = (data) => api.post('/buildings', data)
export const updateBuilding = (id, data) => api.put(`/buildings/${id}`, data)
export const deleteBuilding = (id) => api.delete(`/buildings/${id}`)

export const getRooms = (params) => api.get('/rooms', { params })
export const getRoom = (id) => api.get(`/rooms/${id}`)
export const createRoom = (data) => api.post('/rooms', data)
export const updateRoom = (id, data) => api.put(`/rooms/${id}`, data)
export const deleteRoom = (id) => api.delete(`/rooms/${id}`)
export const createRoomsBatch = (data) => api.post('/rooms/batch', data)
export const createRoomsBatchOverride = (data) => api.post('/rooms/batch/override', data)
export const batchAdjustRoomRent = (data) => api.post('/rooms/batch-adjust-rent', data)
export const getRoomQrLink = (roomId) => api.get(`/qr/rooms/${roomId}/link`)
export const getRoomQrPng = (roomId, params) => api.get(`/qr/rooms/${roomId}/png`, { params, responseType: 'blob' })
export const exportRoomQrsPdf = (data) => api.post('/qr/rooms/batch-pdf', data, { responseType: 'blob' })

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

export const uploadFile = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/files/upload', formData, {
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
export const saveMeterReadings = (data) => api.post('/meter-readings', data)
export const deleteMeterReading = (id) => api.delete(`/meter-readings/${id}`)
export const downloadMeterReadingImportTemplate = () => api.get('/meter-readings/import-template', { responseType: 'blob' })
export const parseMeterReadingImportFile = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/meter-readings/import/parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const confirmMeterReadingImport = (records) => api.post('/meter-readings/import/confirm', { records })

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
export const reportPushDeviceToken = (data) => api.post('/messages/push/register', data)
export const savePushSettings = (data) => api.put('/messages/push/settings', data)
export const getPushSettings = () => api.get('/messages/push/settings')

export const getReminderSettings = () => api.get('/reminder-settings')
export const updateReminderSettings = (data) => api.put('/reminder-settings', data)

export const getOperationLogs = (params) => api.get('/logs', { params })
export const exportLogs = (params) => api.get('/logs/export', { params })
export const getOperationSnapshots = (params) => api.get('/operation-snapshots', { params })
export const getOperationSnapshotDetail = (id) => api.get(`/operation-snapshots/${id}`)
export const recoverOperationSnapshot = (id) => api.post(`/operation-snapshots/${id}/recover`)
export const exportModuleData = (module, data) => api.post(`/export/${module}`, data || {}, { responseType: 'blob' })
export const exportRoomApplianceChecklistPdf = (roomId, type) =>
  api.get('/export/room-appliance-checklist-pdf', { params: { roomId, type }, responseType: 'blob' })

export const getBills = (params) => api.get('/bills', { params })
export const getBill = (id) => api.get(`/bills/${id}`)
export const generateBill = (month) => {
  if (month) {
    return api.post(`/bills/generate?month=${month}`)
  }
  return api.post('/bills/generate')
}
export const regenerateBill = (id) => api.post(`/bills/regenerate/${id}`)
export const regenerateBillByContract = (contractId) => api.post(`/bills/regenerate/contract/${contractId}`)
export const voidBill = (id) => api.put(`/bills/${id}/void`)
export const recordPayment = (id, data) => api.post(`/bills/${id}/payments`, data)
export const sendBillSms = (id) => api.get(`/bills/${id}/send-sms`)

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const removeToken = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
export const getUser = () => {
  const userStr = localStorage.getItem(USER_KEY)
  return userStr ? JSON.parse(userStr) : null
}
export const setUser = (user) => localStorage.setItem(USER_KEY, JSON.stringify(user))

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
export const getMobileRoomSummary = (roomId) => api.get(`/mobile/room/${roomId}`)

const THEME_KEY = 'themePreference'
export const getThemePreference = () => localStorage.getItem(THEME_KEY) || 'system'
export const setThemePreference = (value) => localStorage.setItem(THEME_KEY, value)

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

export const getBackupRecords = (params) => api.get('/backup/records', { params })
export const getBackupRecord = (id) => api.get(`/backup/records/${id}`)
export const createManualBackup = () => api.get('/backup/manual')
export const deleteBackup = (id) => api.delete(`/backup/records/${id}`)
export const downloadBackup = (id) => api.get(`/backup/download/${id}`, { responseType: 'blob' })
export const restoreBackup = (id) => api.get(`/backup/restore/${id}`)
export const getBackupConfig = () => api.get('/backup/config')
export const updateBackupConfig = (data) => api.put('/backup/config', data)
export const getBackupRestoreLogs = () => api.get('/backup/restore-logs')
export const getMaintenanceStatus = () => api.get('/backup/maintenance-status')
export const enableMaintenance = (reason) => api.post('/backup/maintenance/enable', { reason })
export const disableMaintenance = () => api.post('/backup/maintenance/disable')

export const canManageBackup = () => hasRole('SUPER_ADMIN')

export default api
