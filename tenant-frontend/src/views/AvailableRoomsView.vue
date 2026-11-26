<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div>
          <div class="card-title" style="margin:0">可租房源</div>
          <p class="muted" style="margin:6px 0 0">新注册用户可先查看空置房间并提交沟通申请</p>
        </div>
        <button class="btn btn-light" style="width:90px" @click="refresh">刷新</button>
      </div>
    </section>

    <section v-if="!rooms.length" class="panel">
      <p class="muted">当前暂无空置房间</p>
    </section>

    <section v-for="room in rooms" :key="room.roomId" class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <strong>{{ room.buildingName }} {{ room.roomNumber }}</strong>
        <span class="muted">{{ room.layout || '-' }}</span>
      </div>
      <p class="muted" style="margin:8px 0 0">楼层：{{ room.floor || '-' }} | 面积：{{ room.area || '-' }}</p>
      <img
        v-if="coverOf(room)"
        :src="coverOf(room)"
        class="thumb"
        alt="房间图片"
        style="margin-top:8px;max-width:220px"
        @error="markBroken(room.roomId)"
      />
      <textarea
        class="textarea"
        style="margin-top:8px"
        v-model.trim="messages[room.roomId]"
        placeholder="请输入想和管理员沟通的内容，例如预算、预计入住时间等"
      ></textarea>
      <button class="btn btn-primary" style="width:100%;margin-top:8px" @click="submit(room)">提交沟通申请</button>
    </section>

    <section class="panel" style="margin-top:10px">
      <button class="btn btn-light" style="width:100%" @click="$router.push('/login')">返回登录页</button>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getVacantRooms, submitRentIntent, getErrorMessage } from '../api'
import { showError, showSuccess } from '../utils/toast'

const rooms = ref([])
const messages = ref({})

/** 记录图片加载失败的房间，避免露出破损图标 */
const brokenRooms = ref(new Set())

/**
 * 房源封面地址。
 *
 * <p>图片实际存在 image_blob 表，房间只持有 imageIds。
 * 已废弃的 imageUrl 字段不再读取——它可能是失效的旧路径，
 * 回退过去只会渲染出破损图。
 */
function coverOf(room) {
  if (brokenRooms.value.has(room.roomId)) return ''
  const ids = Array.isArray(room.imageIds) ? room.imageIds : []
  return ids.length > 0 ? `/api/images/${ids[0]}` : ''
}

function markBroken(roomId) {
  const next = new Set(brokenRooms.value)
  next.add(roomId)
  brokenRooms.value = next
}

onMounted(refresh)

const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const res = await getVacantRooms()
    rooms.value = res.data || []
  } catch (error) {
    showError(getErrorMessage(error, '房源加载失败'))
  } finally {
    loading.value = false
  }
}

async function submit(room) {
  const message = (messages.value[room.roomId] || '').trim()
  if (!message) {
    showError('请先填写沟通内容')
    return
  }
  try {
    await submitRentIntent({ roomId: room.roomId, message })
    messages.value[room.roomId] = ''
    showSuccess('已提交，管理员会尽快联系您')
  } catch (error) {
    showError(getErrorMessage(error, '提交失败，请稍后重试'))
  }
}
</script>
