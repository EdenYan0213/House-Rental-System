import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { batchPayments, getErrorMessage } from '@/api'
import { remainOf, formatMoneyValue } from '@/utils/bills'

/**
 * 账单批量收款。
 *
 * <p>从 1077 行的 `Bills.vue` 抽出。这里是「选中多张 → 一次性登记收款」的完整流程：
 * 校验待收金额、提交、按失败明细分别提示、清空选择并刷新列表。
 *
 * @param tableRef  el-table 的 ref，用于成功后清空勾选
 * @param onSuccess 收款成功后的回调（刷新列表与汇总）
 */
export function useBatchPayment(tableRef, onSuccess) {
  const visible = ref(false)
  const paying = ref(false)
  const selectedBills = ref([])
  const form = reactive({ paymentMethod: 'CASH', remark: '批量收款' })

  /** 所选账单的待收合计。 */
  const selectedRemain = computed(() =>
    selectedBills.value.reduce((sum, b) => sum + remainOf(b), 0)
  )

  function onSelectionChange(rows) {
    selectedBills.value = rows || []
  }

  function open() {
    if (selectedBills.value.length === 0) {
      ElMessage.warning('请先选择要收款的账单')
      return
    }
    if (selectedRemain.value <= 0) {
      ElMessage.warning('所选账单没有待收金额')
      return
    }
    visible.value = true
  }

  async function submit() {
    paying.value = true
    try {
      const res = await batchPayments({
        billIds: selectedBills.value.map(b => b.id),
        paymentMethod: form.paymentMethod,
        remark: form.remark
      })
      const data = res.data || {}

      if (data.failCount > 0) {
        // 部分失败：把每张账单的原因列出来，方便逐个处理
        const detail = (data.failures || [])
          .map(f => `账单#${f.billId}：${f.reason}`)
          .join('\n')
        ElMessageBox.alert(detail, `成功 ${data.successCount} 张，失败 ${data.failCount} 张`, {
          type: 'warning'
        })
      } else {
        ElMessage.success(
          `批量收款完成，共 ${data.successCount} 张，合计 ¥${formatMoneyValue(data.totalPaid)}`
        )
      }

      visible.value = false
      selectedBills.value = []
      tableRef.value?.clearSelection?.()
      await onSuccess?.()
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '批量收款失败'))
    } finally {
      paying.value = false
    }
  }

  return {
    visible,
    paying,
    selectedBills,
    form,
    selectedRemain,
    onSelectionChange,
    open,
    submit
  }
}
