import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ThemeMode } from '@/types/theme'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem('theme') as ThemeMode) || 'light')

  function setTheme(newMode: ThemeMode) {
    mode.value = newMode
    localStorage.setItem('theme', newMode)
    applyTheme(newMode)
  }

  function toggleTheme() {
    const newMode = mode.value === 'light' ? 'dark' : 'light'
    setTheme(newMode)
  }

  function applyTheme(themeMode: ThemeMode) {
    if (themeMode === 'dark') {
      document.documentElement.classList.add('dark')
      document.documentElement.setAttribute('data-theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      document.documentElement.setAttribute('data-theme', 'light')
    }
  }

  // Apply theme on store initialization
  applyTheme(mode.value)

  return {
    mode,
    setTheme,
    toggleTheme,
  }
})
