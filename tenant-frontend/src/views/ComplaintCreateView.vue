<template>
  <div class="page">
    <PageHeader title="发起投诉" to="/complaints" />

    <section class="panel">
      <label class="field-label">投诉类型 <span style="color:var(--danger)">*</span></label>
      <select class="select" v-model="form.type">
        <option value="">请选择投诉类型</option>
        <option v-for="type in types" :key="type" :value="type">{{ type }}</option>
      </select>

      <label class="field-label" style="margin-top:12px">标题 <span style="color:var(--danger)">*</span></label>
      <input class="input" v-model.trim="form.title" maxlength="50" placeholder="一句话概括问题" />
      <p class="muted" style="margin:6px 0 0;text-align:right">{{ form.title.length }}/50</p>

      <label class="field-label" style="margin-top:12px">详细描述 <span style="color:var(--danger)">*</span></label>
      <textarea
        class="textarea"
        v-model.trim="form.content"
        maxlength="500"
        placeholder="请说明事情经过、发生时间与影响，便于快速处理"
      ></textarea>
      <p class="muted" style="margin:6px 0 0;text-align:right">{{ form.content.length }}/500</p>

      <label class="field-label" style="margin-top:12px">上传图片（最多 5 张，可选）</label>
      <input
        ref="fileInput"
        class="input"
        type="file"
        accept="image/*"
        multiple
        :disabled="uploading || form.images.length >= 5"
        @change="onSelectFiles"
      />
      <p v-if="uploading" class="muted" style="margin:6px 0 0">图片上传中…</p>

      <div class="thumb-list" v-if="form.images.length">
        <div v-for="(img, idx) in form.images" :key="img" style="position:relative">
          <img :src="img" class="thumb" alt="投诉图片" />
          <button type="button" class="thumb-remove" title="移除" @click="removeImage(idx)">×</button>
        </div>
      </div>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>

      <button
        class="btn btn-primary"
        style="width:100%;margin-top:12px"
        :disabled="submitting || uploading"
        @click="submit"
      >
        {{ submitting ? '提交中...' : '提交投诉' }}
      </button>

      <p class="muted" style="margin:10px 0 0;text-align:center;font-size:12px">
        同一房间 24 小时内最多提交 5 条投诉
      </p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createComplaint, getTenantCurrent, getErrorMessage, uploadTenantImage } from '../api'
import { showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const submitting = ref(false)
const uploading = ref(false)
const errorMsg = ref('')
const fileInput = ref(null)

const types = ['噪音扰民', '邻里纠纷', '公共设施', '卫生环境', '物业服务', '其他']

const form = reactive({
  roomId: null,
  type: '',
  title: '',
  content: '',
  images: []
})

onMounted(init)

async function init() {
  try {
    const res = await getTenantCurrent()
    const room = res.data?.currentRoom
    form.roomId = room?.roomId || room?.id || null
  } catch (error) {
    showError(getErrorMessage(error, '房间信息加载失败'))
  }
}

async function onSelectFiles(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  const remaining = 5 - form.images.length
  if (remaining <= 0) {
    showError('最多上传 5 张图片')
    event.target.value = ''
    return
  }

  uploading.value = true
  try {
    for (const file of files.slice(0, remaining)) {
      if (file.size > 5 * 1024 * 1024) {
        showError(`${file.name} 超过 5MB，已跳过`)
        continue
      }
      const res = await uploadTenantImage(file)
      const url = res.data?.url || res.data?.data || ''
      if (url) {
        form.images.push(url)
      }
    }
  } catch (error) {
    showError(getErrorMessage(error, '图片上传失败'))
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

function removeImage(index) {
  form.images.splice(index, 1)
}

async function submit() {
  errorMsg.value = ''

  if (!form.type) {
    errorMsg.value = '请选择投诉类型'
    return
  }
  if (!form.title) {
    errorMsg.value = '请填写投诉标题'
    return
  }
  if (!form.content) {
    errorMsg.value = '请填写详细描述'
    return
  }
  if (!form.roomId) {
    errorMsg.value = '未获取到当前房间，请返回重新进入'
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
    showSuccess('投诉提交成功')
    router.replace('/complaints')
  } catch (error) {
    errorMsg.value = getErrorMessage(error, '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>
