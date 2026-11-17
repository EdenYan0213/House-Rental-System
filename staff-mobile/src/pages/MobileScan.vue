<template>
  <div class="mobile-page">
    <div class="head">
      <div class="title">扫码进入房间</div>
      <el-button text @click="toggleCamera">{{ scanning ? '停止' : '开始' }}</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      title="二维码内容支持：/qr/room/{id}、/mobile/room/{id}，也支持手动输入“楼栋+房号”"
      style="margin-bottom: 12px"
    />

    <video ref="videoRef" class="preview" autoplay playsinline muted />

    <el-input v-model="manualText" placeholder="手动粘贴二维码文本，或输入如“A栋101 / A栋 101”" style="margin-top: 12px" />
    <el-button type="primary" style="width: 100%; margin-top: 10px" @click="resolveAndGo(manualText)">进入房间</el-button>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBuildings, getRooms } from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const router = useRouter()
const scanning = ref(false)
const streamRef = ref(null)
const videoRef = ref(null)
const manualText = ref('')
const timerRef = ref(null)
const resolving = ref(false)

function parseRoomId(raw) {
  const text = String(raw || '').trim()
  if (!text) return null
  if (/^\d+$/.test(text)) return Number(text)

  const direct = text.match(/\/room\/(\d+)/)
  if (direct) return Number(direct[1])

  const qr = text.match(/\/qr\/room\/(\d+)/)
  if (qr) return Number(qr[1])

  return null
}

function normalizeText(text) {
  return String(text || '').replace(/\s+/g, '').replace(/[\-_/，,。；;：:]/g, '').toLowerCase()
}

async function findRoomByName(raw) {
  const q = normalizeText(raw)
  if (!q) return null

  const [buildingRes, roomRes] = await Promise.all([
    getBuildings(),
    getRooms({ page: 0, size: 5000 })
  ])

  const buildings = unwrapList(buildingRes.data)
  const rooms = unwrapList(roomRes.data)
  if (!rooms.length) return null

  const buildingMap = new Map(buildings.map(item => [Number(item.id), item.name || '']))
  const withName = rooms.map(room => {
    const buildingName = room.buildingName || buildingMap.get(Number(room.buildingId)) || ''
    const roomNumber = room.roomNumber || ''
    const joined = normalizeText(`${buildingName}${roomNumber}`)
    return { room, joined }
  })

  let hit = withName.find(item => item.joined === q)
  if (hit) return hit.room

  hit = withName.find(item => item.joined.includes(q) || q.includes(item.joined))
  return hit ? hit.room : null
}

async function resolveAndGo(raw) {
  if (resolving.value) return
  resolving.value = true

  const roomId = parseRoomId(raw)
  if (roomId) {
    stopCamera()
    router.push(`/mobile/room/${roomId}`)
    resolving.value = false
    return
  }

  try {
    const room = await findRoomByName(raw)
    if (!room?.id) {
      ElMessage.warning('无法识别房间，请输入“楼栋+房号”')
      resolving.value = false
      return
    }
    stopCamera()
    router.push(`/mobile/room/${room.id}`)
  } catch (_) {
    ElMessage.error('房间匹配失败，请稍后重试')
  } finally {
    resolving.value = false
  }
}

async function startCamera() {
  try {
    const media = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    streamRef.value = media
    videoRef.value.srcObject = media
    scanning.value = true
    startDecodeLoop()
  } catch (_) {
    ElMessage.warning('无法打开摄像头，请使用手动输入')
  }
}

function stopCamera() {
  if (timerRef.value) {
    window.clearInterval(timerRef.value)
    timerRef.value = null
  }
  if (streamRef.value) {
    streamRef.value.getTracks().forEach(track => track.stop())
    streamRef.value = null
  }
  scanning.value = false
}

function startDecodeLoop() {
  if (!('BarcodeDetector' in window)) {
    ElMessage.info('当前浏览器不支持自动识别，请手动粘贴二维码文本')
    return
  }

  const detector = new window.BarcodeDetector({ formats: ['qr_code'] })
  timerRef.value = window.setInterval(async () => {
    if (!videoRef.value || !scanning.value) return
    try {
      const codes = await detector.detect(videoRef.value)
      if (!codes || codes.length === 0) return
      const value = codes[0].rawValue
      if (value) {
        manualText.value = value
        resolveAndGo(value)
      }
    } catch (_) {
      // Keep scanning loop running.
    }
  }, 700)
}

function toggleCamera() {
  if (scanning.value) {
    stopCamera()
    return
  }
  startCamera()
}

onBeforeUnmount(stopCamera)
</script>

<style scoped>
.mobile-page {
  padding: 14px;
  max-width: 760px;
  margin: 0 auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: #1d3266;
}

.preview {
  width: 100%;
  min-height: 260px;
  border-radius: 12px;
  border: 1px solid #e3ebff;
  background: #111;
  object-fit: cover;
}
</style>
