import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserProfile } from '@/api'
import type { UserProfile } from '@/types'

const STORAGE_KEYS = ['token', 'userId', 'username', 'role', 'realName', 'avatar'] as const

function readStored(key: string): string {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || ''
}

function clearAuthStorage() {
  for (const key of STORAGE_KEYS) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  }
}

function persistProfileFields(realName: string, avatar: string, rememberMe: boolean) {
  const storage = rememberMe ? localStorage : sessionStorage
  if (realName) storage.setItem('realName', realName)
  else storage.removeItem('realName')
  if (avatar) storage.setItem('avatar', avatar)
  else storage.removeItem('avatar')
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(readStored('token'))
  const userId = ref<number>(Number(readStored('userId') || 0))
  const username = ref<string>(readStored('username'))
  const role = ref<string>(readStored('role'))
  const realName = ref<string>(readStored('realName'))
  const avatar = ref<string>(readStored('avatar'))
  const profile = ref<UserProfile | null>(null)
  const profileLoading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const displayName = computed(() => realName.value || username.value || '用户')
  const avatarInitial = computed(() => {
    const name = displayName.value
    return name ? name.slice(0, 1).toUpperCase() : 'U'
  })

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

  function applyProfile(p: UserProfile) {
    profile.value = p
    if (p.realName) realName.value = p.realName
    if (p.avatar) avatar.value = p.avatar
    if (p.role) role.value = p.role

    const rememberMe = !!localStorage.getItem('token')
    persistProfileFields(realName.value, avatar.value, rememberMe)
  }

  async function fetchProfile() {
    if (!token.value) return null
    profileLoading.value = true
    try {
      const data = await getUserProfile()
      applyProfile(data)
      return data
    } finally {
      profileLoading.value = false
    }
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    role.value = ''
    realName.value = ''
    avatar.value = ''
    profile.value = null
    clearAuthStorage()
  }

  return {
    token,
    userId,
    username,
    role,
    realName,
    avatar,
    profile,
    profileLoading,
    isLoggedIn,
    isAdmin,
    displayName,
    avatarInitial,
    setLoginData,
    applyProfile,
    fetchProfile,
    logout,
  }
})
