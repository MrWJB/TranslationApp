export interface TocNavNode {
  id?: number | null
  title: string
  level?: number
  url?: string
  localPath?: string
  translatedLocalPath?: string
  available?: boolean
  unavailableReason?: 'external-api' | 'excluded-by-profile' | 'nav-folder' | 'not-crawled'
  external?: boolean
  nodeKey?: string
  children?: TocNavNode[]
}

export function normalizeLocalPath(path?: string | null): string {
  if (!path) return ''
  return path.replace(/\\/g, '/').replace(/^\/+/, '').split('#')[0]
}

export function pathsMatch(a?: string | null, b?: string | null): boolean {
  const left = normalizeLocalPath(a)
  const right = normalizeLocalPath(b)
  if (!left || !right) return false
  return left === right || left.endsWith(`/${right}`) || right.endsWith(`/${left}`)
}

export function findTocNodeByLocalPath<T extends TocNavNode>(
  items: T[],
  targetPath: string,
): T | null {
  for (const item of items) {
    if (pathsMatch(item.localPath, targetPath)) {
      return item
    }
    if (item.children?.length) {
      const found = findTocNodeByLocalPath(item.children as T[], targetPath)
      if (found) return found
    }
  }
  return null
}

export function findTocPathByLocalPath<T extends TocNavNode>(
  items: T[],
  targetPath: string,
  trail: T[] = [],
): T[] | null {
  for (const item of items) {
    const nextTrail = [...trail, item]
    if (pathsMatch(item.localPath, targetPath)) {
      return nextTrail
    }
    if (item.children?.length) {
      const found = findTocPathByLocalPath(item.children as T[], targetPath, nextTrail)
      if (found) return found
    }
  }
  return null
}

export function findTocPathById<T extends TocNavNode>(
  items: T[],
  targetId: number,
  trail: T[] = [],
): T[] | null {
  for (const item of items) {
    const nextTrail = [...trail, item]
    if (item.id === targetId) {
      return nextTrail
    }
    if (item.children?.length) {
      const found = findTocPathById(item.children as T[], targetId, nextTrail)
      if (found) return found
    }
  }
  return null
}

export function findTocNodeByUrl<T extends TocNavNode>(items: T[], targetUrl: string): T | null {
  for (const item of items) {
    if (item.url === targetUrl) {
      return item
    }
    if (item.children?.length) {
      const found = findTocNodeByUrl(item.children as T[], targetUrl)
      if (found) return found
    }
  }
  return null
}

export function findTocPathByUrl<T extends TocNavNode>(
  items: T[],
  targetUrl: string,
  trail: T[] = [],
): T[] | null {
  for (const item of items) {
    const nextTrail = [...trail, item]
    if (item.url === targetUrl) {
      return nextTrail
    }
    if (item.children?.length) {
      const found = findTocPathByUrl(item.children as T[], targetUrl, nextTrail)
      if (found) return found
    }
  }
  return null
}

/** Resolve breadcrumb trail from localPath (preferred), url, or document id. */
export function findTocPath<T extends TocNavNode>(
  items: T[],
  options: { localPath?: string | null; url?: string | null; id?: number | null },
): T[] {
  const { localPath, url, id } = options
  if (localPath) {
    const byPath = findTocPathByLocalPath(items, localPath)
    if (byPath?.length) return byPath
  }
  if (url) {
    const byUrl = findTocPathByUrl(items, url)
    if (byUrl?.length) return byUrl
  }
  if (id != null) {
    return findTocPathById(items, id) ?? []
  }
  return []
}

export function tocAncestorIds(path: TocNavNode[]): number[] {
  return path
    .slice(0, -1)
    .map((node) => node.id)
    .filter((id): id is number => id != null)
}

export function findFirstTocNodeWithPath<T extends TocNavNode>(items: T[]): T | null {
  for (const item of items) {
    if (item.localPath) {
      return item
    }
    if (item.children?.length) {
      const found = findFirstTocNodeWithPath(item.children as T[])
      if (found) return found
    }
  }
  return null
}

export function tocNodeContainsId(item: TocNavNode, activeId: number | null): boolean {
  if (!activeId) return false
  if (item.id === activeId) return true
  return item.children?.some((child) => tocNodeContainsId(child, activeId)) ?? false
}

/** Stable keys for el-tree (many TOC nodes have no database id). */
export function assignTocNodeKeys<T extends TocNavNode>(items: T[], prefix = 'root'): T[] {
  return items.map((item, index) => {
    const slug =
      normalizeLocalPath(item.localPath) ||
      (item.url?.startsWith('http') ? `ext-${item.url.replace(/[^a-z0-9]+/gi, '-').slice(-80)}` : '') ||
      item.title.replace(/\s+/g, '-').toLowerCase()
    const nodeKey = `${prefix}/${index}-${slug}`
    const children = item.children?.length
      ? assignTocNodeKeys(item.children as T[], nodeKey)
      : item.children
    return { ...item, nodeKey, children }
  })
}

export function findTocNodeByKey<T extends TocNavNode>(items: T[], key: string): T | null {
  for (const item of items) {
    if (item.nodeKey === key) return item
    if (item.children?.length) {
      const found = findTocNodeByKey(item.children as T[], key)
      if (found) return found
    }
  }
  return null
}

export function tocAncestorKeys(path: TocNavNode[]): string[] {
  return path
    .slice(0, -1)
    .map((node) => node.nodeKey)
    .filter((key): key is string => Boolean(key))
}
