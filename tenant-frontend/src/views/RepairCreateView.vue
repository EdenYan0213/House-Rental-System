<template>
  <div class="page">
    <PageHeader title="发起报修" to="/repairs" />

    <section class="panel">
      <label class="field-label">故障类型 <span style="color:var(--danger)">*</span></label>
      <select class="select" v-model="form.faultType">
        <option value="">请选择故障类型</option>
        <option v-for="type in faultTypes" :key="type" :value="type">{{ type }}</option>
      </select>

      <label class="field-label" style="margin-top:12px">联系电话</label>
      <input class="input" v-model.trim="form.phone" :placeholder="defaultPhone || '请输入联系电话'" />

      <label class="field-label" style="margin-top:12px">期望上门时间（可选）</label>
      <input class="input" type="datetime-local" v-model="expectedLocal" :min="minDateTime" />

      <label class="field-label" style="margin-top:12px">故障描述 <span style="color:var(--danger)">*</span></label>
      <textarea
        class="textarea"
        v-model.trim="form.description"
        maxlength="200"
        placeholder="请描述故障位置与现象，例如：卫生间水龙头持续漏水"
      ></textarea>
      <p class="muted" style="margin:6px 0 0;text-align:right">{{ form.description.length }}/200</p>

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
          <img :src="img" class="thumb" alt="报修图片" />
          <button
            type="button"
            class="thumb-remove"
            title="移除"
            @click="removeImage(idx)"
          >×</button>
        </div>
      </div>

      <p class="error" v-if="errorMsg">{{ errorMsg }}</p>

      <button
        class="btn btn-primary"
        style="width:100%;margin-top:12px"
        :disabled="submitting || uploading"
        @click="submit"
      >
        {{ submitting ? '提交中...' : '提交报修' }}
      </button>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createRepair, getTenantCurrent, getErrorMessage, uploadTenantImage } from '../api'
import { showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const submitting = ref(false)
const uploading = ref(false)
const errorMsg = ref('')
const expectedLocal = ref('')
const defaultPhone = ref('')
const fileInput = ref(null)

const faultTypes = ['水电故障', '家电故障', '门窗损坏', '墙面地面', '网络问题', '其他']

const form = reactive({
  roomId: null,
  faultType: '',
  description: '',
  phone: '',
  images: []
})

// 期望上门时间不能早于当前
const minDateTime = computed(() => {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
})

onMounted(init)

async function init() {
  try {
    const res = await getTenantCurrent()
    const data = res.data || {}
    const room = data.currentRoom
    form.roomId = room?.roomId || room?.id || null
    // 用租客当前手机号作为默认联系电话
    defaultPhone.value = data.tenant?.phone || ''
    form.phone = defaultPhone.value
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
      // 前端先卡大小，避免白传一遍
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

  if (!form.faultType) {
    errorMsg.value = '请选择故障类型'
    return
  }
  if (!form.description) {
    errorMsg.value = '请填写故障描述'
    return
  }
  if (!form.roomId) {
    errorMsg.value = '未获取到当前房间，请返回重新进入'
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
      // datetime-local 是本地时间，直接拼秒即可，交给后端按 LocalDateTime 解析
      expectedTime: expectedLocal.value ? `${expectedLocal.value}:00` : ''
    }
    await createRepair(payload)
    showSuccess('报修提交成功')
    router.replace('/repairs')
  } catch (error) {
    errorMsg.value = getErrorMessage(error, '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>
