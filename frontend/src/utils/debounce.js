/**
 * 简易防抖：用于搜索框输入，避免每次按键都打后端。
 * 返回的函数带 cancel，便于组件卸载时清理悬挂的定时器。
 */
export function debounce(fn, delay = 300) {
  let timer = null

  const wrapped = (...args) => {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = null
      fn(...args)
    }, delay)
  }

  wrapped.cancel = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  return wrapped
}
