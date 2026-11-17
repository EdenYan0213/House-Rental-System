<template>
  <div class="not-found">
    <el-result icon="warning" title="页面不存在" sub-title="链接可能已失效，或该页面已被移动。">
      <template #extra>
        <el-button type="primary" @click="goHome">返回仪表盘</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/dashboard')
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 20px;
}
</style>
