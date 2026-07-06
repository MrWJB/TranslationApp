<template>
  <div>
    <el-page-header @back="$router.back()" :title="'任务详情'" style="margin-bottom: 20px" />

    <el-card v-if="task" class="content-card" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0">{{ task.title }}</h3>
          <el-tag :type="getStatusType(task.status)" size="large">{{ getStatusLabel(task.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="URL">{{ task.url }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(task.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="showTaskProgress" style="margin-top: 16px">
        <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px">
          <span>{{ progressPhaseLabel(task.progressPhase) }}</span>
          <span v-if="task.status === 'RUNNING'">{{ task.progressCurrent ?? 0 }} / {{ task.progressTotal || task.maxPages || '?' }}</span>
          <span v-else style="color: var(--text-secondary)">排队等待中</span>
        </div>
        <el-progress
          v-if="task.status === 'RUNNING'"
          :percentage="task.progressPercent ?? 0"
          :stroke-width="14"
          striped
          striped-flow
          :duration="10"
        />
        <p v-if="taskProgressText" style="margin: 10px 0 0; color: var(--text-secondary); font-size: 13px">
          {{ taskProgressText }}
        </p>
      </div>

      <el-alert v-if="task.status === 'FAILED'" :title="task.errorMessage" type="error" style="margin-top: 12px" :closable="false" />

      <el-alert
        v-if="qualityReportParsed && task.status === 'COMPLETED'"
        :type="qualityReportAlertType"
        style="margin-top: 12px"
        :closable="false"
        show-icon
      >
        <template #title>爬取质量报告</template>
        <p style="margin: 4px 0; font-size: 13px">
          Profile: {{ qualityReportParsed.profileId }}
          · 已爬 {{ qualityReportParsed.crawledPages }} 页
          · 排除 {{ qualityReportParsed.excludedUrls }} 个 URL
          <span v-if="qualityReportParsed.emptyPages"> · 空正文 {{ qualityReportParsed.emptyPages }} 页</span>
        </p>
        <p v-if="qualityReportParsed.navCoverage != null" style="margin: 4px 0; font-size: 13px">
          导航覆盖率: {{ Math.round(qualityReportParsed.navCoverage * 100) }}%
          ({{ qualityReportParsed.matchedNavNodes }}/{{ qualityReportParsed.totalNavNodes }})
        </p>
        <p v-if="qualityReportParsed.navCoverage != null && qualityReportParsed.navCoverage < 0.8" style="margin: 4px 0; font-size: 12px">
          结构可能不完整，建议增大 maxPages 或检查站点是否需专用 Profile。
        </p>
      </el-alert>
    </el-card>

    <!-- Structured Document View with TOC -->
    <el-row v-if="documents.length > 0 && task?.status === 'COMPLETED'" :gutter="20" style="margin-top: 20px">
      <!-- Table of Contents Sidebar -->
      <el-col :span="6">
        <el-card class="content-card toc-card">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <h3 style="margin: 0">文档目录</h3>
              <el-button
                size="small"
                :loading="rebuildTocLoading"
                @click="handleRebuildToc"
              >
                重建目录
              </el-button>
            </div>
          </template>
          <el-tree
            ref="tocTreeRef"
            :data="tocData"
            :props="treeProps"
            node-key="nodeKey"
            highlight-current
            :expand-on-click-node="false"
            :current-node-key="currentTocNodeKey"
            :default-expanded-keys="expandedTocKeys"
            :indent="18"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <el-tooltip
                v-if="data.available === false && tocUnavailableTooltip(data)"
                :content="tocUnavailableTooltip(data)"
                placement="right"
                :show-after="400"
              >
                <span
                  class="custom-tree-node"
                  :class="tocNodeClass(data)"
                  @click.stop="handleNodeClick(data)"
                >
                  <el-icon v-if="data.level === 0" style="margin-right: 4px"><DocumentIcon /></el-icon>
                  <el-icon v-else-if="data.level <= 2" style="margin-right: 4px"><Folder /></el-icon>
                  <el-icon v-else style="margin-right: 4px"><Tickets /></el-icon>
                  <span class="custom-tree-node__title">{{ node.label }}</span>
                  <el-tag
                    v-if="data.available === false && tocUnavailableBadge(data)"
                    size="small"
                    type="info"
                    class="custom-tree-node__badge"
                  >
                    {{ tocUnavailableBadge(data) }}
                  </el-tag>
                </span>
              </el-tooltip>
              <span
                v-else
                class="custom-tree-node"
                :class="tocNodeClass(data)"
                @click.stop="handleNodeClick(data)"
              >
                <el-icon v-if="data.level === 0" style="margin-right: 4px"><DocumentIcon /></el-icon>
                <el-icon v-else-if="data.level <= 2" style="margin-right: 4px"><Folder /></el-icon>
                <el-icon v-else style="margin-right: 4px"><Tickets /></el-icon>
                <span class="custom-tree-node__title">{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      
      <!-- Document Content -->
      <el-col :span="18">
        <el-card class="content-card">
          <template #header>
            <div>
              <el-breadcrumb v-if="breadcrumbPath.length" separator=">" class="doc-breadcrumb">
                <el-breadcrumb-item v-for="(crumb, index) in breadcrumbPath" :key="crumb.id ?? crumb.localPath ?? index">
                  <span v-if="index === breadcrumbPath.length - 1">{{ crumb.title }}</span>
                  <a v-else href="#" class="doc-breadcrumb-link" @click.prevent="handleNodeClick(crumb)">{{ crumb.title }}</a>
                </el-breadcrumb-item>
              </el-breadcrumb>
              <div class="doc-header-row">
                <h3>{{ currentDoc?.title || documents[0]?.title }}</h3>
                <div class="doc-header-actions">
                  <el-radio-group v-model="viewMode" size="small">
                    <el-radio-button label="translated">中文翻译</el-radio-button>
                    <el-radio-button label="original">英文原文</el-radio-button>
                    <el-radio-button label="compare">对照查看</el-radio-button>
                  </el-radio-group>
                </div>
              </div>
            </div>
          </template>
          
          <!-- Single view mode -->
          <div v-if="viewMode !== 'compare'" class="iframe-panel">
            <iframe
              v-if="viewMode === 'translated' && translatedIframeSrc"
              :src="translatedIframeSrc"
              class="document-iframe"
            />
            <iframe
              v-else-if="viewMode === 'translated' && originalIframeSrc"
              :src="originalIframeSrc"
              class="document-iframe"
            />
            <iframe
              v-else-if="viewMode === 'original' && originalIframeSrc"
              :src="originalIframeSrc"
              class="document-iframe"
            />
            <div v-else-if="currentDoc?.translatedContent && viewMode === 'translated'" class="document-content html-content" v-html="currentDoc.translatedContent" />
            <div v-else-if="currentDoc && !originalIframeSrc" class="document-content html-content" style="text-align: center; padding: 40px; color: #909399">
              <p>无法加载文档文件：{{ currentDoc.localPath || '缺少 localPath' }}</p>
              <p style="font-size: 12px; margin-top: 8px">请确认 crawler-service/crawled-docs 目录存在且后端已重启</p>
            </div>
            <div v-else-if="currentDoc" class="document-content html-content" v-html="displayContent" />
            <div v-else class="document-content html-content" style="text-align: center; padding: 40px; color: #909399">
              <p>请选择左侧文档查看内容</p>
            </div>
            <el-alert
              v-if="viewMode === 'translated' && currentDoc?.localPath && !currentDoc?.translatedLocalPath"
              type="info"
              title="翻译内容尚未生成（请配置 DEEPL_API_KEY 后重新爬取）"
              :closable="false"
              style="position: absolute; top: 12px; left: 12px; right: 12px; z-index: 2"
            />
          </div>
          
          <!-- Compare view mode -->
          <el-row v-else :gutter="20" class="compare-row">
            <el-col :span="12">
              <h4 style="margin-bottom: 10px; color: var(--text-secondary)">英文原文</h4>
              <iframe v-if="originalIframeSrc" :src="originalIframeSrc" class="document-iframe compare-iframe" />
              <div v-else class="document-content html-content" v-html="currentDoc?.originalContent" />
            </el-col>
            <el-col :span="12">
              <h4 style="margin-bottom: 10px; color: var(--text-secondary)">中文翻译</h4>
              <iframe v-if="translatedIframeSrc" :src="translatedIframeSrc" class="document-iframe compare-iframe" />
              <div v-else-if="currentDoc?.translatedContent" class="document-content html-content" v-html="currentDoc.translatedContent" />
              <el-empty v-else description="翻译内容尚未生成" />
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- Fallback: Simple Documents List for old data -->
    <el-card v-if="documents.length > 0 && task?.status === 'COMPLETED' && documents[0]?.sectionLevel === undefined" class="content-card" style="margin-top: 20px">
      <template #header>
        <h3>翻译文档 ({{ documents.length }})</h3>
      </template>
      
      <el-collapse v-model="activeDoc">
        <el-collapse-item v-for="doc in documents" :key="doc.id" :name="doc.id">
          <template #title>
            <div style="display: flex; justify-content: space-between; width: 100%; align-items: center">
              <span>{{ doc.title }}</span>
              <el-link :href="doc.url" target="_blank" type="info" size="small" @click.stop>原文</el-link>
            </div>
          </template>
          
          <el-tabs type="border-card">
            <el-tab-pane label="中文翻译">
              <div class="document-content">{{ doc.translatedContent }}</div>
            </el-tab-pane>
            <el-tab-pane label="英文原文">
              <div class="document-content">{{ doc.originalContent }}</div>
            </el-tab-pane>
          </el-tabs>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getTask, getTaskDocuments, getTableOfContents, getDocumentById, rebuildTaskToc, formatApiError } from '@/api'
import { Document as DocumentIcon, Folder } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { CrawlTask, Document } from '@/types'
import { useThemeStore } from '@/stores/theme'
import {
  assignTocNodeKeys,
  findFirstTocNodeWithPath,
  findTocNodeByLocalPath,
  findTocNodeByUrl,
  findTocPath,
  pathsMatch,
  tocAncestorKeys,
  type TocNavNode,
} from '@/utils/tocNavigation'
import {
  isNavFolderNode,
  isExternalLinkNode,
  openExternalTocLink,
  resolveExternalUrl,
  resolveUnavailableReason,
  unavailableReasonLabel,
  unavailableReasonMessage,
} from '@/utils/tocUnavailable'
import type { ElTree } from 'element-plus'
import { formatDate } from '@/utils/date'

const route = useRoute()
const taskId = Number(route.params.id)
const themeStore = useThemeStore()

const task = ref<CrawlTask | null>(null)
const documents = ref<Document[]>([])
const tocData = ref<any[]>([])
const currentDoc = ref<Document | null>(null)
const viewMode = ref<'translated' | 'original' | 'compare'>('original')
const activeDoc = ref<number | null>(null)
const rebuildTocLoading = ref(false)
const tocTreeRef = ref<InstanceType<typeof ElTree> | null>(null)
const currentTocNodeKey = ref<string | null>(null)
let pollTimer: number | undefined

const qualityReportParsed = computed(() => {
  if (!task.value?.qualityReport) return null
  try {
    return JSON.parse(task.value.qualityReport) as {
      profileId?: string
      navCoverage?: number | null
      matchedNavNodes?: number
      totalNavNodes?: number
      emptyPages?: number
      excludedUrls?: number
      crawledPages?: number
    }
  } catch {
    return null
  }
})

const qualityReportAlertType = computed(() => {
  const cov = qualityReportParsed.value?.navCoverage
  if (cov == null) return 'info'
  return cov >= 0.8 ? 'success' : 'warning'
})

const showTaskProgress = computed(() => {
  if (!task.value) return false
  return task.value.status === 'RUNNING' || task.value.status === 'PENDING'
})

const taskProgressText = computed(() => {
  if (!task.value) return ''
  if (task.value.progressMessage) return task.value.progressMessage
  if (task.value.status === 'PENDING') return '排队等待中…'
  return ''
})

const progressPhaseLabel = (phase?: string) => {
  const map: Record<string, string> = {
    crawling: '正在爬取页面',
    processing: '正在保存文档',
    translating: '正在翻译',
    building_toc: '正在构建目录',
    completed: '已完成',
  }
  return (phase && map[phase]) || '处理中'
}

const originalIframeSrc = computed(() => {
  const doc = currentDoc.value
  if (!doc?.localPath) return ''
  return `/api/crawl/docs?path=${encodeURIComponent(doc.localPath)}&theme=${themeStore.mode}`
})

const translatedIframeSrc = computed(() => {
  if (!currentDoc.value?.translatedLocalPath) return ''
  return `/api/crawl/docs?path=${encodeURIComponent(currentDoc.value.translatedLocalPath)}&theme=${themeStore.mode}`
})

const sendThemeToIframes = () => {
  document.querySelectorAll('.document-iframe').forEach((iframe) => {
    const frame = iframe as HTMLIFrameElement
    if (frame.contentWindow) {
      frame.contentWindow.postMessage({ type: 'theme-change', theme: themeStore.mode }, '*')
    }
  })
}

watch(() => themeStore.mode, sendThemeToIframes)
watch(() => currentDoc.value, () => {
  setTimeout(sendThemeToIframes, 300)
  syncTocSelectionFromCurrentDoc()
})

const breadcrumbPath = computed(() => {
  if (tocData.value.length === 0 || !currentDoc.value) {
    return []
  }
  return findTocPath(tocData.value, {
    localPath: currentDoc.value.localPath,
    url: currentDoc.value.url,
    id: currentDoc.value.id,
  })
})

const expandedTocKeys = computed(() => tocAncestorKeys(breadcrumbPath.value))

watch(breadcrumbPath, () => {
  expandTocAncestors()
})

const expandTocAncestors = () => {
  nextTick(() => {
    const tree = tocTreeRef.value
    if (!tree) return
    for (const key of expandedTocKeys.value) {
      const node = tree.getNode(key)
      if (node) node.expanded = true
    }
  })
}

const syncTocSelectionFromCurrentDoc = () => {
  const doc = currentDoc.value
  if (!doc) {
    return
  }

  const tocNode = doc.localPath
    ? findTocNodeByLocalPath(tocData.value, doc.localPath)
    : doc.url
      ? findTocNodeByUrl(tocData.value, doc.url)
      : null
  if (!tocNode?.nodeKey) {
    return
  }

  currentTocNodeKey.value = tocNode.nodeKey
  nextTick(() => {
    tocTreeRef.value?.setCurrentKey(tocNode.nodeKey!)
    expandTocAncestors()
    scrollActiveTocNodeIntoView()
  })
}

const scrollActiveTocNodeIntoView = () => {
  nextTick(() => {
    const activeNode = tocTreeRef.value?.$el?.querySelector('.el-tree-node.is-current')
    activeNode?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

const resolveDocumentForPath = async (targetPath: string): Promise<Document | null> => {
  const docByPath = documents.value.find((doc) => pathsMatch(doc.localPath, targetPath))
  if (docByPath) {
    return docByPath
  }

  const tocNode = findTocNodeByLocalPath(tocData.value, targetPath)
  if (!tocNode) {
    return null
  }

  let doc = documents.value.find((d) => d.id === tocNode.id)
  if (!doc && tocNode.localPath) {
    doc = docFromTocNode(tocNode)
  }
  if (!doc && tocNode.id) {
    try {
      doc = await getDocumentById(tocNode.id)
    } catch {
      return null
    }
  }
  return doc ?? null
}

const handleIframeMessage = async (event: MessageEvent) => {
  if (event.data?.type !== 'navigate') return
  const targetPath = event.data.path as string
  if (!targetPath) return

  const doc = await resolveDocumentForPath(targetPath)
  if (doc) {
    currentDoc.value = doc
    syncTocSelectionFromCurrentDoc()
  }
}

const treeProps = {
  children: 'children',
  label: 'title',
}

const tocUnavailableTooltip = (data: TocNavNode) => {
  if (data.available !== false) return ''
  const reason = resolveUnavailableReason(data)
  return reason ? unavailableReasonMessage(data, reason) : ''
}

const tocUnavailableBadge = (data: TocNavNode) => {
  if (data.available !== false) return ''
  const reason = resolveUnavailableReason(data)
  return reason ? unavailableReasonLabel(reason) : ''
}

const tocNodeClass = (data: TocNavNode) => ({
  'is-unavailable': data.available === false && !isNavFolderNode(data) && !isExternalLinkNode(data),
  'is-nav-folder': isNavFolderNode(data),
  'is-external': isExternalLinkNode(data),
})

const displayContent = computed(() => {
  if (!currentDoc.value) return ''
  if (viewMode.value === 'translated') {
    return currentDoc.value.translatedContent || ''
  }
  return currentDoc.value.originalContent || ''
})

onMounted(async () => {
  window.addEventListener('message', handleIframeMessage)
  await loadTask()
  await loadDocuments()
  startPollingIfRunning()
})

onUnmounted(() => {
  window.removeEventListener('message', handleIframeMessage)
  if (pollTimer) clearInterval(pollTimer)
})

const startPollingIfRunning = () => {
  if (pollTimer) clearInterval(pollTimer)
  const status = task.value?.status
  if (status !== 'RUNNING' && status !== 'PENDING') {
    if (status === 'COMPLETED') {
      finishTaskLoad()
    }
    return
  }
  pollTimer = window.setInterval(async () => {
    await loadTask()
    const nextStatus = task.value?.status
    if (nextStatus !== 'RUNNING' && nextStatus !== 'PENDING') {
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = undefined
      await finishTaskLoad()
    }
  }, 2000)
}

const finishTaskLoad = async () => {
  await loadDocuments()
  await loadTableOfContents()
  const firstTocNode = findFirstTocNodeWithPath(tocData.value)
  if (firstTocNode) {
    await handleNodeClick(firstTocNode)
  } else if (documents.value.length > 0) {
    currentDoc.value = documents.value[0]
    syncTocSelectionFromCurrentDoc()
  }
}

const loadTask = async () => {
  try {
    task.value = await getTask(taskId)
  } catch {
    // 任务加载失败时不阻塞页面
  }
}

const loadDocuments = async () => {
  try {
    const result = await getTaskDocuments(taskId)
    documents.value = result.documents || []
  } catch {
    // 文档列表加载失败时使用空列表
  }
}

const loadTableOfContents = async () => {
  try {
    const toc = await getTableOfContents(taskId)
    if (toc && toc.length > 0) {
      const parsed = typeof toc === 'string' ? JSON.parse(toc) : toc
      tocData.value = assignTocNodeKeys(parsed)
    } else {
      buildTocFallback()
    }
  } catch {
    buildTocFallback()
  }
}

const buildTocFallback = () => {
  tocData.value = assignTocNodeKeys(
    documents.value.map((doc) => ({
      id: doc.id,
      title: doc.title,
      level: doc.sectionLevel || 0,
      url: doc.url,
      localPath: doc.localPath,
      translatedLocalPath: doc.translatedLocalPath,
      available: true,
      children: [],
    })),
  )
}

const findFirstTocNode = findFirstTocNodeWithPath

const docFromTocNode = (data: TocNavNode): Document => ({
  id: data.id ?? 0,
  title: data.title,
  url: resolveExternalUrl(data) ?? data.url ?? '',
  localPath: data.localPath ?? '',
  translatedLocalPath: data.translatedLocalPath,
  external: data.external,
  sortOrder: 0,
  sectionLevel: data.level ?? 0,
  sectionId: '',
  parentDocumentId: null,
  originalContent: '',
  translatedContent: '',
  createdAt: '',
})

const handleNodeClick = async (data: TocNavNode) => {
  if (openExternalTocLink(data)) {
    return
  }

  if (data.available === false) {
    const reason = resolveUnavailableReason(data)
    if (reason === 'nav-folder') {
      if (data.nodeKey) {
        const treeNode = tocTreeRef.value?.getNode(data.nodeKey)
        if (treeNode) treeNode.expanded = !treeNode.expanded
      }
      return
    }
    ElMessage.info(reason ? unavailableReasonMessage(data, reason) : '该章节不可用')
    return
  }
  if (!data?.localPath && !data?.id) {
    if (data.children?.length) {
      return
    }
    return
  }

  let doc = documents.value.find((d) => d.id === data.id)
  if (!doc && data.localPath) {
    doc = docFromTocNode(data)
  }
  if (!doc && data.id) {
    try {
      doc = await getDocumentById(data.id)
    } catch {
      return
    }
  }
  if (doc) {
    currentDoc.value = doc
    if (data.nodeKey) {
      currentTocNodeKey.value = data.nodeKey
      nextTick(() => {
        tocTreeRef.value?.setCurrentKey(data.nodeKey!)
        expandTocAncestors()
        scrollActiveTocNodeIntoView()
      })
    }
  }
}

const handleRebuildToc = async () => {
  rebuildTocLoading.value = true
  try {
    await rebuildTaskToc(taskId)
    await loadTask()
    await loadDocuments()
    await loadTableOfContents()
    const firstTocNode = findFirstTocNode(tocData.value)
    if (firstTocNode) {
      await handleNodeClick(firstTocNode)
    }
    ElMessage.success('已同步 Spring 官方目录顺序与标题')
  } catch (err: unknown) {
    ElMessage.error('同步目录失败: ' + formatApiError(err))
  } finally {
    rebuildTocLoading.value = false
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
</script>

<style scoped>
.toc-card {
  position: sticky;
  top: 20px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

.doc-breadcrumb {
  margin-bottom: 10px;
}

.doc-breadcrumb-link {
  color: var(--primary-color);
  text-decoration: none;
}

.doc-breadcrumb-link:hover {
  text-decoration: underline;
}

.doc-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.doc-header-row h3 {
  margin: 0;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  font-size: 14px;
  gap: 6px;
  min-width: 0;
}

.custom-tree-node__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-tree-node__badge {
  flex-shrink: 0;
  transform: scale(0.85);
}

.custom-tree-node.is-unavailable {
  color: var(--text-secondary);
  opacity: 0.55;
  cursor: help;
}

.custom-tree-node.is-nav-folder {
  color: var(--text-primary);
  opacity: 0.85;
  cursor: pointer;
}

.custom-tree-node.is-nav-folder:hover {
  color: var(--primary-color);
}

.custom-tree-node.is-external {
  color: var(--primary-color);
  opacity: 0.92;
  cursor: pointer;
}

.custom-tree-node.is-external:hover {
  color: var(--primary-color);
  background: var(--input-bg);
}

.iframe-panel {
  position: relative;
  min-height: calc(100vh - 280px);
}

.document-iframe {
  width: 100%;
  min-height: calc(100vh - 280px);
  border: none;
  background: var(--card-bg);
}

.compare-row .compare-iframe {
  min-height: calc(100vh - 320px);
}

.html-content :deep(h1),
.html-content :deep(h2),
.html-content :deep(h3),
.html-content :deep(h4),
.html-content :deep(h5),
.html-content :deep(h6) {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
  color: var(--text-primary);
}

.html-content :deep(h1) {
  font-size: 2em;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 0.3em;
}

.html-content :deep(h2) {
  font-size: 1.5em;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 0.3em;
}

.html-content :deep(h3) {
  font-size: 1.25em;
}

.html-content :deep(p) {
  margin-top: 0;
  margin-bottom: 16px;
  line-height: 1.8;
}

.html-content :deep(ul),
.html-content :deep(ol) {
  margin-top: 0;
  margin-bottom: 16px;
  padding-left: 2em;
}

.html-content :deep(li) {
  margin-bottom: 8px;
  line-height: 1.6;
}

.html-content :deep(code) {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: var(--input-bg);
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, monospace;
}

.html-content :deep(pre) {
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: var(--input-bg);
  border-radius: 6px;
}

.html-content :deep(pre code) {
  padding: 0;
  margin: 0;
  font-size: 100%;
  background-color: transparent;
}

.html-content :deep(blockquote) {
  margin: 0;
  padding: 0 1em;
  color: var(--text-secondary);
  border-left: 0.25em solid var(--border-color);
}

.html-content :deep(table) {
  border-collapse: collapse;
  border-spacing: 0;
  margin-bottom: 16px;
  width: 100%;
}

.html-content :deep(th),
.html-content :deep(td) {
  padding: 6px 13px;
  border: 1px solid var(--border-color);
}

.html-content :deep(th) {
  font-weight: 600;
  background-color: var(--table-header-bg);
}

.html-content :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: var(--border-color);
  border: 0;
}

.html-content :deep(a) {
  color: var(--primary-color);
  text-decoration: none;
}

.html-content :deep(a:hover) {
  text-decoration: underline;
}

.html-content :deep(img) {
  max-width: 100%;
  box-sizing: border-box;
}

.html-content :deep(details) {
  margin-bottom: 16px;
}

.html-content :deep(summary) {
  cursor: pointer;
  font-weight: 600;
}
</style>
