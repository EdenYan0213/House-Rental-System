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

    <div class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        :disabled="sending"
        placeholder="输入指令，例如：给张三发账单短信"
      />
      <el-button class="send-btn" type="primary" :loading="sending" :disabled="!input.trim()" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { executeAiCommand } from '@/api'

const router = useRouter()
const historyRef = ref(null)
const input = ref('')
const sending = ref(false)
const seq = ref(1)

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
    const msg = error?.response?.data?.message || '请求失败，请稍后重试'
    messages.value.push({ id: seq.value++, role: 'assistant', text: msg })
    ElMessage.error(msg)
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.mobile-ai-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f2f7ff 0%, #ffffff 40%);
}

.head {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #d9e5ff;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

.title {
  font-size: 18px;
  font-weight: 700;
  color: #1a3365;
}

.back-btn,
.clear-btn {
  border: none;
  background: #eaf0ff;
  color: #1a4cc8;
  border-radius: 8px;
  height: 32px;
  padding: 0 12px;
}

.history {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.bubble-row {
  display: flex;
  margin-bottom: 10px;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 92%;
  border-radius: 12px;
  border: 1px solid #dce5ff;
  background: #f4f7ff;
  padding: 10px 12px;
}

.bubble-row.user .bubble {
  background: #1f6bff;
  border-color: #1f6bff;
  color: #fff;
}

.detail-link {
  display: inline-block;
  margin-top: 8px;
  color: inherit;
  text-decoration: underline;
}

.composer {
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid #d9e5ff;
  background: #fff;
}

.send-btn {
  width: 100%;
  margin-top: 10px;
  min-height: 40px;
}
</style>
