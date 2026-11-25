<template>
  <div class="page-remind">
    <div class="page-head">
      <div>
        <h2>催缴与短信</h2>
        <p class="sub">配置逾期催缴规则、短信通道，并查看历史发送记录。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="AlarmClock" :loading="triggering" @click="triggerNow">
          立即触发催缴
        </el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 催缴规则 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>
            <div class="card-head">
              <span>催缴规则</span>
              <el-tag v-if="rules.length" size="small" type="info">{{ rules.length }} 条</el-tag>
            </div>
          </template>

          <el-form :model="newRule" label-width="96px" size="default">
            <el-form-item label="逾期天数">
              <el-input-number v-model="newRule.overdueDays" :min="1" :max="365" controls-position="right" />
              <span class="unit">天后催缴</span>
            </el-form-item>
            <el-form-item label="发送时间">
              <el-time-picker v-model="newRule.sendTime" format="HH:mm" value-format="HH:mm" placeholder="09:00" />
            </el-form-item>
            <el-form-item label="短信模板">
              <el-input
                v-model="newRule.smsTemplate"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="例如：尊敬的{租户姓名}，您房间{房间号}有{逾期笔数}笔账单逾期，请尽快支付。"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingRule" @click="addRule">新增规则</el-button>
            </el-form-item>
          </el-form>

          <el-empty v-if="!rules.length" description="还没有催缴规则" :image-size="72" />
          <el-table v-else :data="rules" size="small">
            <el-table-column prop="overdueDays" label="逾期天数" width="100">
              <template #default="{ row }">第 {{ row.overdueDays }} 天</template>
            </el-table-column>
            <el-table-column prop="sendTime" label="发送时间" width="100" />
            <el-table-column prop="smsTemplate" label="模板" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>

      <!-- 短信通道 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>
            <div class="card-head">
              <span>短信通道</span>
              <el-tag v-if="smsConfig.debugMode" size="small" type="warning">调试模式</el-tag>
              <el-tag v-else size="small" type="success">正式发送</el-tag>
            </div>
          </template>

          <el-form :model="smsConfig" label-width="96px">
            <el-form-item label="AccessKey">
              <el-input v-model="smsConfig.accessKey" placeholder="阿里云 AccessKey ID" autocomplete="off" />
            </el-form-item>
            <el-form-item label="AccessSecret">
              <el-input
                v-model="smsConfig.accessSecret"
                type="password"
                show-password
                placeholder="阿里云 AccessKey Secret"
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item label="短信签名">
              <el-input v-model="smsConfig.signature" placeholder="例如：房屋租赁" />
            </el-form-item>
            <el-form-item label="模板 CODE">
              <el-input v-model="smsConfig.templateCode" placeholder="SMS_xxxxxx" />
            </el-form-item>
            <el-form-item label="调试模式">
              <el-switch v-model="smsConfig.debugMode" />
              <span class="unit">开启后只写日志，不真正发短信</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingSms" @click="saveSms">保存配置</el-button>
              <el-button :loading="testing" @click="testSend">测试发送</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 催缴记录 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <div class="head-left">
            <span>短信记录</span>
            <el-radio-group v-model="logScene" size="small" @change="loadLogs">
              <el-radio-button label="">催缴</el-radio-button>
              <el-radio-button label="ALL">全部短信</el-radio-button>
            </el-radio-group>
          </div>
          <el-button size="small" :icon="Download" :disabled="!logs.length" @click="exportCsv">
            导出 CSV
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="logs" size="small" stripe>
        <template #empty>
          <el-empty :description="logScene === 'ALL' ? '还没有短信记录' : '还没有催缴记录'" :image-size="72" />
        </template>
        <el-table-column prop="createdAt" label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="场景" width="110" v-if="logScene === 'ALL'">
          <template #default="{ row }">
            <el-tag size="small" :type="sceneTag(row.scene)">{{ sceneText(row.scene) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" min-width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="roomNumber" label="房间" width="100" />
        <el-table-column prop="overdueDays" label="逾期" width="80">
          <template #default="{ row }">{{ row.overdueDays }} 天</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="smsStatusTag(row.status)" size="small">{{ smsStatusText(row.status) }}</el-tag>
            <el-tag v-if="row.isDebug" size="small" type="info" class="debug-tag">调试</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlarmClock, Download, Refresh } from '@element-plus/icons-vue'
import { getErrorMessage } from '@/api'
import {
  getRemindRules,
  saveRemindRule,
  getSmsConfig,
  saveSmsConfig,
  getRemindLogs,
  testSendSms,
  triggerRemindNow
} from '@/api/remind'
import { formatDateTime, formatMoney } from '@/utils/format'
import { smsStatusText, smsStatusTag } from '@/utils/labels'

const loading = ref(false)
const savingRule = ref(false)
const savingSms = ref(false)
const testing = ref(false)
const triggering = ref(false)

const rules = ref([])
const logs = ref([])
// '' = 只看催缴；'ALL' = 全部短信（含验证码）
const logScene = ref('')

const newRule = reactive({
  overdueDays: 1,
  sendTime: '09:00',
  smsTemplate: ''
})

const smsConfig = reactive({
  accessKey: '',
  accessSecret: '',
  signature: '',
  templateCode: '',
  debugMode: true
})

/** 三个列表各自独立容错：任何一个失败不影响其余数据展示。 */
async function load() {
  loading.value = true
  try {
    const [ruleRes, configRes, logRes] = await Promise.allSettled([
      getRemindRules(),
      getSmsConfig(),
      getRemindLogs(logScene.value ? { scene: logScene.value } : undefined)
    ])

    if (ruleRes.status === 'fulfilled') {
      rules.value = ruleRes.value.data || []
    } else {
      ElMessage.warning(getErrorMessage(ruleRes.reason, '催缴规则加载失败'))
    }

    if (configRes.status === 'fulfilled') {
      const list = configRes.value.data || []
      if (list.length) Object.assign(smsConfig, list[0])
    } else {
      ElMessage.warning(getErrorMessage(configRes.reason, '短信配置加载失败'))
    }

    if (logRes.status === 'fulfilled') {
      logs.value = logRes.value.data || []
    } else {
      ElMessage.warning(getErrorMessage(logRes.reason, '短信记录加载失败'))
    }
  } finally {
    loading.value = false
  }
}

/** 仅重新拉取短信记录，切换场景时不用重载规则和配置。 */
async function loadLogs() {
  loading.value = true
  try {
    const res = await getRemindLogs(logScene.value ? { scene: logScene.value } : undefined)
    logs.value = res.data || []
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '短信记录加载失败'))
  } finally {
    loading.value = false
  }
}

/** 场景中文名：REMIND / LOGIN_CODE / UPDATE_PHONE / TEST。 */
const SCENE_LABELS = {
  REMIND: '催缴',
  LOGIN_CODE: '登录验证码',
  UPDATE_PHONE: '改手机号',
  TEST: '测试'
}
const SCENE_TAGS = {
  REMIND: 'warning',
  LOGIN_CODE: 'info',
  UPDATE_PHONE: 'info',
  TEST: 'success'
}
const sceneText = s => SCENE_LABELS[s] || s || '催缴'
const sceneTag = s => SCENE_TAGS[s] || 'info'

async function addRule() {
  if (!newRule.smsTemplate.trim()) {
    ElMessage.warning('请填写短信模板')
    return
  }
  savingRule.value = true
  try {
    await saveRemindRule({ ...newRule })
    ElMessage.success('规则已新增')
    newRule.smsTemplate = ''
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '新增规则失败'))
  } finally {
    savingRule.value = false
  }
}

async function saveSms() {
  savingSms.value = true
  try {
    await saveSmsConfig({ ...smsConfig })
    ElMessage.success('配置已保存')
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '保存配置失败'))
  } finally {
    savingSms.value = false
  }
}

async function testSend() {
  testing.value = true
  try {
    const res = await testSendSms({
      tenantName: '测试用户',
      phone: '13800000000',
      roomNumber: 'A-101',
      overdueDays: 1,
      billIds: JSON.stringify([1]),
      totalAmount: 100.0,
      content: '测试催缴短信'
    })
    const isDebug = !!res?.data?.isDebug
    ElMessage.success(isDebug ? '测试短信已模拟发送（调试模式）' : '测试短信已发送')
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '发送失败'))
  } finally {
    testing.value = false
  }
}

async function triggerNow() {
  try {
    await ElMessageBox.confirm(
      '将为所有符合条件的逾期账单立即发送催缴短信，是否继续？',
      '立即触发催缴',
      { type: 'warning', confirmButtonText: '立即触发', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }

  triggering.value = true
  try {
    const res = await triggerRemindNow()
    const data = res?.data || {}
    const lines = [`发送数量：${data.sentCount ?? 0}`]
    if (data.latestMessageTitle) lines.push(`最新消息：${data.latestMessageTitle}`)
    if (data.latestMessageContent) lines.push(data.latestMessageContent)
    ElMessageBox.alert(lines.join('\n'), '催缴已触发', {
      confirmButtonText: '知道了',
      type: 'success'
    })
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '触发催缴失败'))
  } finally {
    triggering.value = false
  }
}

function exportCsv() {
  if (!logs.value.length) {
    ElMessage.warning('暂无记录可导出')
    return
  }
  const headers = ['时间', '场景', '租户', '手机号', '房间', '逾期天数', '金额', '状态', '调试']
  const rows = logs.value.map(r => [
    formatDateTime(r.createdAt),
    sceneText(r.scene),
    r.tenantName,
    r.phone,
    r.roomNumber,
    r.overdueDays,
    r.totalAmount,
    smsStatusText(r.status),
    r.isDebug ? '是' : '否'
  ])
  const csv = [headers, ...rows]
    .map(row => row.map(c => `"${String(c ?? '')}"`).join(','))
    .join('\n')
  // 加 BOM，避免 Excel 打开中文乱码
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `催缴记录_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<style scoped>
.page-remind {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 18px;
  color: var(--text-main);
}

.sub {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.head-actions {
  display: flex;
  gap: 8px;
}

.block {
  margin-bottom: 0;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
}

.unit {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.debug-tag {
  margin-left: 6px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
