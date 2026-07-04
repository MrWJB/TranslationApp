<template>
  <div v-loading="loading">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>{{ categoryName }}</h2>
      <el-select v-model="selectedCategory" @change="handleCategoryChange" placeholder="选择分类" style="width: 150px">
        <el-option label="全部" value="" />
        <el-option label="Java" value="java" />
        <el-option label="Spring" value="spring" />
        <el-option label="Spring Boot" value="spring-boot" />
        <el-option label="Spring Cloud" value="spring-cloud" />
        <el-option label="Spring Mvc" value="spring-mvc" />
        <el-option label="Mysql" value="mysql" />
        <el-option label="Oracle" value="oracle" />
      </el-select>
    </div>
    
    <div v-if="selectedCategory" class="latest-section">
      <div v-if="latestTask" class="latest-task-card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px">
          <div>
            <el-tag type="success" size="small" style="margin-right: 10px">最新</el-tag>
            <span class="task-title">{{ latestTask.title }}</span>
          </div>
          <el-button v-if="latestTask.status === 'COMPLETED'" size="small" type="primary" @click="viewDocuments(latestTask.id)">查看文档</el-button>
        </div>
        
        <el-descriptions :column="3" border>
          <el-descriptions-item label="URL">{{ latestTask.url }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(latestTask.status)">{{ latestTask.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(latestTask.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      
      <div v-else class="empty-state">
        <el-empty description="暂无爬取任务" />
      </div>
    </div>
    
    <div v-else class="all-categories-section">
      <div v-if="latestTasksByCategory.length > 0" class="latest-cards-grid">
        <div v-for="item in latestTasksByCategory" :key="item.category" class="latest-task-card">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px">
            <div>
              <el-tag type="success" size="small" style="margin-right: 8px">最新</el-tag>
              <el-tag size="small" style="margin-right: 8px">{{ categoryNames[item.category] || item.category }}</el-tag>
              <span class="task-title">{{ item.task.title }}</span>
            </div>
            <el-button v-if="item.task.status === 'COMPLETED'" size="small" type="primary" @click="viewDocuments(item.task.id)">查看文档</el-button>
          </div>
          
          <el-descriptions :column="3" border>
            <el-descriptions-item label="URL">{{ item.task.url }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(item.task.status)">{{ item.task.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(item.task.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
      
      <div v-else class="empty-state">
        <el-empty description="暂无爬取任务" />
      </div>
    </div>
    
    <el-collapse v-if="historyTasks.length > 0" style="margin-top: 20px">
      <el-collapse-item>
        <template #title>历史记录 ({{ historyTasks.length }})</template>
        <el-table :data="historyTasks" style="width: 100%" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="任务标题" min-width="200">
            <template #default="{ row }">
              <el-link type="primary" @click="viewDocuments(row.id)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ categoryNames[normalizeTaskCategory(row)] || normalizeTaskCategory(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="url" label="URL" min-width="250" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status === 'COMPLETED'" size="small" @click="viewDocuments(row.id)">查看文档</el-button>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getTasks } from '@/api'
import type { CrawlTask } from '@/types'
import { formatDate } from '@/utils/date'
import { categoryNames, normalizeTaskCategory, taskMatchesCategory } from '@/utils/docCategories'
const router = useRouter()
const route = useRoute()
const tasks = ref<CrawlTask[]>([])
const loading = ref(false)
const selectedCategory = ref('')

const categoryName = computed(() => {
  const pathCategory = route.path.split('/')[2] || ''
  if (!pathCategory) return '文档列表'
  return categoryNames[pathCategory] || pathCategory
})

const updateCategoryFromRoute = () => {
  const pathCategory = route.path.split('/')[2] || ''
  selectedCategory.value = pathCategory
}

onMounted(async () => {
  updateCategoryFromRoute()
  await loadTasks()
})

watch(() => route.path, async () => {
  updateCategoryFromRoute()
  await loadTasks()
})

const loadTasks = async () => {
  loading.value = true
  try {
    const result = await getTasks(0, 1000)
    tasks.value = result.tasks
  } catch (err) {
    console.error('Failed to load tasks:', err)
  } finally {
    loading.value = false
  }
}

const documentTasks = computed(() => {
  return tasks.value.filter(task => task.taskType === 'document')
})

const filteredTasks = computed(() => {
  const category = selectedCategory.value
  if (!category) return documentTasks.value
  return documentTasks.value.filter(task => taskMatchesCategory(task, category))
})

const sortedTasks = computed(() => {
  return [...filteredTasks.value].sort((a, b) => {
    return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
  })
})

const latestTask = computed(() => {
  return sortedTasks.value[0]
})

const historyTasks = computed(() => {
  if (selectedCategory.value) {
    return sortedTasks.value.slice(1)
  }
  
  const latestTaskIds = new Set(latestTasksByCategory.value.map(item => item.task.id))
  return sortedTasks.value.filter(task => !latestTaskIds.has(task.id))
})

const latestTasksByCategory = computed(() => {
  if (selectedCategory.value) return []
  
  const tasksByCategory: Record<string, CrawlTask[]> = {}
  documentTasks.value.forEach(task => {
    const category = normalizeTaskCategory(task)
    if (!tasksByCategory[category]) {
      tasksByCategory[category] = []
    }
    tasksByCategory[category].push(task)
  })
  
  return Object.entries(tasksByCategory)
    .map(([category, categoryTasks]) => {
      const sorted = [...categoryTasks].sort((a, b) => {
        return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
      })
      return { category, task: sorted[0] }
    })
    .sort((a, b) => {
      return new Date(b.task.createdAt || 0).getTime() - new Date(a.task.createdAt || 0).getTime()
    })
})

const statusType = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

const handleCategoryChange = (value: string) => {
  if (value) {
    router.push(`/documents/${value}`)
  } else {
    router.push('/documents')
  }
}

const viewDocuments = (taskId: number) => {
  router.push(`/document/${taskId}`)
}
</script>

<style scoped>
h2 {
  color: var(--text-primary);
  margin-top: 0;
  margin-bottom: 20px;
}

.text-muted {
  color: var(--text-secondary);
}

.latest-task-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 20px;
  box-shadow: var(--el-shadow-sm);
  margin-bottom: 16px;
}

.latest-task-card :deep(.el-descriptions) {
  background: var(--el-bg-color);
}

.latest-task-card :deep(.el-descriptions__body) {
  background: var(--el-bg-color);
}

.latest-task-card :deep(.el-descriptions__table) {
  background: var(--el-bg-color);
}

.latest-task-card :deep(.el-descriptions__row) {
  background: var(--el-bg-color);
}

.latest-task-card :deep(.el-descriptions__cell) {
  background: var(--el-bg-color);
  border-color: var(--el-border-color);
}

.latest-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(600px, 1fr));
  gap: 16px;
}

.task-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-state {
  padding: 40px;
}
</style>
