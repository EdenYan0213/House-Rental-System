<template>
  <div class="ai-page">
    <header class="head">
      <button type="button" class="head-btn" aria-label="返回" @click="goBack">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <div class="head-title">
        <span class="head-name">AI 助手</span>
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
 * 键盘弹出时把整页顶上去，同时把高度锁定到「真实可见高度」。
 *
 * 为什么不用 100vh/100dvh：
 * - OPPO/小米等自带浏览器的 Chromium 内核较旧，不支持 100dvh，会回落到 100vh；
 * - 而它们的 100vh 把底部工具栏也算了进去，比可见区高出 60~100px，
 *   结果整页超出一屏——输入框被挤到屏幕外，页面还会跟着偏移。
 *
 * 这里直接用 visualViewport.height（拿不到则退回 innerHeight）写入
 * --ai-viewport-h，CSS 用它做高度上限，任何内核都准确。
 */
const MIN_HEIGHT = 320

function syncViewport() {
  const vv = window.visualViewport
  const visibleH = Math.round(vv ? vv.height : window.innerHeight)
  if (visibleH > MIN_HEIGHT) {
    document.documentElement.style.setProperty('--ai-viewport-h', `${visibleH}px`)
  }
  if (!vv) return
  // 软键盘占掉的高度（桌面浏览器恒为 0）
  const overlap = Math.max(0, window.innerHeight - vv.height - vv.offsetTop)
  document.documentElement.style.setProperty('--kb-offset', `${overlap}px`)
}

onMounted(() => {
  syncViewport()
  window.visualViewport?.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('scroll', syncViewport)
  window.addEventListener('orientationchange', syncViewport)
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.visualViewport?.removeEventListener('resize', syncViewport)
  window.visualViewport?.removeEventListener('scroll', syncViewport)
  window.removeEventListener('orientationchange', syncViewport)
  window.removeEventListener('resize', syncViewport)
  document.documentElement.style.removeProperty('--kb-offset')
  document.documentElement.style.removeProperty('--ai-viewport-h')
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
  /* 真实可见高度兜底：旧内核的 100vh 会把工具栏算进去，导致整页溢出 */
  max-height: var(--ai-viewport-h, 100%);
  overflow: hidden;
}

/* 顶栏 */
.head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px var(--m-gutter);
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
</style>
