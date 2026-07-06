import { defineStore } from 'pinia'
import { ref } from 'vue'

export type AppLanguage = 'zh-CN' | 'en-US'

export interface UserPreferences {
  notifyEnabled: boolean
  language: AppLanguage
}

const PREF_KEY = 'user-preferences'

function readStoredPreferences(): UserPreferences {
  try {
    const raw = localStorage.getItem(PREF_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<UserPreferences>
      return {
        notifyEnabled: parsed.notifyEnabled !== false,
        language: parsed.language === 'en-US' ? 'en-US' : 'zh-CN',
      }
    }
  } catch {
    /* ignore corrupt storage */
  }
  return { notifyEnabled: true, language: 'zh-CN' }
}

export function applyLanguage(language: AppLanguage) {
  document.documentElement.lang = language === 'en-US' ? 'en' : 'zh-CN'
}

export const usePreferencesStore = defineStore('preferences', () => {
  const stored = readStoredPreferences()
  const notifyEnabled = ref(stored.notifyEnabled)
  const language = ref<AppLanguage>(stored.language)

  function save() {
    const prefs: UserPreferences = {
      notifyEnabled: notifyEnabled.value,
      language: language.value,
    }
    localStorage.setItem(PREF_KEY, JSON.stringify(prefs))
    applyLanguage(language.value)
  }

  function init() {
    applyLanguage(language.value)
  }

  return {
    notifyEnabled,
    language,
    save,
    init,
  }
})
