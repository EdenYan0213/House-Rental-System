<template>
  <div class="ai-chat">
    <!-- 对话区 -->
    <div ref="historyRef" class="chat-history">
      <!-- 欢迎态：还没有任何用户消息时，展示能力介绍 -->
      <div v-if="!hasUserMessage" class="welcome">
        <div class="welcome-logo">
          <el-icon :size="26"><MagicStick /></el-icon>
        </div>
        <h2 class="welcome-title">有什么可以帮你？</h2>
        <p class="welcome-desc">
          用一句话描述要做的事，我会调用系统功能完成，并记录到操作日志。
        </p>

        <div class="capability-list">
          <div
            v-for="cap in capabilities"
            :key="cap.label"
            class="capability"
          >
            <span class="cap-icon">
              <el-icon :size="16"><component :is="cap.icon" /></el-icon>
            </span>
            <div class="cap-text">
              <div class="cap-label">{{ cap.label }}</div>
              <div class="cap-example">{{ cap.example }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 消息气泡 -->
      <template v-for="item in messages" :key="item.id">
        <div class="bubble-row" :class="item.role">
          <div v-if="item.role === 'assistant'" class="bubble-avatar">
            <el-icon :size="15"><MagicStick /></el-icon>
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
        </div>
      </div>
    </div>

    <!-- 快捷指令 -->
    <div v-if="showPrompts" class="prompt-bar">
      <button
        v-for="p in quickPrompts"
        :key="p"
        type="button"
        class="prompt-chip"
        :disabled="sending"
        @click="send(p)"
      >{{ p }}</button>
    </div>

    <!-- 输入区 -->
    <div class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 4 }"
        resize="none"
        :disabled="sending"
        placeholder="说点什么，例如：今天101水表1250电表3680"
        class="composer-input"
        @keydown.enter.exact.prevent="send()"
      />
      <button
        type="button"
        class="send-btn"
        :disabled="!canSend"
        @click="send()"
      >
        <el-icon :size="18"><Promotion /></el-icon>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick, Promotion, Edit, Message, Document, TrendCharts
} from '@element-plus/icons-vue'
import { executeAiCommand, getErrorMessage } from '@/api'

const emit = defineEmits(['done'])

const historyRef = ref(null)
const input = ref('')
const sending = ref(false)
const seq = ref(1)
const messages = ref([])

const hasUserMessage = computed(() => messages.value.some(m => m.role === 'user'))
const showPrompts = computed(() => !hasUserMessage.value)
const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

/** 能力清单：欢迎态展示，让用户知道能说什么。 */
const capabilities = [
  { label: '录入水电', example: '今天A栋101水表1250电表3680', icon: Edit },
  { label: '发账单短信', example: '给张三发账单短信', icon: Message },
  { label: '查合同', example: '查一下张三的合同', icon: Document },
  { label: '查用电量', example: '101这个月用了多少电', icon: TrendCharts }
]

const quickPrompts = capabilities.map(c => c.example)

function scrollToBottom() {
  nextTick(() => {
    const el = historyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
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
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ id: seq.value++, role: 'user', text: content })
  input.value = ''
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
}

function clearMessages() {
  if (messages.value.length === 0) {
    ElMessage.info('当前没有对话内容')
    return
  }
  reset()
  ElMessage.success('已清空对话')
}

defineExpose({ reset, clearMessages })
</script>

<style scoped>
.ai-chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--bg-main);
}

/* ---------- 对话区 ---------- */
.chat-history {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: var(--m-gutter);
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

/* 欢迎态 */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 16px 4px 8px;
  /* 内容不多时在对话区内垂直居中，避免中间出现大片空白 */
  margin: auto 0;
}

.welcome-logo {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  background: linear-gradient(135deg, var(--accent-3), var(--accent-4));
  margin-bottom: 14px;
}

.welcome-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.welcome-desc {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 280px;
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
  padding: 11px 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-surface);
  text-align: left;
}

.cap-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-3);
  background: color-mix(in srgb, var(--accent-3) 12%, transparent);
  flex: 0 0 auto;
}

.cap-text {
  min-width: 0;
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
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  background: var(--accent-3);
  flex: 0 0 auto;
}

.bubble {
  max-width: 78%;
  padding: 10px 13px;
  border-radius: 12px;
  font-size: 13px;
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
  border-top-right-radius: 4px;
}

.bubble-row.assistant .bubble {
  border-top-left-radius: 4px;
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
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.suggestion {
  border: 1px solid var(--accent-2);
  background: var(--accent-1);
  color: var(--accent-3);
  border-radius: 999px;
  padding: 5px 11px;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
}

.suggestion:active {
  opacity: 0.75;
}

/* 思考中 */
.thinking {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 15px 16px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.25; }
  40% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .dot { animation: none; opacity: 0.6; }
}

/* ---------- 快捷指令 ---------- */
.prompt-bar {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px var(--m-gutter);
  border-top: 1px solid var(--border-color);
  background: var(--bg-surface);
}

.prompt-bar::-webkit-scrollbar {
  display: none;
}

.prompt-chip {
  flex: 0 0 auto;
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-main);
  border-radius: 999px;
  padding: 7px 13px;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}

.prompt-chip:active:not(:disabled) {
  background: var(--accent-1);
  border-color: var(--accent-2);
}

/* ---------- 输入区 ---------- */
.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px var(--m-gutter) calc(10px + var(--m-safe-bottom));
  background: var(--bg-surface);
  border-top: 1px solid var(--border-color);
}

.composer-input {
  flex: 1;
  min-width: 0;
}

.composer-input :deep(.el-textarea__inner) {
  border-radius: 20px;
  padding: 9px 14px;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  box-shadow: none;
  background: var(--bg-main);
}

.composer-input :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--accent-3) inset;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: var(--accent-3);
  color: var(--text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: 0 0 auto;
  transition: opacity 0.15s ease, transform 0.12s ease;
}

.send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.send-btn:active:not(:disabled) {
  transform: scale(0.94);
}
</style>
