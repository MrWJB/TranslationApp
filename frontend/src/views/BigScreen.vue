<template>
  <div class="bigscreen" :class="{ fullscreen: isFullscreen }">
    <header class="bs-header">
      <div class="bs-header-left">
        <h1>用户在线数据大屏</h1>
        <span class="bs-subtitle">TranslationApp 实时运营监控中心</span>
      </div>
      <div class="bs-header-center">
        <button
          v-for="opt in rangeOptions"
          :key="opt.value"
          class="range-btn"
          :class="{ active: range === opt.value }"
          @click="changeRange(opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>
      <div class="bs-header-right">
        <span class="clock">{{ clockText }}</span>
        <span class="updated">更新于 {{ data?.generatedAt || '--' }}</span>
        <el-button size="small" @click="toggleFullscreen">{{ isFullscreen ? '退出全屏' : '全屏' }}</el-button>
        <el-button size="small" type="primary" @click="router.push('/dashboard')">返回系统</el-button>
      </div>
    </header>

    <div v-if="loading && !data" class="bs-loading">数据加载中...</div>
    <div v-else-if="error" class="bs-error">{{ error }}</div>

    <template v-else-if="data">
      <section class="kpi-row">
        <div v-for="card in kpiCards" :key="card.label" class="kpi-card">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="kpi-desc">{{ card.desc }}</div>
        </div>
      </section>

      <section class="chart-row chart-row-main">
        <div class="panel panel-lg">
          <div class="panel-title">{{ data.rangeLabel }}在线人数趋势</div>
          <div ref="trendChartRef" class="chart-box"></div>
        </div>
        <div class="panel">
          <div class="panel-title">实时在线用户（{{ data.realtimeOnlineUsers.length }}）</div>
          <div class="online-list">
            <div v-for="u in data.realtimeOnlineUsers" :key="u.userId" class="online-item">
              <span class="dot online"></span>
              <span class="name">{{ u.realName || u.username }}</span>
              <span class="meta">{{ u.gender }} · {{ u.age ?? '?' }}岁 · {{ u.province }}</span>
            </div>
            <div v-if="!data.realtimeOnlineUsers.length" class="empty-tip">当前暂无在线用户</div>
          </div>
        </div>
      </section>

      <section class="chart-row">
        <div class="panel">
          <div class="panel-title">男女比例（全部用户）</div>
          <div ref="genderAllChartRef" class="chart-box chart-sm"></div>
        </div>
        <div class="panel">
          <div class="panel-title">男女比例（当前在线）</div>
          <div ref="genderOnlineChartRef" class="chart-box chart-sm"></div>
        </div>
        <div class="panel">
          <div class="panel-title">年龄段分布（全部用户）</div>
          <div ref="ageAllChartRef" class="chart-box chart-sm"></div>
        </div>
        <div class="panel">
          <div class="panel-title">年龄段分布（当前在线）</div>
          <div ref="ageOnlineChartRef" class="chart-box chart-sm"></div>
        </div>
      </section>

      <section class="chart-row">
        <div class="panel panel-lg">
          <div class="panel-title">地区分布（全部用户 TOP15）</div>
          <div ref="regionAllChartRef" class="chart-box"></div>
        </div>
        <div class="panel">
          <div class="panel-title">地区分布（当前在线）</div>
          <div ref="regionOnlineChartRef" class="chart-box"></div>
        </div>
      </section>

      <section class="chart-row">
        <div class="panel">
          <div class="panel-title">今日分时在线（24小时）</div>
          <div ref="hourlyChartRef" class="chart-box"></div>
        </div>
        <div class="panel">
          <div class="panel-title">活跃用户趋势（登录）</div>
          <div ref="loginChartRef" class="chart-box"></div>
        </div>
        <div class="panel">
          <div class="panel-title">城市 TOP10</div>
          <div ref="cityChartRef" class="chart-box"></div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getBigScreenDashboard } from '@/api/analytics'
import { ANALYTICS_RANGE_OPTIONS, type AnalyticsRange, type BigScreenDashboard } from '@/types/analytics'
import { formatApiError } from '@/api'
import { useEcharts } from '@/composables/useEcharts'

const router = useRouter()
const rangeOptions = ANALYTICS_RANGE_OPTIONS
const range = ref<AnalyticsRange>('TODAY')
const data = ref<BigScreenDashboard | null>(null)
const loading = ref(false)
const error = ref('')
const clockText = ref('')
const isFullscreen = ref(false)

const trendChartRef = ref<HTMLElement>()
const genderAllChartRef = ref<HTMLElement>()
const genderOnlineChartRef = ref<HTMLElement>()
const ageAllChartRef = ref<HTMLElement>()
const ageOnlineChartRef = ref<HTMLElement>()
const regionAllChartRef = ref<HTMLElement>()
const regionOnlineChartRef = ref<HTMLElement>()
const hourlyChartRef = ref<HTMLElement>()
const loginChartRef = ref<HTMLElement>()
const cityChartRef = ref<HTMLElement>()

const { initChart, disposeCharts, resizeCharts } = useEcharts()
let refreshTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

const kpiCards = computed(() => {
  if (!data.value) return []
  const d = data.value
  return [
    { label: '实时在线', value: d.realtimeOnline, desc: `在线率 ${d.onlineRate}%`, color: '#00e5ff' },
    { label: '今日峰值', value: d.todayPeakOnline, desc: '今日最高同时在线', color: '#ffd54f' },
    { label: `${d.rangeLabel}峰值`, value: d.periodPeakOnline, desc: '统计周期内峰值', color: '#ff8a65' },
    { label: `${d.rangeLabel}均值`, value: d.periodAvgOnline, desc: '平均同时在线', color: '#81c784' },
    { label: '总注册用户', value: d.totalUsers, desc: `启用 ${d.enabledUsers}`, color: '#64b5f6' },
    { label: `${d.rangeLabel}活跃`, value: d.activeUsersInPeriod, desc: `登录 ${d.loginCountInPeriod} 次`, color: '#ba68c8' },
    { label: `${d.rangeLabel}新增`, value: d.newUsersInPeriod, desc: '新注册用户', color: '#4dd0e1' },
  ]
})

const updateClock = (): void => {
  const now = new Date()
  clockText.value = now.toLocaleString('zh-CN', { hour12: false })
}

const pieOption = (title: string, items: { label: string; count: number }[]) => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, textStyle: { color: '#9fb3d9', fontSize: 11 } },
  color: ['#5470c6', '#ee6666', '#fac858', '#91cc75'],
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    center: ['50%', '45%'],
    label: { color: '#cfe0ff', formatter: '{b}\n{d}%' },
    data: items.map(item => ({ name: item.label, value: item.count })),
  }],
})

const barOption = (
  categories: string[],
  values: number[],
  color = '#00bcd4',
  horizontal = false
) => ({
  tooltip: { trigger: 'axis' },
  grid: { left: horizontal ? 80 : 40, right: 20, top: 20, bottom: horizontal ? 20 : 50 },
  xAxis: horizontal
    ? { type: 'value', axisLabel: { color: '#8faadc' }, splitLine: { lineStyle: { color: '#1e3a5f' } } }
    : { type: 'category', data: categories, axisLabel: { color: '#8faadc', rotate: categories.length > 8 ? 35 : 0, fontSize: 10 } },
  yAxis: horizontal
    ? { type: 'category', data: categories, axisLabel: { color: '#8faadc', fontSize: 11 } }
    : { type: 'value', axisLabel: { color: '#8faadc' }, splitLine: { lineStyle: { color: '#1e3a5f' } } },
  series: [{
    type: 'bar',
    data: values,
    itemStyle: { color, borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] },
    barMaxWidth: 28,
  }],
})

const lineOption = (times: string[], values: number[], peakValues: number[], name: string) => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['平均在线', '峰值'], textStyle: { color: '#9fb3d9' }, top: 0 },
  grid: { left: 45, right: 20, top: 35, bottom: 30 },
  xAxis: { type: 'category', data: times, axisLabel: { color: '#8faadc', fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { color: '#8faadc' }, splitLine: { lineStyle: { color: '#1e3a5f' } } },
  series: [
    { name: '平均在线', type: 'line', smooth: true, data: values, areaStyle: { opacity: 0.15 }, itemStyle: { color: '#00e5ff' } },
    { name: '峰值', type: 'line', smooth: true, data: peakValues, itemStyle: { color: '#ffd54f' }, lineStyle: { type: 'dashed' } },
  ],
})

const renderCharts = async (): Promise<void> => {
  if (!data.value) return
  disposeCharts()
  const d = data.value

  const trend = await initChart(trendChartRef.value)
  trend?.setOption(lineOption(
    d.onlineTrend.map(point => point.time),
    d.onlineTrend.map(point => point.onlineCount),
    d.onlineTrend.map(point => point.peakInBucket),
    '在线趋势'
  ))

  ;(await initChart(genderAllChartRef.value))?.setOption(pieOption('全部', d.genderDistributionAll))
  ;(await initChart(genderOnlineChartRef.value))?.setOption(pieOption('在线', d.genderDistributionOnline))

  const ageAll = d.ageDistributionAll
  ;(await initChart(ageAllChartRef.value))?.setOption(barOption(ageAll.map(item => item.label), ageAll.map(item => item.count), '#7e57c2'))

  const ageOnline = d.ageDistributionOnline
  ;(await initChart(ageOnlineChartRef.value))?.setOption(barOption(ageOnline.map(item => item.label), ageOnline.map(item => item.count), '#26a69a'))

  const regionAll = d.regionDistributionAll.slice(0, 15)
  ;(await initChart(regionAllChartRef.value))?.setOption(barOption(
    regionAll.map(item => item.label),
    regionAll.map(item => item.count),
    '#42a5f5',
    true
  ))

  const regionOnline = d.regionDistributionOnline
  ;(await initChart(regionOnlineChartRef.value))?.setOption(barOption(
    regionOnline.map(item => item.label),
    regionOnline.map(item => item.count),
    '#66bb6a',
    true
  ))

  ;(await initChart(hourlyChartRef.value))?.setOption(lineOption(
    d.hourlyOnlineToday.map(point => point.time),
    d.hourlyOnlineToday.map(point => point.onlineCount),
    d.hourlyOnlineToday.map(point => point.peakInBucket),
    '分时'
  ))

  const login = d.loginTrend
  ;(await initChart(loginChartRef.value))?.setOption(barOption(login.map(item => item.label), login.map(item => item.count), '#ab47bc'))

  const cities = d.cityDistributionTop10
  ;(await initChart(cityChartRef.value))?.setOption(barOption(
    cities.map(item => item.label),
    cities.map(item => item.count),
    '#29b6f6',
    true
  ))

  resizeCharts()
}

const loadData = async (): Promise<void> => {
  loading.value = true
  error.value = ''
  try {
    data.value = await getBigScreenDashboard(range.value)
    await nextTick()
    await renderCharts()
  } catch (loadError: unknown) {
    error.value = formatApiError(loadError)
  } finally {
    loading.value = false
  }
}

const changeRange = (nextRange: AnalyticsRange): void => {
  range.value = nextRange
  void loadData()
}

const onResize = (): void => resizeCharts()

const toggleFullscreen = (): void => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.()
    isFullscreen.value = true
  } else {
    document.exitFullscreen?.()
    isFullscreen.value = false
  }
}

watch(data, () => {
  void nextTick(() => renderCharts())
})

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  void loadData()
  refreshTimer = setInterval(() => void loadData(), 30000)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (clockTimer) clearInterval(clockTimer)
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<style scoped>
.bigscreen {
  min-height: 100vh;
  background: radial-gradient(ellipse at top, #0d1b2a 0%, #050810 55%, #020409 100%);
  color: #e3ecff;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}

.bigscreen.fullscreen {
  padding: 12px 16px;
}

.bs-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 229, 255, 0.2);
}

.bs-header-left h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  background: linear-gradient(90deg, #00e5ff, #7c4dff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.bs-subtitle {
  font-size: 12px;
  color: #6b8cae;
}

.bs-header-center {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.range-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(0, 229, 255, 0.25);
  color: #9fb3d9;
  padding: 6px 14px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.range-btn.active,
.range-btn:hover {
  background: rgba(0, 229, 255, 0.15);
  border-color: #00e5ff;
  color: #00e5ff;
}

.bs-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.clock {
  font-size: 18px;
  font-weight: 600;
  color: #00e5ff;
  font-variant-numeric: tabular-nums;
}

.updated {
  font-size: 11px;
  color: #6b8cae;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.kpi-card {
  background: linear-gradient(135deg, rgba(13, 27, 42, 0.9), rgba(20, 40, 70, 0.6));
  border: 1px solid rgba(0, 229, 255, 0.15);
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 0 20px rgba(0, 100, 180, 0.1);
}

.kpi-label {
  font-size: 12px;
  color: #8faadc;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  margin: 4px 0;
  font-variant-numeric: tabular-nums;
}

.kpi-desc {
  font-size: 11px;
  color: #6b8cae;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.chart-row-main {
  grid-template-columns: 2fr 1fr;
}

.panel {
  background: rgba(10, 22, 40, 0.85);
  border: 1px solid rgba(0, 229, 255, 0.12);
  border-radius: 8px;
  padding: 10px 12px 6px;
  min-height: 260px;
}

.panel-lg {
  min-height: 300px;
}

.panel-title {
  font-size: 13px;
  color: #9fb3d9;
  margin-bottom: 6px;
  padding-left: 8px;
  border-left: 3px solid #00e5ff;
}

.chart-box {
  height: 240px;
}

.chart-sm {
  height: 220px;
}

.online-list {
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.online-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  font-size: 12px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot.online {
  background: #00e676;
  box-shadow: 0 0 6px #00e676;
}

.name {
  font-weight: 600;
  min-width: 72px;
}

.meta {
  color: #6b8cae;
  flex: 1;
  text-align: right;
}

.empty-tip {
  text-align: center;
  color: #6b8cae;
  padding: 40px 0;
}

.bs-loading,
.bs-error {
  text-align: center;
  padding: 80px;
  color: #9fb3d9;
}

.bs-error {
  color: #ff8a65;
}

@media (max-width: 1400px) {
  .kpi-row {
    grid-template-columns: repeat(4, 1fr);
  }
  .chart-row {
    grid-template-columns: 1fr 1fr;
  }
  .chart-row-main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .bs-header {
    grid-template-columns: 1fr;
  }
  .kpi-row,
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
