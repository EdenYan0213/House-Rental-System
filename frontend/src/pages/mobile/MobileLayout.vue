<template>
  <div class="mobile-shell">
    <div class="mobile-body">
      <router-view />
    </div>
    <nav class="tabbar">
      <button
        v-for="item in navItems"
        :key="item.path"
        class="tab"
        :class="{ active: isActive(item) }"
        @click="go(item.path)"
      >
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const navItems = [
  { label: '首页', path: '/mobile/home' },
  { label: '抄表', path: '/mobile/meter-tasks' },
  { label: '报修', path: '/mobile/repairs' },
  { label: '账单', path: '/mobile/bills' },
  { label: '消息', path: '/mobile/messages' },
  { label: '我的', path: '/mobile/me' }
]

const currentPath = computed(() => route.path)

function isActive(item) {
  if (item.path === '/mobile/me' && currentPath.value.startsWith('/mobile/settings')) {
    return true
  }
  return currentPath.value === item.path
}

function go(path) {
  if (path !== currentPath.value) {
    router.push(path)
  }
}
</script>

<style scoped>
.mobile-shell {
  min-height: 100vh;
  background: linear-gradient(180deg, #f6f9ff 0%, #ffffff 30%);
}

.mobile-body {
  padding-bottom: 72px;
}

.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
  padding: 8px 10px calc(8px + env(safe-area-inset-bottom));
  border-top: 1px solid #e6ecff;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  z-index: 20;
}

.tab {
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #4f5d75;
  font-size: 12px;
  line-height: 34px;
}

.tab.active {
  background: #1f6bff;
  color: #fff;
  font-weight: 600;
}
</style>
