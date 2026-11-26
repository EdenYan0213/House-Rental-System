<template>
  <div class="page">
    <section class="panel">
    <PageHeader title="意见反馈" to="/me" />

      <label class="field-label" style="margin-top:10px">反馈类型</label>
      <select class="select" v-model="form.type">
        <option value="">请选择</option>
        <option value="功能建议">功能建议</option>
        <option value="问题反馈">问题反馈</option>
        <option value="服务评价">服务评价</option>
        <option value="其他">其他</option>
      </select>

      <label class="field-label" style="margin-top:10px">联系方式</label>
      <input class="input" v-model.trim="form.phone" placeholder="手机号或微信号（可选）" />

      <label class="field-label" style="margin-top:10px">反馈内容</label>
      <textarea class="textarea" v-model.trim="form.content" maxlength="500" placeholder="请描述您的建议或遇到的问题（最多500字）"></textarea>
      <p class="muted" style="margin:6px 0 0;text-align:right">{{ form.content.length }}/500</p>

      <label class="field-label" style="margin-top:10px">上传图片（最多3张）</label>
      <input class="input" type="file" accept="image/*" multiple @change="onSelectFiles" />
      <div class="thumb-list" v-if="form.images.length">
        <img v-for="(img, idx) in form.images" :key="idx" :src="img" class="thumb" alt="反馈图片" />
      </div>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>
      <button class="btn btn-primary" style="width:100%;margin-top:10px" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交反馈' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createFeedback, uploadTenantImage, getErrorMessage } from '../api'
import { showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const submitting = ref(false)
const errorMsg = ref('')
const form = reactive({
  type: '',
  phone: '',
  content: '',
  images: []
})

async function onSelectFiles(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  const remaining = 3 - form.images.length
  const targetFiles = files.slice(0, Math.max(remaining, 0))
  try {
    for (const file of targetFiles) {
      if (file.size > 5 * 1024 * 1024) {
        showError(`${file.name} 超过 5MB，已跳过`)
        continue
      }
      const res = await uploadTenantImage(file)
      const url = res.data?.url || res.data?.data || ''
      if (url) form.images.push(url)
    }
  } catch (error) {
    showError(getErrorMessage(error, '图片上传失败'))
  } finally {
    event.target.value = ''
  }
}

async function submit() {
  errorMsg.value = ''
  if (!form.type || !form.content) {
    errorMsg.value = '请填写反馈类型和内容'
    return
  }
  submitting.value = true
  try {
    await createFeedback({
      type: form.type,
      phone: form.phone,
      content: form.content,
      images: form.images
    })
    showSuccess('感谢您的反馈')
    router.replace('/me')
  } catch (error) {
    errorMsg.value = getErrorMessage(error, '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>
