import type { ECharts } from 'echarts/core'

type EchartsModule = typeof import('echarts')

let echartsLoader: Promise<EchartsModule> | null = null

/**
 * 按需加载 ECharts，避免进入主包或无关页面。
 */
async function loadEcharts(): Promise<EchartsModule> {
  if (!echartsLoader) {
    echartsLoader = import('echarts')
  }
  return echartsLoader
}

/**
 * 大屏图表生命周期管理：懒加载 ECharts 并统一销毁/缩放。
 */
export function useEcharts() {
  const charts: ECharts[] = []

  const initChart = async (element: HTMLElement | undefined): Promise<ECharts | null> => {
    if (!element) {
      return null
    }
    const echarts = await loadEcharts()
    const chart = echarts.init(element, undefined, { renderer: 'canvas' })
    charts.push(chart)
    return chart
  }

  const disposeCharts = (): void => {
    charts.forEach(chart => chart.dispose())
    charts.length = 0
  }

  const resizeCharts = (): void => {
    charts.forEach(chart => chart.resize())
  }

  return {
    initChart,
    disposeCharts,
    resizeCharts,
  }
}
