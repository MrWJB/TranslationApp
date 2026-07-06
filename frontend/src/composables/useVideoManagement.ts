import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteVideo, getVideoDetail, getVideoList } from '@/api/video'
import { formatApiError } from '@/api/index'
import type { VideoCategory, VideoDetail, VideoListItem } from '@/types/video'

export interface UseVideoManagementOptions {
  /** 媒体类型中文名，用于提示文案 */
  mediaLabel?: string
}

/**
 * 视频管理页通用逻辑，封装列表、详情与删除操作。
 *
 * @param category 视频分类编码
 * @param options  可选文案配置
 */
export function useVideoManagement(category: VideoCategory, options: UseVideoManagementOptions = {}) {
  const mediaLabel = options.mediaLabel ?? '视频'

  const loading = ref(false)
  const tableData = ref<VideoListItem[]>([])
  const currentPage = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const activeNames = ref<string[]>([])

  const showDetail = ref(false)
  const detailData = ref<VideoDetail | null>(null)

  const episodePage = ref(1)
  const episodePageSize = ref(20)

  const pagedEpisodes = computed(() => {
    if (!detailData.value?.episodes) {
      return []
    }
    const start = (episodePage.value - 1) * episodePageSize.value
    const end = start + episodePageSize.value
    return detailData.value.episodes.slice(start, end)
  })

  const updatePagedEpisodes = (): void => {
    // 分页切换时依赖 computed 自动更新
  }

  const fetchData = async (): Promise<void> => {
    loading.value = true
    try {
      const result = await getVideoList({
        category,
        page: currentPage.value,
        size: pageSize.value,
      })
      tableData.value = result.items
      total.value = result.total
    } catch (error: unknown) {
      ElMessage.error(formatApiError(error) || '获取数据失败')
    } finally {
      loading.value = false
    }
  }

  const handleView = async (row: VideoListItem): Promise<void> => {
    episodePage.value = 1
    try {
      detailData.value = await getVideoDetail(row.id, row.category)
      showDetail.value = true
    } catch (error: unknown) {
      ElMessage.error(formatApiError(error) || '获取详情失败')
    }
  }

  const handleDelete = async (row: VideoListItem): Promise<void> => {
    try {
      await ElMessageBox.confirm(`确定要删除该${mediaLabel}吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteVideo(row.id, row.category)
      ElMessage.success('删除成功')
      await fetchData()
    } catch (error: unknown) {
      if (error !== 'cancel') {
        ElMessage.error(formatApiError(error) || '删除失败')
      }
    }
  }

  const handleAdd = (): void => {
    ElMessage.info(`请通过爬取任务添加${mediaLabel}`)
  }

  const handlePlayEpisode = (episode: { url?: string }): void => {
    if (episode.url) {
      window.open(episode.url, '_blank')
    }
  }

  onMounted(() => {
    void fetchData()
  })

  return {
    loading,
    tableData,
    currentPage,
    pageSize,
    total,
    activeNames,
    showDetail,
    detailData,
    episodePage,
    episodePageSize,
    pagedEpisodes,
    updatePagedEpisodes,
    fetchData,
    handleView,
    handleDelete,
    handleAdd,
    handlePlayEpisode,
  }
}
