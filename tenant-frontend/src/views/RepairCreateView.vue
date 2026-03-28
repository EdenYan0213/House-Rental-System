<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">发起报修</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>

      <label class="field-label" style="margin-top:10px">故障类型</label>
      <select class="select" v-model="form.faultType">
        <option value="">请选择</option>
        <option value="水电故障">水电故障</option>
        <option value="家电故障">家电故障</option>
        <option value="门窗损坏">门窗损坏</option>
        <option value="其他">其他</option>
      </select>

      <label class="field-label" style="margin-top:10px">联系电话</label>
      <input class="input" v-model.trim="form.phone" placeholder="默认当前手机号" />

      <label class="field-label" style="margin-top:10px">期望上门时间（可选）</label>
      <input class="input" type="datetime-local" v-model="expectedLocal" />

      <label class="field-label" style="margin-top:10px">故障描述</label>
      <textarea class="textarea" v-model.trim="form.description" maxlength="200" placeholder="请描述故障位置、现象等（最多200字）"></textarea>
      <p class="muted" style="margin:6px 0 0;text-align:right">{{ form.description.length }}/200</p>

      <label class="field-label" style="margin-top:10px">上传图片（最多5张）</label>
      <input class="input" type="file" accept="image/*" multiple @change="onSelectFiles" />
      <div class="thumb-list" v-if="form.images.length">
        <img v-for="(img, idx) in form.images" :key="idx" :src="img" class="thumb" alt="报修图片" />
      </div>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交报修' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createRepair, getTenantCurrent, uploadTenantImage } from '../api'

const router = useRouter()
const submitting = ref(false)
const errorMsg = ref('')
const expectedLocal = ref('')
const form = reactive({
  roomId: null,
  faultType: '',
  description: '',
  phone: '',
  images: []
})

initRoom()

async function initRoom() {
  try {
    const res = await getTenantCurrent()
    const room = res.data?.currentRoom
    form.roomId = room?.roomId || room?.id || null
  } catch (_) {
    // ignore
  }
}

async function onSelectFiles(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  const remaining = 5 - form.images.length
  const targetFiles = files.slice(0, Math.max(remaining, 0))
  for (const file of targetFiles) {
    const res = await uploadTenantImage(file)
    const url = res.data?.url || res.data?.data || ''
    if (url) {
      form.images.push(url)
    }
  }
  event.target.value = ''
}

async function submit() {
  errorMsg.value = ''
  if (!form.faultType || !form.description) {
    errorMsg.value = '请填写故障类型和故障描述'
    return
  }
  submitting.value = true
  try {
    const payload = {
      roomId: form.roomId,
      faultType: form.faultType,
      description: form.description,
      phone: form.phone,
      images: form.images,
      expectedTime: expectedLocal.value ? new Date(expectedLocal.value).toISOString().slice(0, 19) : ''
    }
    await createRepair(payload)
    alert('报修提交成功')
    router.replace('/repairs')
  } catch (error) {
    errorMsg.value = error?.response?.data?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>
