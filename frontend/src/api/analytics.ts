import { api } from './index'
import type { AnalyticsRange, BigScreenDashboard } from '@/types/analytics'

export const getBigScreenDashboard = (range: AnalyticsRange = 'TODAY'): Promise<BigScreenDashboard> => {
  return api.get('/admin/analytics/dashboard', { params: { range } })
}

export const triggerAnalyticsSnapshot = (): Promise<void> => {
  return api.post('/admin/analytics/snapshot')
}
