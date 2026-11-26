/**
 * 轻量提示层。
 *
 * <p>这个项目没有 Element Plus，原来到处用 `alert()`——阻塞式、样式不可控、
 * 移动端还会弹浏览器原生框。这里用纯 DOM 实现 toast 与确认框，无依赖。
 */

let container = null

function ensureContainer() {
  if (container && document.body.contains(container)) return container
  container = document.createElement('div')
  container.className = 'toast-container'
  document.body.appendChild(container)
  return container
}

function createToast(message, type, duration) {
  const toast = document.createElement('div')
  toast.className = `toast toast-${type}`
  toast.textContent = String(message ?? '')
  ensureContainer().appendChild(toast)

  // 触发过渡（插入后再加 class，避免和初始渲染合并）
  requestAnimationFrame(() => toast.classList.add('toast-show'))

  window.setTimeout(() => {
    toast.classList.remove('toast-show')
    window.setTimeout(() => toast.remove(), 200)
  }, duration)
}

export function showSuccess(message, duration = 2000) {
  createToast(message, 'success', duration)
}

export function showError(message, duration = 3000) {
  createToast(message, 'error', duration)
}

export function showInfo(message, duration = 2000) {
  createToast(message, 'info', duration)
}

/**
 * 确认框，返回 Promise<boolean>。
 *
 * <p>替代 `confirm()`：可自定义按钮文案，且不会在 Capacitor/移动端内嵌浏览器里
 * 出现地址栏前缀。
 */
export function confirmDialog(message, { title = '提示', okText = '确定', cancelText = '取消' } = {}) {
  return new Promise(resolve => {
    const mask = document.createElement('div')
    mask.className = 'dialog-mask'
    mask.innerHTML = `
      <div class="dialog">
        <div class="dialog-title"></div>
        <div class="dialog-body"></div>
        <div class="dialog-actions">
          <button class="btn btn-light" data-role="cancel"></button>
          <button class="btn btn-primary" data-role="ok"></button>
        </div>
      </div>`
    // 用 textContent 赋值，避免 message 里的 HTML 被解析
    mask.querySelector('.dialog-title').textContent = title
    mask.querySelector('.dialog-body').textContent = message
    mask.querySelector('[data-role=cancel]').textContent = cancelText
    mask.querySelector('[data-role=ok]').textContent = okText

    function close(result) {
      mask.remove()
      resolve(result)
    }

    mask.querySelector('[data-role=cancel]').addEventListener('click', () => close(false))
    mask.querySelector('[data-role=ok]').addEventListener('click', () => close(true))
    mask.addEventListener('click', event => {
      if (event.target === mask) close(false)
    })

    document.body.appendChild(mask)
  })
}

/** 输入框弹窗，返回 Promise<string|null>（取消为 null）。 */
export function promptDialog(message, { title = '请输入', placeholder = '', okText = '确定', cancelText = '取消' } = {}) {
  return new Promise(resolve => {
    const mask = document.createElement('div')
    mask.className = 'dialog-mask'
    mask.innerHTML = `
      <div class="dialog">
        <div class="dialog-title"></div>
        <div class="dialog-body"></div>
        <input class="input" data-role="input" />
        <div class="dialog-actions">
          <button class="btn btn-light" data-role="cancel"></button>
          <button class="btn btn-primary" data-role="ok"></button>
        </div>
      </div>`
    mask.querySelector('.dialog-title').textContent = title
    mask.querySelector('.dialog-body').textContent = message
    mask.querySelector('[data-role=cancel]').textContent = cancelText
    mask.querySelector('[data-role=ok]').textContent = okText

    const input = mask.querySelector('[data-role=input]')
    input.placeholder = placeholder

    function close(result) {
      mask.remove()
      resolve(result)
    }

    mask.querySelector('[data-role=cancel]').addEventListener('click', () => close(null))
    mask.querySelector('[data-role=ok]').addEventListener('click', () => close(input.value.trim()))
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') close(input.value.trim())
    })
    mask.addEventListener('click', event => {
      if (event.target === mask) close(null)
    })

    document.body.appendChild(mask)
    input.focus()
  })
}
