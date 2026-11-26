<template>
  <div class="panel">
    <div v-for="n in rows" :key="n" class="list-item" style="border:none;padding:0;margin-bottom:14px">
      <div class="skeleton skeleton-line" style="width:60%"></div>
      <div class="skeleton skeleton-line" style="width:90%"></div>
      <div class="skeleton skeleton-line" style="width:40%;margin-bottom:0"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 3 }
})
</script>
