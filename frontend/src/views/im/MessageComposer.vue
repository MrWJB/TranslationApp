<template>
  <footer class="message-composer">
    <div class="toolbar">
      <el-popover
        v-model:visible="emojiVisible"
        placement="top-start"
        :width="320"
        trigger="click"
        popper-class="im-emoji-popover"
      >
        <template #reference>
          <el-button circle title="插入表情">😀</el-button>
        </template>
        <div class="emoji-panel">
          <div v-for="group in emojiGroups" :key="group.label" class="emoji-group">
            <div class="emoji-group-label">{{ group.label }}</div>
            <div class="emoji-grid">
              <button
                v-for="emoji in group.items"
                :key="emoji"
                type="button"
                class="emoji-btn"
                @click="insertEmoji(emoji)"
              >
                {{ emoji }}
              </button>
            </div>
          </div>
        </div>
      </el-popover>
      <el-upload :show-file-list="false" :auto-upload="false" accept="image/*" @change="onImageSelect">
        <el-button circle :icon="Picture" title="发送图片" />
      </el-upload>
      <el-upload :show-file-list="false" :auto-upload="false" @change="onFileSelect">
        <el-button circle :icon="Paperclip" title="发送文件" />
      </el-upload>
    </div>
    <el-input
      ref="textareaRef"
      v-model="text"
      type="textarea"
      :rows="3"
      placeholder="输入消息，Enter 发送，Shift+Enter 换行"
      resize="none"
      @keydown.enter="onEnter"
      @input="onTyping"
    />
    <div class="composer-footer">
      <el-button type="primary" :loading="sending" @click="sendText">发送</el-button>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage, type InputInstance } from 'element-plus'
import { Picture, Paperclip } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { useImStore } from '@/stores/im'

const emojiGroups = [
  {
    label: '常用',
    items: ['😀', '😂', '🥰', '😊', '😉', '😍', '🤔', '😅', '😭', '😡', '👍', '👎', '👏', '🙏', '❤️', '🔥'],
  },
  {
    label: '表情',
    items: ['😁', '😆', '🤣', '😇', '🙂', '😘', '😋', '😎', '🤗', '🤭', '😴', '🥳', '😱', '🤯', '😤', '🙄'],
  },
  {
    label: '手势',
    items: ['👌', '✌️', '🤝', '💪', '👋', '🤞', '🫶', '✊', '🤙', '👀', '💯', '✅', '❌', '⭐', '🎉', '🎁'],
  },
]

const imStore = useImStore()
const text = ref('')
const sending = ref(false)
const emojiVisible = ref(false)
const textareaRef = ref<InputInstance>()
let typingTimer: ReturnType<typeof setTimeout> | null = null

function getTextareaEl(): HTMLTextAreaElement | undefined {
  return textareaRef.value?.textarea
}

function insertEmoji(emoji: string) {
  const el = getTextareaEl()
  const value = text.value
  const start = el?.selectionStart ?? value.length
  const end = el?.selectionEnd ?? start
  text.value = value.slice(0, start) + emoji + value.slice(end)
  emojiVisible.value = false
  nextTick(() => {
    el?.focus()
    const pos = start + emoji.length
    el?.setSelectionRange(pos, pos)
  })
  onTyping()
}

function onTyping() {
  const convId = imStore.activeConversationId
  if (!convId) return
  imStore.setTyping(convId, true)
  if (typingTimer) clearTimeout(typingTimer)
  typingTimer = setTimeout(() => imStore.setTyping(convId, false), 2000)
}

function onEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    sendText()
  }
}

async function sendText() {
  const content = text.value.trim()
  const convId = imStore.activeConversationId
  if (!content || !convId) return
  sending.value = true
  try {
    await imStore.sendMessage({
      conversationId: convId,
      type: 'TEXT',
      content,
    })
    text.value = ''
    imStore.setTyping(convId, false)
  } finally {
    sending.value = false
  }
}

async function onImageSelect(uploadFile: UploadFile) {
  const file = uploadFile.raw
  const convId = imStore.activeConversationId
  if (!file || !convId) return
  sending.value = true
  try {
    const attachmentId = await imStore.uploadFile(file)
    await imStore.sendMessage({
      conversationId: convId,
      type: 'IMAGE',
      content: file.name,
      attachmentId,
    })
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    sending.value = false
  }
}

async function onFileSelect(uploadFile: UploadFile) {
  const file = uploadFile.raw
  const convId = imStore.activeConversationId
  if (!file || !convId) return
  sending.value = true
  try {
    const attachmentId = await imStore.uploadFile(file)
    await imStore.sendMessage({
      conversationId: convId,
      type: 'FILE',
      content: file.name,
      attachmentId,
    })
  } catch {
    ElMessage.error('文件上传失败')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.message-composer {
  border-top: 1px solid var(--border-color);
  background: var(--card-bg);
  padding: 12px 16px;
}

.toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}

.composer-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.emoji-panel {
  max-height: 240px;
  overflow-y: auto;
}

.emoji-group + .emoji-group {
  margin-top: 8px;
}

.emoji-group-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
}

.emoji-btn {
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  padding: 4px;
  border-radius: 6px;
  cursor: pointer;
}

.emoji-btn:hover {
  background: var(--hover-bg, rgba(127, 127, 127, 0.15));
}
</style>

<style>
.im-emoji-popover.el-popover {
  background: var(--card-bg);
  border-color: var(--border-color);
}

.im-emoji-popover .el-popover__title,
.im-emoji-popover .emoji-group-label {
  color: var(--text-secondary);
}
</style>
