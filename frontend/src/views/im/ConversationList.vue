<template>
  <div class="conversation-list">
    <div class="list-header">
      <el-input v-model="keyword" placeholder="搜索会话" clearable prefix-icon="Search" />
      <el-dropdown trigger="click" @command="onMenuCommand">
        <el-button type="primary" circle :icon="Plus" title="更多" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="add-friend">添加好友</el-dropdown-item>
            <el-dropdown-item command="create-group">创建群聊</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-scrollbar class="list-body">
      <div
        v-for="conv in filteredConversations"
        :key="conv.id"
        class="conv-item"
        :class="{ active: conv.id === imStore.activeConversationId }"
        @click="emit('select', conv.id)"
      >
        <el-avatar :size="40" :src="getConversationAvatarUrl(conv, imStore.friends)">
          {{ avatarText(conv) }}
        </el-avatar>
        <div class="conv-info">
          <div class="conv-top">
            <span class="conv-title">{{ displayTitle(conv) }}</span>
            <span class="conv-time">{{ formatImConversationTime(conv.lastMessageAt) }}</span>
          </div>
          <div class="conv-bottom">
            <span class="conv-preview">{{ conv.lastMessagePreview || '暂无消息' }}</span>
            <el-badge v-if="conv.unreadCount > 0" :value="conv.unreadCount" />
          </div>
        </div>
      </div>
      <el-empty v-if="!filteredConversations.length" description="暂无会话" :image-size="60" />
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useImStore } from '@/stores/im'
import { formatImConversationTime } from '@/utils/date'
import { getConversationAvatarUrl, getConversationDisplayTitle } from '@/utils/conversation'
import type { Conversation } from '@/types/im'

defineProps<{ compact?: boolean }>()
const emit = defineEmits<{ select: [id: number]; 'create-group': [] }>()

const imStore = useImStore()
const openAddFriend = inject<(() => void) | undefined>('openAddFriendDialog', undefined)
const keyword = ref('')

function onMenuCommand(command: string) {
  if (command === 'add-friend') {
    openAddFriend?.()
  } else if (command === 'create-group') {
    emit('create-group')
  }
}

const filteredConversations = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return imStore.conversations
  return imStore.conversations.filter((c) => displayTitle(c).toLowerCase().includes(q))
})

function displayTitle(conv: Conversation) {
  return getConversationDisplayTitle(conv, imStore.friends)
}

function avatarText(conv: Conversation) {
  const title = displayTitle(conv)
  return title.slice(0, 1).toUpperCase()
}
</script>

<style scoped>
.conversation-list {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.list-header {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.list-body {
  flex: 1;
}

.conv-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid var(--border-color);
}

.conv-item:hover,
.conv-item.active {
  background: var(--table-row-hover);
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-top,
.conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.conv-title {
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.conv-preview {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
</style>
