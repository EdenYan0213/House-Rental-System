import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { Capacitor } from '@capacitor/core'
import { App as CapacitorApp } from '@capacitor/app'
import 'element-plus/dist/index.css'
import ElementPlus, { ElMessage } from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './styles/theme.css'
import { initTheme } from './utils/theme'
import { initMobilePush } from './utils/mobilePush'

initTheme()

const app = createApp(App)
app.use(router).use(ElementPlus)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

initMobilePush(router)

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
    ElMessage({
      message: '再按一次返回键退出应用',
      type: 'warning',
      duration: 1500
    })
  })
}

app.mount('#app')
