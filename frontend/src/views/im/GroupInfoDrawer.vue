<template>
  <el-drawer v-model="visible" title="群信息" size="360px">
    <template v-if="conv">
      <el-form label-width="72px">
        <el-form-item label="群名称">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="群公告">
          <el-input v-model="form.announcement" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>

      <div class="members-section">
        <h4>群成员 ({{ conv.members?.length || 0 }})</h4>
        <div v-for="m in conv.members" :key="m.userId" class="member-row">
          <el-avatar :size="32">{{ (m.realName || m.username || '?').slice(0, 1) }}</el-avatar>
          <span>{{ m.realName || m.username || `用户 ${m.userId}` }}</span>
          <el-tag size="small">{{ m.role }}</el-tag>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getConversation, updateConversation } from '@/api/im'
import { useImStore } from '@/stores/im'

const imStore = useImStore()
const visible = ref(false)
const saving = ref(false)
const form = reactive({ title: '', announcement: '' })

const conv = computed(() => imStore.activeConversation)

watch(visible, async (v) => {
  if (v && conv.value) {
    const detail = await getConversation(conv.value.id)
    form.title = detail.title || ''
    form.announcement = detail.announcement || ''
    const idx = imStore.conversations.findIndex((c) => c.id === detail.id)
    if (idx >= 0) imStore.conversations[idx] = { ...imStore.conversations[idx], ...detail }
  }
})

function open() {
  visible.value = true
}

async function save() {
  if (!conv.value) return
  saving.value = true
  try {
    await updateConversation(conv.value.id, {
      title: form.title,
      announcement: form.announcement,
    })
    ElMessage.success('已保存')
    visible.value = false
    await imStore.loadConversations()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.members-section {
  margin-top: 24px;
}

.members-section h4 {
  margin-bottom: 12px;
  color: var(--text-primary);
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
}
</style>
