<template>
  <div class="mobile-shell" :class="{ 'is-fullscreen': isFullscreenRoute }">
    <div class="mobile-body">
      <router-view />
    </div>

    <nav v-if="!isFullscreenRoute" class="tabbar">
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

    <!-- AI 助手浮窗：可拖动，位置记忆；点击（未拖动）进入全屏对话页 -->
    <button
      v-if="canUseAi && !isFullscreenRoute"
      type="button"
      class="ai-fab"
      :class="{ 'is-dragging': fabDragging, 'is-moved': !!fabPos }"
      :style="fabStyle"
      @touchstart="onFabStart"
      @touchmove="onFabMove"
      @touchend="onFabEnd"
      @touchcancel="onFabCancel"
      @mousedown="onFabStart"
      @mousemove="onFabMove"
      @mouseup="onFabEnd"
      @click="onFabClick"
    >
      <el-icon :size="22"><MagicStick /></el-icon>
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HomeFilled, Edit, Tools, Wallet, Bell, User, MagicStick
} from '@element-plus/icons-vue'
import { getUnreadCount, getUser } from '@/api'

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

/* ---------- AI 浮窗拖动 ----------
 * 拖动与点击分开判定：touch/mouse 事件只负责拖动，真正的「打开/关闭」
 * 一律放在 @click 里。
 *
 * 之前的写法用 pointerup 手动 toggle，在微信 X5 内核上会失败：
 * 页面接管滚动后浏览器发 pointercancel（拿不到 pointerup），且
 * touch-action: none 在 X5 上不可靠，轻点抖动超过 4px 还会被判成拖动。
 * 现在阈值放宽到 10px，拖动时 preventDefault 抑制滚动，浏览器自然不会
 * 补发 click；万一补发了，justDragged 也会把它挡掉。
 */
const FAB_POS_KEY = 'mobileAiFabPos'
const DRAG_THRESHOLD = 10
const fabSize = 52
const fabDragging = ref(false)
// 相对视口左上角的位置；null 表示还没拖动过，用 CSS 默认位置
const fabPos = ref(loadFabPos())

const dragState = { active: false, moved: false, startX: 0, startY: 0, originX: 0, originY: 0 }
// 刚拖动过：吞掉浏览器随后补发的 click，避免「拖完顺手打开面板」
let justDragged = false

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

/** 触摸与鼠标共用：touches 存在则取第一根手指。 */
function pointOf(e) {
  const t = e.touches?.[0] || e.changedTouches?.[0]
  return t ? { x: t.clientX, y: t.clientY } : { x: e.clientX, y: e.clientY }
}

function onFabStart(e) {
  if (e.type === 'mousedown' && e.button !== 0) return
  const rect = e.currentTarget.getBoundingClientRect()
  const p = pointOf(e)
  dragState.active = true
  dragState.moved = false
  dragState.startX = p.x
  dragState.startY = p.y
  dragState.originX = rect.left
  dragState.originY = rect.top
  justDragged = false
}

function onFabMove(e) {
  if (!dragState.active) return
  const p = pointOf(e)
  const dx = p.x - dragState.startX
  const dy = p.y - dragState.startY
  if (!dragState.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return
  if (!dragState.moved) {
    dragState.moved = true
    fabDragging.value = true
  }
  // 拖动期间阻止页面滚动/下拉刷新（非 passive 监听才生效）
  if (e.cancelable) e.preventDefault()
  fabPos.value = clampFab(dragState.originX + dx, dragState.originY + dy)
}

function onFabEnd() {
  if (!dragState.active) return
  dragState.active = false
  const moved = dragState.moved
  dragState.moved = false
  fabDragging.value = false
  if (moved) {
    justDragged = true
    if (fabPos.value) saveFabPos(fabPos.value)
  }
}

function onFabCancel() {
  dragState.active = false
  dragState.moved = false
  fabDragging.value = false
}

/** 唯一的入口点。拖完紧跟的那次 click 要丢掉。 */
function onFabClick() {
  if (justDragged) {
    justDragged = false
    return
  }
  router.push('/mobile/ai-assistant')
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

/**
 * 全屏页（目前只有 AI 助手）：隐藏底部导航和浮窗。
 * 聊天页需要整屏高度和自己的输入栏，留着 tabbar 会挤掉输入框。
 */
const isFullscreenRoute = computed(() => route.meta?.fullscreen === true)

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

/* 全屏页占满一屏，内部自己滚动 */
.mobile-shell.is-fullscreen {
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.mobile-shell.is-fullscreen .mobile-body {
  flex: 1;
  min-height: 0;
  display: flex;
  /* 全屏页（AI 聊天）：不要底部留白，让输入栏自己贴底 */
  padding-bottom: 0;
}

.mobile-body {
  /* 给底部导航留出空间，避免最后一项被遮住 */
  padding-bottom: calc(64px + env(safe-area-inset-bottom, 0px));
}

.mobile-shell.is-fullscreen .mobile-body > * {
  flex: 1;
  min-width: 0;
  min-height: 0;
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
  /* 必须高于 Element Plus 的 el-loading-mask(2000) 与 el-message(2100+)。
     否则页面在加载中时浮窗虽然看得见，点击事件却被遮罩吞掉——
     表现为「点了没反应」。这里取 2200，低于 el-dialog(2300+) 避免盖住弹窗。 */
  z-index: 2200;
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

/* 桌面预览时跟随 shell 右边界 */
@media (min-width: 521px) {
  /* 只在仍使用默认位置时跟随 shell 右边界；拖动过则由内联样式决定 */
  .ai-fab:not(.is-dragging):not(.is-moved) {
    right: calc(50vw - 260px + 16px);
  }
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
