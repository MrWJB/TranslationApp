import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const STORAGE_KEYS = ['token', 'userId', 'username', 'role'] as const

function readStored(key: string): string {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || ''
}

function clearAuthStorage() {
  for (const key of STORAGE_KEYS) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(readStored('token'))
  const userId = ref<number>(Number(readStored('userId') || 0))
  const username = ref<string>(readStored('username'))
  const role = ref<string>(readStored('role'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  function setLoginData(t: string, id: number, u: string, r: string, rememberMe = false) {
    token.value = t
    userId.value = id
    username.value = u
    role.value = r

    clearAuthStorage()
    const storage = rememberMe ? localStorage : sessionStorage
    storage.setItem('token', t)
    storage.setItem('userId', String(id))
    storage.setItem('username', u)
    storage.setItem('role', r)
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    role.value = ''
    clearAuthStorage()
  }

  return {
    token,
    userId,
    username,
    role,
    isLoggedIn,
    isAdmin,
    setLoginData,
    logout,
  }
})
