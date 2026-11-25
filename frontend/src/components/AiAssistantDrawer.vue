<template>
  <el-drawer
    v-model="visible"
    title="AI 助手"
    direction="rtl"
    :size="480"
    :destroy-on-close="false"
    class="ai-drawer"
  >
    <div class="ai-wrap">
      <!-- 对话历史 -->
      <div ref="historyRef" class="ai-history">
        <div v-for="item in messages" :key="item.id" class="bubble-row" :class="item.role">
          <div class="bubble">
            <div class="text">{{ item.text }}</div>
            <div v-if="item.suggestions?.length" class="suggestions">
              <el-tag
                v-for="(s, i) in item.suggestions"
                :key="i"
                size="small"
                effect="plain"
                class="suggestion-tag"
                @click="useSuggestion(s, item.intent)"
              >{{ suggestionText(s) }}</el-tag>
            </div>
          </div>
        </div>

        <div v-if="sending" class="bubble-row assistant">
          <div class="bubble thinking">
            <span class="dot" /><span class="dot" /><span class="dot" />
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="ai-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :disabled="sending"
          placeholder="例如：今天A栋101水表1250.5电表3680（Ctrl/⌘ + Enter 发送）"
          @keydown.enter.ctrl.prevent="send"
          @keydown.enter.meta.prevent="send"
        />
        <div class="ai-ops">
          <span class="hint">支持：录入水电 · 发账单短信 · 查合同 · 查用电量</span>
          <div class="btns">
            <el-button size="small" :disabled="sending" @click="clearMessages">清空</el-button>
            <el-button type="primary" size="small" :loading="sending" :disabled="!canSend" @click="send">
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { executeAiCommand, getErrorMessage } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const historyRef = ref(null)
const input = ref('')
const sending = ref(false)
const seq = ref(1)

const GREETING = '你好，我可以帮你：\n• 录入水电（支持一次多个房间）\n• 发送账单短信\n• 查询合同\n• 查询某月用电量\n\n直接说人话即可，例如「今天A栋101水表1250.5电表3680」。'

const messages = ref([
  { id: 0, role: 'assistant', text: GREETING }
])

const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

function scrollToBottom() {
  nextTick(() => {
    const el = historyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/**
 * 把结构化候选（{tenantName, buildingName, roomNumber, contractStatus}）拼成
 * 一句可直接改写的输入，避免用户对着「A栋101（合同进行中）」手敲。
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
  // 按意图补全成完整说法，用户点一下就能发送
  if (intent === 'SEND_SMS') {
    input.value = `给${picked}发账单短信`
  } else if (intent === 'QUERY_CONTRACT') {
    input.value = `查一下${picked}的合同`
  } else {
    input.value = picked
  }
}

function clearMessages() {
  messages.value = [{ id: 0, role: 'assistant', text: GREETING }]
  seq.value = 1
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return

  messages.value.push({ id: seq.value++, role: 'user', text })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    const res = await executeAiCommand({ message: text })
    const data = res.data || {}

    // 未配置模型：给出配置指引而不是笼统失败
    if (data.intent === 'NOT_CONFIGURED') {
      messages.value.push({
        id: seq.value++,
        role: 'assistant',
        text: data.message || 'AI 助手尚未配置模型。'
      })
      return
    }

    const reply = {
      id: seq.value++,
      role: 'assistant',
      intent: data.intent,
      text: data.message || (data.success ? '已完成' : '未能处理该指令'),
      suggestions: Array.isArray(data.suggestions) ? data.suggestions : []
    }
    messages.value.push(reply)
  } catch (e) {
    messages.value.push({
      id: seq.value++,
      role: 'assistant',
      text: getErrorMessage(e, 'AI 助手请求失败，请稍后重试')
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

// 打开时滚到底部
watch(visible, v => {
  if (v) scrollToBottom()
})

defineExpose({ open: () => { visible.value = true } })
</script>

<style scoped>
.ai-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 12px;
}

.ai-history {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.bubble-row {
  display: flex;
  margin-bottom: 12px;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 85%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
  background: var(--el-fill-color-light);
  color: var(--text-main);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-row.user .bubble {
  background: var(--accent-color);
  color: #fff;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.suggestion-tag {
  cursor: pointer;
}

/* 思考中的三点动画 */
.thinking {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 14px 16px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

.ai-input {
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
}

.ai-ops {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
}

.hint {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
}

.btns {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
