import { ElMessageBox } from 'element-plus'
import { matchDocumentMenu, type DocumentMenuMatchResult } from '@/api'

export interface DocumentMenuCrawlOptions {
  createMenu?: boolean
  menuName?: string
  menuPath?: string
}

export async function promptDocumentMenuIfNeeded(
  url: string,
  options: { category?: string; profileName?: string; taskType?: string } = {}
): Promise<DocumentMenuCrawlOptions> {
  if (options.taskType && options.taskType !== 'document') {
    return {}
  }

  let match: DocumentMenuMatchResult
  try {
    match = await matchDocumentMenu(url, options.category, options.profileName)
  } catch {
    return {}
  }

  if (match.matched) {
    return {}
  }

  try {
    const { value } = await ElMessageBox.prompt(
      `路径：${match.menuPath}\n建议名称：${match.suggestedName}`,
      '创建文档菜单',
      {
        confirmButtonText: '创建并继续',
        cancelButtonText: '跳过',
        distinguishCancelAndClose: true,
        inputValue: match.suggestedName,
        inputPlaceholder: '请输入菜单显示名称（简称）',
        inputValidator: (val) => (val?.trim() ? true : '请输入菜单名称'),
      }
    )
    return {
      createMenu: true,
      menuName: value.trim(),
      menuPath: match.menuPath,
    }
  } catch {
    return {}
  }
}
