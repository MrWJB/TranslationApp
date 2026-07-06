<template>
  <div class="oauth-callback-page">
    <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
    <p>{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const message = ref('正在完成登录...')

onMounted(() => {
  const error = route.query.error as string | undefined
  if (error) {
    message.value = decodeURIComponent(error)
    setTimeout(() => router.replace('/register'), 2500)
    return
  }

  const token = route.query.token as string | undefined
  const username = route.query.username as string | undefined
  const userId = route.query.userId as string | undefined
  const role = route.query.role as string | undefined

  if (token && username && userId && role) {
    userStore.setLoginData(token, Number(userId), username, role)
    userStore.fetchProfile().finally(() => router.replace('/'))
    return
  }

  message.value = 'OAuth 回调参数无效'
  setTimeout(() => router.replace('/register'), 2500)
})
</script>

<style scoped>
.oauth-callback-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-primary);
}

.loading-icon {
  animation: spin 1s linear infinite;
  color: #409eff;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
