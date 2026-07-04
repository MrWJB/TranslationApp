import axios from 'axios'
import type { LoginRequest, LoginResponse, RegisterRequest, PhoneRegisterRequest, OAuthProviderInfo, CaptchaResponse, CrawlTask, Document, TocItem, SystemUser, UserCreateRequest, UserUpdateRequest, PasswordResetRequest, Role, RoleCreateRequest, RoleUpdateRequest, Permission, PermissionCreateRequest, PermissionUpdateRequest, Menu, MenuCreateRequest, MenuUpdateRequest } from '@/types'

export const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// Request interceptor - add token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

export function formatApiError(err: unknown): string {
  if (!err) return '未知错误'
  if (typeof err === 'string') return err
  if (typeof err === 'object') {
    const payload = err as Record<string, unknown>
    if (typeof payload.message === 'string' && payload.message) return payload.message
    if (typeof payload.error === 'string' && payload.error) return payload.error
  }
  return '未知错误'
}

// Response interceptor - handle errors
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      sessionStorage.removeItem('token')
      window.location.href = '/login'
    }
    const payload = error.response?.data
    if (typeof payload === 'string' && payload) {
      return Promise.reject({ message: payload })
    }
    if (payload && typeof payload === 'object') {
      return Promise.reject(payload)
    }
    const isNetworkError = !error.response
    return Promise.reject({
      message: isNetworkError
        ? '无法连接后端 (8080)，请确认 backend、crawler-service (3000) 已启动，并通过 npm run dev (5173) 访问前端'
        : (error.message || '请求失败'),
    })
  }
)

// Auth APIs
export const getCaptcha = (): Promise<CaptchaResponse> => {
  return api.get('/auth/captcha')
}

export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return api.post('/auth/login', data)
}

export const register = (data: RegisterRequest): Promise<LoginResponse> => {
  return api.post('/auth/register', data)
}

export const sendPhoneCode = (phone: string): Promise<{ message: string }> => {
  return api.post('/auth/register/phone/send-code', { phone })
}

export const registerByPhone = (data: PhoneRegisterRequest): Promise<LoginResponse> => {
  return api.post('/auth/register/phone', data)
}

export const getOAuthProviders = (): Promise<OAuthProviderInfo[]> => {
  return api.get('/auth/oauth/providers')
}

// Crawl APIs
export const startCrawl = (url: string, maxPages: number = 10): Promise<any> => {
  return api.post('/crawl/start', { url, maxPages })
}

export interface CrawlConfig {
  url: string
  maxPages?: number
  taskType?: string
  category?: string
  siteKey?: string
  mediaCategory?: string
  videoQuality?: number
  extractAudio?: boolean
  extractSubtitles?: boolean
  createMenu?: boolean
  menuName?: string
  menuPath?: string
}

export const startCrawlWithConfig = (config: CrawlConfig): Promise<any> => {
  return api.post('/crawl/start', config)
}

export const identifyUrlType = (url: string): Promise<{
  url: string
  taskType: string
  confidence: number
  reason: string
  suggestions: Record<string, string[]>
}> => {
  return api.post('/crawl/identify-type', { url })
}

export interface SiteAnalyzeResult {
  success: boolean
  baseUrl?: string
  siteKey?: string
  profile?: { id: string; name: string; confidence: number; discovery: string; htmlShell: string }
  structure?: { type: string; mainSelector?: string; navRoot?: string }
  navPreview?: Array<{ title: string; href: string; childCount: number }>
  estimatedPages?: number | null
  warnings?: string[]
  error?: string
}

export const analyzeDocumentSite = (url: string): Promise<SiteAnalyzeResult> => {
  return api.post('/crawl/analyze', { url })
}

export const getStorageKeys = async (): Promise<string[]> => {
  const res = await api.get('/crawl/storage-keys') as { keys?: string[] }
  return res.keys || []
}

export interface DocumentMenuMatchResult {
  matched: boolean
  normalizedCategory: string
  siteKey: string
  menuPath: string
  suggestedName: string
  menu?: Menu
}

export const matchDocumentMenu = (
  url: string,
  category?: string,
  profileName?: string
): Promise<DocumentMenuMatchResult> => {
  return api.get('/crawl/match-menu', {
    params: { url, category, profileName },
  })
}

export const getDocumentMenus = (): Promise<Menu[]> => {
  return api.get('/crawl/document-menus')
}

export const getCategoriesInfo = (): Promise<{
  taskTypes: string[]
  docCategories: Array<{ code: string; name: string }>
  videoCategories: Array<{ code: string; name: string }>
}> => {
  return api.get('/crawl/categories-info')
}

export const importLocalDocuments = (url?: string): Promise<{ taskId: number; message: string; documentCount: number }> => {
  return api.post('/crawl/import-local', null, { params: url ? { url } : undefined })
}

export const refreshDocumentFormat = (): Promise<{ message: string; pagesRefreshed: number }> => {
  return api.post('/crawl/refresh-format', null, { timeout: 3600000 })
}

export const rebuildTaskToc = (taskId: number): Promise<{ taskId: number; message: string }> => {
  return api.post(`/crawl/tasks/${taskId}/rebuild-toc`)
}

export const getTasks = (page: number = 0, size: number = 10): Promise<{ tasks: CrawlTask[], total: number, page: number, size: number, totalPages: number }> => {
  return api.get('/crawl/tasks', { params: { page, size } })
}

export const getTask = (id: number): Promise<CrawlTask> => {
  return api.get(`/crawl/tasks/${id}`)
}

export const deleteTask = (id: number): Promise<{ message: string }> => {
  return api.delete(`/crawl/tasks/${id}`)
}

export const getTaskDocuments = (taskId: number, page: number = 0, size: number = 500): Promise<{ documents: Document[], total: number, page: number, size: number }> => {
  return api.get(`/crawl/tasks/${taskId}/documents`, { params: { page, size } })
}

export const searchDocuments = (keyword: string): Promise<Document[]> => {
  return api.get('/crawl/search', { params: { keyword } })
}

export const getTableOfContents = (taskId: number): Promise<TocItem[]> => {
  return api.get(`/crawl/tasks/${taskId}/toc`)
}

export const getDocumentById = (id: number): Promise<Document> => {
  return api.get(`/crawl/documents/${id}`)
}

export type ServiceStatus = {
  backend: { status: string; port: number }
  crawler: { status: string; port: number; url: string }
}

export const getServiceStatus = (): Promise<ServiceStatus> => {
  return api.get('/public/services')
}

// System Management APIs - Users
export const getUsers = (page: number = 0, size: number = 10): Promise<{ content: SystemUser[], totalElements: number, totalPages: number }> => {
  return api.get('/admin/users', { params: { page, size } })
}

export const getUser = (id: number): Promise<SystemUser> => {
  return api.get(`/admin/users/${id}`)
}

export const createUser = (data: UserCreateRequest): Promise<SystemUser> => {
  return api.post('/admin/users', data)
}

export const updateUser = (id: number, data: UserUpdateRequest): Promise<SystemUser> => {
  return api.put(`/admin/users/${id}`, data)
}

export const resetUserPassword = (id: number, data: PasswordResetRequest): Promise<void> => {
  return api.put(`/admin/users/${id}/password`, data)
}

export const deleteUser = (id: number): Promise<void> => {
  return api.delete(`/admin/users/${id}`)
}

// System Management APIs - Roles
export const getRoles = (): Promise<Role[]> => {
  return api.get('/admin/roles')
}

export const getRole = (id: number): Promise<Role> => {
  return api.get(`/admin/roles/${id}`)
}

export const createRole = (data: RoleCreateRequest): Promise<Role> => {
  return api.post('/admin/roles', data)
}

export const updateRole = (id: number, data: RoleUpdateRequest): Promise<Role> => {
  return api.put(`/admin/roles/${id}`, data)
}

export const deleteRole = (id: number): Promise<void> => {
  return api.delete(`/admin/roles/${id}`)
}

// System Management APIs - Permissions
export const getPermissions = (): Promise<Permission[]> => {
  return api.get('/admin/permissions')
}

export const getPermissionTree = (): Promise<Permission[]> => {
  return api.get('/admin/permissions/tree')
}

export const getPermission = (id: number): Promise<Permission> => {
  return api.get(`/admin/permissions/${id}`)
}

export const createPermission = (data: PermissionCreateRequest): Promise<Permission> => {
  return api.post('/admin/permissions', data)
}

export const updatePermission = (id: number, data: PermissionUpdateRequest): Promise<Permission> => {
  return api.put(`/admin/permissions/${id}`, data)
}

export const deletePermission = (id: number): Promise<void> => {
  return api.delete(`/admin/permissions/${id}`)
}

// System Management APIs - Menus
export const getMenus = (): Promise<Menu[]> => {
  return api.get('/admin/menus')
}

export const getMenuTree = (): Promise<Menu[]> => {
  return api.get('/admin/menus/tree')
}

export const getVisibleMenuTree = (): Promise<Menu[]> => {
  return api.get('/admin/menus/visible')
}

export const getMenu = (id: number): Promise<Menu> => {
  return api.get(`/admin/menus/${id}`)
}

export const createMenu = (data: MenuCreateRequest): Promise<Menu> => {
  return api.post('/admin/menus', data)
}

export const updateMenu = (id: number, data: MenuUpdateRequest): Promise<Menu> => {
  return api.put(`/admin/menus/${id}`, data)
}

export const deleteMenu = (id: number): Promise<void> => {
  return api.delete(`/admin/menus/${id}`)
}
