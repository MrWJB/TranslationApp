<template>
  <div class="chat-layout">
    <aside class="chat-sidebar">
      <div class="sidebar-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
          <el-badge v-if="tab.key === 'messages' && imStore.unreadTotal > 0" :value="imStore.unreadTotal" class="tab-badge" />
        </button>
      </div>
      <div class="sidebar-panel">
        <ConversationList v-if="activeTab === 'messages'" @select="onSelectConversation" />
        <ContactList v-else-if="activeTab === 'contacts'" @chat="onStartChat" />
        <OrgTree v-else @chat="onStartChat" />
      </div>
    </aside>

    <section class="chat-middle">
      <ConversationList
        v-if="activeTab === 'messages'"
        compact
        @select="onSelectConversation"
        @create-group="groupCreateRef?.open()"
      />
      <ContactList v-else-if="activeTab === 'contacts'" @chat="onStartChat" />
      <OrgTree v-else @chat="onStartChat" />
    </section>

    <main class="chat-main">
      <ChatPanel v-if="imStore.activeConversationId" @group-info="groupInfoRef?.open()" />
      <div v-else class="chat-empty">
        <el-icon :size="64"><ChatDotRound /></el-icon>
        <p>选择一个会话开始聊天</p>
      </div>
    </main>

    <AddFriendDialog ref="addFriendRef" />
    <GroupCreateDialog ref="groupCreateRef" />
    <GroupInfoDrawer ref="groupInfoRef" />
    <CallOverlay />
  </div>
</template>

<script setup lang="ts">
import { onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, ChatLineRound, User, OfficeBuilding } from '@element-plus/icons-vue'
import { useImStore } from '@/stores/im'
import ConversationList from './ConversationList.vue'
import ContactList from './ContactList.vue'
import OrgTree from './OrgTree.vue'
import ChatPanel from './ChatPanel.vue'
import AddFriendDialog from './AddFriendDialog.vue'
import GroupCreateDialog from './GroupCreateDialog.vue'
import GroupInfoDrawer from './GroupInfoDrawer.vue'
import CallOverlay from './CallOverlay.vue'

const imStore = useImStore()
const route = useRoute()
const router = useRouter()

const tabs = [
  { key: 'messages', label: '消息', icon: ChatLineRound },
  { key: 'contacts', label: '通讯录', icon: User },
  { key: 'org', label: '组织', icon: OfficeBuilding },
] as const

type TabKey = (typeof tabs)[number]['key']
const activeTab = ref<TabKey>('messages')
const addFriendRef = ref<InstanceType<typeof AddFriendDialog> | null>(null)
const groupCreateRef = ref<InstanceType<typeof GroupCreateDialog> | null>(null)
const groupInfoRef = ref<InstanceType<typeof GroupInfoDrawer> | null>(null)

provide('openAddFriendDialog', () => addFriendRef.value?.open())

async function onSelectConversation(id: number) {
  await imStore.openConversation(id)
  router.replace({ name: 'MessagesConversation', params: { conversationId: String(id) } })
}

async function onStartChat(userId: number) {
  activeTab.value = 'messages'
  const conv = await imStore.startDirectChat(userId)
  router.replace({ name: 'MessagesConversation', params: { conversationId: String(conv.id) } })
}

watch(
  () => route.params.conversationId,
  async (id) => {
    if (id) {
      activeTab.value = 'messages'
      await imStore.openConversation(Number(id))
    }
  },
  { immediate: true }
)

onMounted(() => {
  imStore.init()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 60px);
  margin: -20px;
  background: var(--background-color);
  color: var(--text-primary);
}

.chat-sidebar {
  width: 280px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  background: var(--card-bg);
}

.sidebar-tabs {
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 4px;
  border-bottom: 1px solid var(--border-color);
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-regular);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.tab-btn:hover,
.tab-btn.active {
  background: var(--table-row-hover);
  color: var(--primary-color);
}

.tab-badge {
  margin-left: auto;
}

.sidebar-panel {
  flex: 1;
  overflow: hidden;
}

.chat-middle {
  width: 320px;
  border-right: 1px solid var(--border-color);
  background: var(--card-bg);
  display: none;
  overflow: hidden;
}

@media (min-width: 1200px) {
  .chat-sidebar .sidebar-panel {
    display: none;
  }
  .chat-middle {
    display: flex;
    flex-direction: column;
  }
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--background-color);
}

.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  gap: 12px;
}
</style>
