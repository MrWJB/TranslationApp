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
      <div class="header-right">
        <el-tooltip :content="themeStore.mode === 'dark' ? '切换亮色主题' : '切换暗色主题'" placement="bottom">
          <el-button :icon="themeStore.mode === 'dark' ? Sunny : Moon" circle size="small" @click="themeStore.toggleTheme" />
        </el-tooltip>
        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="user-dropdown-trigger">
            <el-avatar :size="36" :src="userStore.avatar || undefined" class="header-avatar">
              {{ userStore.avatarInitial }}
            </el-avatar>
            <span class="user-display-name">{{ userStore.displayName }}</span>
            <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled class="dropdown-user-info">
                <div>{{ userStore.displayName }}</div>
                <div class="dropdown-username">@{{ userStore.username }}</div>
              </el-dropdown-item>
              <el-dropdown-item divided command="profile">
                <el-icon><User /></el-icon>个人设置
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container>
      <el-aside :width="isCollapsed ? '64px' : '200px'" class="aside-container">
        <el-menu class="sidebar-menu" :default-active="activeMenu" :collapse="isCollapsed" router>
          <SidebarMenuNodes :menus="sidebarMenus" :im-unread="imUnread" />
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useImStore } from '@/stores/im'
import { useThemeStore } from '@/stores/theme'
import { getUserMenuTree } from '@/api'
import type { Menu } from '@/types'
import SidebarMenuNodes from './SidebarMenuNodes.vue'
import { Fold, Expand, Document, User, ArrowDown, SwitchButton, Sunny, Moon } from '@element-plus/icons-vue'

/** 未在菜单管理中配置时的兜底项，可通过菜单管理覆盖 */
const FALLBACK_MENUS: Menu[] = [
  {
    id: -1,
    name: '消息',
    path: '/messages',
    icon: 'ChatLineRound',
    sortOrder: 50,
    isVisible: true,
    isEnabled: true,
  },
  {
    id: -2,
    name: '个人设置',
    path: '/profile',
    icon: 'Postcard',
    sortOrder: 90,
    isVisible: true,
    isEnabled: true,
  },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const imStore = useImStore()
const themeStore = useThemeStore()

const imUnread = computed(() => imStore.unreadTotal)

const isCollapsed = ref(false)
const sidebarMenus = ref<Menu[]>([])

function collectPaths(menus: Menu[], paths = new Set<string>()): Set<string> {
  for (const menu of menus) {
    if (menu.path) paths.add(menu.path)
    if (menu.children?.length) collectPaths(menu.children, paths)
  }
  return paths
}

function mergeFallbackMenus(tree: Menu[]): Menu[] {
  const paths = collectPaths(tree)
  const extras = FALLBACK_MENUS.filter((m) => m.path && !paths.has(m.path))
  if (extras.length === 0) return tree
  return [...tree, ...extras].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
}

const loadSidebarMenus = async () => {
  try {
    const menus = await getUserMenuTree()
    sidebarMenus.value = mergeFallbackMenus(menus)
  } catch {
    sidebarMenus.value = [...FALLBACK_MENUS]
  }
}

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const activeMenu = computed(() => route.path)

onMounted(() => {
  loadSidebarMenus()
  userStore.fetchProfile().catch(() => {})
  imStore.loadConversations().catch(() => {})
  if (!imStore.wsConnected) imStore.connect()
})

watch(
  () => route.path,
  (_, oldPath) => {
    if (oldPath?.startsWith('/system/')) {
      loadSidebarMenus()
    }
  }
)

onUnmounted(() => {
  /* keep WS alive while app is open; disconnect on logout only */
})

const handleLogout = () => {
  imStore.disconnect()
  userStore.logout()
  router.push('/login')
}

const handleUserCommand = (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    handleLogout()
  }
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

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-dropdown-trigger:hover {
  background-color: var(--table-row-hover);
}

.header-avatar {
  flex-shrink: 0;
  background: var(--primary-color);
  color: #fff;
  font-size: 14px;
}

.user-display-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.dropdown-arrow {
  font-size: 12px;
  color: var(--text-secondary);
}

:deep(.dropdown-user-info) {
  cursor: default;
  opacity: 1 !important;
  line-height: 1.4;
}

.dropdown-username {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
