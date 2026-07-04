<template>
  <div class="contact-list">
    <div class="list-header">
      <el-input v-model="keyword" placeholder="搜索联系人" clearable prefix-icon="Search" />
      <el-button type="primary" @click="openAddFriend?.()">添加好友</el-button>
      <el-badge :value="pendingCount" :hidden="pendingCount === 0">
        <el-button circle :icon="Bell" @click="showRequests = true" title="好友申请" />
      </el-badge>
    </div>
    <el-scrollbar class="list-body">
      <div
        v-for="friend in filteredFriends"
        :key="friend.userId"
        class="contact-item"
        @click="emit('chat', friend.userId)"
      >
        <div class="avatar-wrap">
          <el-avatar :size="40" :src="friend.avatar">
            {{ (friend.realName || friend.username).slice(0, 1) }}
          </el-avatar>
          <span class="presence-dot" :class="friend.presenceStatus?.toLowerCase() || 'offline'" />
        </div>
        <div class="contact-info">
          <span class="contact-name">{{ friend.remark || friend.realName || friend.username }}</span>
          <span class="contact-sub">{{ friend.username }}</span>
        </div>
        <el-button size="small" type="primary" link @click.stop="emit('chat', friend.userId)">发消息</el-button>
      </div>
      <el-empty v-if="!filteredFriends.length" :image-size="60">
        <template #description>
          <p>暂无好友</p>
          <p class="empty-hint">点击上方「添加好友」搜索组织成员，对方同意后会显示在此</p>
        </template>
        <el-button type="primary" @click="openAddFriend?.()">添加好友</el-button>
      </el-empty>
    </el-scrollbar>

    <el-dialog v-model="showRequests" title="好友申请" width="480px">
      <div v-for="req in imStore.friendRequests.filter(r => r.status === 'PENDING')" :key="req.id" class="request-item">
        <span>{{ friendRequestDisplayName(req) }}</span>
        <span class="request-msg">{{ req.message }}</span>
        <div class="request-actions">
          <el-button size="small" type="primary" @click="imStore.acceptRequest(req.id)">同意</el-button>
          <el-button size="small" @click="imStore.rejectRequest(req.id)">拒绝</el-button>
        </div>
      </div>
      <el-empty v-if="pendingCount === 0" description="暂无待处理申请" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useImStore } from '@/stores/im'
import type { FriendRequest } from '@/types/im'

function friendRequestDisplayName(req: FriendRequest) {
  return req.fromUser?.realName || req.fromUser?.username || `用户 ${req.fromUserId}`
}

const emit = defineEmits<{ chat: [userId: number] }>()
const imStore = useImStore()
const openAddFriend = inject<(() => void) | undefined>('openAddFriendDialog', undefined)
const keyword = ref('')
const showRequests = ref(false)

const pendingCount = computed(() =>
  imStore.friendRequests.filter((r) => r.status === 'PENDING').length
)

const filteredFriends = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return imStore.friends
  return imStore.friends.filter(
    (f) =>
      f.username.toLowerCase().includes(q) ||
      (f.realName?.toLowerCase().includes(q) ?? false) ||
      (f.remark?.toLowerCase().includes(q) ?? false)
  )
})
</script>

<style scoped>
.contact-list {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.list-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.list-header .el-input {
  flex: 1;
  min-width: 120px;
}

.list-body {
  flex: 1;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-color);
  transition: background 0.2s;
}

.contact-item:hover {
  background: var(--table-row-hover);
}

.avatar-wrap {
  position: relative;
}

.presence-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--card-bg);
  background: var(--text-secondary);
}

.presence-dot.online {
  background: var(--success-color);
}

.presence-dot.away {
  background: var(--warning-color);
}

.contact-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.contact-name {
  font-weight: 500;
  color: var(--text-primary);
}

.contact-sub {
  font-size: 12px;
  color: var(--text-secondary);
}

.request-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
}

.request-msg {
  font-size: 13px;
  color: var(--text-secondary);
}

.request-actions {
  display: flex;
  gap: 8px;
}

.empty-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
