import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { Capacitor } from '@capacitor/core'
import { App as CapacitorApp } from '@capacitor/app'
import 'element-plus/dist/index.css'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './styles/theme.css'
import { initTheme, applyThemePreference } from './utils/theme'
import { initMobilePush } from './utils/mobilePush'

applyThemePreference(initTheme())

const app = createApp(App)
app.use(router).use(ElementPlus, { locale: zhCn })

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

initMobilePush(router)

// Android 返回键：根页面双击退出，其余页面后退
if (Capacitor.isNativePlatform()) {
  const rootPaths = new Set(['/mobile/home', '/login'])
  let lastBackPressAt = 0

  CapacitorApp.addListener('backButton', ({ canGoBack }) => {
    const current = router.currentRoute.value.path || ''
    if (canGoBack && !rootPaths.has(current)) {
      router.back()
      return
    }

    const now = Date.now()
    if (now - lastBackPressAt < 1800) {
      CapacitorApp.exitApp()
      return
    }

    lastBackPressAt = now
    ElMessage({ message: '再按一次返回键退出应用', type: 'warning', duration: 1500 })
  })
}

app.mount('#app')
