<template>
  <div
    ref="wrapRef"
    class="pull-refresh"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend="onTouchEnd"
  >
    <!-- 顶部提示条 -->
    <div
      class="pr-indicator"
      :style="{ height: indicatorHeight + 'px', opacity: indicatorOpacity }"
    >
      <span v-if="state === 'loading'" class="pr-text">正在刷新…</span>
      <span v-else-if="state === 'ready'" class="pr-text">松手刷新</span>
      <span v-else class="pr-text">下拉刷新</span>
    </div>

    <slot />
  </div>
</template>

<script setup>
import { ref } from 'vue'

/**
 * 移动端下拉刷新。
 *
 * <p>只在页面滚动到顶部时响应下拉，避免与内容滚动冲突；
 * 触发阈值 60px，超过后松手调用 refresh()。桌面端无 touch 事件，不受影响。
 */
const props = defineProps({
  // 刷新回调，应当返回 Promise
  onRefresh: { type: Function, required: true },
  // 触发阈值（px）
  threshold: { type: Number, default: 60 }
})

const wrapRef = ref(null)
const state = ref('idle') // idle | pulling | ready | loading
const distance = ref(0)
const startY = ref(0)
const pulling = ref(false)

const indicatorHeight = ref(0)
const indicatorOpacity = ref(0)

function isAtTop() {
  const el = wrapRef.value
  if (!el) return true
  // 页面级滚动
  if (window.scrollY > 0) return false
  return el.scrollTop <= 0
}

function onTouchStart(e) {
  if (state.value === 'loading' || !isAtTop()) return
  startY.value = e.touches[0].clientY
  pulling.value = true
}

function onTouchMove(e) {
  if (!pulling.value || state.value === 'loading') return
  const delta = e.touches[0].clientY - startY.value
  if (delta <= 0) {
    resetHeight()
    return
  }
  // 阻尼：越往下拉越迟钝，避免下拉过长
  const damped = Math.min(delta * 0.45, props.threshold * 1.6)
  distance.value = damped
  indicatorHeight.value = Math.min(damped, props.threshold)
  indicatorOpacity.value = Math.min(damped / props.threshold, 1)
  state.value = damped >= props.threshold ? 'ready' : 'pulling'
}

async function onTouchEnd() {
  if (!pulling.value) return
  pulling.value = false

  if (state.value === 'ready' && distance.value >= props.threshold) {
    state.value = 'loading'
    indicatorHeight.value = 40
    indicatorOpacity.value = 1
    try {
      await props.onRefresh()
    } finally {
      state.value = 'idle'
      resetHeight()
    }
  } else {
    state.value = 'idle'
    resetHeight()
  }
}

function resetHeight() {
  distance.value = 0
  indicatorHeight.value = 0
  indicatorOpacity.value = 0
}
</script>

<style scoped>
.pull-refresh {
  min-height: 100%;
}

.pr-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: height 0.18s ease, opacity 0.18s ease;
}

.pr-text {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
