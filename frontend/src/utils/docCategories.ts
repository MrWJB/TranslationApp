/**
 * Map siteKey / stored categories to UI document-list filters.
 */
export const categoryNames: Record<string, string> = {
  '': '文档列表',
  java: 'Java',
  spring: 'Spring',
  'spring-boot': 'Spring Boot',
  'spring-cloud': 'Spring Cloud',
  'spring-mvc': 'Spring Mvc',
  mysql: 'Mysql',
  oracle: 'Oracle',
}

/** Normalize task category for grouping in document list. */
export function normalizeTaskCategory(task: { category?: string; url?: string }): string {
  const cat = (task.category || '').trim()
  const url = (task.url || '').toLowerCase()
  if (cat === 'spring-framework-reference' || url.includes('spring-framework')) return 'spring'
  if (cat.includes('spring-boot') || url.includes('spring-boot')) return 'spring-boot'
  if (cat.includes('spring-cloud') || url.includes('spring-cloud')) return 'spring-cloud'
  if (cat.includes('spring-mvc') || url.includes('spring-mvc')) return 'spring-mvc'
  if (cat && categoryNames[cat]) return cat
  if (url.includes('mysql')) return 'mysql'
  if (url.includes('oracle')) return 'oracle'
  if (url.includes('java') || url.includes('javase')) return 'java'
  return cat || 'other'
}

export function taskMatchesCategory(
  task: { category?: string; url?: string },
  filterCategory: string
): boolean {
  if (!filterCategory) return true
  const normalized = normalizeTaskCategory(task)
  if (normalized === filterCategory) return true
  const rawCategory = (task.category || '').trim()
  return rawCategory === filterCategory
}
