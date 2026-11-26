<template>
  <div class="top-bar">
    <span class="back-link" @click="goBack">‹ 返回</span>
    <span class="top-bar-title">{{ title }}</span>
    <span style="width:48px"></span>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  title: { type: String, default: '' },
  // 返回目标；优先于历史回退，避免从外部直链进入时返回空白页
  to: { type: String, default: '' }
})

const router = useRouter()

function goBack() {
  if (props.to) {
    router.replace(props.to)
    return
  }
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/home')
  }
}
</script>
