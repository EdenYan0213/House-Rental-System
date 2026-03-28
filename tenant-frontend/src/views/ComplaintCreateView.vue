<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="row-between">
        <div class="card-title" style="margin:0">发起投诉</div>
        <span class="link" @click="$router.back()">返回</span>
      </div>

      <label class="field-label" style="margin-top:10px">投诉类型</label>
      <select class="select" v-model="form.type">
        <option value="">请选择</option>
        <option value="环境卫生">环境卫生</option>
        <option value="服务态度">服务态度</option>
        <option value="收费问题">收费问题</option>
        <option value="其他">其他</option>
      </select>

      <label class="field-label" style="margin-top:10px">投诉标题</label>
      <input class="input" v-model.trim="form.title" maxlength="50" placeholder="请简要概述问题（最多50字）" />

      <label class="field-label" style="margin-top:10px">投诉内容</label>
      <textarea class="textarea" v-model.trim="form.content" maxlength="500" placeholder="请详细描述投诉情况（最多500字）"></textarea>
      <p class="muted" style="margin:6px 0 0;text-align:right">{{ form.content.length }}/500</p>

      <label class="field-label" style="margin-top:10px">上传图片（最多5张）</label>
      <input class="input" type="file" accept="image/*" multiple @change="onSelectFiles" />
      <div class="thumb-list" v-if="form.images.length">
        <img v-for="(img, idx) in form.images" :key="idx" :src="img" class="thumb" alt="投诉图片" />
      </div>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交投诉' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createComplaint, getTenantCurrent, uploadTenantImage } from '../api'

const router = useRouter()
const submitting = ref(false)
const errorMsg = ref('')
const form = reactive({
  roomId: null,
  type: '',
  title: '',
  content: '',
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
  if (!form.type || !form.title || !form.content) {
    errorMsg.value = '请完整填写投诉信息'
    return
  }
  submitting.value = true
  try {
    await createComplaint({
      roomId: form.roomId,
      type: form.type,
      title: form.title,
      content: form.content,
      images: form.images
    })
    alert('投诉提交成功')
    router.replace('/complaints')
  } catch (error) {
    errorMsg.value = error?.response?.data?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>
