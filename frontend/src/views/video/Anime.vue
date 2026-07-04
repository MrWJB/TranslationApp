<template>
  <div class="video-management">
    <div class="header">
      <h2>动漫管理</h2>
      <el-button type="primary" @click="handleAdd">新增动漫</el-button>
    </div>

    <el-collapse v-model="activeNames" accordion v-loading="loading">
      <el-collapse-item 
        v-for="row in tableData" 
        :key="row.id" 
        :name="row.id"
        class="video-list-item"
      >
        <template #title>
          <div class="list-header">
            <div class="cover-wrap">
              <img v-if="row.coverImage && !row.coverImage.includes('load.png')" :src="row.coverImage" class="cover" />
              <div v-else class="cover-placeholder">
                <el-icon><Picture /></el-icon>
              </div>
            </div>
            <div class="basic-info">
              <h3 class="title">{{ row.title }}</h3>
              <div class="info-row">
                <el-tag size="small" type="primary">{{ row.category }}</el-tag>
                <span class="region">{{ row.region }}</span>
                <span class="year">{{ row.year }}</span>
                <el-tag :type="row.status === '已完结' ? 'success' : 'warning'" size="small">
                  {{ row.status || '未知' }}
                </el-tag>
              </div>
              <div class="meta-row">
                <span class="rating">
                  <el-icon class="star"><Star /></el-icon>
                  {{ row.rating || '-' }}
                </span>
                <span class="divider">|</span>
                <span class="episodes">{{ row.totalEpisodes }}集</span>
                <span class="divider">|</span>
                <span class="actors">{{ row.actors || '暂无主演' }}</span>
              </div>
            </div>
            <div class="actions">
              <el-button size="small" @click.stop="handleView(row)">查看</el-button>
              <el-button size="small" type="danger" @click.stop="handleDelete(row)">删除</el-button>
            </div>
          </div>
        </template>
        <div class="expanded-content">
          <div v-if="row.description" class="description">
            <h4>简介</h4>
            <p>{{ row.description }}</p>
          </div>
          <div class="detail-info">
            <div class="info-grid">
              <div class="info-item">
                <span class="label">导演</span>
                <span class="value">{{ row.director || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">主演</span>
                <span class="value">{{ row.actors || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">地区</span>
                <span class="value">{{ row.region || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">年份</span>
                <span class="value">{{ row.year || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">状态</span>
                <span class="value">{{ row.status || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">标签</span>
                <span class="value">{{ row.tags || '-' }}</span>
              </div>
            </div>
          </div>
          <div v-if="row.totalEpisodes" class="episode-section">
            <h4>剧集列表</h4>
            <div class="episode-list">
              <span 
                v-for="num in Math.min(row.totalEpisodes, 50)" 
                :key="num" 
                class="episode-tag"
              >
                第{{ num }}集
              </span>
              <span v-if="row.totalEpisodes > 50" class="episode-more">
                还有 {{ row.totalEpisodes - 50 }} 集...
              </span>
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="fetchData"
      @current-change="fetchData"
      style="margin-top: 20px; justify-content: flex-end"
    />

    <el-dialog v-model="showDetail" title="视频详情" width="900px" top="5vh">
      <div v-if="detailData" class="detail-content">
        <div class="detail-header">
          <div class="detail-cover-wrap">
            <img v-if="detailData.coverImage && !detailData.coverImage.includes('load.png')" 
                 :src="detailData.coverImage" class="detail-cover" />
            <div v-else class="detail-cover-placeholder">
              <el-icon class="placeholder-icon"><Picture /></el-icon>
              <span>暂无封面</span>
            </div>
          </div>
          <div class="detail-info">
            <h3>{{ detailData.title }}</h3>
            <div class="info-row">
              <span class="label">分类：</span>
              <span>{{ detailData.category }}</span>
            </div>
            <div class="info-row">
              <span class="label">地区：</span>
              <span>{{ detailData.region }}</span>
            </div>
            <div class="info-row">
              <span class="label">评分：</span>
              <span>{{ detailData.rating || '-' }} ({{ detailData.ratingCount || 0 }}人评分)</span>
            </div>
            <div class="info-row">
              <span class="label">主演：</span>
              <span>{{ detailData.actors || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="label">导演：</span>
              <span>{{ detailData.director || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="label">年份：</span>
              <span>{{ detailData.year || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="label">集数：</span>
              <span>{{ detailData.totalEpisodes }}集</span>
            </div>
            <div class="info-row">
              <span class="label">状态：</span>
              <el-tag :type="detailData.status === '已完结' ? 'success' : 'warning'">
                {{ detailData.status || '未知' }}
              </el-tag>
            </div>
            <div class="info-row">
              <span class="label">标签：</span>
              <span>{{ detailData.tags || '-' }}</span>
            </div>
          </div>
        </div>
        <div class="detail-description">
          <h4>简介</h4>
          <p>{{ detailData.description || '暂无简介' }}</p>
        </div>
        <div v-if="detailData.episodes && detailData.episodes.length > 0" class="detail-episodes">
          <h4>剧集列表</h4>
          <el-table :data="pagedEpisodes" stripe size="small" :max-height="400">
            <el-table-column prop="episodeNumber" label="集数" width="80" />
            <el-table-column prop="title" label="标题" />
            <el-table-column label="播放" width="100">
              <template #default="{ row }">
                <el-button size="small" @click="handlePlayEpisode(row)">播放</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="episodePage"
            v-model:page-size="episodePageSize"
            :total="detailData.episodes.length"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="updatePagedEpisodes"
            @current-change="updatePagedEpisodes"
            style="margin-top: 10px; justify-content: flex-end"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Star } from '@element-plus/icons-vue'
import axios from 'axios'

const loading = ref(false)
const tableData = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const activeNames = ref<string[]>([])

const showDetail = ref(false)
const detailData = ref<any>(null)

const episodePage = ref(1)
const episodePageSize = ref(20)

const pagedEpisodes = computed(() => {
  if (!detailData.value?.episodes) return []
  const start = (episodePage.value - 1) * episodePageSize.value
  const end = start + episodePageSize.value
  return detailData.value.episodes.slice(start, end)
})

const updatePagedEpisodes = () => {
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await axios.get('/api/videos', {
      params: {
        category: 'anime',
        page: currentPage.value,
        size: pageSize.value
      }
    })
    const data = res.data || res
    if (data.code === 200) {
      tableData.value = data.data || []
      total.value = data.total || 0
    } else {
      tableData.value = data.content || data.data || []
      total.value = data.totalElements || data.total || 0
    }
  } catch (e: any) {
    ElMessage.error(e.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const handleView = async (row: any) => {
  episodePage.value = 1
  try {
    const res: any = await axios.get(`/api/videos/${row.id}`, {
      params: { category: row.category }
    })
    const data = res.data || res
    if (data.code === 200) {
      detailData.value = data.data
    } else {
      detailData.value = data
    }
    showDetail.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '获取详情失败')
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要删除该视频吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res: any = await axios.delete(`/api/videos/${row.id}`, {
      params: { category: row.category }
    })
    const data = res.data || res
    if (data.code === 200 || res.status === 200) {
      ElMessage.success('删除成功')
      fetchData()
    } else {
      ElMessage.error(data.message || '删除失败')
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

const handleAdd = () => {
  ElMessage.info('请通过爬取任务添加视频')
}

const handlePlayEpisode = (row: any) => {
  window.open(row.url, '_blank')
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.video-management {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header h2 {
  margin: 0;
}
.video-list-item {
  margin-bottom: 12px;
  border-radius: 8px;
  overflow: hidden;
}
.video-list-item :deep(.el-collapse-item__header) {
  padding: 0;
  border-bottom: 1px solid var(--el-border-color);
}
.video-list-item :deep(.el-collapse-item__content) {
  padding: 16px;
  background: var(--el-bg-color-page);
}
.list-header {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 16px;
}
.cover-wrap {
  flex-shrink: 0;
  width: 80px;
  height: 110px;
}
.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}
.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-radius: 6px;
  color: #999;
}
.basic-info {
  flex: 1;
  min-width: 0;
}
.basic-info .title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.info-row .region,
.info-row .year {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.meta-row .rating {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #e6a23c;
  font-weight: 600;
}
.meta-row .star {
  font-size: 12px;
}
.meta-row .divider {
  color: #ddd;
}
.meta-row .episodes {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.meta-row .actors {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.actions {
  display: flex;
  gap: 8px;
}
.expanded-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.description {
  padding: 12px;
  background: var(--el-bg-color);
  border-radius: 6px;
}
.description h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
}
.description p {
  margin: 0;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.detail-info .info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.detail-info .info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.detail-info .info-item .label {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  min-width: 40px;
}
.detail-info .info-item .value {
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.episode-section {
  padding: 12px;
  background: var(--el-bg-color);
  border-radius: 6px;
}
.episode-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
}
.episode-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.episode-tag {
  padding: 4px 12px;
  background: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.episode-more {
  padding: 4px 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.detail-content {
  padding: 10px;
}
.detail-header {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}
.detail-cover-wrap {
  flex-shrink: 0;
}
.detail-cover {
  width: 220px;
  height: 308px;
  object-fit: cover;
  border-radius: 8px;
}
.detail-cover-placeholder {
  width: 220px;
  height: 308px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-radius: 8px;
  color: #999;
}
.detail-info {
  flex: 1;
  min-width: 0;
}
.detail-info h3 {
  margin: 0 0 16px 0;
  font-size: 22px;
  font-weight: 600;
}
.detail-content .info-row {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}
.detail-content .info-row .label {
  color: var(--el-text-color-secondary);
  width: 60px;
  flex-shrink: 0;
}
.detail-description {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--el-bg-color-page);
  border-radius: 8px;
}
.detail-description h4 {
  margin: 0 0 10px 0;
  font-size: 16px;
}
.detail-description p {
  margin: 0;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.detail-episodes {
  padding: 16px;
  background: var(--el-bg-color-page);
  border-radius: 8px;
}
.detail-episodes h4 {
  margin: 0 0 15px 0;
  font-size: 16px;
}
</style>