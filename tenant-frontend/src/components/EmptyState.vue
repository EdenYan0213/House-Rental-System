<template>
  <div class="empty-state">
    <div class="empty-state-icon" aria-hidden="true">·</div>
    <div>{{ text }}</div>
    <button v-if="actionText" class="btn btn-light" style="margin-top:12px" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  actionText: { type: String, default: '' }
})
defineEmits(['action'])
</script>
