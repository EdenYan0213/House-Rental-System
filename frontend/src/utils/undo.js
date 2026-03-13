import { h } from 'vue'
import { ElButton, ElMessage, ElNotification } from 'element-plus'
import { recoverOperationSnapshot } from '../api'

export function showUndoToast(payload, onRecovered) {
  const snapshotId = payload?.undoSnapshotId
  if (!snapshotId) {
    return
  }

  const expireAt = payload?.undoExpireAt
  const title = payload?.undoTip || '操作成功，可在短时间内撤销'

  ElNotification({
    title,
    duration: 5000,
    position: 'top-right',
    message: h('div', { style: 'display:flex;align-items:center;gap:10px;' }, [
      h('span', { style: 'color:#606266;font-size:12px;' }, expireAt ? `有效期至 ${String(expireAt).replace('T', ' ').slice(0, 19)}` : '可立即撤销'),
      h(ElButton, {
        type: 'warning',
        size: 'small',
        onClick: async () => {
          try {
            await recoverOperationSnapshot(snapshotId)
            ElMessage.success('撤销成功')
            if (typeof onRecovered === 'function') {
              await onRecovered()
            }
          } catch (e) {
            ElMessage.error(e?.response?.data || e?.message || '撤销失败')
          }
        }
      }, () => '撤销')
    ])
  })
}
