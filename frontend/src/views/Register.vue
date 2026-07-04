<template>
  <div class="auth-page register-page">
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
    </div>

    <div class="login-wrapper">
      <section class="login-brand">
        <div class="brand-content">
          <h1 class="brand-title">文档翻译系统</h1>
          <p class="brand-tagline">创建账户，开启智能翻译与协作</p>
        </div>
      </section>

      <section class="login-panel">
        <div class="login-card">
          <header class="login-header">
            <h2>注册账户</h2>
            <p>选择注册方式完成账户创建</p>
          </header>

          <el-tabs v-model="activeTab" class="register-tabs">
            <el-tab-pane label="账号注册" name="account">
              <el-form
                ref="accountFormRef"
                :model="accountForm"
                :rules="accountRules"
                class="login-form"
                @submit.prevent="handleAccountRegister"
              >
                <el-form-item prop="username">
                  <el-input v-model="accountForm.username" placeholder="用户名" size="large" :prefix-icon="User" clearable />
                </el-form-item>
                <el-form-item prop="password">
                  <el-input
                    v-model="accountForm.password"
                    type="password"
                    placeholder="密码（至少8位，含字母和数字）"
                    size="large"
                    :prefix-icon="Lock"
                    show-password
                  />
                </el-form-item>
                <el-form-item prop="confirmPassword">
                  <el-input
                    v-model="accountForm.confirmPassword"
                    type="password"
                    placeholder="确认密码"
                    size="large"
                    :prefix-icon="Lock"
                    show-password
                  />
                </el-form-item>
                <el-form-item prop="realName">
                  <el-input v-model="accountForm.realName" placeholder="真实姓名（可选）" size="large" clearable />
                </el-form-item>
                <el-form-item prop="email">
                  <el-input v-model="accountForm.email" placeholder="邮箱（可选）" size="large" clearable />
                </el-form-item>
                <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleAccountRegister">
                  {{ loading ? '注册中...' : '注 册' }}
                </el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="手机注册" name="phone">
              <el-form
                ref="phoneFormRef"
                :model="phoneForm"
                :rules="phoneRules"
                class="login-form"
                @submit.prevent="handlePhoneRegister"
              >
                <el-form-item prop="phone">
                  <el-input v-model="phoneForm.phone" placeholder="手机号" size="large" :prefix-icon="Iphone" clearable />
                </el-form-item>
                <el-form-item prop="code">
                  <div class="code-row">
                    <el-input v-model="phoneForm.code" placeholder="验证码" size="large" maxlength="8" />
                    <el-button
                      size="large"
                      :disabled="smsCountdown > 0 || sendingCode"
                      :loading="sendingCode"
                      @click="handleSendCode"
                    >
                      {{ smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码' }}
                    </el-button>
                  </div>
                </el-form-item>
                <el-form-item prop="realName">
                  <el-input v-model="phoneForm.realName" placeholder="昵称（可选）" size="large" clearable />
                </el-form-item>
                <p class="sms-hint">开发环境验证码：123456（可在后端 app.sms.mock-code 配置）</p>
                <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handlePhoneRegister">
                  {{ loading ? '注册中...' : '手机注册' }}
                </el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="第三方" name="oauth">
              <p class="oauth-desc">使用第三方账号快速注册/登录</p>
              <div class="oauth-grid">
                <button
                  v-for="item in oauthProviders"
                  :key="item.id"
                  type="button"
                  class="oauth-btn"
                  :disabled="!item.configured"
                  :title="item.configured ? `使用 ${item.name} 注册` : '配置后可使用'"
                  @click="handleOAuth(item)"
                >
                  <component :is="oauthIcon(item.id)" class="oauth-icon" />
                  <span>{{ item.name }}</span>
                </button>
              </div>
              <p v-if="oauthHint" class="oauth-hint">{{ oauthHint }}</p>
            </el-tab-pane>
          </el-tabs>

          <transition name="fade">
            <el-alert
              v-if="errorMsg"
              :title="errorMsg"
              type="error"
              show-icon
              closable
              class="login-error"
              @close="errorMsg = ''"
            />
          </transition>

          <p class="auth-footer-link">
            已有账户？
            <router-link to="/login">立即登录</router-link>
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Iphone } from '@element-plus/icons-vue'
import {
  register as registerAccount,
  registerByPhone,
  sendPhoneCode,
  getOAuthProviders,
  formatApiError,
} from '@/api'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import type { FormInstance, FormRules } from 'element-plus'
import type { OAuthProviderInfo, RegisterRequest } from '@/types'
import '@/styles/auth-page.css'

const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const activeTab = ref('account')
const loading = ref(false)
const sendingCode = ref(false)
const errorMsg = ref('')
const smsCountdown = ref(0)
const oauthProviders = ref<OAuthProviderInfo[]>([])
const oauthHint = ref('未配置的提供商需在 application.yml 中填写 OAuth 凭证，或开启 OAUTH_DEMO_MODE=true 进行演示。')

let countdownTimer: ReturnType<typeof setInterval> | null = null

const accountFormRef = ref<FormInstance>()
const phoneFormRef = ref<FormInstance>()

const accountForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  email: '',
})

const phoneForm = reactive({
  phone: '',
  code: '',
  realName: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (value !== accountForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const accountRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3-32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码需包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

const phoneRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效手机号', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const finishLogin = (data: { token: string; userId: number; username: string; role: string }) => {
  userStore.setLoginData(data.token, data.userId, data.username, data.role)
  router.push('/')
}

const handleAccountRegister = async () => {
  const formEl = accountFormRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    errorMsg.value = ''
    try {
      const payload: RegisterRequest = {
        username: accountForm.username,
        password: accountForm.password,
        realName: accountForm.realName || undefined,
        email: accountForm.email || undefined,
      }
      const data = await registerAccount(payload)
      finishLogin(data)
    } catch (err: unknown) {
      errorMsg.value = formatApiError(err)
    } finally {
      loading.value = false
    }
  })
}

const handleSendCode = async () => {
  if (!phoneForm.phone.match(/^1[3-9]\d{9}$/)) {
    errorMsg.value = '请输入有效手机号'
    return
  }
  sendingCode.value = true
  errorMsg.value = ''
  try {
    await sendPhoneCode(phoneForm.phone)
    smsCountdown.value = 60
    countdownTimer = setInterval(() => {
      smsCountdown.value -= 1
      if (smsCountdown.value <= 0 && countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch (err: unknown) {
    errorMsg.value = formatApiError(err)
  } finally {
    sendingCode.value = false
  }
}

const handlePhoneRegister = async () => {
  const formEl = phoneFormRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    errorMsg.value = ''
    try {
      const data = await registerByPhone({
        phone: phoneForm.phone,
        code: phoneForm.code,
        realName: phoneForm.realName || undefined,
      })
      finishLogin(data)
    } catch (err: unknown) {
      errorMsg.value = formatApiError(err)
    } finally {
      loading.value = false
    }
  })
}

const handleOAuth = (item: OAuthProviderInfo) => {
  if (!item.configured) return
  window.location.href = `/api/auth/oauth/${item.id}/authorize`
}

const oauthIcon = (id: string) => {
  const colors: Record<string, string> = {
    github: '#24292f',
    wechat: '#07c160',
    qq: '#12b7f5',
    wecom: '#2eab49',
  }
  const labels: Record<string, string> = {
    github: 'GH',
    wechat: '微',
    qq: 'Q',
    wecom: '企',
  }
  return h(
    'span',
    {
      class: 'oauth-badge',
      style: {
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '20px',
        height: '20px',
        borderRadius: '4px',
        fontSize: '11px',
        fontWeight: '700',
        color: '#fff',
        background: colors[id] || '#409eff',
      },
    },
    labels[id] || '?'
  )
}

onMounted(async () => {
  try {
    oauthProviders.value = await getOAuthProviders()
  } catch {
    oauthProviders.value = [
      { id: 'github', name: 'GitHub', configured: false },
      { id: 'wechat', name: '微信', configured: false },
      { id: 'qq', name: 'QQ', configured: false },
      { id: 'wecom', name: '企业微信', configured: false },
    ]
  }
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.register-page .brand-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 10px;
}

.register-page .brand-tagline {
  font-size: 15px;
  opacity: 0.88;
}

.register-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.register-tabs :deep(.el-form-item) {
  margin-bottom: 18px;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-row .el-button {
  flex-shrink: 0;
  min-width: 110px;
}

.sms-hint,
.oauth-desc,
.oauth-hint {
  font-size: 12px;
  color: var(--login-text-muted);
  margin: 0 0 12px;
  line-height: 1.5;
}

.login-error {
  margin-top: 16px;
  border-radius: 10px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
