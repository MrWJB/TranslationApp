import type { TocItem, UnavailableReason } from '@/types'

export type { UnavailableReason }

/** Minimal fields needed for external-link / availability helpers */
export type TocLinkLike = {
  title?: string
  url?: string | null
  external?: boolean
  available?: boolean
  localPath?: string | null
  unavailableReason?: UnavailableReason
  children?: TocLinkLike[]
}

export function normalizeTocPath(path?: string | null): string {
  if (!path) return ''
  return path.replace(/\\/g, '/').replace(/^\/+/, '').split('#')[0]
}

export function resolveExternalUrl(node: TocLinkLike): string | null {
  const url = (node.url || '').trim()
  if (url.startsWith('http')) return url
  return null
}

const EXTERNAL_LINK_TITLES = new Set([
  'Java API',
  'Kotlin API',
  'Wiki',
  'Javadoc',
  'Specifications',
  'Java APIs',
  'Kotlin APIs',
  'Gradle Plugin',
  'Maven Plugin',
])

export function isExternalLinkNode(node: TocLinkLike): boolean {
  const url = resolveExternalUrl(node)
  const title = (node.title || '').trim()
  if (url) {
    if (node.external === true) return true
    if (/javadoc-api|kdoc-api/i.test(url)) return true
    if (/github\.com/i.test(url)) return true
    if (EXTERNAL_LINK_TITLES.has(title)) return true
    if (node.unavailableReason === 'external-api') return true
    if (isExternalApiTocNode(node)) return true
  }
  if (node.external === true && (node.url || '').trim()) return true
  if (node.unavailableReason === 'external-api' && (node.url || node.localPath)) return true
  if (isExternalApiTocNode(node)) return true
  if (EXTERNAL_LINK_TITLES.has(title) && (node.url || node.localPath)) return true
  return false
}

/** @deprecated use isExternalLinkNode */
export const isExternalViewableNode = isExternalLinkNode

/** Open external TOC link in a new browser tab. Returns true if handled. */
export function openExternalTocLink(node: TocLinkLike): boolean {
  if (!isExternalLinkNode(node)) return false
  const url = resolveExternalUrl(node)
  if (!url) return false
  window.open(url, '_blank', 'noopener,noreferrer')
  return true
}

export function isExternalApiTocNode(node: TocLinkLike): boolean {
  if (node.external === true) return true
  const path = normalizeTocPath(node.localPath)
  const url = node.url || ''
  if (/\/api\/java(?:\/|$)/i.test(path)) return true
  if (/^api\/java(?:\/|$)/i.test(path)) return true
  if (/\/api\/kotlin(?:\/|$)/i.test(path)) return true
  if (/^api\/kotlin(?:\/|$)/i.test(path)) return true
  if (/gradle-plugin\/api\/java(?:\/|$)/i.test(path)) return true
  if (/maven-plugin\/api\/java(?:\/|$)/i.test(path)) return true
  if (/appendix\/api\//i.test(path)) return true
  if (/javadoc-api|kdoc-api/i.test(url)) return true
  return false
}

export function isExcludedByProfileTocNode(node: TocLinkLike): boolean {
  const path = normalizeTocPath(node.localPath)
  if (!path) return false
  // Paths still excluded by antora-spring profile (Javadoc handled as external-api).
  if (/appendix\/api\//i.test(path)) return true
  if (/\/api\/java(?:\/|$)/i.test(path)) return true
  if (/^api\/java(?:\/|$)/i.test(path)) return true
  if (/\/api\/kotlin(?:\/|$)/i.test(path)) return true
  if (/^api\/kotlin(?:\/|$)/i.test(path)) return true
  if (/gradle-plugin\/api\/java(?:\/|$)/i.test(path)) return true
  if (/maven-plugin\/api\/java(?:\/|$)/i.test(path)) return true
  return false
}

export function resolveUnavailableReason(node: TocLinkLike & { children?: TocLinkLike[] }): UnavailableReason | null {
  if (node.available !== false) return null
  if (node.unavailableReason) {
    return node.unavailableReason
  }
  if (isExternalApiTocNode(node)) return 'external-api'
  if (!normalizeTocPath(node.localPath) && (node.children?.length ?? 0) > 0) {
    return 'nav-folder'
  }
  if (isExcludedByProfileTocNode(node)) return 'excluded-by-profile'
  return 'not-crawled'
}

export function unavailableReasonLabel(reason: UnavailableReason): string {
  switch (reason) {
    case 'external-api':
      return '外部 API'
    case 'excluded-by-profile':
      return '已排除'
    case 'nav-folder':
      return ''
    case 'not-crawled':
      return '未爬取'
    default:
      return '不可用'
  }
}

export function unavailableReasonMessage(node: TocLinkLike, reason: UnavailableReason): string {
  switch (reason) {
    case 'external-api':
      return node.url
        ? `外部 API 文档（Javadoc/KDoc），本系统不爬取翻译。官方链接：${node.url}`
        : '外部 API 文档（Javadoc/KDoc），本系统不爬取翻译。'
    case 'excluded-by-profile':
      return '该章节被爬虫 Profile 排除（如 Javadoc 路径）。本系统不收录此类页面。'
    case 'nav-folder':
      return ''
    case 'not-crawled':
      return '该章节尚未爬取（可能因网络超时或 maxPages 不足）。请重新爬取或点击「重建目录」。'
    default:
      return '该章节不可用。'
  }
}

export function isNavFolderNode(node: TocLinkLike & { children?: TocLinkLike[] }): boolean {
  return resolveUnavailableReason(node) === 'nav-folder'
}
