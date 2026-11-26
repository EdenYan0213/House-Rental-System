<template>
  <div class="page">
    <PageHeader title="语言与提醒" to="/me" />

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">消息语言</div>
      <p class="muted" style="margin:0 0 10px">
        选择接收系统消息（如账单提醒、报修进度）时使用的语言。界面本身固定为中文。
      </p>
      <select class="select" v-model="form.language">
        <option value="zh-CN">中文</option>
        <option value="en-US">English</option>
      </select>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">推送提醒</div>
      <label class="switch-row">
        <input type="checkbox" v-model="form.notifyOverdue" />
        账单逾期提醒
      </label>
      <label class="switch-row">
        <input type="checkbox" v-model="form.notifyRepair" />
        报修/投诉进度
      </label>
      <label class="switch-row">
        <input type="checkbox" v-model="form.notifyContract" />
        合同到期提醒
      </label>
      <label class="switch-row">
        <input type="checkbox" v-model="form.notifyNotice" />
        公告通知
      </label>
    </section>

    <button class="btn btn-primary" style="width:100%" :disabled="saving" @click="save">
      {{ saving ? '保存中...' : '保存设置' }}
    </button>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getTenantPreferences, updateTenantPreferences, getErrorMessage } from '../api'
import { showError, showSuccess } from '../utils/toast'
import PageHeader from '../components/PageHeader.vue'

const saving = ref(false)

const form = reactive({
  language: 'zh-CN',
  notifyOverdue: true,
  notifyRepair: true,
  notifyContract: true,
  notifyNotice: true
})

onMounted(load)

async function load() {
  try {
    const res = await getTenantPreferences()
    Object.assign(form, res.data || {})
  } catch (error) {
    showError(getErrorMessage(error, '设置加载失败'))
  }
}

async function save() {
  saving.value = true
  try {
    const res = await updateTenantPreferences({ ...form })
    Object.assign(form, res.data || {})
    showSuccess('保存成功')
  } catch (error) {
    showError(getErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>
