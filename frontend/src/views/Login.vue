<template>
  <div class="login-page" :class="{ 'is-dark': themeStore.mode === 'dark' }">
    <button
      type="button"
      class="theme-toggle"
      :aria-label="themeStore.mode === 'dark' ? '切换亮色主题' : '切换暗色主题'"
      @click="themeStore.toggleTheme"
    >
      <el-icon :size="18">
        <Sunny v-if="themeStore.mode === 'dark'" />
        <Moon v-else />
      </el-icon>
    </button>

    <div class="login-bg" aria-hidden="true">
      <div class="bg-gradient" />
      <div class="bg-orb bg-orb-1" />
      <div class="bg-orb bg-orb-2" />
      <div class="bg-orb bg-orb-3" />
      <div class="bg-grid" />
    </div>

    <div class="login-wrapper">
      <section class="login-brand">
        <div class="brand-content">
          <div class="brand-logo">
            <div class="logo-icon-wrap">
              <el-icon :size="32"><Document /></el-icon>
            </div>
            <div class="logo-icon-wrap logo-icon-secondary">
              <el-icon :size="20"><ChatLineRound /></el-icon>
            </div>
          </div>
          <h1 class="brand-title">文档翻译系统</h1>
          <p class="brand-tagline">智能翻译 · 即时通讯 · 团队协作</p>
          <ul class="brand-features">
            <li>
              <el-icon><Reading /></el-icon>
              <span>多格式文档爬取与翻译</span>
            </li>
            <li>
              <el-icon><Connection /></el-icon>
              <span>实时消息与团队沟通</span>
            </li>
            <li>
              <el-icon><DataAnalysis /></el-icon>
              <span>任务进度可视化追踪</span>
            </li>
          </ul>
        </div>
      </section>

      <section class="login-panel">
        <div class="login-card">
          <header class="login-header">
            <h2>欢迎回来</h2>
            <p>登录您的账户以继续</p>
          </header>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            class="login-form"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="用户名"
                size="large"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                size="large"
                :prefix-icon="Lock"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item prop="captchaCode" class="captcha-item">
              <div class="captcha-row">
                <el-input
                  v-model="loginForm.captchaCode"
                  placeholder="验证码"
                  size="large"
                  maxlength="4"
                  clearable
                  @keyup.enter="handleLogin"
                />
                <button
                  type="button"
                  class="captcha-image-btn"
                  :title="captchaLoading ? '加载中...' : '点击刷新验证码'"
                  :disabled="captchaLoading"
                  @click="loadCaptcha"
                >
                  <img
                    v-if="captchaImage"
                    :src="captchaImage"
                    alt="验证码"
                    class="captcha-image"
                  />
                  <span v-else class="captcha-placeholder">加载中</span>
                </button>
              </div>
            </el-form-item>

            <div class="login-options">
              <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            </div>

            <transition name="fade">
              <el-alert
                v-if="errorMsg"
                :title="errorMsg"
                type="error"
                show-icon
                :closable="true"
                class="login-error"
                @close="errorMsg = ''"
              />
            </transition>

            <el-form-item class="login-submit-item">
              <el-button
                type="primary"
                size="large"
                class="login-btn"
                :loading="loading"
                @click="handleLogin"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>
          </el-form>

          <p class="auth-footer-link">
            还没有账户？
            <router-link to="/register">立即注册</router-link>
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Sunny, Moon, Document, ChatLineRound, Connection, DataAnalysis } from '@element-plus/icons-vue'
import { login, getCaptcha } from '@/api'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import type { FormInstance } from 'element-plus'

const REMEMBER_ME_KEY = 'rememberMe'
const REMEMBERED_USERNAME_KEY = 'rememberedUsername'

const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const loading = ref(false)
const captchaLoading = ref(false)
const errorMsg = ref('')
const loginFormRef = ref<FormInstance>()
const rememberMe = ref(false)
const captchaImage = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const loadCaptcha = async () => {
  captchaLoading.value = true
  try {
    const data = await getCaptcha()
    loginForm.captchaId = data.captchaId
    loginForm.captchaCode = ''
    captchaImage.value = `data:image/png;base64,${data.imageBase64}`
  } catch {
    errorMsg.value = '验证码加载失败，请稍后重试'
  } finally {
    captchaLoading.value = false
  }
}

const restoreRememberedUsername = () => {
  if (localStorage.getItem(REMEMBER_ME_KEY) === 'true') {
    rememberMe.value = true
    const savedUsername = localStorage.getItem(REMEMBERED_USERNAME_KEY)
    if (savedUsername) {
      loginForm.username = savedUsername
    }
  }
}

const persistRememberMePreference = () => {
  if (rememberMe.value) {
    localStorage.setItem(REMEMBER_ME_KEY, 'true')
    localStorage.setItem(REMEMBERED_USERNAME_KEY, loginForm.username)
  } else {
    localStorage.removeItem(REMEMBER_ME_KEY)
    localStorage.removeItem(REMEMBERED_USERNAME_KEY)
  }
}

onMounted(() => {
  restoreRememberedUsername()
  loadCaptcha()
})

const handleLogin = async () => {
  const formEl = loginFormRef.value
  if (!formEl) return

  await formEl.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    errorMsg.value = ''

    try {
      const data = await login({
        username: loginForm.username,
        password: loginForm.password,
        captchaId: loginForm.captchaId,
        captchaCode: loginForm.captchaCode,
        rememberMe: rememberMe.value,
      })
      persistRememberMePreference()
      userStore.setLoginData(data.token, data.userId, data.username, data.role, rememberMe.value)
      await userStore.fetchProfile()
      router.push('/')
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '登录失败，请检查用户名和密码'
      errorMsg.value = typeof err === 'object' && err !== null && 'message' in err
        ? String((err as { message: unknown }).message)
        : message
      await loadCaptcha()
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  --login-accent: #409eff;
  --login-accent-deep: #337ecc;
  --login-glass-bg: rgba(255, 255, 255, 0.72);
  --login-glass-border: rgba(255, 255, 255, 0.45);
  --login-card-shadow: 0 24px 48px rgba(31, 38, 135, 0.12);
  --login-text-muted: #909399;

  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 24px;
}

.login-page.is-dark {
  --login-glass-bg: rgba(18, 18, 24, 0.92);
  --login-glass-border: rgba(255, 255, 255, 0.1);
  --login-card-shadow:
    0 32px 64px rgba(0, 0, 0, 0.6),
    0 0 0 1px rgba(255, 255, 255, 0.06);
  --login-text-muted: #b4b8c0;
  --login-input-bg: rgba(10, 10, 15, 0.95);
  --login-input-border: rgba(255, 255, 255, 0.12);
  --login-input-text: #f1f5f9;
  --login-input-placeholder: #8b919a;
}

.theme-toggle {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--login-glass-border);
  border-radius: 50%;
  background: var(--login-glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--text-primary);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.theme-toggle:hover {
  transform: scale(1.05);
  border-color: var(--login-accent);
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.2);
}

.is-dark .theme-toggle {
  background: rgba(18, 18, 24, 0.95);
  border-color: rgba(255, 255, 255, 0.14);
  color: #fbbf24;
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(255, 255, 255, 0.06);
}

.is-dark .theme-toggle:hover {
  border-color: var(--login-accent);
  color: #fcd34d;
  box-shadow:
    0 4px 24px rgba(64, 158, 255, 0.3),
    0 0 0 1px rgba(64, 158, 255, 0.15);
}

.login-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.bg-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #e8f4fd 0%, #f0e6ff 40%, #e0ecff 100%);
  transition: background 0.4s ease;
}

.is-dark .bg-gradient {
  background: linear-gradient(135deg, #0a0a0f 0%, #0d0d12 50%, #0a0a0f 100%);
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  animation: float 12s ease-in-out infinite;
}

.bg-orb-1 {
  width: 420px;
  height: 420px;
  background: #409eff;
  top: -10%;
  left: -5%;
}

.bg-orb-2 {
  width: 320px;
  height: 320px;
  background: #764ba2;
  bottom: -5%;
  right: 10%;
  animation-delay: -4s;
}

.bg-orb-3 {
  width: 240px;
  height: 240px;
  background: #67c23a;
  top: 40%;
  right: -5%;
  animation-delay: -8s;
  opacity: 0.3;
}

.is-dark .bg-orb-1 {
  background: #1e3a5f;
  opacity: 0.15;
}

.is-dark .bg-orb-2 {
  background: #3d2066;
  opacity: 0.12;
}

.is-dark .bg-orb-3 {
  background: #0d4f5c;
  opacity: 0.1;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(64, 158, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(64, 158, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 70% 70% at 50% 50%, black 20%, transparent 100%);
}

.is-dark .bg-grid {
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, -20px) scale(1.05); }
  66% { transform: translate(-15px, 15px) scale(0.95); }
}

.login-wrapper {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 520px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--login-card-shadow);
  border: 1px solid var(--login-glass-border);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.is-dark .login-wrapper {
  box-shadow: var(--login-card-shadow);
  border-color: var(--login-glass-border);
}

.login-brand {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 48px 40px;
  background: linear-gradient(160deg, rgba(64, 158, 255, 0.92) 0%, rgba(51, 126, 204, 0.95) 50%, rgba(118, 75, 162, 0.9) 100%);
  color: #fff;
}

.is-dark .login-brand {
  background: linear-gradient(160deg, #0a0a0f 0%, #121218 45%, #16161e 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: inset -1px 0 24px rgba(0, 0, 0, 0.25);
}

.is-dark .brand-title {
  color: #f8fafc;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

.is-dark .brand-tagline {
  opacity: 1;
  color: #cbd5e1;
}

.is-dark .brand-features li {
  opacity: 1;
  color: #e2e8f0;
}

.is-dark .brand-features .el-icon {
  background: rgba(64, 158, 255, 0.22);
  color: #60a5fa;
}

.is-dark .logo-icon-wrap {
  background: rgba(64, 158, 255, 0.14);
  border-color: rgba(64, 158, 255, 0.35);
}

.is-dark .logo-icon-secondary {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.2);
}

.brand-content {
  max-width: 340px;
}

.brand-logo {
  position: relative;
  display: inline-flex;
  margin-bottom: 28px;
}

.logo-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.logo-icon-secondary {
  position: absolute;
  bottom: -8px;
  right: -16px;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.95);
  color: var(--login-accent);
  border: 2px solid rgba(255, 255, 255, 0.5);
}

.brand-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin-bottom: 10px;
  line-height: 1.3;
}

.brand-tagline {
  font-size: 15px;
  opacity: 0.88;
  margin-bottom: 36px;
  letter-spacing: 0.04em;
}

.brand-features {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  opacity: 0.92;
}

.brand-features .el-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.15);
}

.login-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: var(--login-glass-bg);
}

.login-card {
  width: 100%;
  max-width: 360px;
}

.login-header {
  margin-bottom: 32px;
}

.login-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.login-header p {
  font-size: 14px;
  color: var(--login-text-muted);
}

.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px var(--border-color) inset;
  transition: box-shadow 0.2s;
}

.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--login-accent) inset;
}

.is-dark .login-header h2 {
  color: #f1f5f9;
}

.is-dark .login-header p {
  color: var(--login-text-muted);
}

.is-dark .login-form :deep(.el-input__wrapper) {
  background-color: var(--login-input-bg) !important;
  box-shadow: 0 0 0 1px var(--login-input-border) inset !important;
}

.is-dark .login-form :deep(.el-input__wrapper:hover),
.is-dark .login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--login-accent) inset, 0 0 0 3px rgba(64, 158, 255, 0.12) !important;
}

.is-dark .login-form :deep(.el-input__inner) {
  color: var(--login-input-text);
}

.is-dark .login-form :deep(.el-input__inner::placeholder) {
  color: var(--login-input-placeholder);
}

.is-dark .login-form :deep(.el-input__prefix),
.is-dark .login-form :deep(.el-input__suffix) {
  color: #9ca3af;
}

.captcha-item {
  margin-bottom: 16px !important;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-row :deep(.el-input) {
  flex: 1;
}

.captcha-image-btn {
  flex-shrink: 0;
  width: 130px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.captcha-image-btn:hover:not(:disabled) {
  border-color: var(--login-accent);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12);
}

.captcha-image-btn:disabled {
  cursor: wait;
  opacity: 0.7;
}

.captcha-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: var(--login-text-muted);
}

.is-dark .captcha-image-btn {
  background: var(--login-input-bg);
  border-color: var(--login-input-border);
}

.is-dark .captcha-image-btn:hover:not(:disabled) {
  border-color: var(--login-accent);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}

.login-options {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.login-options :deep(.el-checkbox__label) {
  color: var(--login-text-muted);
  font-size: 14px;
}

.is-dark .login-options :deep(.el-checkbox__label) {
  color: var(--login-text-muted);
}

.is-dark .login-options :deep(.el-checkbox__inner) {
  background-color: var(--login-input-bg);
  border-color: var(--login-input-border);
}

.login-error {
  margin-bottom: 18px;
  border-radius: 10px;
}

.login-submit-item {
  margin-bottom: 0;
  margin-top: 8px;
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.12em;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, var(--login-accent) 0%, var(--login-accent-deep) 100%);
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.35);
  background: linear-gradient(135deg, #53a8ff 0%, var(--login-accent) 100%);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.auth-footer-link {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: var(--login-text-muted);
}

.auth-footer-link a {
  color: var(--login-accent);
  text-decoration: none;
  margin-left: 4px;
}

.auth-footer-link a:hover {
  text-decoration: underline;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 768px) {
  .login-page {
    padding: 16px;
    align-items: flex-start;
    padding-top: 72px;
  }

  .login-wrapper {
    flex-direction: column;
    min-height: unset;
    max-width: 420px;
  }

  .login-brand {
    padding: 32px 28px 28px;
  }

  .brand-title {
    font-size: 22px;
  }

  .brand-features {
    display: none;
  }

  .brand-tagline {
    margin-bottom: 0;
  }

  .login-panel {
    padding: 32px 28px 36px;
  }

  .theme-toggle {
    top: 16px;
    right: 16px;
  }
}
</style>
