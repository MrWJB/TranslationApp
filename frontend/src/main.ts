import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from '@/stores/theme'
import { usePreferencesStore } from '@/stores/preferences'
import './assets/styles.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useThemeStore(pinia)
usePreferencesStore(pinia).init()
app.use(router)
app.mount('#app')
