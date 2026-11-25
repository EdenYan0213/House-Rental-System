<template>
  <div class="mobile-shell">
    <div class="mobile-body">
      <router-view />
    </div>

    <nav class="tabbar">
      <button
        v-for="item in navItems"
        :key="item.path"
        type="button"
        class="tab"
        :class="{ active: isActive(item) }"
        @click="go(item.path)"
      >
        <span class="tab-icon">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <!-- 自绘徽标：绝对定位不参与布局，避免撑开图标高度导致文字错位 -->
          <span
            v-if="item.path === '/mobile/messages' && unreadCount > 0"
            class="tab-badge"
          >{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </span>
        <span class="tab-label">{{ item.label }}</span>
      </button>
    </nav>

    <!-- AI 助手浮窗：可拖动，位置记忆；点击（未拖动）才唤起抽屉 -->
    <button
      v-if="canUseAi"
      type="button"
      class="ai-fab"
      :class="{ 'is-open': aiOpen, 'is-dragging': fabDragging, 'is-moved': !!fabPos }"
      :style="fabStyle"
      @pointerdown="onFabPointerDown"
      @pointermove="onFabPointerMove"
      @pointerup="onFabPointerUp"
      @pointercancel="onFabPointerCancel"
    >
      <el-icon :size="22">
        <Close v-if="aiOpen" />
        <MagicStick v-else />
      </el-icon>
    </button>

    <!-- AI 抽屉：从底部推出，占屏 88% -->
    <transition name="ai-slide">
      <div v-if="aiOpen" class="ai-sheet" @click.self="aiOpen = false">
        <div class="ai-sheet-panel">
          <header class="ai-sheet-head">
            <span class="ai-sheet-title">AI 助手</span>
            <div class="ai-sheet-ops">
              <button type="button" class="ai-head-btn" @click="aiChatRef?.clearMessages()">清空</button>
              <button type="button" class="ai-head-btn" @click="aiOpen = false">关闭</button>
            </div>
          </header>
          <div class="ai-sheet-body">
            <AiChat ref="aiChatRef" @done="onAiDone" />
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HomeFilled, Edit, Tools, Wallet, Bell, User, MagicStick, Close
} from '@element-plus/icons-vue'
import { getUnreadCount, getUser } from '@/api'
import AiChat from '@/components/AiChat.vue'

const route = useRoute()
const router = useRouter()

const navItems = [
  { label: '首页', path: '/mobile/home', icon: HomeFilled },
  { label: '抄表', path: '/mobile/meter-tasks', icon: Edit },
  { label: '报修', path: '/mobile/repairs', icon: Tools },
  { label: '账单', path: '/mobile/bills', icon: Wallet },
  { label: '消息', path: '/mobile/messages', icon: Bell },
  { label: '我的', path: '/mobile/me', icon: User }
]

// 底部「消息」上的未读角标
const unreadCount = ref(0)

// AI 助手抽屉
const aiOpen = ref(false)
const aiChatRef = ref(null)

/* ---------- AI 浮窗拖动 ----------
 * 用 Pointer Events 统一鼠标/触摸，避免维护两套监听。
 * 关键：按下后位移超过 DRAG_THRESHOLD 才判定为拖动，
 * 否则松手时当作点击打开抽屉——直接绑 @click 会导致
 * 想拖一下结果弹出了面板。
 */
const FAB_POS_KEY = 'mobileAiFabPos'
const DRAG_THRESHOLD = 4
const fabSize = 52
const fabDragging = ref(false)
// 相对视口左上角的位置；null 表示还没拖动过，用 CSS 默认位置
const fabPos = ref(loadFabPos())

const dragState = { active: false, moved: false, startX: 0, startY: 0, originX: 0, originY: 0 }

function loadFabPos() {
  try {
    const raw = localStorage.getItem(FAB_POS_KEY)
    if (!raw) return null
    const p = JSON.parse(raw)
    if (typeof p?.x === 'number' && typeof p?.y === 'number') return p
  } catch {
    // 存储损坏就当没拖过
  }
  return null
}

function saveFabPos(pos) {
  try {
    localStorage.setItem(FAB_POS_KEY, JSON.stringify(pos))
  } catch {
    // 隐私模式下可能写不进去，忽略即可
  }
}

/** 把坐标夹在可视区内，避免拖到屏幕外再也点不到。 */
function clampFab(x, y) {
  const margin = 8
  const maxX = Math.max(margin, window.innerWidth - fabSize - margin)
  const maxY = Math.max(margin, window.innerHeight - fabSize - margin)
  return {
    x: Math.min(Math.max(margin, x), maxX),
    y: Math.min(Math.max(margin, y), maxY)
  }
}

const fabStyle = computed(() => {
  if (!fabPos.value) return {}
  return { left: `${fabPos.value.x}px`, top: `${fabPos.value.y}px`, right: 'auto', bottom: 'auto' }
})

function onFabPointerDown(e) {
  // 只处理主键/单指
  if (e.pointerType === 'mouse' && e.button !== 0) return
  const rect = e.currentTarget.getBoundingClientRect()
  dragState.active = true
  dragState.moved = false
  dragState.startX = e.clientX
  dragState.startY = e.clientY
  dragState.originX = rect.left
  dragState.originY = rect.top
  // 捕获指针，手指滑出按钮范围也能继续收到 move
  e.currentTarget.setPointerCapture?.(e.pointerId)
}

function onFabPointerMove(e) {
  if (!dragState.active) return
  const dx = e.clientX - dragState.startX
  const dy = e.clientY - dragState.startY
  if (!dragState.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return
  dragState.moved = true
  fabDragging.value = true
  fabPos.value = clampFab(dragState.originX + dx, dragState.originY + dy)
}

function onFabPointerUp(e) {
  if (!dragState.active) return
  dragState.active = false
  e.currentTarget.releasePointerCapture?.(e.pointerId)
  if (dragState.moved) {
    fabDragging.value = false
    if (fabPos.value) saveFabPos(fabPos.value)
    return
  }
  // 没移动 → 当作点击
  aiOpen.value = !aiOpen.value
}

function onFabPointerCancel(e) {
  dragState.active = false
  fabDragging.value = false
  e.currentTarget?.releasePointerCapture?.(e.pointerId)
}

/** 屏幕尺寸变化时（横竖屏切换）重新夹一次，别把浮窗留在可视区外。 */
function onViewportResize() {
  if (fabPos.value) fabPos.value = clampFab(fabPos.value.x, fabPos.value.y)
}

/** 仅管理员/超管可用，与后端 @PreAuthorize 对齐。 */
const canUseAi = computed(() => {
  const role = getUser()?.role?.toUpperCase()
  return role === 'SUPER_ADMIN' || role === 'ADMIN'
})

/** AI 完成操作后，触发的页面可以监听这个事件刷新数据。 */
function onAiDone() {
  // 广播一个自定义事件，各页面按需监听
  window.dispatchEvent(new CustomEvent('mobile:ai-done'))
}

onMounted(async () => {
  window.addEventListener('resize', onViewportResize)
  try {
    const res = await getUnreadCount()
    unreadCount.value = Number(res.data?.count || res.data || 0)
  } catch {
    unreadCount.value = 0
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onViewportResize)
})

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
  background: var(--bg-main);
  /* 桌面浏览器预览时约束成手机宽度，避免内容被拉宽 */
  max-width: 520px;
  margin: 0 auto;
  position: relative;
}

.mobile-body {
  /* 给底部导航留出空间，避免最后一项被遮住 */
  padding-bottom: calc(64px + env(safe-area-inset-bottom, 0px));
}

/* ---------- 底部导航 ---------- */
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  padding: 6px 4px calc(6px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--border-color);
  background: var(--bg-surface);
  z-index: 20;
  max-width: 520px;
  margin: 0 auto;
}

.tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-family: inherit;
  padding: 6px 0 4px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.15s ease;
  min-height: 48px;
}

.tab-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 固定高度：徽标绝对定位后不会影响这里，6 个 tab 的基线才一致 */
  height: 22px;
  width: 22px;
}

.tab-label {
  font-size: 11px;
  line-height: 1.2;
  letter-spacing: 0.02em;
}

/* 激活态：图标与文字都换成主色，并加一个短下划线指示 */
.tab.active {
  color: var(--accent-3);
}

.tab.active .tab-label {
  font-weight: 600;
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 2px;
  width: 16px;
  height: 2px;
  border-radius: 1px;
  background: var(--accent-3);
}

/* 未读徽标：锚在图标右上，不参与布局 */
/* ---------- AI 浮窗按钮 ---------- */
.ai-fab {
  position: fixed;
  right: 16px;
  /* 默认停在底部导航之上；拖动后由内联 left/top 接管 */
  bottom: calc(76px + env(safe-area-inset-bottom, 0px));
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  box-shadow: 0 4px 14px rgba(35, 84, 72, 0.35);
  cursor: grab;
  z-index: 30;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  -webkit-tap-highlight-color: transparent;
  /* 拖动时不要触发浏览器自身的滚动/下拉刷新/长按菜单 */
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}

.ai-fab:active {
  transform: scale(0.92);
}

/* 拖动过：位置改由内联 left/top 决定，必须把默认的 right/bottom 清掉，
   否则 fixed 元素会同时受左右/上下约束而被拉伸 */
.ai-fab.is-moved {
  right: auto;
  bottom: auto;
}
.ai-fab.is-moved:not(.is-dragging) {
  right: auto;
}

/* 拖动中：去掉缩放反馈、换手型，避免「越拖越小」的错觉 */
.ai-fab.is-dragging {
  transform: none;
  cursor: grabbing;
  transition: none;
  box-shadow: 0 8px 22px rgba(35, 84, 72, 0.4);
}

.ai-fab.is-open {
  background: var(--bg-surface);
  color: var(--text-main);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-md);
}

/* 桌面预览时跟随 shell 右边界 */
@media (min-width: 521px) {
  /* 只在仍使用默认位置时跟随 shell 右边界；拖动过则由内联样式决定 */
  .ai-fab:not(.is-dragging):not(.is-moved) {
    right: calc(50vw - 260px + 16px);
  }
}

/* ---------- AI 抽屉 ---------- */
.ai-sheet {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  max-width: 520px;
  margin: 0 auto;
}

.ai-sheet-panel {
  width: 100%;
  height: 88vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-main);
  border-radius: 16px 16px 0 0;
  overflow: hidden;
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.18);
}

.ai-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px var(--m-gutter);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
  flex: 0 0 auto;
}

.ai-sheet-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.ai-sheet-ops {
  display: flex;
  gap: 4px;
}

.ai-head-btn {
  border: none;
  background: transparent;
  color: var(--accent-3);
  font-size: 13px;
  font-family: inherit;
  padding: 6px 8px;
  min-height: 34px;
  cursor: pointer;
}

.ai-sheet-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* 子组件必须能收缩：否则长文本/横向滚动的快捷指令会把面板撑出视口 */
.ai-sheet-body > * {
  flex: 1 1 auto;
  min-width: 0;
}

/* 滑入动画 */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: opacity 0.2s ease;
}

.ai-slide-enter-active .ai-sheet-panel,
.ai-slide-leave-active .ai-sheet-panel {
  transition: transform 0.24s cubic-bezier(0.32, 0.72, 0, 1);
}

.ai-slide-enter-from,
.ai-slide-leave-to {
  opacity: 0;
}

.ai-slide-enter-from .ai-sheet-panel,
.ai-slide-leave-to .ai-sheet-panel {
  transform: translateY(100%);
}

.tab-badge {
  position: absolute;
  top: -4px;
  left: 50%;
  transform: translateX(2px);
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--danger-color);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  pointer-events: none;
  box-sizing: border-box;
}
</style>
