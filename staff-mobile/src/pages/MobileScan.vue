<template>
  <div class="mobile-page">
    <header class="m-head">
      <h1 class="m-head-title">扫码进入房间</h1>
      <p class="m-head-sub">扫描房间二维码，或手动输入房间号。</p>
    </header>

    <!-- 摄像头预览 -->
    <section class="camera-block">
      <div class="camera-frame" :class="{ 'is-scanning': scanning }">
        <video ref="videoRef" class="preview" autoplay playsinline muted />
        <div v-if="!scanning" class="camera-idle">
          <el-icon :size="40"><Camera /></el-icon>
          <p>摄像头未开启</p>
        </div>
        <div v-else class="scan-line"></div>
      </div>
      <el-button
        :type="scanning ? 'default' : 'primary'"
        class="camera-btn"
        @click="toggleCamera"
      >{{ scanning ? '停止扫描' : '开始扫描' }}</el-button>
    </section>

    <!-- 手动输入 -->
    <section>
      <div class="m-section-title">手动输入</div>
      <div class="m-card">
        <el-input
          v-model="manualText"
          placeholder="如 A栋101，或粘贴二维码链接"
          clearable
          class="manual-input"
          @keyup.enter="resolveAndGo(manualText)"
        />
        <el-button
          type="primary"
          class="manual-btn"
          :loading="resolving"
          :disabled="!manualText.trim()"
          @click="resolveAndGo(manualText)"
        >进入房间</el-button>
        <p class="manual-hint">
          支持格式：<span class="m-num">/qr/room/12</span>、<span class="m-num">/mobile/room/12</span>，或「楼栋名 + 房号」
        </p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
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
.camera-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 取景框：固定 4:3，扫描中显示扫描线 */
.camera-frame {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--m-radius);
  overflow: hidden;
  background: #0f1412;
  border: 1px solid var(--border-color);
}

.camera-frame.is-scanning {
  border-color: var(--accent-3);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent-3) 25%, transparent);
}

.preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.camera-idle {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
}

.camera-idle p {
  margin: 0;
}

/* 扫描线动画 */
.scan-line {
  position: absolute;
  left: 10%;
  right: 10%;
  height: 2px;
  background: var(--accent-3);
  box-shadow: 0 0 12px var(--accent-3);
  animation: scan 2.2s ease-in-out infinite;
}

@keyframes scan {
  0%, 100% { top: 12%; opacity: 0.4; }
  50% { top: 88%; opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .scan-line { animation: none; top: 50%; }
}

.camera-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
}

/* 手动输入 */
.manual-input :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.manual-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
  margin-top: 10px;
}

.manual-hint {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.6;
}
</style>
