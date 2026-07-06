/** 视频分类编码 */
export type VideoCategory = 'anime' | 'movie' | 'short-drama' | 'tv-series' | 'variety'

/** 视频列表项 */
export interface VideoListItem {
  id: string
  title: string
  url?: string
  category: string
  coverImage?: string
  description?: string
  rating?: string
  region?: string
  actors?: string
  director?: string
  year?: string
  status?: string
  tags?: string
  totalEpisodes?: number
  crawledAt?: string
  localPath?: string
}

/** 剧集信息 */
export interface VideoEpisode {
  title: string
  url: string
  episodeNumber: number
}

/** 视频详情 */
export interface VideoDetail extends VideoListItem {
  thumbnail?: string
  ratingCount?: string
  genre?: string
  season?: string
  updateTime?: string
  views?: string
  duration?: string
  episodes?: VideoEpisode[]
  videoUrls?: string[]
  m3u8Urls?: string[]
}

/** 视频 API 通用响应 */
export interface VideoApiResponse<T> {
  code: number
  message: string
  data: T
  total?: number
  page?: number
  size?: number
}

/** 分页列表响应 */
export interface VideoListResult {
  items: VideoListItem[]
  total: number
}
