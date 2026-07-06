export type AnalyticsRange = 'TODAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'

export interface LabelCount {
  label: string
  count: number
  percent: number
}

export interface OnlineTrendPoint {
  time: string
  onlineCount: number
  peakInBucket: number
}

export interface RealtimeOnlineUser {
  userId: number
  username: string
  realName?: string
  gender?: string
  age?: number
  province?: string
  city?: string
  lastSeen?: string
}

export interface BigScreenDashboard {
  range: AnalyticsRange
  rangeLabel: string
  generatedAt: string
  realtimeOnline: number
  todayPeakOnline: number
  periodPeakOnline: number
  periodAvgOnline: number
  totalUsers: number
  enabledUsers: number
  newUsersInPeriod: number
  activeUsersInPeriod: number
  loginCountInPeriod: number
  onlineRate: number
  onlineTrend: OnlineTrendPoint[]
  hourlyOnlineToday: OnlineTrendPoint[]
  genderDistributionAll: LabelCount[]
  genderDistributionOnline: LabelCount[]
  ageDistributionAll: LabelCount[]
  ageDistributionOnline: LabelCount[]
  regionDistributionAll: LabelCount[]
  regionDistributionOnline: LabelCount[]
  cityDistributionTop10: LabelCount[]
  loginTrend: LabelCount[]
  realtimeOnlineUsers: RealtimeOnlineUser[]
}

export const ANALYTICS_RANGE_OPTIONS: { value: AnalyticsRange; label: string }[] = [
  { value: 'TODAY', label: '近当天' },
  { value: 'WEEK', label: '近一周' },
  { value: 'MONTH', label: '近一个月' },
  { value: 'QUARTER', label: '近一个季度' },
  { value: 'YEAR', label: '近一年' },
]
