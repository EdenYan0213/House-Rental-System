<template>
  <el-dialog
    v-model="visible"
    title="办理退租"
    width="680px"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <div v-loading="loading">
      <!-- 合同概览 -->
      <div class="contract-head">
        <div class="head-main">
          <span class="room">{{ check.roomNumber || '-' }}</span>
          <span class="building">{{ check.buildingName || '' }}</span>
        </div>
        <div class="head-sub">
          合同 {{ check.contractNo || '-' }} · 租户 {{ check.tenantName || '-' }}
        </div>
      </div>

      <!-- 预检告警 -->
      <el-alert
        v-if="check.unpaidCount > 0"
        type="warning"
        :closable="false"
        show-icon
        class="block-alert"
      >
        <template #title>
          该合同有 {{ check.unpaidCount }} 张未结清账单，合计 ¥{{ money(check.unpaidAmount) }}
        </template>
        <div class="unpaid-list">
          <div v-for="b in check.unpaidBills" :key="b.id" class="unpaid-item">
            <span>{{ b.billMonth }}</span>
            <span>应收 ¥{{ money(b.totalAmount) }}</span>
            <span class="unpaid-remain">待收 ¥{{ money(b.remainAmount) }}</span>
          </div>
        </div>
        <el-checkbox v-model="form.force" class="force-check">
          我已确认，仍要强制退租（欠费将保留在账单中，后续可继续催收）
        </el-checkbox>
      </el-alert>

      <el-alert
        v-for="(w, i) in check.warnings || []"
        :key="i"
        v-show="!String(w).includes('未结清账单')"
        type="info"
        :closable="false"
        show-icon
        class="block-alert"
        :title="w"
      />

      <el-divider content-position="left">退租信息</el-divider>

      <el-form :model="form" label-width="110px">
        <el-form-item label="退租日期" required>
          <el-date-picker
            v-model="form.terminateDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="退租原因">
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="可选，例如：租期届满不再续租" />
        </el-form-item>
      </el-form>

      <el-divider content-position="left">押金结算</el-divider>

      <el-descriptions :column="3" border size="small" class="money-desc">
        <el-descriptions-item label="已收押金">¥{{ money(check.receivedDeposit) }}</el-descriptions-item>
        <el-descriptions-item label="已退押金">¥{{ money(check.refundedDeposit) }}</el-descriptions-item>
        <el-descriptions-item label="可退余额">
          <span class="balance">¥{{ money(check.depositBalance) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" label-width="110px" style="margin-top: 12px">
        <el-form-item label="退还押金">
          <el-switch v-model="form.refundDeposit" :disabled="check.depositBalance <= 0" />
          <span v-if="check.depositBalance <= 0" class="hint">（无可退押金）</span>
        </el-form-item>
        <template v-if="form.refundDeposit">
          <el-form-item label="扣款金额">
            <el-input-number v-model="form.deductAmount" :min="0" :max="check.depositBalance" :step="50" :precision="2" />
            <span class="hint">房屋损坏、清洁费等，会写入押金备注</span>
          </el-form-item>
          <el-form-item v-if="form.deductAmount > 0" label="扣款原因">
            <el-input v-model="form.deductReason" placeholder="例如：墙面破损修补" />
          </el-form-item>
          <el-form-item label="实退金额">
            <span class="refund-amount">¥{{ money(actualRefund) }}</span>
          </el-form-item>
        </template>
      </el-form>

      <el-divider content-position="left">租户处理</el-divider>

      <el-form :model="form" label-width="110px">
        <el-form-item label="加入黑名单">
          <el-switch v-model="form.addToBlacklist" />
        </el-form-item>
        <el-form-item v-if="form.addToBlacklist" label="拉黑原因">
          <el-select v-model="form.blacklistReason" style="width: 100%" allow-create filterable>
            <el-option label="欠费" value="欠费" />
            <el-option label="扰民" value="扰民" />
            <el-option label="房屋损坏" value="房屋损坏" />
            <el-option label="违约" value="违约" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 执行后会做什么 -->
      <div class="preview">
        <div class="preview-title">确认后系统将执行：</div>
        <ul class="preview-list">
          <li v-if="form.refundDeposit && actualRefund > 0">
            登记退还押金 <b>¥{{ money(actualRefund) }}</b>
            <span v-if="form.deductAmount > 0">（含扣款 ¥{{ money(form.deductAmount) }}）</span>
          </li>
          <li>将合同 <b>{{ check.contractNo }}</b> 置为「已解约」</li>
          <li>将房间 <b>{{ check.roomNumber }}</b> 置为「空闲」</li>
          <li v-if="form.addToBlacklist">将租户 <b>{{ check.tenantName }}</b> 加入黑名单</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="danger"
        :loading="submitting"
        :disabled="loading || (check.unpaidCount > 0 && !form.force)"
        @click="submit"
      >
        确认办理退租
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkMoveOut, submitMoveOut, getErrorMessage } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  contractId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'done'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const loading = ref(false)
const submitting = ref(false)
const check = ref({})

const form = ref({
  terminateDate: new Date().toISOString().slice(0, 10),
  reason: '',
  refundDeposit: true,
  deductAmount: 0,
  deductReason: '',
  addToBlacklist: false,
  blacklistReason: '欠费',
  force: false
})

const actualRefund = computed(() => {
  const balance = Number(check.value.depositBalance || 0)
  const deduct = Number(form.value.deductAmount || 0)
  return Math.max(balance - deduct, 0)
})

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function onOpen() {
  if (!props.contractId) return
  loading.value = true
  form.value.force = false
  form.value.deductAmount = 0
  form.value.refundDeposit = true
  try {
    const res = await checkMoveOut(props.contractId)
    check.value = res.data || {}
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '退租预检失败'))
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.value.terminateDate) {
    return ElMessage.warning('请选择退租日期')
  }
  if (check.value.unpaidCount > 0 && !form.value.force) {
    return ElMessage.warning('存在未结清账单，请先收款或勾选强制退租')
  }

  const tips = []
  if (form.value.refundDeposit && actualRefund.value > 0) {
    tips.push(`退还押金 ¥${money(actualRefund.value)}`)
  }
  tips.push('合同解约', '房间置为空闲')
  if (form.value.addToBlacklist) tips.push('租户加入黑名单')

  try {
    await ElMessageBox.confirm(
      `即将执行：${tips.join('、')}。退租后可在 30 分钟内撤销。`,
      '确认办理退租',
      { type: 'warning', confirmButtonText: '确认办理', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = await submitMoveOut(props.contractId, {
      terminateDate: form.value.terminateDate,
      reason: form.value.reason,
      refundDeposit: form.value.refundDeposit,
      refundAmount: form.value.refundDeposit ? actualRefund.value : 0,
      deductAmount: form.value.deductAmount,
      deductReason: form.value.deductReason,
      addToBlacklist: form.value.addToBlacklist,
      blacklistReason: form.value.blacklistReason,
      force: form.value.force
    })
    ElMessage.success('退租办理完成')
    visible.value = false
    emit('done', res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '退租办理失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.contract-head {
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  margin-bottom: 14px;
}

.head-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.room {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
}

.building {
  font-size: 13px;
  color: var(--text-muted);
}

.head-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.block-alert {
  margin-bottom: 12px;
}

.unpaid-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.unpaid-item {
  display: flex;
  gap: 16px;
}

.unpaid-remain {
  font-weight: 600;
  color: var(--danger-color);
}

.force-check {
  margin-top: 10px;
}

.money-desc {
  margin-bottom: 4px;
}

.balance {
  font-weight: 700;
  color: var(--success-color);
}

.refund-amount {
  font-size: 18px;
  font-weight: 700;
  color: var(--danger-color);
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.preview {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px dashed var(--border-color);
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 8px;
}

.preview-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-muted);
}
</style>
