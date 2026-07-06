<template>
  <template v-for="menu in menus" :key="menu.id">
    <el-sub-menu v-if="hasSidebarChildren(menu)" :index="menuIndex(menu)">
      <template #title>
        <el-icon><component :is="resolveMenuIcon(menu.icon)" /></el-icon>
        <span>{{ menu.name }}</span>
      </template>
      <SidebarMenuNodes :menus="sidebarChildren(menu)" :im-unread="imUnread" />
    </el-sub-menu>
    <el-menu-item v-else-if="menu.path && !menu.path.includes(':')" :index="menu.path">
      <el-icon><component :is="resolveMenuIcon(menu.icon)" /></el-icon>
      <template #title>
        <span v-if="menu.path === '/messages'" class="menu-with-badge">
          {{ menu.name }}
          <el-badge v-if="imUnread > 0" :value="imUnread" class="menu-badge" />
        </span>
        <span v-else>{{ menu.name }}</span>
      </template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { Menu } from '@/types'
import { resolveMenuIcon } from '@/utils/menuIcons'
import SidebarMenuNodes from './SidebarMenuNodes.vue'

defineProps<{
  menus: Menu[]
  imUnread?: number
}>()

function menuIndex(menu: Menu) {
  return menu.path || `menu-${menu.id}`
}

/** 路由参数占位（如 /tasks/:id）不在侧边栏展示，也不因此把父级变成空 sub-menu */
function sidebarChildren(menu: Menu): Menu[] {
  return (menu.children ?? []).filter((child) => child.path && !child.path.includes(':'))
}

function hasSidebarChildren(menu: Menu) {
  return sidebarChildren(menu).length > 0
}
</script>

<style scoped>
.menu-with-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.menu-badge :deep(.el-badge__content) {
  transform: none;
  position: static;
}
</style>
