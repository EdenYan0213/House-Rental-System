<template>
  <el-drawer
    v-model="visibleRef"
    title="AI助手"
    direction="rtl"
    size="420px"
    :destroy-on-close="false"
  >
    <div class="ai-wrap">
      <div class="history" ref="historyRef">
        <div v-for="item in messages" :key="item.id" class="bubble-row" :class="item.role">
          <div class="bubble">
            <div class="text" style="white-space: pre-wrap">{{ item.text }}</div>
            <a v-if="item.link" class="detail-link" :href="item.link">查看详情</a>
          </div>
        </div>
      </div>

      <el-input
        v-model="input"
        type="textarea"
        :rows="4"
        placeholder="请输入指令，例如：今天A栋101水表1250.5电表3680，A栋102水表1312.3电表3725"
        :disabled="sending"
        @keydown.ctrl.enter.prevent="send"
      />

      <div class="ops">
        <el-button @click="clearMessages" :disabled="sending">清空对话</el-button>
        <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">发送</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { executeAiCommand, getErrorMessage } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const visibleRef = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const historyRef = ref(null)
const input = ref('')
const sending = ref(false)
const sequence = ref(1)
const messages = ref([
  {
    id: 0,
    role: 'assistant',
    text: '你好，我可以帮你录入水电（支持一次录入多个房间）、发送账单短信、查询合同和查询用电量。'
  }
])

const canSend = computed(() => input.value.trim().length > 0)

watch(() => messages.value.length, async () => {
  await nextTick()
  if (historyRef.value) {
    historyRef.value.scrollTop = historyRef.value.scrollHeight
  }
})

function clearMessages() {
  messages.value = [
    {
      id: 0,
      role: 'assistant',
      text: '会话已清空。你可以继续输入新的指令。'
    }
  ]
}

async function send() {
  const content = input.value.trim()
  if (!content || sending.value) return

  messages.value.push({ id: sequence.value++, role: 'user', text: content })
  input.value = ''
  sending.value = true

  try {
    const res = await executeAiCommand({ message: content })
    const data = res.data || {}
    messages.value.push({
      id: sequence.value++,
      role: 'assistant',
      text: data.message || 'AI助手未返回内容',
      link: data.detailLink || ''
    })
  } catch (error) {
    const msg = getErrorMessage(error, '请求失败，请稍后重试')
    messages.value.push({ id: sequence.value++, role: 'assistant', text: msg })
    ElMessage.error(msg)
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.ai-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.bubble-row {
  display: flex;
  margin-bottom: 10px;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 90%;
  border-radius: 10px;
  padding: 10px 12px;
  border: 1px solid #dce5ff;
  background: #f4f7ff;
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

.ops {
  display: flex;
  justify-content: space-between;
}
</style>
