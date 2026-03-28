<template>
  <nav class="tabbar">
    <button v-for="item in items" :key="item.path" :class="['tab-item', { active: currentPath === item.path }]" @click="go(item.path)">
      {{ item.label }}
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { currentLanguage, t } from '../utils/i18n'

const route = useRoute()
const router = useRouter()

const items = computed(() => {
  const _lang = currentLanguage.value
  return [
    { label: t('tab_home'), path: '/home' },
    { label: t('tab_repairs'), path: '/repairs' },
    { label: t('tab_complaints'), path: '/complaints' },
    { label: t('tab_bills'), path: '/bills' },
    { label: t('tab_me'), path: '/me' }
  ]
})

const currentPath = computed(() => route.path)

function go(path) {
  if (path !== route.path) {
    router.push(path)
  }
}
</script>
