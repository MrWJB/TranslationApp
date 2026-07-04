<template>
  <div class="chat-panel">
    <ChatHeader @search="onSearch" @group-info="emit('group-info')" />
    <MessageList ref="messageListRef" :highlight-id="highlightMessageId" />
    <MessageComposer />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useImStore } from '@/stores/im'
import ChatHeader from './ChatHeader.vue'
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'

const emit = defineEmits<{ 'group-info': [] }>()
const imStore = useImStore()
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)
const highlightMessageId = ref<number | null>(null)

async function onSearch(q: string) {
  if (!imStore.activeConversationId) return
  const results = await imStore.search(q, imStore.activeConversationId)
  if (results.length) {
    highlightMessageId.value = results[0].id
    messageListRef.value?.scrollToMessage(results[0].id)
  }
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
