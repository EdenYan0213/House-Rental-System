const CACHE_PREFIX = 'mobile_cache_'

function keyOf(key) {
  return `${CACHE_PREFIX}${key}`
}

export function setMobileCache(key, data, ttlMs) {
  const payload = {
    data,
    ts: Date.now(),
    ttl: Number(ttlMs || 0)
  }
  localStorage.setItem(keyOf(key), JSON.stringify(payload))
}

export function getMobileCache(key) {
  try {
    const raw = localStorage.getItem(keyOf(key))
    if (!raw) return null
    const payload = JSON.parse(raw)
    if (!payload || typeof payload !== 'object') return null
    const expired = payload.ttl > 0 && Date.now() - payload.ts > payload.ttl
    if (expired) return null
    return payload.data
  } catch (_) {
    return null
  }
}

export function clearMobileCache() {
  const preserveKeys = new Set([
    'token',
    'user',
    'themePreference',
    'api_base_url',
    'mobile_meter_offline_queue',
    'mobile_repair_offline_queue',
    'mobile_notify_settings'
  ])

  const keys = Object.keys(localStorage)
  keys.forEach((k) => {
    if (preserveKeys.has(k)) return
    if (k.startsWith(CACHE_PREFIX)) {
      localStorage.removeItem(k)
    }
  })
}
