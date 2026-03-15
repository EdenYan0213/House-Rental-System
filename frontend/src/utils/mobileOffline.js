import { saveMeterReadings, createRepairOrder } from '@/api'

const METER_QUEUE_KEY = 'mobile_meter_offline_queue'
const REPAIR_QUEUE_KEY = 'mobile_repair_offline_queue'

function readQueue() {
  try {
    const raw = localStorage.getItem(METER_QUEUE_KEY)
    const data = raw ? JSON.parse(raw) : []
    return Array.isArray(data) ? data : []
  } catch (_) {
    return []
  }
}

function writeQueue(queue) {
  localStorage.setItem(METER_QUEUE_KEY, JSON.stringify(queue || []))
}

function readRepairQueue() {
  try {
    const raw = localStorage.getItem(REPAIR_QUEUE_KEY)
    const data = raw ? JSON.parse(raw) : []
    return Array.isArray(data) ? data : []
  } catch (_) {
    return []
  }
}

function writeRepairQueue(queue) {
  localStorage.setItem(REPAIR_QUEUE_KEY, JSON.stringify(queue || []))
}

export function getOfflineMeterQueue() {
  return readQueue()
}

export function enqueueOfflineMeterReading(record) {
  const queue = readQueue()
  const key = `${record.roomId}_${record.recordDate}`
  const next = queue.filter(item => `${item.roomId}_${item.recordDate}` !== key)
  next.push({ ...record, queuedAt: new Date().toISOString() })
  writeQueue(next)
}

export async function syncOfflineMeterReadings() {
  const queue = readQueue()
  if (queue.length === 0) {
    return { uploaded: 0, failed: 0 }
  }

  const remain = []
  let uploaded = 0
  for (const item of queue) {
    try {
      await saveMeterReadings([{ roomId: item.roomId, recordDate: item.recordDate, waterReading: item.waterReading, electricReading: item.electricReading }])
      uploaded += 1
    } catch (_) {
      remain.push(item)
    }
  }

  writeQueue(remain)
  return { uploaded, failed: remain.length }
}

export function getOfflineRepairQueue() {
  return readRepairQueue()
}

export function enqueueOfflineRepair(record) {
  const queue = readRepairQueue()
  queue.push({ ...record, queuedAt: new Date().toISOString() })
  writeRepairQueue(queue)
}

export async function syncOfflineRepairs() {
  const queue = readRepairQueue()
  if (queue.length === 0) {
    return { uploaded: 0, failed: 0 }
  }

  const remain = []
  let uploaded = 0
  for (const item of queue) {
    try {
      await createRepairOrder(item)
      uploaded += 1
    } catch (_) {
      remain.push(item)
    }
  }

  writeRepairQueue(remain)
  return { uploaded, failed: remain.length }
}
