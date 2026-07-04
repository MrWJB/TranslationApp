<template>
  <header class="chat-header">
    <div class="header-left">
      <h3>{{ title }}</h3>
      <span v-if="typingText" class="typing">{{ typingText }}</span>
    </div>
    <div class="header-actions">
      <el-input
        v-model="searchQ"
        placeholder="搜索消息"
        size="small"
        clearable
        style="width: 160px"
        @keyup.enter="emit('search', searchQ)"
      />
      <el-button v-if="isGroup" circle :icon="InfoFilled" @click="emit('group-info')" title="群信息" />
      <el-button circle :icon="Phone" @click="startAudioCall" title="语音通话" />
      <el-button circle :icon="VideoCamera" @click="startVideoCall" title="视频通话" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { InfoFilled, Phone, VideoCamera } from '@element-plus/icons-vue'
import { useImStore } from '@/stores/im'
import { getConversationDisplayTitle } from '@/utils/conversation'
import { useUserStore } from '@/stores/user'

const emit = defineEmits<{ search: [q: string]; 'group-info': [] }>()
const imStore = useImStore()
const userStore = useUserStore()
const searchQ = ref('')

const conv = computed(() => imStore.activeConversation)

const title = computed(() => {
  if (!conv.value) return ''
  return getConversationDisplayTitle(conv.value, imStore.friends)
})

const isGroup = computed(() => conv.value?.type === 'GROUP')

const typingText = computed(() => {
  const id = imStore.activeConversationId
  if (!id) return ''
  const users = imStore.typingUsers[id]
  if (!users?.size) return ''
  const others = [...users].filter((u) => u !== userStore.userId)
  if (!others.length) return ''
  return '对方正在输入...'
})

async function startAudioCall() {
  if (!conv.value) return
  await imStore.startCall(conv.value.id, 'AUDIO')
}

async function startVideoCall() {
  if (!conv.value) return
  await imStore.startCall(conv.value.id, 'VIDEO')
}
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--card-bg);
}

.header-left h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.typing {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
