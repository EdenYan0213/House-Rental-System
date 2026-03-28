<template>
  <div class="tenant-shell">
    <main class="tenant-main">
      <router-view />
    </main>
    <TenantTabBar />
  </div>
</template>

<script setup>
import TenantTabBar from '../components/TenantTabBar.vue'
</script>
