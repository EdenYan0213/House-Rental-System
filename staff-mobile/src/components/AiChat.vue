<template>
  <div class="ai-chat">
    <!-- 对话区 -->
    <div ref="historyRef" class="chat-history" :class="{ 'has-messages': hasUserMessage }">
      <!-- 欢迎态：还没有任何用户消息时，展示能力介绍（点击即发送） -->
      <div v-if="!hasUserMessage" class="welcome">
        <div class="welcome-logo">
          <el-icon :size="26"><MagicStick /></el-icon>
        </div>
        <h2 class="welcome-title">{{ greeting }}</h2>
        <p class="welcome-desc">
          用一句话说要做的事，我会直接调用系统功能完成。
        </p>

        <div class="capability-list">
          <button
            v-for="cap in capabilities"
            :key="cap.label"
            type="button"
            class="capability"
            @click="send(cap.example)"
          >
            <span class="cap-icon">
              <el-icon :size="16"><component :is="cap.icon" /></el-icon>
            </span>
            <span class="cap-text">
              <span class="cap-label">{{ cap.label }}</span>
              <span class="cap-example">{{ cap.example }}</span>
            </span>
            <el-icon class="cap-arrow" :size="13"><ArrowRight /></el-icon>
          </button>
        </div>
      </div>

      <!-- 消息气泡 -->
      <template v-for="item in messages" :key="item.id">
        <div class="bubble-row" :class="item.role">
          <div v-if="item.role === 'assistant'" class="bubble-avatar">
            <el-icon :size="14"><MagicStick /></el-icon>
          </div>

          <div class="bubble" :class="{ 'is-error': item.error }">
            <div class="bubble-text">{{ item.text }}</div>

            <!-- 结构化候选：同名租户、多楼栋同号房间等 -->
            <div v-if="item.suggestions?.length" class="bubble-suggestions">
              <button
                v-for="(s, i) in item.suggestions"
                :key="i"
                type="button"
                class="suggestion"
                @click="useSuggestion(s, item.intent)"
              >
                {{ suggestionText(s) }}
              </button>
            </div>
          </div>
        </div>
      </template>

      <!-- 思考中 -->
      <div v-if="sending" class="bubble-row assistant">
        <div class="bubble-avatar">
          <el-icon :size="15"><MagicStick /></el-icon>
        </div>
        <div class="bubble thinking">
          <span class="dot" /><span class="dot" /><span class="dot" />
          <span class="thinking-text">正在处理</span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="composer">
      <div class="composer-box">
        <textarea
          ref="inputRef"
          v-model="input"
          class="composer-input"
          rows="1"
          :disabled="sending"
          :placeholder="sending ? '正在处理，请稍候…' : '说点什么，例如：今天101水表1250电表3680'"
          enterkeyhint="send"
          @input="resizeInput"
          @keydown="onKeydown"
          @focus="scrollToBottom"
        />
        <button
          type="button"
          class="send-btn"
          :class="{ 'is-ready': canSend }"
          :disabled="!canSend"
          aria-label="发送"
          @click="send()"
        >
          <el-icon :size="18"><Promotion /></el-icon>
        </button>
      </div>
      <p class="composer-hint">AI 生成内容仅供参考，涉及金额请核对后确认</p>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick, Promotion, ArrowRight, Edit, Message, Document, TrendCharts
} from '@element-plus/icons-vue'
import { executeAiCommand, getErrorMessage } from '@/api'

const emit = defineEmits(['done'])

const historyRef = ref(null)
const inputRef = ref(null)
const input = ref('')
const sending = ref(false)
const seq = ref(1)
const messages = ref([])

const hasUserMessage = computed(() => messages.value.some(m => m.role === 'user'))
const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

/** 按时间打招呼，比固定文案更自然。 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了，需要我做什么？'
  if (h < 12) return '早上好，需要我做什么？'
  if (h < 14) return '中午好，需要我做什么？'
  if (h < 18) return '下午好，需要我做什么？'
  return '晚上好，需要我做什么？'
})

/** 能力清单：欢迎态展示，点击直接发送示例指令。 */
const capabilities = [
  { label: '录入水电', example: '今天101水表1250电表3680', icon: Edit },
  { label: '发账单短信', example: '给张三发账单短信', icon: Message },
  { label: '查合同', example: '查一下张三的合同', icon: Document },
  { label: '查用电量', example: '101这个月用了多少电', icon: TrendCharts }
]

function scrollToBottom() {
  nextTick(() => {
    const el = historyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** textarea 跟随内容增长，最多 5 行，避免把对话区挤没。 */
function resizeInput() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  const max = 5 * 21 + 20
  el.style.height = `${Math.min(el.scrollHeight, max)}px`
  el.style.overflowY = el.scrollHeight > max ? 'auto' : 'hidden'
}

/**
 * 手机键盘上按回车就是发送；但中文输入法选词时的回车
 * （isComposing / keyCode 229）必须放行，否则选字就发出去了。
 */
function onKeydown(e) {
  if (e.key !== 'Enter' || e.shiftKey) return
  if (e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  send()
}

/**
 * 把结构化候选（{tenantName, buildingName, roomNumber, contractStatus}）
 * 拼成一句可直接发送的指令。
 */
function suggestionText(s) {
  if (!s) return ''
  if (typeof s === 'string') return s
  const building = s.buildingName && s.buildingName !== '-' ? s.buildingName : ''
  const room = s.roomNumber && s.roomNumber !== '-' ? s.roomNumber : ''
  const tenant = s.tenantName || ''
  const placement = `${building}${room}`
  if (tenant && placement) return `${placement} ${tenant}`
  return tenant || placement || '继续'
}

function useSuggestion(s, intent) {
  const picked = suggestionText(s)
  if (intent === 'SEND_SMS') {
    input.value = `给${picked}发账单短信`
  } else if (intent === 'QUERY_CONTRACT') {
    input.value = `查一下${picked}的合同`
  } else {
    input.value = picked
  }
  nextTick(() => inputRef.value?.focus())
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ id: seq.value++, role: 'user', text: content })
  input.value = ''
  nextTick(resizeInput)
  sending.value = true
  scrollToBottom()

  try {
    const res = await executeAiCommand({ message: content })
    const data = res.data || {}

    if (data.intent === 'NOT_CONFIGURED') {
      messages.value.push({
        id: seq.value++,
        role: 'assistant',
        text: data.message || 'AI 助手尚未配置模型。',
        error: true
      })
      return
    }

    messages.value.push({
      id: seq.value++,
      role: 'assistant',
      intent: data.intent,
      text: data.message || (data.success ? '已完成' : '未能处理该指令'),
      suggestions: Array.isArray(data.suggestions) ? data.suggestions : [],
      error: !data.success && !data.requiresClarification
    })

    // 操作类指令完成后通知外层刷新数据
    if (data.success) {
      emit('done', data)
    }
  } catch (e) {
    messages.value.push({
      id: seq.value++,
      role: 'assistant',
      text: getErrorMessage(e, 'AI 助手请求失败，请稍后重试'),
      error: true
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function reset() {
  messages.value = []
  input.value = ''
  seq.value = 1
  nextTick(resizeInput)
}

function clearMessages() {
  if (messages.value.length === 0) {
    ElMessage.info('当前没有对话内容')
    return
  }
  reset()
  ElMessage.success('已清空对话')
}

onMounted(resizeInput)

defineExpose({ reset, clearMessages })
</script>

<style scoped>
.ai-chat {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--bg-main);
}

/* ---------- 对话区 ---------- */
.chat-history {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: var(--m-gutter);
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

/* 有消息时：内容贴底堆叠，空白留在上方（与豆包等聊天产品一致）
   欢迎态时：交给 .welcome 的 margin:auto 居中，不能用 flex-end */
.chat-history.has-messages {
  justify-content: flex-end;
}

/* ---------- 欢迎态 ---------- */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 4px 2px 8px;
  /* 欢迎态占满可用高度并垂直居中 */
  margin: auto 0;
}

.welcome-logo {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  margin-bottom: 14px;
  box-shadow: 0 6px 18px rgba(35, 84, 72, 0.22);
}

.welcome-title {
  margin: 0 0 7px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: 0.01em;
}

.welcome-desc {
  margin: 0 0 18px;
  font-size: 12.5px;
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 265px;
}

.capability-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.capability {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--m-radius);
  background: var(--bg-surface);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.1s ease;
  -webkit-tap-highlight-color: transparent;
}

.capability:active {
  background: var(--accent-1);
  border-color: var(--accent-2);
  transform: scale(0.985);
}

.cap-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-3);
  background: color-mix(in srgb, var(--accent-3) 12%, transparent);
  flex: 0 0 auto;
}

.cap-text {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.cap-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.3;
}

.cap-example {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cap-arrow {
  color: var(--text-muted);
  flex: 0 0 auto;
  opacity: 0.5;
}

/* ---------- 气泡 ---------- */
.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble-avatar {
  width: 27px;
  height: 27px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  flex: 0 0 auto;
  margin-top: 1px;
}

.bubble {
  max-width: 78%;
  padding: 9px 12px;
  border-radius: 13px;
  font-size: 13.5px;
  line-height: 1.6;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  color: var(--text-main);
}

.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-row.user .bubble {
  background: var(--accent-3);
  border-color: var(--accent-3);
  color: var(--text-inverse);
  border-top-right-radius: 5px;
}

.bubble-row.assistant .bubble {
  border-top-left-radius: 5px;
}

/* 失败消息：左边红条提示 */
.bubble.is-error {
  border-left: 3px solid var(--danger-color);
}

/* 候选建议 */
.bubble-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px solid var(--border-color);
}

.suggestion {
  border: 1px solid var(--accent-2);
  background: var(--accent-1);
  color: var(--accent-3);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  min-height: 30px;
}

.suggestion:active {
  opacity: 0.7;
}

/* ---------- 思考中 ---------- */
.thinking {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 13px 15px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-3);
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.18s; }
.dot:nth-child(3) { animation-delay: 0.36s; }

.thinking-text {
  margin-left: 5px;
  font-size: 12px;
  color: var(--text-muted);
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.85); }
  40% { opacity: 1; transform: scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .dot { animation: none; opacity: 0.6; }
}

/* ---------- 输入区 ---------- */
.composer {
  flex: 0 0 auto;
  padding: 8px var(--m-gutter) calc(8px + var(--m-safe-bottom));
  background: var(--bg-surface);
  border-top: 1px solid var(--border-color);
}

.composer-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 6px 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: 22px;
  background: var(--bg-main);
  transition: border-color 0.15s ease;
}

.composer-box:focus-within {
  border-color: var(--accent-2);
}

.composer-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  resize: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 21px;
  color: var(--text-main);
  padding: 8px 0;
  max-height: calc(5 * 21px + 16px);
  /* 手机上一行高度约 37px，符合触控习惯 */
}

.composer-input::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--bg-surface);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.1s ease;
  -webkit-tap-highlight-color: transparent;
}

.send-btn.is-ready {
  background: var(--accent-3);
  color: var(--text-inverse);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.send-btn.is-ready:active {
  transform: scale(0.9);
}

.composer-hint {
  margin: 6px 0 0;
  text-align: center;
  font-size: 10.5px;
  line-height: 1.4;
  color: var(--text-muted);
  opacity: 0.75;
}
</style>
