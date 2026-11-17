<template>
  <router-view />
</template>

<script setup>
// 移动端只渲染路由内容，布局由 MobileLayout 承担
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  font-family: -apple-system, 'PingFang SC', 'Segoe UI', sans-serif;
  background: var(--bg-main);
  color: var(--text-main);
  -webkit-tap-highlight-color: transparent;
}
</style>
