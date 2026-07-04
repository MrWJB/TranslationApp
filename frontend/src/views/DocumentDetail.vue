<template>
  <div class="document-detail">
    <div class="content-wrapper">
      <div class="toc-panel">
        <div class="toc-title">目录</div>
        <el-scrollbar class="toc-scroll">
          <div v-if="toc.length === 0" class="toc-empty">暂无目录</div>
          <ul v-else class="toc-list">
            <li v-for="item in toc" :key="item.id ?? item.localPath ?? item.title" class="toc-item">
              <toc-recursive :item="item" :active-id="activeDocId" @select="handleSelect" />
            </li>
          </ul>
        </el-scrollbar>
      </div>

      <div class="doc-wrapper">
        <div class="doc-breadcrumb-wrapper">
          <el-breadcrumb v-if="breadcrumbPath.length" separator=">" class="doc-breadcrumb">
            <el-breadcrumb-item v-for="(crumb, index) in breadcrumbPath" :key="crumb.id ?? crumb.localPath ?? index">
              <span v-if="index === breadcrumbPath.length - 1">{{ crumb.title }}</span>
              <a v-else href="#" class="doc-breadcrumb-link" @click.prevent="handleSelect(crumb)">{{ crumb.title }}</a>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="doc-panel">
          <el-tabs v-model="activeTab" type="border-card">
            <el-tab-pane label="中文翻译" name="translated">
              <iframe v-if="translatedIframeSrc" :src="translatedIframeSrc" class="document-iframe" />
              <div v-else-if="currentDoc?.translatedContent" class="document-content html-content" v-html="currentDoc.translatedContent" />
              <template v-else-if="currentDoc?.localPath">
                <el-alert type="info" title="翻译内容尚未生成（请配置 DEEPL_API_KEY 后重新爬取）" :closable="false" style="margin: 12px" />
                <iframe :src="originalIframeSrc" class="document-iframe" />
              </template>
              <el-empty v-else description="请从左侧目录选择章节" />
            </el-tab-pane>
            <el-tab-pane label="英文原文" name="original">
              <iframe v-if="originalIframeSrc" :src="originalIframeSrc" class="document-iframe" />
              <div v-else-if="currentDoc" class="document-content html-content" v-html="currentDoc.originalContent" />
              <el-empty v-else description="请从左侧目录选择章节" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getTableOfContents, getDocumentById, getTask } from '@/api'
import type { TocItem, Document } from '@/types'
import TocRecursive from '@/components/TocRecursive.vue'
import { useThemeStore } from '@/stores/theme'
import {
  assignTocNodeKeys,
  findFirstTocNodeWithPath,
  findTocNodeByLocalPath,
  findTocPath,
} from '@/utils/tocNavigation'
import { openExternalTocLink } from '@/utils/tocUnavailable'

const route = useRoute()
const taskId = Number(route.params.id)
const themeStore = useThemeStore()

const toc = ref<TocItem[]>([])
const activeDocId = ref<number | null>(null)
const currentDoc = ref<Document | null>(null)
const activeTab = ref('translated')

const breadcrumbPath = computed(() => {
  if (!currentDoc.value || toc.value.length === 0) {
    return []
  }
  return findTocPath(toc.value, {
    localPath: currentDoc.value.localPath,
    id: currentDoc.value.id,
  })
})

const originalIframeSrc = computed(() => {
  if (!currentDoc.value?.localPath) return ''
  return `/api/crawl/docs?path=${encodeURIComponent(currentDoc.value.localPath)}&theme=${themeStore.mode}`
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

watch(() => themeStore.mode, () => {
  sendThemeToIframes()
  nextTick(() => {
    document.querySelectorAll('.document-iframe').forEach((iframe) => {
      const frame = iframe as HTMLIFrameElement
      const src = frame.src
      frame.src = ''
      frame.src = src
    })
  })
})
watch(() => currentDoc.value?.localPath, () => {
  scrollActiveTocIntoView()
})

const scrollActiveTocIntoView = () => {
  nextTick(() => {
    const activeNode = document.querySelector('.document-detail .toc-node.is-active')
    activeNode?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

const docFromTocNode = (item: TocItem): Document => ({
  id: item.id ?? 0,
  title: item.title,
  url: item.url,
  localPath: item.localPath ?? '',
  translatedLocalPath: item.translatedLocalPath,
  sortOrder: 0,
  sectionLevel: item.level ?? 0,
  sectionId: '',
  parentDocumentId: null,
  originalContent: '',
  translatedContent: '',
  createdAt: '',
})

const loadDocument = async (id: number) => {
  activeDocId.value = id
  try {
    currentDoc.value = await getDocumentById(id)
    setTimeout(sendThemeToIframes, 300)
    scrollActiveTocIntoView()
  } catch (err) {
    console.error('Failed to load document:', err)
  }
}

const resolveDocumentForPath = async (targetPath: string): Promise<Document | null> => {
  const tocNode = findTocNodeByLocalPath(toc.value, targetPath)
  if (!tocNode) {
    return null
  }
  if (tocNode.id) {
    try {
      return await getDocumentById(tocNode.id)
    } catch (err) {
      console.error('Failed to load document:', err)
    }
  }
  if (tocNode.localPath) {
    return docFromTocNode(tocNode)
  }
  return null
}

const handleSelect = async (item: TocItem) => {
  if (openExternalTocLink(item)) {
    return
  }
  if (item.id) {
    await loadDocument(item.id)
    return
  }
  if (item.localPath) {
    activeDocId.value = item.id ?? null
    currentDoc.value = docFromTocNode(item)
    setTimeout(sendThemeToIframes, 300)
    scrollActiveTocIntoView()
  }
}

const findTocByLocalPath = findTocNodeByLocalPath

const handleIframeMessage = async (event: MessageEvent) => {
  if (event.data?.type !== 'navigate') return
  const targetPath = event.data.path
  if (!targetPath) return

  const match = findTocByLocalPath(toc.value, targetPath)
  if (match) {
    await handleSelect(match)
    return
  }

  const doc = await resolveDocumentForPath(targetPath)
  if (doc) {
    activeDocId.value = doc.id
    currentDoc.value = doc
    setTimeout(sendThemeToIframes, 300)
    scrollActiveTocIntoView()
  }
}

onMounted(async () => {
  window.addEventListener('message', handleIframeMessage)
  try {
    await getTask(taskId)  // 确保任务存在
    toc.value = assignTocNodeKeys(await getTableOfContents(taskId))

    const firstNode = findFirstTocNodeWithPath(toc.value)
    if (firstNode) {
      await handleSelect(firstNode)
    }

    setTimeout(sendThemeToIframes, 500)
  } catch (err) {
    console.error('Failed to load task:', err)
  }
})

onUnmounted(() => {
  window.removeEventListener('message', handleIframeMessage)
})
</script>

<style scoped>
.document-detail {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.toc-panel {
  width: 280px;
  min-width: 280px;
  background: var(--card-bg);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.toc-title {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
}

.toc-scroll {
  flex: 1;
  overflow: hidden;
}

.toc-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 8px 0;
}

.toc-item {
  list-style: none;
}

.doc-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.doc-breadcrumb-wrapper {
  flex-shrink: 0;
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-color);
  padding: 10px 16px;
  z-index: 100;
}

.doc-panel {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.doc-breadcrumb {
  margin: 0;
}

.doc-breadcrumb-link {
  color: var(--primary-color);
  text-decoration: none;
}

.doc-breadcrumb-link:hover {
  text-decoration: underline;
}

.doc-panel :deep(.el-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.doc-panel :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  padding: 0;
  position: relative;
}

.doc-panel :deep(.el-tab-pane) {
  height: 100%;
  position: relative;
}

.document-iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: none;
  background: transparent;
  color-scheme: light dark;
}

.document-content {
  height: 100%;
  overflow-y: auto;
  padding: 20px;
}

:deep(.el-tabs--border-card) {
  background-color: var(--card-bg);
  border-color: var(--border-color);
  height: 100%;
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background-color: var(--input-bg);
  border-bottom-color: var(--border-color);
}

:deep(.el-tabs__item) {
  color: var(--text-primary);
}

:deep(.el-tabs__item.is-active) {
  color: var(--primary-color);
}
</style>
