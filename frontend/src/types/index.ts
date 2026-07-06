export interface User {
  id: number
  username: string
  role: string
}

export interface LoginRequest {
  username: string
  password: string
  captchaId: string
  captchaCode: string
  rememberMe?: boolean
}

export interface CaptchaResponse {
  captchaId: string
  imageBase64: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
  realName?: string
}

export interface PhoneRegisterRequest {
  phone: string
  code: string
  realName?: string
}

export interface OAuthProviderInfo {
  id: string
  name: string
  configured: boolean
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  role: string
}

export interface UserProfile {
  id: number
  username: string
  email?: string
  phone?: string
  phoneVerified?: boolean
  realName?: string
  avatar?: string
  gender?: string
  birthDate?: string
  province?: string
  city?: string
  realNameVerified?: boolean
  idCardMasked?: string
  lastLoginTime?: string
  createdAt?: string
  role?: string
}

export interface UserProfileUpdateRequest {
  realName?: string
  avatar?: string
  gender?: string
  birthDate?: string
  province?: string
  city?: string
  email?: string
  phone?: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface RealNameVerifyRequest {
  realName: string
  idCardNumber: string
}

export interface CrawlTask {
  id: number
  url: string
  title: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  taskType?: string           // document, video, audio, image, mixed, unknown
  category?: string           // 文档分类
  mediaCategory?: string      // 视频分类
  typeConfidence?: number     // 类型识别置信度
  userConfirmed?: boolean     // 用户是否确认了类型
  maxPages?: number
  progressPhase?: string
  progressCurrent?: number
  progressTotal?: number
  progressMessage?: string
  progressPercent?: number
  qualityReport?: string
  originalContent: string
  translatedContent: string
  errorMessage: string
  createdAt: string
  updatedAt: string
  completedAt: string
}

export interface Document {
  id: number
  title: string
  url: string
  taskId?: number
  sortOrder: number
  sectionLevel: number
  sectionId: string
  parentDocumentId: number | null
  originalContent: string
  translatedContent: string
  localPath: string
  translatedLocalPath?: string
  external?: boolean
  category?: string
  createdAt: string
}

export type UnavailableReason = 'external-api' | 'excluded-by-profile' | 'nav-folder' | 'not-crawled'

export interface TocItem {
  id?: number | null
  title: string
  level: number
  url: string
  localPath?: string
  translatedLocalPath?: string
  available?: boolean
  unavailableReason?: UnavailableReason
  external?: boolean
  children?: TocItem[]
}

// System Management Types
export interface SystemUser {
  id: number
  username: string
  email?: string
  phone?: string
  realName?: string
  avatar?: string
  isEnabled: boolean
  isLocked: boolean
  roleIds: number[]
}

export interface UserCreateRequest {
  username: string
  password: string
  email?: string
  phone?: string
  realName?: string
  roleIds?: number[]
}

export interface UserUpdateRequest {
  email?: string
  phone?: string
  realName?: string
  avatar?: string
  isEnabled?: boolean
  isLocked?: boolean
  roleIds?: number[]
}

export interface PasswordResetRequest {
  newPassword: string
}

export interface Role {
  id: number
  name: string
  description?: string
  isSystem: boolean
  permissionIds: number[]
  menuIds: number[]
}

export interface RoleCreateRequest {
  name: string
  description?: string
  permissionIds?: number[]
  menuIds?: number[]
}

export interface RoleUpdateRequest {
  name?: string
  description?: string
  permissionIds?: number[]
  menuIds?: number[]
}

export interface Permission {
  id: number
  code: string
  name: string
  description?: string
  moduleName?: string
  parentId?: number
  sortOrder: number
  children?: Permission[]
}

export interface PermissionCreateRequest {
  code: string
  name: string
  description?: string
  moduleName?: string
  parentId?: number
  sortOrder?: number
}

export interface PermissionUpdateRequest {
  name?: string
  description?: string
  moduleName?: string
  parentId?: number
  sortOrder?: number
}

export interface Menu {
  id: number
  name: string
  parentId?: number
  icon?: string
  path?: string
  componentPath?: string
  sortOrder: number
  isVisible: boolean
  isEnabled: boolean
  children?: Menu[]
}

export interface MenuCreateRequest {
  name: string
  parentId?: number
  icon?: string
  path?: string
  componentPath?: string
  sortOrder?: number
  isVisible?: boolean
  isEnabled?: boolean
}

export interface MenuUpdateRequest {
  name?: string
  parentId?: number
  icon?: string
  path?: string
  componentPath?: string
  sortOrder?: number
  isVisible?: boolean
  isEnabled?: boolean
}
