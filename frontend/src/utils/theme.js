const THEME_KEY = 'themePreference'
let mediaListenerBound = false

function normalizePreference(value) {
  if (value === 'light' || value === 'dark' || value === 'system') return value
  return 'system'
}

function getSystemTheme() {
  if (!window.matchMedia) return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function resolveTheme(preference) {
  const pref = normalizePreference(preference)
  return pref === 'system' ? getSystemTheme() : pref
}

export function getStoredThemePreference() {
  return normalizePreference(localStorage.getItem(THEME_KEY) || 'system')
}

export function applyThemePreference(preference) {
  const pref = normalizePreference(preference)
  const finalTheme = resolveTheme(pref)
  const root = document.documentElement
  root.classList.remove('theme-light', 'theme-dark')
  root.classList.add(finalTheme === 'dark' ? 'theme-dark' : 'theme-light')
  root.setAttribute('data-theme', finalTheme)
  localStorage.setItem(THEME_KEY, pref)
  return finalTheme
}

export function initTheme() {
  const pref = getStoredThemePreference()
  applyThemePreference(pref)

  if (!mediaListenerBound && window.matchMedia) {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    mq.addEventListener('change', () => {
      const currentPref = getStoredThemePreference()
      if (currentPref === 'system') {
        applyThemePreference('system')
      }
    })
    mediaListenerBound = true
  }

  return pref
}
