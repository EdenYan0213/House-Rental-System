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

const route = useRoute()
const router = useRouter()

// 界面固定中文；语言偏好只影响后端下发的消息文案
const items = [
  { label: '首页', path: '/home' },
  { label: '报修', path: '/repairs' },
  { label: '投诉', path: '/complaints' },
  { label: '账单', path: '/bills' },
  { label: '我的', path: '/me' }
]

const currentPath = computed(() => route.path)

function go(path) {
  if (path !== route.path) {
    router.push(path)
  }
}
</script>
