import type { Component } from 'vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { Document } from '@element-plus/icons-vue'

const iconMap = ElementPlusIcons as Record<string, Component>

export function resolveMenuIcon(iconName?: string): Component {
  if (!iconName) return Document
  return iconMap[iconName] || Document
}
