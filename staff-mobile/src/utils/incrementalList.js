import { computed, ref, watch } from 'vue'

/**
 * 分批渲染（移动端长列表）。
 *
 * 后端多数列表接口一次性返回全量数据，移动端一次渲染上百条会卡顿。
 * 这里只控制「渲染多少条」，数据仍在内存里，配合触底加载使用。
 *
 * 用法：
 *   const { visible, hasMore, showMore, reset } = useIncrementalList(tenants, 20)
 *   // 模板：v-for="item in visible"
 *   // 触底：showMore()
 */
export function useIncrementalList(sourceRef, step = 20) {
  const limit = ref(step)

  const visible = computed(() => {
    const all = sourceRef.value || []
    return all.slice(0, limit.value)
  })

  const hasMore = computed(() => (sourceRef.value || []).length > limit.value)
  const remaining = computed(() =>
    Math.max((sourceRef.value || []).length - limit.value, 0)
  )

  function showMore() {
    if (hasMore.value) {
      limit.value += step
    }
  }

  /** 数据源变化（刷新/重新搜索）时重置为初始条数 */
  function reset() {
    limit.value = step
  }

  watch(
    () => (sourceRef.value || []).length,
    (len, prev) => {
      // 数据变多（加载更多数据）时保持当前进度；变少（搜索）时重置
      if (prev != null && len < prev) {
        reset()
      }
      if (prev === 0 && len > 0) {
        reset()
      }
    }
  )

  return { visible, hasMore, remaining, showMore, reset, limit }
}
