import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('@/views/OAuthCallback.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/bigscreen',
    name: 'BigScreen',
    component: () => import('@/views/BigScreen.vue'),
    meta: { requiresAuth: true, fullscreen: true },
  },
  {
    path: '/',
    component: () => import('@/components/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('@/views/TaskList.vue'),
      },
      {
        path: 'tasks/:id',
        name: 'TaskDetail',
        component: () => import('@/views/TaskDetail.vue'),
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/DocumentList.vue'),
      },
      {
        path: 'documents/:category',
        name: 'DocumentsCategory',
        component: () => import('@/views/DocumentList.vue'),
      },
      {
        path: 'document/:id',
        name: 'DocumentDetail',
        component: () => import('@/views/DocumentDetail.vue'),
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/im/ChatLayout.vue'),
      },
      {
        path: 'messages/:conversationId',
        name: 'MessagesConversation',
        component: () => import('@/views/im/ChatLayout.vue'),
      },
      {
        path: 'profile',
        name: 'UserSettings',
        component: () => import('@/views/profile/UserSettings.vue'),
      },
      {
        path: 'system/departments',
        name: 'DepartmentManagement',
        component: () => import('@/views/system/DepartmentManagement.vue'),
      },
      {
        path: 'system/users',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagement.vue'),
      },
      {
        path: 'system/roles',
        name: 'RoleManagement',
        component: () => import('@/views/system/RoleManagement.vue'),
      },
      {
        path: 'system/permissions',
        name: 'PermissionManagement',
        component: () => import('@/views/system/PermissionManagement.vue'),
      },
      {
        path: 'system/menus',
        name: 'MenuManagement',
        component: () => import('@/views/system/MenuManagement.vue'),
      },
      {
        path: 'video/anime',
        name: 'AnimeManagement',
        component: () => import('@/views/video/Anime.vue'),
      },
      {
        path: 'video/short-drama',
        name: 'ShortDramaManagement',
        component: () => import('@/views/video/ShortDrama.vue'),
      },
      {
        path: 'video/tv-series',
        name: 'TVSeriesManagement',
        component: () => import('@/views/video/TVSeries.vue'),
      },
      {
        path: 'video/movie',
        name: 'MovieManagement',
        component: () => import('@/views/video/Movie.vue'),
      },
      {
        path: 'video/variety',
        name: 'VarietyManagement',
        component: () => import('@/views/video/Variety.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && token) {
    next('/')
  } else {
    next()
  }
})

export default router
