<template>
  <div class="dashboard">
    <h2 class="dashboard-title">欢迎使用文档翻译系统</h2>

    <el-alert
      v-if="serviceAlert"
      :title="serviceAlert"
      type="warning"
      show-icon
      :closable="false"
      class="service-alert"
    />
    
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon size="24" color="#409eff"><Upload /></el-icon>
              <span>新建爬取任务</span>
            </div>
          </template>
          <el-form class="crawl-form" @submit.prevent="handleStartCrawl" label-width="96px">
            <el-form-item label="目标URL">
              <el-input 
                v-model="crawlUrl" 
                placeholder="输入文档站点 URL，如 https://docs.example.com/guide/"
                @blur="identifyType"
              />
              <div v-if="identifying" class="form-hint">
                <el-text type="info">正在识别类型...</el-text>
              </div>
              <div v-else-if="typeIdentified" class="form-hint">
                <el-text type="success">
                  <el-icon><SuccessFilled /></el-icon>
                  自动识别：{{ getTaskTypeLabel(identifiedType.type) }}
                  <el-tag size="small" type="warning" v-if="identifiedType.confidence < 0.8">
                    置信度: {{ Math.round(identifiedType.confidence * 100) }}%
                  </el-tag>
                </el-text>
              </div>
            </el-form-item>

            <el-form-item label="任务类型">
              <el-radio-group v-model="taskType" @change="onTaskTypeChange" size="small">
                <el-radio value="document">
                  <el-icon><Document /></el-icon>
                  文档
                </el-radio>
                <el-radio value="video">
                  <el-icon><VideoPlay /></el-icon>
                  视频
                </el-radio>
                <el-radio value="unknown">
                  <el-icon><QuestionFilled /></el-icon>
                  其他
                </el-radio>
              </el-radio-group>
            </el-form-item>

            <template v-if="taskType === 'document'">
              <el-form-item label="页面数">
                <el-input-number v-model="maxPages" :min="1" :max="500" />
              </el-form-item>

              <el-form-item label="存储键">
                <div class="storage-key-field">
                  <el-select
                    v-model="storageParent"
                    clearable
                    filterable
                    allow-create
                    default-first-option
                    placeholder="父目录（可选）"
                    class="storage-parent-select"
                    size="small"
                    @change="storageKeyTouched = true"
                  >
                    <el-option
                      v-for="key in storageKeys"
                      :key="key"
                      :label="key"
                      :value="key"
                    />
                  </el-select>
                  <el-input
                    v-model="storageKeySegment"
                    placeholder="如 relational-reference 或 spring-data/reference"
                    size="small"
                    :class="{ 'is-error': storageKeyError }"
                    @input="storageKeyTouched = true"
                  />
                </div>
                <p v-if="storageKeyError" class="form-error">{{ storageKeyError }}</p>
                <p v-else class="form-hint">
                  保存至 crawler-service/crawled-docs/<strong>{{ effectiveStorageKey || '…' }}</strong>/
                </p>
              </el-form-item>

              <div v-if="siteAnalysis" class="site-analysis-wrap">
                <el-alert type="info" :closable="false" show-icon class="site-analysis-preview">
                  <template #title>
                    结构预览：{{ siteAnalysis.profile?.name || '未知' }}
                    <el-tag size="small" class="preview-tag">{{ siteAnalysis.profile?.discovery || 'nav-only' }}</el-tag>
                  </template>
                  <p v-if="siteAnalysis.estimatedPages" class="preview-meta">导航约 {{ siteAnalysis.estimatedPages }} 页</p>
                  <ul v-if="siteAnalysis.navPreview?.length" class="preview-nav-list">
                    <li v-for="(item, i) in siteAnalysis.navPreview.slice(0, 8)" :key="i">{{ item.title }}</li>
                  </ul>
                  <p v-for="(w, i) in siteAnalysis.warnings || []" :key="'w'+i" class="preview-warning">{{ w }}</p>
                </el-alert>
              </div>
              <p class="form-hint doc-tip">
                支持任意文档站点；系统自动检测 Profile、导航与正文，目录以原站 nav 为准。
              </p>
            </template>

            <template v-if="taskType === 'video'">
              <el-form-item label="视频分类">
                <el-select v-model="mediaCategory" placeholder="选择分类" style="width: 100%" size="small">
                  <el-option label="动漫" value="anime" />
                  <el-option label="电影" value="movie" />
                  <el-option label="电视剧" value="tv-series" />
                  <el-option label="综艺" value="variety" />
                  <el-option label="其他" value="other" />
                </el-select>
              </el-form-item>
              <el-form-item label="视频质量">
                <el-select v-model="videoQuality" placeholder="选择质量" style="width: 100%" size="small">
                  <el-option label="1080p" :value="1080" />
                  <el-option label="720p" :value="720" />
                  <el-option label="480p" :value="480" />
                </el-select>
              </el-form-item>
            </template>

            <el-button type="primary" style="width: 100%" :loading="loading" @click="handleStartCrawl">
              开始爬取
            </el-button>
            <el-button style="width: 100%; margin-top: 10px; margin-left: 0" :loading="importLoading" @click="handleImportLocal">
              导入本地文档
            </el-button>
            <el-button style="width: 100%; margin-top: 10px; margin-left: 0" :loading="refreshLoading" @click="handleRefreshFormat">
              刷新文档格式
            </el-button>
            <p class="form-hint refresh-tip">
              Spring 文档可点击「刷新文档格式」重新生成官方样式 HTML（约需数分钟）。
            </p>
          </el-form>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon size="24" color="#67c23a"><List /></el-icon>
              <span>最近任务</span>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item v-for="task in recentTasks" :key="task.id" :timestamp="formatDateTime(task.createdAt)" placement="top">
              <el-link type="primary" @click="$router.push(`/tasks/${task.id}`)">{{ task.title }}</el-link>
              <el-tag :type="getStatusType(task.status)" size="small" style="margin-left: 10px">{{ getStatusLabel(task.status) }}</el-tag>
              <div v-if="task.status === 'RUNNING' || task.status === 'PENDING'" class="task-progress">
                <el-progress
                  v-if="task.status === 'RUNNING'"
                  :percentage="task.progressPercent ?? 0"
                  :stroke-width="10"
                  striped
                  striped-flow
                />
                <p class="progress-message">
                  {{ task.progressMessage || (task.status === 'PENDING' ? '排队等待中…' : '处理中…') }}
                </p>
              </div>
            </el-timeline-item>
            <el-timeline-item v-if="recentTasks.length === 0">暂无任务</el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon size="24" color="#e6a23c"><Document /></el-icon>
              <span>搜索文档</span>
            </div>
          </template>
          <el-input v-model="searchKeyword" placeholder="输入关键词搜索文档" @keyup.enter="handleSearch">
            <template #append>
              <el-button @click="handleSearch">搜索</el-button>
            </template>
          </el-input>
          <div v-if="searchResults.length > 0" class="search-results">
            <el-link v-for="doc in searchResults" :key="doc.id" type="primary" class="search-result-link" @click="viewDocument(doc)">
              {{ doc.title }}
            </el-link>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { startCrawlWithConfig, getTasks, searchDocuments, importLocalDocuments, refreshDocumentFormat, formatApiError, getServiceStatus, identifyUrlType, analyzeDocumentSite, getStorageKeys, type SiteAnalyzeResult } from '@/api'
import { ElMessage } from 'element-plus'
import type { CrawlTask, Document as DocumentType } from '@/types'
import { Upload, List, Document, VideoPlay, QuestionFilled, SuccessFilled } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/date'
import { promptDocumentMenuIfNeeded } from '@/utils/documentMenuMatch'

interface TypeIdentification {
  type: string
  confidence: number
  reason: string
  suggestions?: Record<string, string[]>
}

const SITE_KEY_PATTERN = /^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?(\/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?)*$/

function validateStorageKey(key: string): string | null {
  const trimmed = key.trim()
  if (!trimmed) return '请输入存储键'
  if (trimmed.length > 200) return '存储键过长'
  if (trimmed.includes('..') || trimmed.startsWith('/') || trimmed.endsWith('/')) return '路径格式无效'
  if (trimmed.includes('//') || /[\\:*?"<>|]/.test(trimmed)) return '包含非法字符'
  if (!SITE_KEY_PATTERN.test(trimmed)) return '仅允许字母、数字、连字符、下划线及 / 分隔的子目录'
  return null
}

function splitStorageKey(key: string): { parent: string; segment: string } {
  const slash = key.indexOf('/')
  if (slash <= 0) {
    return { parent: '', segment: key }
  }
  return { parent: key.slice(0, slash), segment: key.slice(slash + 1) }
}

const router = useRouter()
const crawlUrl = ref('')
const maxPages = ref(100)
const loading = ref(false)
const importLoading = ref(false)
const refreshLoading = ref(false)
const recentTasks = ref<CrawlTask[]>([])
const searchKeyword = ref('')
const searchResults = ref<DocumentType[]>([])
const serviceAlert = ref('')
let serviceCheckTimer: number | undefined
let taskPollTimer: number | undefined

const identifying = ref(false)
const typeIdentified = ref(false)
const identifiedType = ref<TypeIdentification>({ type: 'unknown', confidence: 0, reason: '' })
const taskType = ref('document')
const category = ref('java')
const mediaCategory = ref('movie')
const videoQuality = ref(720)
const siteAnalysis = ref<SiteAnalyzeResult | null>(null)
const analyzingSite = ref(false)
const storageKeys = ref<string[]>([])
const storageParent = ref('')
const storageKeySegment = ref('')
const storageKeyTouched = ref(false)

const effectiveStorageKey = computed(() => {
  const segment = storageKeySegment.value.trim()
  if (!segment) return ''
  const parent = storageParent.value.trim()
  return parent ? `${parent}/${segment}` : segment
})

const storageKeyError = computed(() => {
  if (!storageKeyTouched.value && !effectiveStorageKey.value) return null
  return validateStorageKey(effectiveStorageKey.value)
})

watch(siteAnalysis, (analysis) => {
  if (!analysis?.siteKey || storageKeyTouched.value) return
  const { parent, segment } = splitStorageKey(analysis.siteKey)
  storageParent.value = parent
  storageKeySegment.value = segment
})

onMounted(() => {
  loadRecentTasks()
  loadStorageKeys()
  checkServices()
  serviceCheckTimer = window.setInterval(checkServices, 15000)
  taskPollTimer = window.setInterval(loadRecentTasks, 2000)
})

onUnmounted(() => {
  if (serviceCheckTimer) {
    window.clearInterval(serviceCheckTimer)
  }
  if (taskPollTimer) {
    window.clearInterval(taskPollTimer)
  }
})

const loadStorageKeys = async () => {
  try {
    storageKeys.value = await getStorageKeys()
  } catch (err) {
    console.error('Failed to load storage keys:', err)
  }
}

const checkServices = async () => {
  try {
    const status = await getServiceStatus()
    const issues: string[] = []
    if (status.backend.status !== 'ok') {
      issues.push('后端 (8080) 未就绪')
    }
    if (status.crawler.status !== 'ok') {
      issues.push('爬虫服务 (3000) 未就绪')
    }
    serviceAlert.value = issues.length > 0
      ? `${issues.join('、')}。请运行 scripts/start-all.ps1 或分别启动三个服务后再操作。`
      : ''
  } catch {
    serviceAlert.value = '无法连接后端 (8080)。请确认 backend 已启动，并通过 npm run dev (5173) 访问前端。'
  }
}

const loadRecentTasks = async () => {
  try {
    const result = await getTasks(0, 5)
    recentTasks.value = result.tasks
  } catch (err) {
    console.error('Failed to load tasks:', err)
  }
}

const identifyType = async () => {
  if (!crawlUrl.value) return
  
  identifying.value = true
  siteAnalysis.value = null
  if (!storageKeyTouched.value) {
    storageParent.value = ''
    storageKeySegment.value = ''
  }
  try {
    const result = await identifyUrlType(crawlUrl.value)
    identifiedType.value = {
      type: result.taskType,
      confidence: result.confidence,
      reason: result.reason,
      suggestions: result.suggestions
    }
    typeIdentified.value = true
    
    if (result.taskType && result.taskType !== 'unknown') {
      taskType.value = result.taskType
      if (result.confidence >= 0.8 && result.suggestions) {
        if (result.taskType === 'document' && result.suggestions.document) {
          category.value = result.suggestions.document[0] || 'java'
        } else if (result.taskType === 'video' && result.suggestions.video) {
          mediaCategory.value = result.suggestions.video[0] || 'movie'
        }
      }
    }

    if (taskType.value === 'document' && crawlUrl.value.trim()) {
      analyzingSite.value = true
      try {
        siteAnalysis.value = await analyzeDocumentSite(crawlUrl.value.trim())
        if (siteAnalysis.value?.estimatedPages && siteAnalysis.value.estimatedPages > maxPages.value) {
          maxPages.value = Math.min(500, siteAnalysis.value.estimatedPages + 20)
        }
      } catch (err) {
        console.error('Site analyze failed:', err)
      } finally {
        analyzingSite.value = false
      }
    }
  } catch (err) {
    console.error('Failed to identify type:', err)
  } finally {
    identifying.value = false
  }
}

const onTaskTypeChange = (type: string) => {
  if (type === 'document') {
    category.value = 'java'
  } else if (type === 'video') {
    mediaCategory.value = 'movie'
    videoQuality.value = 720
  }
}

const handleStartCrawl = async () => {
  if (!crawlUrl.value) {
    ElMessage.warning('请输入目标URL')
    return
  }

  const siteKey = effectiveStorageKey.value
  if (taskType.value === 'document') {
    storageKeyTouched.value = true
    const keyError = validateStorageKey(siteKey)
    if (keyError) {
      ElMessage.warning(keyError)
      return
    }
  }
  
  loading.value = true
  try {
    const menuOptions = await promptDocumentMenuIfNeeded(crawlUrl.value.trim(), {
      taskType: taskType.value,
      category: siteKey || category.value,
      profileName: siteAnalysis.value?.profile?.name,
    })

    const result = await startCrawlWithConfig({
      url: crawlUrl.value.trim(),
      maxPages: taskType.value === 'document' ? maxPages.value : 5,
      taskType: taskType.value,
      siteKey: taskType.value === 'document' ? siteKey : undefined,
      category: taskType.value === 'document' ? siteKey : undefined,
      mediaCategory: taskType.value === 'video' ? mediaCategory.value : undefined,
      videoQuality: taskType.value === 'video' ? videoQuality.value : undefined,
      ...menuOptions,
    })
    ElMessage.success('爬取任务已启动')
    router.push(`/tasks/${result.taskId}`)
  } catch (err: unknown) {
    ElMessage.error('启动任务失败: ' + formatApiError(err))
  } finally {
    loading.value = false
  }
}

const handleImportLocal = async () => {
  importLoading.value = true
  try {
    const result = await importLocalDocuments(crawlUrl.value)
    ElMessage.success(`已导入 ${result.documentCount} 篇文档`)
    await loadRecentTasks()
    router.push(`/tasks/${result.taskId}`)
  } catch (err: unknown) {
    ElMessage.error('导入失败: ' + formatApiError(err))
  } finally {
    importLoading.value = false
  }
}

const handleRefreshFormat = async () => {
  refreshLoading.value = true
  try {
    const result = await refreshDocumentFormat()
    ElMessage.success(`已刷新 ${result.pagesRefreshed} 篇文档格式`)
    await loadRecentTasks()
  } catch (err: unknown) {
    ElMessage.error('刷新失败: ' + formatApiError(err))
  } finally {
    refreshLoading.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value) return
  try {
    searchResults.value = await searchDocuments(searchKeyword.value)
  } catch (err) {
    console.error('Search failed:', err)
  }
}

const viewDocument = (doc: DocumentType) => {
  if (doc.taskId) {
    router.push(`/documents/${doc.taskId}`)
  } else {
    ElMessage.info('无法定位文档所属任务')
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    PENDING: 'warning',
    RUNNING: '',
    COMPLETED: 'success',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '等待中',
    RUNNING: '进行中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[status] || status
}

const getTaskTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    document: '文档',
    video: '视频',
    audio: '音频',
    image: '图片',
    mixed: '混合',
    unknown: '未知',
  }
  return map[type] || type
}
</script>

<style scoped>
.dashboard-title {
  margin-bottom: 20px;
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 600;
}

.service-alert {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
}

.card-header span {
  color: inherit;
}

.crawl-form :deep(.el-form-item__label) {
  white-space: nowrap;
  color: var(--text-regular);
}

.form-hint {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}

.doc-tip,
.refresh-tip {
  margin: 0;
}

.refresh-tip {
  margin-top: 10px;
}

.form-error {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}

.storage-key-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.storage-parent-select {
  width: 100%;
}

.site-analysis-wrap {
  margin-bottom: 12px;
}

.site-analysis-preview {
  background-color: var(--input-bg) !important;
  border-color: var(--border-color) !important;
}

.site-analysis-preview :deep(.el-alert__title) {
  color: var(--text-primary);
}

.site-analysis-preview :deep(.el-alert__description),
.site-analysis-preview :deep(.el-alert__content) {
  color: var(--text-regular);
}

.preview-tag {
  margin-left: 8px;
}

.preview-meta {
  margin: 4px 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.preview-nav-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--text-regular);
}

.preview-warning {
  margin: 4px 0;
  color: var(--el-color-warning);
  font-size: 12px;
}

.task-progress {
  margin-top: 8px;
  max-width: 280px;
}

.progress-message {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.search-results {
  margin-top: 15px;
}

.search-result-link {
  display: block;
  margin-bottom: 8px;
}
</style>
