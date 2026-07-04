<template>
  <div class="member-item">
    <el-avatar :size="32">{{ displayName.slice(0, 1) }}</el-avatar>
    <span class="member-name">{{ displayName }}</span>
    <div class="member-actions">
      <el-button size="small" plain @click="emit('chat', user.id)">发消息</el-button>
      <span v-if="isSelf" class="status-tag">自己</span>
      <span v-else-if="isFriend" class="status-tag">已是好友</span>
      <span v-else-if="sentUserIds.has(user.id)" class="status-tag">已申请</span>
      <el-button
        v-else
        type="primary"
        size="small"
        :loading="addingUserId === user.id"
        @click="emit('add-friend', user)"
      >
        添加好友
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useImStore } from '@/stores/im'
import { useUserStore } from '@/stores/user'
import type { ImUserSummary } from '@/types/im'

const props = defineProps<{
  user: ImUserSummary
  addingUserId: number | null
  sentUserIds: Set<number>
}>()

const emit = defineEmits<{ chat: [userId: number]; 'add-friend': [user: ImUserSummary] }>()

const imStore = useImStore()
const userStore = useUserStore()

const displayName = computed(() => props.user.realName || props.user.username)
const isSelf = computed(() => props.user.id === userStore.userId)
const isFriend = computed(() => imStore.friends.some((f) => f.userId === props.user.id))
</script>

<style scoped>
.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: 6px;
}

.member-item:hover {
  background: var(--table-row-hover);
}

.member-name {
  flex: 1;
  min-width: 0;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.status-tag {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}
</style>
