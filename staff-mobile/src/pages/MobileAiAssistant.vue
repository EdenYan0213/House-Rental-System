<template>
  <div class="ai-page">
    <header class="head">
      <button type="button" class="head-btn" aria-label="返回" @click="goBack">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <div class="head-title">
        <span class="head-name">AI 助手</span>
        <span class="head-sub">租赁管理指令</span>
      </div>
      <button type="button" class="head-btn head-btn--text" @click="onClear">清空</button>
    </header>

    <AiChat ref="aiChatRef" @done="onDone" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import AiChat from '@/components/AiChat.vue'

const router = useRouter()
const aiChatRef = ref(null)

function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    // 深链/直接打开时没有上一页，回工作台
    router.replace('/mobile/home')
  }
}

function onClear() {
  aiChatRef.value?.clearMessages()
}

/** AI 改完数据后广播事件，页面按需监听刷新。 */
function onDone() {
  window.dispatchEvent(new CustomEvent('mobile:ai-done'))
}

/**
 * 键盘弹出时把整页顶上去：iOS/Android WebView 里 100dvh 不会跟着
 * 软键盘变化，输入框会被盖住。用 visualViewport 补偿这一截高度。
 */
const keyboardOffset = ref(0)

function syncViewport() {
  const vv = window.visualViewport
  if (!vv) return
  // 软键盘占掉的高度（桌面浏览器恒为 0）
  const overlap = Math.max(0, window.innerHeight - vv.height - vv.offsetTop)
  keyboardOffset.value = overlap
  document.documentElement.style.setProperty('--kb-offset', `${overlap}px`)
}

onMounted(() => {
  syncViewport()
  window.visualViewport?.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('scroll', syncViewport)
})

onBeforeUnmount(() => {
  window.visualViewport?.removeEventListener('resize', syncViewport)
  window.visualViewport?.removeEventListener('scroll', syncViewport)
  document.documentElement.style.removeProperty('--kb-offset')
})
</script>

<style scoped>
.ai-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  background: var(--bg-main);
  /* 软键盘补偿：由 visualViewport 写入，键盘收起时归零 */
  padding-bottom: var(--kb-offset, 0px);
}

/* 顶栏 */
.head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px var(--m-gutter);
  padding-top: calc(8px + env(safe-area-inset-top, 0px));
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
}

.head-btn {
  width: var(--m-tap);
  height: var(--m-tap);
  border: none;
  background: transparent;
  color: var(--text-main);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  flex: 0 0 auto;
  border-radius: 10px;
  -webkit-tap-highlight-color: transparent;
}

.head-btn:active {
  background: var(--accent-1);
}

.head-btn--text {
  width: auto;
  padding: 0 10px;
  color: var(--text-muted);
}

.head-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.25;
}

.head-name {
  font-size: 15.5px;
  font-weight: 700;
  color: var(--text-main);
}

.head-sub {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 1px;
}
</style>
