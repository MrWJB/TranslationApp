<template>
  <el-scrollbar ref="scrollbarRef" class="message-list" @scroll="onScroll">
    <div class="load-more" v-if="hasMore">
      <el-button size="small" link :loading="loading" @click="loadMore">加载更多</el-button>
    </div>
    <MessageItem
      v-for="msg in activeMessages"
      :key="msg.id || msg.clientMsgId"
      :message="msg"
      :highlight="msg.id === highlightId"
    />
  </el-scrollbar>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useImStore } from '@/stores/im'
import MessageItem from './MessageItem.vue'

defineProps<{ highlightId?: number | null }>()

const imStore = useImStore()
const { activeMessages, activeConversationId } = storeToRefs(imStore)
const scrollbarRef = ref()
const loading = ref(false)
const hasMore = ref(true)

async function scrollToBottom() {
  await nextTick()
  const wrap = scrollbarRef.value?.wrapRef as HTMLElement | undefined
  if (wrap) wrap.scrollTop = wrap.scrollHeight
}

async function loadMore() {
  const convId = activeConversationId.value
  if (!convId || loading.value) return
  const msgs = activeMessages.value
  if (!msgs.length) return
  loading.value = true
  try {
    const older = await imStore.loadMessages(convId, msgs[0].id)
    hasMore.value = older.length >= 30
  } finally {
    loading.value = false
  }
}

function onScroll() {
  /* placeholder for scroll tracking */
}

function scrollToMessage(messageId: number) {
  const el = document.getElementById(`msg-${messageId}`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

watch(
  () => activeMessages.value.length,
  () => scrollToBottom()
)

watch(
  () => activeConversationId.value,
  () => {
    hasMore.value = true
    scrollToBottom()
  }
)

onMounted(scrollToBottom)

defineExpose({ scrollToMessage, scrollToBottom })
</script>

<style scoped>
.message-list {
  flex: 1;
  padding: 16px;
}

.load-more {
  text-align: center;
  padding-bottom: 8px;
}
</style>
