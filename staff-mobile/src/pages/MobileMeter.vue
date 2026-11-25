<template>
  <div class="mobile-form">
    <!-- 顶栏：返回 + 标题 -->
    <header class="form-head">
      <el-button link :icon="ArrowLeft" class="back-link" @click="goBack">返回</el-button>
      <span class="form-head-title">录入水电</span>
      <span class="form-head-spacer"></span>
    </header>

    <!-- 房间信息 -->
    <section class="room-banner">
      <div class="banner-room m-num">{{ roomLabel }}</div>
      <div class="banner-hint">日期：{{ form.recordDate }}</div>
    </section>

    <!-- 读数表单 -->
    <div class="m-card">
      <div class="field">
        <label class="field-label">水表读数</label>
        <el-input-number
          v-model="form.waterReading"
          :min="0"
          :step="1"
          controls-position="right"
          class="field-input"
        />
        <p v-if="waterUsage !== null" class="field-hint">
          本次用量 <span class="m-num" :class="{ 'is-negative': waterUsage < 0 }">{{ waterUsage }}</span>
          （上次 {{ lastWater ?? '无记录' }}）
        </p>
      </div>

      <div class="field">
        <label class="field-label">电表读数</label>
        <el-input-number
          v-model="form.electricReading"
          :min="0"
          :step="1"
          controls-position="right"
          class="field-input"
        />
        <p v-if="electricUsage !== null" class="field-hint">
          本次用量 <span class="m-num" :class="{ 'is-negative': electricUsage < 0 }">{{ electricUsage }}</span>
          （上次 {{ lastElectric ?? '无记录' }}）
        </p>
      </div>

      <div class="field">
        <label class="field-label">抄表日期</label>
        <el-date-picker
          v-model="form.recordDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="field-input"
        />
      </div>
    </div>

    <div class="footer-action">
      <el-button type="primary" :loading="saving" class="submit-btn" @click="submit">提交</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getMobileRoomSummary, saveMeterReadings, getLastMeterReadings } from '@/api'
import { enqueueOfflineMeterReading } from '@/utils/mobileOffline'

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const roomId = computed(() => Number(route.params.roomId))
const roomLabel = ref('')
const lastWater = ref(null)
const lastElectric = ref(null)

/** 本次用量 = 当前输入 − 上次读数；任一缺失则为 null。 */
const waterUsage = computed(() => usageOf(form.value.waterReading, lastWater.value))
const electricUsage = computed(() => usageOf(form.value.electricReading, lastElectric.value))

function usageOf(current, last) {
  if (last == null || current == null) return null
  const diff = Number(current) - Number(last)
  return Number.isFinite(diff) ? Math.round(diff * 100) / 100 : null
}

const form = ref({
  recordDate: new Date().toISOString().slice(0, 10),
  waterReading: 0,
  electricReading: 0
})

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }

  const from = String(route.query.from || '')
  if (from === 'meter-tasks') {
    router.push('/mobile/meter-tasks')
    return
  }
  if (from === 'room') {
    router.push(`/mobile/room/${roomId.value}`)
    return
  }
  router.push('/mobile/home')
}

async function submit() {
  saving.value = true
  try {
    await saveMeterReadings([{ roomId: roomId.value, ...form.value }])
    ElMessage.success('录入成功')
    goBack()
  } catch (_) {
    enqueueOfflineMeterReading({ roomId: roomId.value, ...form.value })
    ElMessage.warning('网络异常，已离线暂存，恢复网络后将自动上传')
    goBack()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // 房间信息与上次读数并行取，减少等待
  const summaryPromise = getMobileRoomSummary(roomId.value).catch(() => null)
  const lastPromise = getLastMeterReadings({ beforeDate: form.value.recordDate }).catch(() => null)

  const [res, lastRes] = await Promise.all([summaryPromise, lastPromise])

  if (res) {
    if (res.data?.canOperate === false) {
      ElMessage.warning('当前账号仅可查看，无法录入水电')
      goBack()
      return
    }
    const room = res.data?.room
    if (room) {
      roomLabel.value = `${room.buildingName} ${room.roomNumber}`
    }
  }
  if (!roomLabel.value) {
    roomLabel.value = `房间 ${roomId.value}`
  }

  // 上次读数用于算本次用量
  const last = lastRes?.data?.[String(roomId.value)]
  if (last) {
    lastWater.value = last.waterReading ?? null
    lastElectric.value = last.electricReading ?? null
  }
})
</script>

<style scoped>
.mobile-form {
  padding: var(--m-gutter);
  padding-bottom: calc(var(--m-gutter) + 72px + var(--m-safe-bottom));
  display: flex;
  flex-direction: column;
  gap: var(--m-gap);
  min-height: 100%;
  box-sizing: border-box;
}

/* 顶栏 */
.form-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
}

.back-link {
  flex: 0 0 auto;
  color: var(--accent-3);
}

.form-head-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-main);
}

.form-head-spacer {
  flex: 1;
}

/* 房间横幅 */
.room-banner {
  padding: 16px var(--m-gutter);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  border-radius: var(--m-radius);
  color: var(--text-inverse);
}

.banner-room {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
}

.banner-hint {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}

/* 表单字段 */
.field {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
}

.field:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.field:first-child {
  padding-top: 0;
}

.field-label {
  display: block;
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.field-input {
  width: 100%;
}

.field-input :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.field-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.field-hint .m-num {
  font-weight: 700;
  color: var(--accent-3);
  font-size: 14px;
}

.field-hint .m-num.is-negative {
  color: var(--danger-color);
}

/* 底部提交 */
.footer-action {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(56px + var(--m-safe-bottom));
  padding: 10px var(--m-gutter);
  background: var(--bg-surface);
  border-top: 1px solid var(--border-color);
  max-width: 520px;
  margin: 0 auto;
  z-index: 10;
}

.submit-btn {
  width: 100%;
  min-height: var(--m-tap);
  border-radius: 10px;
}
</style>
