import { ref } from 'vue'
import { getErrorMessage } from '../api/index.js'
import { showError } from '../utils/toast.js'

/**
 * 无限滚动列表的通用分页逻辑。
 *
 * <p>账单/报修/投诉/公告/消息五个页面此前各写了一份几乎相同的
 * `resetAndLoad` + `loadMore`（重置页码、追加数据、判断是否还有下一页、错误提示），
 * 改一处要改五遍。抽这里统一，页面只提供 `fetchPage`。
 *
 * <p>用法：
 * ```js
 * const { rows, loading, hasMore, loadMore, resetAndLoad } =
 *   usePagedList((page, size) => getRepairList({ page, size, status: status.value }))
 * ```
 *
 * @param fetchPage (page, size) => Promise<axiosResponse>
 * @param options.size 每页条数，默认 10
 * @param options.errorText 加载失败提示文案
 */
export function usePagedList(fetchPage, { size = 10, errorText = '加载失败' } = {}) {
  const rows = ref([])
  const loading = ref(false)
  const hasMore = ref(true)
  const page = ref(0)
  /** 后端返回的全量统计（仅部分接口提供，如账单 summary） */
  const extra = ref(null)

  async function loadMore() {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
      const res = await fetchPage(page.value, size)
      const data = res.data || {}
      const list = data.content || []
      rows.value = rows.value.concat(list)
      page.value += 1
      hasMore.value = rows.value.length < Number(data.totalElements || 0)
      if (data.summary) {
        extra.value = data.summary
      }
    } catch (error) {
      showError(getErrorMessage(error, errorText))
    } finally {
      loading.value = false
    }
  }

  async function resetAndLoad() {
    rows.value = []
    page.value = 0
    hasMore.value = true
    await loadMore()
  }

  return { rows, loading, hasMore, page, extra, loadMore, resetAndLoad }
}
