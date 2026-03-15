<template>
  <div class="mobile-form">
    <el-page-header @back="goBack" content="发起报修" />
    <el-card class="panel">
      <el-form :model="form" label-width="90px">
        <el-form-item label="房间ID">
          <el-input :model-value="roomLabel" disabled />
        </el-form-item>
        <el-form-item label="故障类型">
          <el-select v-model="form.faultType" style="width: 100%">
            <el-option label="水电" value="水电" />
            <el-option label="门窗" value="门窗" />
            <el-option label="家电" value="家电" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联器具">
          <el-select v-model="form.applianceId" clearable style="width: 100%">
            <el-option v-for="item in appliances" :key="item.id" :label="item.applianceName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload :http-request="uploadImage" :show-file-list="false" :before-upload="beforeUpload" multiple>
            <el-button>上传图片</el-button>
          </el-upload>
          <div class="imgs">
            <img v-for="(img, i) in form.images" :key="i" :src="resolveImageUrl(img)" />
          </div>
        </el-form-item>
      </el-form>
    </el-card>
    <div class="footer-action">
      <el-button type="primary" :loading="saving" @click="submit" style="width: 100%">提交工单</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createRepairOrder, uploadFile, getMobileRoomSummary, getUser } from '@/api'
import { enqueueOfflineRepair } from '@/utils/mobileOffline'

const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.params.roomId))
const saving = ref(false)
const appliances = ref([])
const roomLabel = ref('')

const form = ref({
  roomId: roomId.value,
  faultType: '水电',
  description: '',
  applianceId: null,
  images: [],
  reporterType: 'admin'
})

function goBack() {
  router.push(`/mobile/room/${roomId.value}`)
}

function beforeUpload(file) {
  const okType = file.type?.startsWith('image/')
  const okSize = file.size / 1024 / 1024 < 10
  if (!okType) ElMessage.error('请上传图片文件')
  if (!okSize) ElMessage.error('图片不能超过10MB')
  return okType && okSize
}

async function uploadImage(options) {
  try {
    const res = await uploadFile(options.file)
    if (res.data?.url) {
      form.value.images.push(res.data.url)
      options.onSuccess(res.data)
    }
  } catch (e) {
    options.onError(e)
    ElMessage.error('上传失败')
  }
}

function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) {
    return url
  }
  return `/api/files/${url}`
}

async function submit() {
  saving.value = true
  try {
    await createRepairOrder({
      ...form.value,
      roomId: roomId.value,
      reporterType: 'admin',
      reportedBy: getUser()?.userId
    })
    ElMessage.success('报修提交成功')
    goBack()
  } catch (_) {
    enqueueOfflineRepair({
      ...form.value,
      roomId: roomId.value,
      reporterType: 'admin',
      reportedBy: getUser()?.userId
    })
    ElMessage.warning('网络异常，报修已离线暂存，恢复网络后自动上传')
    goBack()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getMobileRoomSummary(roomId.value)
    if (!res.data?.canOperate) {
      ElMessage.warning('当前账号仅可查看，无法发起报修')
      goBack()
      return
    }
    appliances.value = res.data.appliances || []
    const room = res.data?.room
    roomLabel.value = room ? `${room.buildingName} ${room.roomNumber}` : `房间ID ${roomId.value}`
  } catch (_) {
    appliances.value = []
    roomLabel.value = `房间ID ${roomId.value}`
  }
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
.imgs {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.imgs img {
  width: 72px;
  height: 72px;
  object-fit: cover;
}
.footer-action {
  position: sticky;
  bottom: 0;
  background: var(--bg-main);
  padding: 10px 0;
}
</style>
