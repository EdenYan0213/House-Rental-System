// Normalize backend list responses for mobile pages.
export function unwrapList(payload) {
  if (!payload) return []
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload.content)) return payload.content
  if (Array.isArray(payload.records)) return payload.records
  if (Array.isArray(payload.items)) return payload.items
  if (Array.isArray(payload.data)) return payload.data
  return []
}

export function unwrapObject(payload) {
  if (!payload) return {}
  if (payload.data && typeof payload.data === 'object' && !Array.isArray(payload.data)) {
    return payload.data
  }
  if (typeof payload === 'object' && !Array.isArray(payload)) {
    return payload
  }
  return {}
}
