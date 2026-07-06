<template>
  <div class="user-settings">
    <div class="page-header">
      <h2>个人设置</h2>
      <p class="page-desc">管理您的个人信息、账户安全与偏好</p>
    </div>

    <el-card shadow="never" class="settings-card">
      <div class="profile-summary">
        <el-avatar :size="72" :src="avatarSrc" class="summary-avatar">
          {{ userStore.avatarInitial }}
        </el-avatar>
        <div class="summary-info">
          <h3>{{ userStore.displayName }}</h3>
          <p>@{{ userStore.username }}</p>
          <div class="summary-tags">
            <el-tag size="small" type="info">{{ profile?.role || userStore.role }}</el-tag>
            <el-tag v-if="profile?.realNameVerified" size="small" type="success">已实名</el-tag>
            <el-tag v-if="profile?.phoneVerified" size="small" type="success">手机已验证</el-tag>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="settings-tabs">
        <el-tab-pane label="基本信息" name="basic">
          <el-form
            ref="basicFormRef"
            :model="basicForm"
            :rules="basicRules"
            label-width="96px"
            class="settings-form"
            v-loading="savingBasic"
          >
            <el-form-item label="头像">
              <div class="avatar-upload">
                <el-avatar :size="80" :src="avatarPreview" class="avatar-preview">
                  {{ userStore.avatarInitial }}
                </el-avatar>
                <div class="avatar-actions">
                  <el-upload
                    :show-file-list="false"
                    accept="image/*"
                    :before-upload="beforeAvatarUpload"
                    :http-request="handleAvatarUpload"
                  >
                    <el-button size="small" :loading="uploadingAvatar">更换头像</el-button>
                  </el-upload>
                  <p class="form-hint">支持 JPG/PNG/GIF/WebP，不超过 2MB</p>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="basicForm.username" disabled />
            </el-form-item>
            <el-form-item label="昵称/姓名" prop="realName">
              <el-input v-model="basicForm.realName" placeholder="请输入显示名称" maxlength="32" />
            </el-form-item>
            <el-form-item label="性别">
              <el-radio-group v-model="basicForm.gender">
                <el-radio value="MALE">男</el-radio>
                <el-radio value="FEMALE">女</el-radio>
                <el-radio value="UNKNOWN">保密</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="生日">
              <el-date-picker
                v-model="basicForm.birthDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="所在地区">
              <div class="region-row">
                <el-select
                  v-model="basicForm.province"
                  placeholder="选择省份"
                  clearable
                  filterable
                  style="width: 100%"
                >
                  <el-option
                    v-for="province in provinces"
                    :key="province"
                    :label="province"
                    :value="province"
                  />
                </el-select>
                <el-select
                  v-model="basicForm.city"
                  placeholder="选择城市"
                  clearable
                  filterable
                  :disabled="!basicForm.province"
                  style="width: 100%"
                >
                  <el-option
                    v-for="city in cityOptions"
                    :key="city"
                    :label="city"
                    :value="city"
                  />
                </el-select>
              </div>
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="basicForm.email" placeholder="example@mail.com" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="basicForm.phone" placeholder="11 位手机号" maxlength="11" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveBasicInfo">保存基本信息</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="账户安全" name="security">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="96px"
            class="settings-form narrow-form"
            v-loading="savingPassword"
          >
            <el-alert
              title="定期修改密码有助于保护账户安全"
              type="info"
              show-icon
              :closable="false"
              class="section-alert"
            />
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="实名认证" name="realname">
          <div class="realname-section">
            <el-result
              v-if="profile?.realNameVerified"
              icon="success"
              title="已完成实名认证"
              :sub-title="`姓名：${profile.realName || '-'}，证件号：${profile.idCardMasked || '已脱敏'}`"
            />
            <template v-else>
              <el-alert
                title="实名认证后可使用更多功能，您的身份信息将加密存储"
                type="warning"
                show-icon
                :closable="false"
                class="section-alert"
              />
              <el-form
                ref="realNameFormRef"
                :model="realNameForm"
                :rules="realNameRules"
                label-width="96px"
                class="settings-form narrow-form"
                v-loading="submittingRealName"
              >
                <el-form-item label="真实姓名" prop="realName">
                  <el-input v-model="realNameForm.realName" placeholder="与身份证一致的姓名" />
                </el-form-item>
                <el-form-item label="身份证号" prop="idCardNumber">
                  <el-input v-model="realNameForm.idCardNumber" placeholder="18 位身份证号码" maxlength="18" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="submitRealName">提交认证</el-button>
                </el-form-item>
              </el-form>
            </template>
          </div>
        </el-tab-pane>

        <el-tab-pane label="偏好设置" name="preferences">
          <div class="preferences-section settings-form narrow-form">
            <el-form label-width="96px">
              <el-form-item label="界面主题">
                <div class="theme-options">
                  <button
                    type="button"
                    class="theme-option"
                    :class="{ active: themeStore.mode === 'light' }"
                    @click="themeStore.setTheme('light')"
                  >
                    <span class="theme-preview theme-preview--light" aria-hidden="true">
                      <span class="theme-preview-bar" />
                      <span class="theme-preview-body" />
                    </span>
                    <span class="theme-label">浅色</span>
                  </button>
                  <button
                    type="button"
                    class="theme-option"
                    :class="{ active: themeStore.mode === 'dark' }"
                    @click="themeStore.setTheme('dark')"
                  >
                    <span class="theme-preview theme-preview--dark" aria-hidden="true">
                      <span class="theme-preview-bar" />
                      <span class="theme-preview-body" />
                    </span>
                    <span class="theme-label">深色</span>
                  </button>
                </div>
              </el-form-item>
              <el-form-item label="消息通知">
                <div class="preference-switch-row">
                  <el-switch
                    v-model="preferencesStore.notifyEnabled"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                    class="preference-switch"
                  />
                  <span class="preference-switch-desc">
                    {{ preferencesStore.notifyEnabled ? '已开启系统消息通知' : '已关闭系统消息通知' }}
                  </span>
                </div>
              </el-form-item>
              <el-form-item label="语言">
                <el-select v-model="preferencesStore.language" style="width: 200px">
                  <el-option label="简体中文" value="zh-CN" />
                  <el-option label="English" value="en-US" />
                </el-select>
                <p class="form-hint">切换后将更新 Element Plus 组件语言</p>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="savePreferences">保存偏好</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { usePreferencesStore } from '@/stores/preferences'
import { PROVINCES, citiesForProvince } from '@/data/chinaRegions'
import { updateUserProfile, uploadUserAvatar, verifyRealName, changePassword, formatApiError } from '@/api'
import type { UserProfile } from '@/types'

const userStore = useUserStore()
const themeStore = useThemeStore()
const preferencesStore = usePreferencesStore()

const provinces = computed(() => {
  if (basicForm.province && !PROVINCES.includes(basicForm.province)) {
    return [basicForm.province, ...PROVINCES]
  }
  return PROVINCES
})

const activeTab = ref('basic')
const profile = computed(() => userStore.profile)
const savingBasic = ref(false)
const savingPassword = ref(false)
const submittingRealName = ref(false)
const uploadingAvatar = ref(false)

const basicFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const realNameFormRef = ref<FormInstance>()

const basicForm = reactive({
  username: '',
  realName: '',
  gender: 'UNKNOWN',
  birthDate: '',
  province: '',
  city: '',
  email: '',
  phone: '',
})

const cityOptions = computed(() => {
  const cities = citiesForProvince(basicForm.province)
  if (basicForm.city && !cities.includes(basicForm.city)) {
    return [basicForm.city, ...cities]
  }
  return cities
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const realNameForm = reactive({
  realName: '',
  idCardNumber: '',
})

const avatarPreview = ref('')
const avatarSrc = computed(() => avatarPreview.value || userStore.avatar || undefined)

const basicRules: FormRules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{
    validator: (_rule, value, callback) => {
      if (!value || /^1\d{10}$/.test(value)) callback()
      else callback(new Error('请输入有效的手机号'))
    },
    trigger: 'blur',
  }],
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

const realNameRules: FormRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 32, message: '姓名长度 2-32 位', trigger: 'blur' },
  ],
  idCardNumber: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    {
      pattern: /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/,
      message: '身份证号格式不正确',
      trigger: 'blur',
    },
  ],
}

function fillBasicForm(p: UserProfile) {
  basicForm.username = p.username
  basicForm.realName = p.realName || ''
  basicForm.gender = p.gender || 'UNKNOWN'
  basicForm.birthDate = p.birthDate || ''
  basicForm.province = p.province || ''
  basicForm.city = p.city || ''
  basicForm.email = p.email || ''
  basicForm.phone = p.phone || ''
  avatarPreview.value = p.avatar || ''
}

watch(profile, (p) => {
  if (p) fillBasicForm(p)
}, { immediate: true })

watch(() => basicForm.province, (province, prev) => {
  if (prev && province !== prev) {
    const cities = citiesForProvince(province)
    if (!cities.includes(basicForm.city)) {
      basicForm.city = ''
    }
  }
})

onMounted(async () => {
  if (!userStore.profile) {
    await userStore.fetchProfile()
  }
})

function beforeAvatarUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像不能超过 2MB')
    return false
  }
  return true
}

async function handleAvatarUpload(options: UploadRequestOptions) {
  uploadingAvatar.value = true
  try {
    const file = options.file as File
    const updated = await uploadUserAvatar(file)
    userStore.applyProfile(updated)
    avatarPreview.value = updated.avatar || ''
    ElMessage.success('头像已更新')
  } catch (err) {
    ElMessage.error(formatApiError(err))
  } finally {
    uploadingAvatar.value = false
  }
}

async function saveBasicInfo() {
  const formEl = basicFormRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    savingBasic.value = true
    try {
      const updated = await updateUserProfile({
        realName: basicForm.realName || undefined,
        gender: basicForm.gender,
        birthDate: basicForm.birthDate || undefined,
        province: basicForm.province || undefined,
        city: basicForm.city || undefined,
        email: basicForm.email || undefined,
        phone: basicForm.phone || undefined,
      })
      userStore.applyProfile(updated)
      ElMessage.success('基本信息已保存')
    } catch (err) {
      ElMessage.error(formatApiError(err))
    } finally {
      savingBasic.value = false
    }
  })
}

async function savePassword() {
  const formEl = passwordFormRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    savingPassword.value = true
    try {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })
      ElMessage.success('密码修改成功，请重新登录')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } catch (err) {
      ElMessage.error(formatApiError(err))
    } finally {
      savingPassword.value = false
    }
  })
}

async function submitRealName() {
  const formEl = realNameFormRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    submittingRealName.value = true
    try {
      const updated = await verifyRealName({
        realName: realNameForm.realName,
        idCardNumber: realNameForm.idCardNumber,
      })
      userStore.applyProfile(updated)
      ElMessage.success('实名认证成功')
    } catch (err) {
      ElMessage.error(formatApiError(err))
    } finally {
      submittingRealName.value = false
    }
  })
}

function savePreferences() {
  preferencesStore.save()
  ElMessage.success('偏好设置已保存')
}
</script>

<style scoped>
.user-settings {
  max-width: 920px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.page-desc {
  margin: 0;
  color: var(--text-secondary, #909399);
  font-size: 14px;
}

.settings-card {
  border-radius: 8px;
}

.profile-summary {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-bottom: 20px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border-color, #ebeef5);
}

.summary-avatar {
  flex-shrink: 0;
  background: var(--primary-color, #409eff);
  color: #fff;
  font-size: 28px;
}

.summary-info h3 {
  margin: 0 0 4px;
  font-size: 20px;
}

.summary-info p {
  margin: 0 0 8px;
  color: var(--text-secondary, #909399);
}

.summary-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.settings-form {
  max-width: 560px;
  padding-top: 16px;
}

.narrow-form {
  max-width: 480px;
}

.avatar-upload {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-preview {
  background: var(--primary-color, #409eff);
  color: #fff;
  font-size: 32px;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.region-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.form-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary, #909399);
}

.section-alert {
  margin-bottom: 20px;
}

.realname-section,
.preferences-section {
  padding-top: 16px;
}

.theme-options {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.theme-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 120px;
  padding: 12px;
  border: 2px solid var(--border-color, #dcdfe6);
  border-radius: 8px;
  background: var(--card-bg, #fff);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, background-color 0.2s;
}

.theme-option:hover {
  border-color: var(--primary-color, #409eff);
}

.theme-option.active {
  border-color: var(--primary-color, #409eff);
  box-shadow: 0 0 0 1px var(--primary-color, #409eff);
  background: color-mix(in srgb, var(--primary-color, #409eff) 8%, var(--card-bg, #fff));
}

.theme-preview {
  display: flex;
  flex-direction: column;
  width: 88px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--border-color, #dcdfe6);
}

.theme-preview-bar {
  height: 14px;
  flex-shrink: 0;
}

.theme-preview-body {
  flex: 1;
}

.theme-preview--light {
  background: #f5f7fa;
}

.theme-preview--light .theme-preview-bar {
  background: #ffffff;
}

.theme-preview--light .theme-preview-body {
  background: #ffffff;
}

.theme-preview--dark {
  background: #0a0a0f;
}

.theme-preview--dark .theme-preview-bar {
  background: #121218;
}

.theme-preview--dark .theme-preview-body {
  background: #121218;
}

.theme-label {
  font-size: 14px;
  color: var(--text-primary, #303133);
}

.preference-switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.preference-switch {
  flex-shrink: 0;
}

.preference-switch-desc {
  font-size: 13px;
  color: var(--text-secondary, #909399);
}
</style>
