import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoomQrPng, exportRoomQrsPdf, getErrorMessage } from '@/api'

/**
 * 房间二维码：单房间预览/下载、批量导出 PDF。
 *
 * <p>原先这套逻辑散在 `Rooms.vue` 里约 130 行，引用的
 * `qrBatchForm` / `showQrDialog` / `qrImageUrl` 等状态**一个都没有声明**，
 * 模板里也没有对应弹窗，属于点了就 `ReferenceError` 的死代码。
 * 抽到 composable 后把状态一并声明，并补上弹窗。
 *
 * <p>所有 `catch` 都走 `getErrorMessage`，不再用 `catch (_)` 静默吞掉。
 *
 * 用法：模板里自行渲染 `showQrDialog` / `showQrBatchDialog` 两个弹窗，
 * 绑定下方返回的状态与方法即可。
 */
export function useRoomQr({ rooms, selectedRoomIds, buildingName }) {
  const showQrDialog = ref(false)
  const currentQrRoom = ref(null)
  const qrImageUrl = ref('')
  const qrLoading = ref(false)

  const showQrBatchDialog = ref(false)
  const qrBatchLoading = ref(false)
  const qrBatchForm = ref({
    useSelectedOnly: false,
    floor: null,
    paperSize: 'A4',
    qrPerPage: 6,
    showLogo: true
  })

  /** 打开单房间二维码预览。 */
  async function showRoomQr(room) {
    currentQrRoom.value = room
    qrLoading.value = true
    try {
      const res = await getRoomQrPng(room.id, { showLogo: false })
      if (qrImageUrl.value) {
        URL.revokeObjectURL(qrImageUrl.value)
      }
      qrImageUrl.value = URL.createObjectURL(res.data)
      showQrDialog.value = true
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '二维码生成失败'))
    } finally {
      qrLoading.value = false
    }
  }

  /** 下载当前房间二维码 PNG。 */
  async function downloadQrPng() {
    if (!currentQrRoom.value) return
    try {
      const res = await getRoomQrPng(currentQrRoom.value.id, { showLogo: true })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(res.data)
      link.download = `${buildingName.value}_${currentQrRoom.value.roomNumber}_二维码.png`
      link.click()
      URL.revokeObjectURL(link.href)
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '下载失败'))
    }
  }

  /** 新窗口打印当前二维码。 */
  function printQr() {
    if (!qrImageUrl.value) return
    const win = window.open('', '_blank')
    if (!win) {
      ElMessage.warning('浏览器拦截了打印窗口，请允许弹窗后重试')
      return
    }
    win.document.write(
      `<img src="${qrImageUrl.value}" style="width:320px;display:block;margin:20px auto;"/>`
    )
    win.document.close()
    win.print()
  }

  /** 打开批量导出对话框，默认沿用当前选择。 */
  function openQrBatchDialog() {
    qrBatchForm.value.useSelectedOnly = selectedRoomIds.value.length > 0
    showQrBatchDialog.value = true
  }

  /** 批量导出二维码 PDF。 */
  async function exportQrBatchPdf() {
    qrBatchLoading.value = true
    try {
      let targetRooms = qrBatchForm.value.useSelectedOnly
        ? rooms.value.filter(room => selectedRoomIds.value.includes(room.id))
        : rooms.value

      if (qrBatchForm.value.floor != null) {
        targetRooms = targetRooms.filter(r => Number(r.floor) === Number(qrBatchForm.value.floor))
      }
      if (targetRooms.length === 0) {
        ElMessage.warning('没有可导出的房间')
        return
      }

      const res = await exportRoomQrsPdf({
        roomIds: targetRooms.map(r => r.id),
        paperSize: qrBatchForm.value.paperSize,
        qrPerPage: qrBatchForm.value.qrPerPage,
        showLogo: qrBatchForm.value.showLogo
      })

      const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
      const link = document.createElement('a')
      link.href = URL.createObjectURL(res.data)
      link.download = `${buildingName.value || '房间'}二维码_${stamp}.pdf`
      link.click()
      URL.revokeObjectURL(link.href)
      ElMessage.success('导出成功')
      showQrBatchDialog.value = false
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '导出失败'))
    } finally {
      qrBatchLoading.value = false
    }
  }

  /** 预览图是 objectURL，离开页面必须释放，否则内存泄漏。 */
  function releaseQrUrl() {
    if (qrImageUrl.value) {
      URL.revokeObjectURL(qrImageUrl.value)
      qrImageUrl.value = ''
    }
  }

  return {
    showQrDialog,
    currentQrRoom,
    qrImageUrl,
    qrLoading,
    showQrBatchDialog,
    qrBatchLoading,
    qrBatchForm,
    showRoomQr,
    downloadQrPng,
    printQr,
    openQrBatchDialog,
    exportQrBatchPdf,
    releaseQrUrl
  }
}
