<template>
  <div
    :id="`msg-${message.id}`"
    class="message-item"
    :class="{ mine: isMine, highlight }"
  >
    <el-avatar v-if="!isMine" :size="36" :src="message.senderAvatar">
      {{ (message.senderName || '?').slice(0, 1) }}
    </el-avatar>
    <div class="bubble-wrap">
      <div v-if="!isMine && showSender" class="sender-name">{{ message.senderName }}</div>
      <div class="bubble" :class="message.type.toLowerCase()">
        <template v-if="message.type === 'TEXT' || message.type === 'SYSTEM'">
          <span class="text-content">{{ message.content }}</span>
        </template>
        <template v-else-if="message.type === 'IMAGE'">
          <el-image
            :src="message.attachment?.downloadUrl || message.content"
            fit="cover"
            class="msg-image"
            :preview-src-list="[message.attachment?.downloadUrl || message.content]"
          />
        </template>
        <template v-else-if="message.type === 'FILE'">
          <div class="file-msg">
            <el-icon><Document /></el-icon>
            <span>{{ message.attachment?.fileName || message.content }}</span>
          </div>
        </template>
        <template v-else-if="message.type === 'LINK'">
          <a :href="message.content" target="_blank" rel="noopener" class="link-msg">{{ message.content }}</a>
        </template>
        <template v-else>
          <span>{{ message.content }}</span>
        </template>
      </div>
      <div class="meta">
        <span class="time">{{ formatImMessageTime(message.createdAt) }}</span>
        <span v-if="isMine && message.readCount" class="read">已读</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useImStore } from '@/stores/im'
import { formatImMessageTime } from '@/utils/date'
import type { Message } from '@/types/im'

const props = defineProps<{
  message: Message
  highlight?: boolean
}>()

const userStore = useUserStore()
const imStore = useImStore()

const isMine = computed(() => props.message.senderId === userStore.userId)
const showSender = computed(() => imStore.activeConversation?.type === 'GROUP')
</script>

<style scoped>
.message-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.message-item.mine {
  flex-direction: row-reverse;
}

.message-item.highlight .bubble {
  outline: 2px solid var(--primary-color);
}

.bubble-wrap {
  max-width: 65%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-item.mine .bubble-wrap {
  align-items: flex-end;
}

.sender-name {
  font-size: 12px;
  color: var(--text-secondary);
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--card-bg);
  color: var(--text-primary);
  word-break: break-word;
}

.message-item.mine .bubble {
  background: var(--primary-color);
  color: #fff;
}

.msg-image {
  max-width: 240px;
  max-height: 200px;
  border-radius: 8px;
}

.file-msg,
.link-msg {
  display: flex;
  align-items: center;
  gap: 6px;
}

.link-msg {
  color: inherit;
}

.meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--text-secondary);
}

.read {
  color: var(--success-color);
}
</style>
