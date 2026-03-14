import api from './index'

export const getRemindRules = () => api.get('/remind/rules')
export const saveRemindRule = (data) => api.post('/remind/rules', data)

export const getSmsConfig = () => api.get('/remind/sms/config')
export const saveSmsConfig = (data) => api.post('/remind/sms/config', data)

export const getRemindLogs = () => api.get('/remind/logs')
export const testSendSms = (data) => api.post('/remind/sms/test', data)
export const triggerRemindNow = () => api.post('/remind/trigger')

export default {
  getRemindRules,
  saveRemindRule,
  getSmsConfig,
  saveSmsConfig,
  getRemindLogs,
  testSendSms,
  triggerRemindNow,
}
