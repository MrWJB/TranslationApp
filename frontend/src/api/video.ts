import { api } from './index'
import type {
  VideoApiResponse,
  VideoCategory,
  VideoDetail,
  VideoListItem,
  VideoListResult,
} from '@/types/video'

export interface VideoListParams {
  category: VideoCategory
  page: number
  size: number
}

/**
 * 获取指定分类的视频分页列表。
 */
export async function getVideoList(params: VideoListParams): Promise<VideoListResult> {
  const response = await api.get('/videos', { params }) as VideoApiResponse<VideoListItem[]>
  return {
    items: response.data ?? [],
    total: response.total ?? 0,
  }
}

/**
 * 获取单个视频详情。
 */
export async function getVideoDetail(id: string, category: string): Promise<VideoDetail> {
  const response = await api.get(`/videos/${id}`, { params: { category } }) as VideoApiResponse<VideoDetail>
  return response.data
}

/**
 * 删除指定视频元数据。
 */
export async function deleteVideo(id: string, category: string): Promise<void> {
  await api.delete(`/videos/${id}`, { params: { category } })
}

/**
 * 获取视频分类目录列表。
 */
export async function getVideoCategories(): Promise<string[]> {
  const response = await api.get('/videos/categories') as VideoApiResponse<string[]>
  return response.data ?? []
}
