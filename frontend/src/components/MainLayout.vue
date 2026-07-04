<template>
  <el-container class="main-layout">
    <el-header class="main-header">
      <div class="header-left">
        <div class="logo-area">
          <el-icon :size="28" class="logo-icon"><Document /></el-icon>
          <span class="logo-text">文档翻译系统</span>
        </div>
        <div class="collapse-toggle" @click="toggleCollapse">
          <el-icon :size="20">
            <Expand v-if="isCollapsed" />
            <Fold v-else />
          </el-icon>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 12px">
        <el-tooltip :content="themeStore.mode === 'dark' ? '切换亮色主题' : '切换暗色主题'" placement="bottom">
          <el-button :icon="themeStore.mode === 'dark' ? 'Sunny' : 'Moon'" circle size="small" @click="themeStore.toggleTheme" />
        </el-tooltip>
        <span style="margin-right: 15px">{{ userStore.username }}</span>
        <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside :width="isCollapsed ? '64px' : '200px'" class="aside-container">
        <el-menu class="sidebar-menu" :default-active="activeMenu" :collapse="isCollapsed" router>
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <el-icon><List /></el-icon>
            <template #title>爬取任务</template>
          </el-menu-item>
          <el-menu-item index="/messages">
            <el-icon><ChatLineRound /></el-icon>
            <template #title>
              <span class="menu-with-badge">
                消息
                <el-badge v-if="imUnread > 0" :value="imUnread" class="menu-badge" />
              </span>
            </template>
          </el-menu-item>
          <el-sub-menu index="documents">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>文档列表</span>
            </template>
            <el-menu-item v-for="item in documentMenuItems" :key="item.path" :index="item.path">
              <el-icon><component :is="resolveDocMenuIcon(item.icon)" /></el-icon>
              <template #title>{{ item.name }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="video">
            <template #title>
              <el-icon><VideoPlay /></el-icon>
              <span>视频管理</span>
            </template>
            <el-menu-item index="/video/anime">
              <el-icon><MagicStick /></el-icon>
              <template #title>动漫</template>
            </el-menu-item>
            <el-menu-item index="/video/short-drama">
              <el-icon><Film /></el-icon>
              <template #title>短剧</template>
            </el-menu-item>
            <el-menu-item index="/video/tv-series">
              <el-icon><Monitor /></el-icon>
              <template #title>电视剧</template>
            </el-menu-item>
            <el-menu-item index="/video/movie">
              <el-icon><VideoCamera /></el-icon>
              <template #title>电影</template>
            </el-menu-item>
            <el-menu-item index="/video/variety">
              <el-icon><Microphone /></el-icon>
              <template #title>综艺</template>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/system/users">
              <el-icon><User /></el-icon>
              <template #title>用户管理</template>
            </el-menu-item>
            <el-menu-item index="/system/roles">
              <el-icon><UserFilled /></el-icon>
              <template #title>角色管理</template>
            </el-menu-item>
            <el-menu-item index="/system/permissions">
              <el-icon><Key /></el-icon>
              <template #title>权限管理</template>
            </el-menu-item>
            <el-menu-item index="/system/menus">
              <el-icon><MenuIcon /></el-icon>
              <template #title>菜单管理</template>
            </el-menu-item>
            <el-menu-item index="/system/departments">
              <el-icon><OfficeBuilding /></el-icon>
              <template #title>部门管理</template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useImStore } from '@/stores/im'
import { useThemeStore } from '@/stores/theme'
import { getDocumentMenus } from '@/api'
import type { Menu } from '@/types'
import { Fold, Expand, HomeFilled, List, Document, Notebook, Connection, Lightning, Cloudy, DataLine, DataAnalysis, Lock, VideoPlay, MagicStick, Film, Monitor, VideoCamera, Microphone, Setting, User, UserFilled, Key, Menu as MenuIcon, ChatLineRound, OfficeBuilding } from '@element-plus/icons-vue'

interface DocMenuItem {
  path: string
  name: string
  icon: string
}

const DEFAULT_DOC_MENUS: DocMenuItem[] = [
  { path: '/documents/java', name: 'Java', icon: 'Notebook' },
  { path: '/documents/spring', name: 'Spring', icon: 'Connection' },
  { path: '/documents/spring-boot', name: 'Spring Boot', icon: 'Lightning' },
  { path: '/documents/spring-cloud', name: 'Spring Cloud', icon: 'Cloudy' },
  { path: '/documents/spring-mvc', name: 'Spring Mvc', icon: 'DataLine' },
  { path: '/documents/mysql', name: 'Mysql', icon: 'DataAnalysis' },
  { path: '/documents/oracle', name: 'Oracle', icon: 'Lock' },
]

const DOC_ICON_MAP: Record<string, Component> = {
  Notebook,
  Connection,
  Lightning,
  Cloudy,
  DataLine,
  DataAnalysis,
  Lock,
  Document,
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const imStore = useImStore()
const themeStore = useThemeStore()

const imUnread = computed(() => imStore.unreadTotal)

const isCollapsed = ref(false)
const documentMenuItems = ref<DocMenuItem[]>([...DEFAULT_DOC_MENUS])

const mergeDocumentMenus = (dbMenus: Menu[]) => {
  const merged = new Map<string, DocMenuItem>()
  for (const item of DEFAULT_DOC_MENUS) {
    merged.set(item.path, item)
  }
  for (const menu of dbMenus) {
    if (!menu.path) continue
    merged.set(menu.path, {
      path: menu.path,
      name: menu.name,
      icon: menu.icon || 'Document',
    })
  }
  documentMenuItems.value = Array.from(merged.values())
}

const loadDocumentMenus = async () => {
  try {
    const menus = await getDocumentMenus()
    if (menus.length > 0) {
      mergeDocumentMenus(menus)
    }
  } catch (err) {
    console.error('Failed to load document menus:', err)
  }
}

const resolveDocMenuIcon = (iconName?: string) => {
  return DOC_ICON_MAP[iconName || 'Document'] || Document
}

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const activeMenu = computed(() => route.path)

onMounted(() => {
  loadDocumentMenus()
  imStore.loadConversations().catch(() => {})
  if (!imStore.wsConnected) imStore.connect()
})

onUnmounted(() => {
  /* keep WS alive while app is open; disconnect on logout only */
})

const handleLogout = () => {
  imStore.disconnect()
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.main-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  background: var(--header-bg);
  box-shadow: 0 2px 8px var(--shadow-color);
  transition: background-color 0.3s, color 0.3s;
  color: var(--text-primary);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  color: var(--primary-color);
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  color: var(--primary-color);
}

.collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: 6px;
  transition: background-color 0.3s, color 0.3s;
}

.collapse-toggle:hover {
  background-color: var(--table-row-hover);
  color: var(--primary-color);
}

.aside-container {
  background: var(--aside-bg);
  box-shadow: 2px 0 8px var(--shadow-color);
  transition: width 0.3s ease, background-color 0.3s;
  overflow: hidden;
}

.el-menu {
  border-right: none;
  background: var(--aside-bg);
  transition: background-color 0.3s;
  overflow: hidden;
  --el-menu-level-padding: 18px;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 200px;
}

/* 仅顶层菜单项，勿匹配 .el-menu--inline 内的子项（否则会覆盖缩进） */
:deep(.sidebar-menu:not(.el-menu--collapse):not(.el-menu--inline) > .el-menu-item),
:deep(.sidebar-menu:not(.el-menu--collapse) > .el-sub-menu > .el-sub-menu__title) {
  padding-left: var(--el-menu-base-level-padding);
  padding-right: var(--el-menu-base-level-padding);
}

:deep(.sidebar-menu .el-menu--inline > .el-menu-item) {
  padding-left: calc(
    var(--el-menu-base-level-padding) + var(--el-menu-level, 1) * var(--el-menu-level-padding)
  );
}

.el-menu--collapse {
  transition: width 0.3s ease;
}

.el-menu-item,
.el-sub-menu__title {
  transition: padding 0.3s ease;
}

.el-menu--collapse > .el-menu-item,
.el-menu--collapse > .el-sub-menu > .el-sub-menu__title {
  padding-left: 0 !important;
  padding-right: 0;
  justify-content: center;
}

:deep(.el-menu--popup.el-menu--vertical .el-menu-item) {
  padding-left: calc(
    var(--el-menu-base-level-padding) + var(--el-menu-level, 1) * var(--el-menu-level-padding)
  );
}

.el-main {
  background: var(--background-color);
  transition: background-color 0.3s;
  color: var(--text-primary);
}

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
