<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>爬取任务列表</h2>
      <el-button type="primary" @click="openNewTaskDialog">
        <el-icon><Plus /></el-icon>
        新建任务
      </el-button>
    </div>

    <el-table :data="tasks" style="width: 100%" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="任务名称" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/tasks/${row.id}`)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="url" label="URL" min-width="250" show-overflow-tooltip />
      <el-table-column prop="taskType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="getTaskTypeColor(row.taskType)" size="small">
            {{ getTaskTypeLabel(row.taskType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="120">
        <template #default="{ row }">
          <span v-if="row.taskType === 'document'">{{ row.category || '-' }}</span>
          <span v-else-if="row.taskType === 'video'">{{ row.mediaCategory || '-' }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/tasks/${row.id}`)">查看</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 20px">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- New Task Dialog -->
    <el-dialog v-model="showNewTaskDialog" title="新建爬取任务" width="600px" @close="resetForm">
      <el-form :model="newTaskForm" label-width="100px">
        <el-form-item label="目标URL">
          <el-input 
            v-model="newTaskForm.url" 
            placeholder="输入要爬取的URL地址"
            @blur="identifyType"
          />
          <div v-if="identifying" style="margin-top: 8px">
            <el-text type="info">正在识别类型...</el-text>
          </div>
          <div v-else-if="typeIdentified" style="margin-top: 8px">
            <el-text type="success">
              <el-icon><SuccessFilled /></el-icon>
              自动识别：{{ getTaskTypeLabel(identifiedType.type) }}
              <el-tag size="small" type="warning" v-if="identifiedType.confidence < 0.8">
                置信度: {{ Math.round(identifiedType.confidence * 100) }}%
              </el-tag>
              <el-tooltip :content="identifiedType.reason" placement="top">
                <el-icon style="margin-left: 4px"><InfoFilled /></el-icon>
              </el-tooltip>
            </el-text>
          </div>
        </el-form-item>

        <el-form-item label="任务类型">
          <el-radio-group v-model="newTaskForm.taskType" @change="onTaskTypeChange">
            <el-radio value="document">
              <el-icon><Document /></el-icon>
              文档爬取
            </el-radio>
            <el-radio value="video">
              <el-icon><VideoPlay /></el-icon>
              视频爬取
            </el-radio>
            <el-radio value="unknown">
              <el-icon><QuestionFilled /></el-icon>
              其他类型
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 文档类型配置 -->
        <template v-if="newTaskForm.taskType === 'document'">
          <el-form-item label="文档分类">
            <el-select v-model="newTaskForm.category" placeholder="选择文档分类" style="width: 100%">
              <el-option label="Java" value="java" />
              <el-option label="Spring" value="spring" />
              <el-option label="Spring Boot" value="spring-boot" />
              <el-option label="Spring Cloud" value="spring-cloud" />
              <el-option label="Spring MVC" value="spring-mvc" />
              <el-option label="MySQL" value="mysql" />
              <el-option label="Oracle" value="oracle" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="最大页面数">
            <el-input-number v-model="newTaskForm.maxPages" :min="1" :max="500" />
          </el-form-item>
        </template>

        <!-- 视频类型配置 -->
        <template v-if="newTaskForm.taskType === 'video'">
          <el-form-item label="视频分类">
            <el-select v-model="newTaskForm.mediaCategory" placeholder="选择视频分类" style="width: 100%">
              <el-option label="动漫" value="anime" />
              <el-option label="短剧" value="short-drama" />
              <el-option label="电视剧" value="tv-series" />
              <el-option label="电影" value="movie" />
              <el-option label="综艺" value="variety" />
              <el-option label="教育" value="education" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="视频质量">
            <el-select v-model="newTaskForm.videoQuality" placeholder="选择视频质量" style="width: 100%">
              <el-option label="1080p (高清)" :value="1080" />
              <el-option label="720p (标清)" :value="720" />
              <el-option label="480p" :value="480" />
              <el-option label="360p" :value="360" />
            </el-select>
          </el-form-item>
          <el-form-item label="提取音频">
            <el-switch v-model="newTaskForm.extractAudio" />
          </el-form-item>
          <el-form-item label="提取字幕">
            <el-switch v-model="newTaskForm.extractSubtitles" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showNewTaskDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { deleteTask, getTasks, startCrawlWithConfig, identifyUrlType, analyzeDocumentSite, formatApiError, type SiteAnalyzeResult } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CrawlTask } from '@/types'
import { Document, VideoPlay, Plus, SuccessFilled, InfoFilled, QuestionFilled } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/date'
import { promptDocumentMenuIfNeeded } from '@/utils/documentMenuMatch'

interface TypeIdentification {
  type: string
  confidence: number
  reason: string
  suggestions?: Record<string, string[]>
}

const tasks = ref<CrawlTask[]>([])
const loading = ref(false)
const showNewTaskDialog = ref(false)
const submitting = ref(false)
const identifying = ref(false)
const typeIdentified = ref(false)
const identifiedType = ref<TypeIdentification>({ type: 'unknown', confidence: 0, reason: '' })
const siteAnalysis = ref<SiteAnalyzeResult | null>(null)
let refreshTimer: number | undefined

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const newTaskForm = ref({
  url: '',
  maxPages: 5,
  taskType: 'document',
  category: 'java',
  mediaCategory: 'movie',
  videoQuality: 720,
  extractAudio: false,
  extractSubtitles: false,
})

const openNewTaskDialog = () => {
  showNewTaskDialog.value = true
  typeIdentified.value = false
}

const resetForm = () => {
  newTaskForm.value = {
    url: '',
    maxPages: 5,
    taskType: 'document',
    category: 'java',
    mediaCategory: 'movie',
    videoQuality: 720,
    extractAudio: false,
    extractSubtitles: false,
  }
  typeIdentified.value = false
  siteAnalysis.value = null
  identifiedType.value = { type: 'unknown', confidence: 0, reason: '' }
}

const identifyType = async () => {
  if (!newTaskForm.value.url) return
  
  identifying.value = true
  siteAnalysis.value = null
  try {
    const result = await identifyUrlType(newTaskForm.value.url)
    identifiedType.value = {
      type: result.taskType,
      confidence: result.confidence,
      reason: result.reason,
      suggestions: result.suggestions
    }
    typeIdentified.value = true
    
    // 自动设置任务类型
    if (result.taskType && result.taskType !== 'unknown') {
      newTaskForm.value.taskType = result.taskType
      
      // 如果置信度高，自动选择建议分类
      if (result.confidence >= 0.8 && result.suggestions) {
        if (result.taskType === 'document' && result.suggestions.document) {
          newTaskForm.value.category = result.suggestions.document[0] || 'java'
        } else if (result.taskType === 'video' && result.suggestions.video) {
          newTaskForm.value.mediaCategory = result.suggestions.video[0] || 'movie'
        }
      }
    }

    if (newTaskForm.value.taskType === 'document' && newTaskForm.value.url.trim()) {
      try {
        siteAnalysis.value = await analyzeDocumentSite(newTaskForm.value.url.trim())
        if (siteAnalysis.value?.estimatedPages && siteAnalysis.value.estimatedPages > newTaskForm.value.maxPages) {
          newTaskForm.value.maxPages = Math.min(500, siteAnalysis.value.estimatedPages + 20)
        }
      } catch {
        // 站点分析失败时继续展示识别结果
      }
    }
  } catch {
    // URL 类型识别失败时不阻塞表单
  } finally {
    identifying.value = false
  }
}

const onTaskTypeChange = (type: string) => {
  // 用户手动修改类型时，重置分类
  if (type === 'document') {
    newTaskForm.value.category = 'java'
  } else if (type === 'video') {
    newTaskForm.value.mediaCategory = 'movie'
  }
}

onMounted(() => {
  loadTasks()
  refreshTimer = window.setInterval(loadTasks, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

const loadTasks = async () => {
  loading.value = true
  try {
    const result = await getTasks(currentPage.value - 1, pageSize.value)
    tasks.value = result.tasks
    total.value = result.total
  } catch {
    // 任务列表加载失败时不阻塞页面
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadTasks()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadTasks()
}

const handleCreateTask = async () => {
  if (!newTaskForm.value.url) {
    ElMessage.warning('请输入目标URL')
    return
  }
  
  submitting.value = true
  try {
    const menuOptions = await promptDocumentMenuIfNeeded(newTaskForm.value.url.trim(), {
      taskType: newTaskForm.value.taskType,
      category: siteAnalysis.value?.siteKey || newTaskForm.value.category,
      profileName: siteAnalysis.value?.profile?.name,
    })

    await startCrawlWithConfig({
      url: newTaskForm.value.url,
      maxPages: newTaskForm.value.maxPages,
      taskType: newTaskForm.value.taskType,
      category: newTaskForm.value.taskType === 'document'
        ? (siteAnalysis.value?.siteKey || newTaskForm.value.category)
        : undefined,
      mediaCategory: newTaskForm.value.taskType === 'video' ? newTaskForm.value.mediaCategory : undefined,
      videoQuality: newTaskForm.value.taskType === 'video' ? newTaskForm.value.videoQuality : undefined,
      extractAudio: newTaskForm.value.taskType === 'video' ? newTaskForm.value.extractAudio : undefined,
      extractSubtitles: newTaskForm.value.taskType === 'video' ? newTaskForm.value.extractSubtitles : undefined,
      ...menuOptions,
    })
    ElMessage.success('任务创建成功')
    showNewTaskDialog.value = false
    loadTasks()
  } catch (err: unknown) {
    ElMessage.error('创建任务失败: ' + formatApiError(err))
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除此任务吗？', '提示', { type: 'warning' })
    await deleteTask(id)
    ElMessage.success('删除成功')
    loadTasks()
  } catch {
    // User cancelled
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

const getTaskTypeColor = (type: string) => {
  const map: Record<string, string> = {
    document: '',
    video: 'warning',
    audio: 'success',
    image: 'info',
    mixed: 'danger',
    unknown: 'info',
  }
  return map[type] || 'info'
}
</script>

<style scoped>
h2 {
  color: var(--text-primary);
  margin-top: 0;
  margin-bottom: 20px;
}
</style>
