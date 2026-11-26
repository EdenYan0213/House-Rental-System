<template>
  <div class="mobile-ai-page">
    <header class="head">
      <button class="back-btn" @click="goBack">返回</button>
      <div class="title">AI助手</div>
      <button class="clear-btn" @click="clearMessages">清空</button>
    </header>

    <div class="history" ref="historyRef">
      <div v-for="item in messages" :key="item.id" class="bubble-row" :class="item.role">
        <div class="bubble">
          <div class="text" style="white-space: pre-wrap">{{ item.text }}</div>
          <a v-if="item.link" class="detail-link" :href="item.link">查看详情</a>
        </div>
      </div>
    </div>

    <!-- 快捷指令：点一下就填入输入框，移动端打字成本高 -->
    <div class="quick-prompts">
      <button
        v-for="p in quickPrompts"
        :key="p"
        type="button"
        class="prompt-chip"
        :disabled="sending"
        @click="input = p"
      >{{ p }}</button>
    </div>

    <div class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        :disabled="sending"
        placeholder="输入指令，例如：给张三发账单短信"
        class="composer-input"
      />
      <el-button
        class="send-btn"
        type="primary"
        :icon="Promotion"
        :loading="sending"
        :disabled="!input.trim()"
        @click="send"
      >发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion, ArrowLeft } from '@element-plus/icons-vue'
import { executeAiCommand, getErrorMessage } from '@/api'

const router = useRouter()
const historyRef = ref(null)
const input = ref('')
const sending = ref(false)
const seq = ref(1)

const quickPrompts = [
  '今天101水表1250电表3680',
  '给张三发账单短信',
  '查一下张三的合同',
  '101这个月用了多少电'
]

const messages = ref([
  { id: 0, role: 'assistant', text: '你好，我可以帮你处理常用租赁管理指令。' }
])

watch(() => messages.value.length, async () => {
  await nextTick()
  if (historyRef.value) {
    historyRef.value.scrollTop = historyRef.value.scrollHeight
  }
})

function goBack() {
  router.back()
}

function clearMessages() {
  messages.value = [{ id: 0, role: 'assistant', text: '会话已清空。' }]
}

async function send() {
  const content = input.value.trim()
  if (!content || sending.value) return

  messages.value.push({ id: seq.value++, role: 'user', text: content })
  input.value = ''
  sending.value = true

  try {
    const res = await executeAiCommand({ message: content })
    const data = res.data || {}
    messages.value.push({
      id: seq.value++,
      role: 'assistant',
      text: data.message || 'AI助手未返回内容',
      link: data.detailLink || ''
    })
  } catch (error) {
    const msg = getErrorMessage(error, '请求失败，请稍后重试')
    messages.value.push({ id: seq.value++, role: 'assistant', text: msg })
    ElMessage.error(msg)
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.mobile-ai-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  max-width: 520px;
  margin: 0 auto;
  background: var(--bg-main);
  box-sizing: border-box;
}

/* 顶栏 */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px var(--m-gutter);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 5;
}

.back-btn,
.clear-btn {
  border: none;
  background: transparent;
  color: var(--accent-3);
  font-size: 13px;
  font-family: inherit;
  padding: 6px 4px;
  cursor: pointer;
  min-height: 34px;
}

.clear-btn {
  color: var(--text-muted);
}

.title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

/* 对话历史 */
.history {
  flex: 1;
  overflow-y: auto;
  padding: var(--m-gutter);
  padding-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bubble-row {
  display: flex;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 82%;
  padding: 10px 13px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  word-break: break-word;
}

.bubble-row.user .bubble {
  background: var(--accent-3);
  border-color: var(--accent-3);
  color: var(--text-inverse);
}

.detail-link {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--accent-3);
}

/* 快捷指令 */
.quick-prompts {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px var(--m-gutter);
  background: var(--bg-surface);
  border-top: 1px solid var(--border-color);
}

.quick-prompts::-webkit-scrollbar {
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
  -webkit-tap-highlight-color: transparent;
}

.prompt-chip:active:not(:disabled) {
  background: var(--accent-1);
  border-color: var(--accent-2);
}

/* 输入区 */
.composer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 10px var(--m-gutter) calc(10px + var(--m-safe-bottom));
  background: var(--bg-surface);
  border-top: 1px solid var(--border-color);
}

.composer-input {
  flex: 1;
  min-width: 0;
}

.composer-input :deep(.el-textarea__inner) {
  border-radius: 10px;
  font-family: inherit;
  font-size: 13px;
}

.send-btn {
  flex: 0 0 auto;
  min-height: 40px;
  border-radius: 10px;
  padding: 0 16px;
}
</style>
