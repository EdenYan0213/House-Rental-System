import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'
import { reportPushDeviceToken } from '@/api'

const PUSH_TOKEN_KEY = 'mobile_push_token'

function isPushEnabled() {
  // Push requires Firebase config on native Android. Keep it opt-in for local debug.
  const flag = (import.meta.env.VITE_ENABLE_PUSH || '').toString().trim().toLowerCase()
  return flag === '1' || flag === 'true' || flag === 'yes' || flag === 'on'
}

function resolveRouteFromPayload(payload) {
  const route = payload?.route || payload?.data?.route
  if (route) return route

  const type = payload?.type || payload?.data?.type
  if (!type) return '/mobile/messages'

  if (type.startsWith('repair')) return '/mobile/repairs'
  if (type.startsWith('bill')) return '/mobile/bills'
  if (type.startsWith('contract')) return '/mobile/tenants'
  return '/mobile/messages'
}

export async function initMobilePush(router) {
  if (!Capacitor.isNativePlatform()) return
  if (!isPushEnabled()) return

  PushNotifications.addListener('registration', async (token) => {
    const value = token?.value || ''
    if (!value) return
    localStorage.setItem(PUSH_TOKEN_KEY, value)
    try {
      await reportPushDeviceToken({
        token: value,
        platform: Capacitor.getPlatform(),
        app: 'mobile-admin'
      })
    } catch (_) {
      // Backend endpoint may not be ready yet.
    }
  })

  PushNotifications.addListener('registrationError', () => {
    // Keep app usable even if push registration fails.
  })

  PushNotifications.addListener('pushNotificationReceived', () => {
    // Foreground notification hook reserved for custom UI.
  })

  PushNotifications.addListener('pushNotificationActionPerformed', (event) => {
    const route = resolveRouteFromPayload(event?.notification)
    router.push(route)
  })

  const permStatus = await PushNotifications.requestPermissions()
  if (permStatus.receive !== 'granted') return

  try {
    await PushNotifications.register()
  } catch (_) {
    // Keep app usable when native push dependencies are not configured.
  }
}

export function getCachedPushToken() {
  return localStorage.getItem(PUSH_TOKEN_KEY) || ''
}
