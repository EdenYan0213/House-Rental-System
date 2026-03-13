<template>
  <div class="mobile-form">
    <el-page-header @back="goBack" content="录入水电" />
    <el-card class="panel">
      <el-form :model="form" label-width="90px">
        <el-form-item label="房间ID">
          <el-input :model-value="roomLabel" disabled />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="水表读数">
          <el-input-number v-model="form.waterReading" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="电表读数">
          <el-input-number v-model="form.electricReading" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
    </el-card>
    <div class="footer-action">
      <el-button type="primary" :loading="saving" @click="submit" style="width: 100%">提交</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMobileRoomSummary, saveMeterReadings } from '@/api'

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const roomId = computed(() => Number(route.params.roomId))
const roomLabel = ref('')

const form = ref({
  recordDate: new Date().toISOString().slice(0, 10),
  waterReading: 0,
  electricReading: 0
})

function goBack() {
  router.push(`/mobile/room/${roomId.value}`)
}

async function submit() {
  saving.value = true
  try {
    await saveMeterReadings([{ roomId: roomId.value, ...form.value }])
    ElMessage.success('录入成功')
    goBack()
  } catch (_) {
    ElMessage.error('提交失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getMobileRoomSummary(roomId.value)
    if (!res.data?.canOperate) {
      ElMessage.warning('当前账号仅可查看，无法录入水电')
      goBack()
      return
    }
    const room = res.data?.room
    if (room) {
      roomLabel.value = `${room.buildingName} ${room.roomNumber}`
      return
    }
  } catch (_) {
    // Keep fallback label on load failure.
  }
  roomLabel.value = `房间ID ${roomId.value}`
})
</script>

<style scoped>
.mobile-form {
  padding: 12px;
  max-width: 760px;
  margin: 0 auto;
}
.panel {
  margin-top: 12px;
}
.footer-action {
  position: sticky;
  bottom: 0;
  background: var(--bg-main);
  padding: 10px 0;
}
</style>
