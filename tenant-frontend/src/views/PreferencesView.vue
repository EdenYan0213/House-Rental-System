<template>
  <div class="page">
    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('lang_title') }}</div>
      <label class="field-label">{{ t('lang_switch') }}</label>
      <select class="select" v-model="form.language">
        <option value="zh-CN">{{ t('lang_zh') }}</option>
        <option value="en-US">{{ t('lang_en') }}</option>
      </select>
    </section>

    <section class="panel" style="margin-bottom:10px">
      <div class="card-title">{{ t('lang_notify') }}</div>
      <label class="switch-row"><input type="checkbox" v-model="form.notifyOverdue" /> {{ t('lang_overdue') }}</label>
      <label class="switch-row"><input type="checkbox" v-model="form.notifyRepair" /> {{ t('lang_repair') }}</label>
      <label class="switch-row"><input type="checkbox" v-model="form.notifyContract" /> {{ t('lang_contract') }}</label>
      <label class="switch-row"><input type="checkbox" v-model="form.notifyNotice" /> {{ t('lang_notice') }}</label>
    </section>

    <button class="btn btn-primary" style="width:100%" @click="save">{{ t('save') }}</button>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { getTenantPreferences, updateTenantPreferences } from '../api'
import { setLanguage, t } from '../utils/i18n'

const form = reactive({
  language: 'zh-CN',
  notifyOverdue: true,
  notifyRepair: true,
  notifyContract: true,
  notifyNotice: true
})

onMounted(load)

async function load() {
  const res = await getTenantPreferences()
  Object.assign(form, res.data || {})
  setLanguage(form.language)
}

async function save() {
  const res = await updateTenantPreferences({ ...form })
  Object.assign(form, res.data || {})
  setLanguage(form.language)
  alert(t('save_ok'))
}
</script>
