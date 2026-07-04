<template>
  <el-dialog v-model="visible" title="添加好友" width="520px" destroy-on-close @close="reset">
    <div class="search-row">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名或姓名"
        clearable
        prefix-icon="Search"
        @keyup.enter="doSearch"
        @clear="onClear"
      />
      <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
    </div>
    <p class="search-hint">输入用户名或真实姓名，搜索组织内成员</p>
    <el-scrollbar class="results" max-height="360px">
      <div v-for="user in results" :key="user.id" class="result-item">
        <el-avatar :size="40">{{ displayName(user).slice(0, 1) }}</el-avatar>
        <div class="result-info">
          <span class="result-name">{{ displayName(user) }}</span>
          <span class="result-sub">{{ user.username }}</span>
        </div>
        <div class="result-actions">
          <span v-if="user.id === userStore.userId" class="status-tag">自己</span>
          <span v-else-if="isFriend(user.id)" class="status-tag">已是好友</span>
          <span v-else-if="sentUserIds.has(user.id)" class="status-tag">已发送申请</span>
          <el-button
            v-else
            type="primary"
            size="small"
            :loading="addingUserId === user.id"
            @click="onAddFriend(user)"
          >
            添加好友
          </el-button>
        </div>
      </div>
      <el-empty v-if="searched && !results.length" description="未找到匹配成员" :image-size="60" />
      <p v-else-if="!searched" class="placeholder-hint">在上方输入关键词并点击搜索</p>
    </el-scrollbar>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { searchUsers } from '@/api/im'
import { useImStore } from '@/stores/im'
import { useUserStore } from '@/stores/user'
import type { ImUserSummary } from '@/types/im'

const imStore = useImStore()
const userStore = useUserStore()

const visible = ref(false)
const keyword = ref('')
const results = ref<ImUserSummary[]>([])
const searching = ref(false)
const searched = ref(false)
const addingUserId = ref<number | null>(null)
const sentUserIds = ref(new Set<number>())

function displayName(user: ImUserSummary) {
  return user.realName || user.username
}

function isFriend(userId: number) {
  return imStore.friends.some((f) => f.userId === userId)
}

function onClear() {
  results.value = []
  searched.value = false
}

function reset() {
  keyword.value = ''
  results.value = []
  searched.value = false
  addingUserId.value = null
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  searching.value = true
  searched.value = true
  try {
    results.value = await searchUsers(q)
  } catch {
    ElMessage.error('搜索失败')
    results.value = []
  } finally {
    searching.value = false
  }
}

async function onAddFriend(user: ImUserSummary) {
  addingUserId.value = user.id
  try {
    await imStore.addFriend(user.id)
    sentUserIds.value.add(user.id)
    ElMessage.success(`已向 ${displayName(user)} 发送好友申请`)
  } catch (err: unknown) {
    const msg =
      typeof err === 'object' && err && 'message' in err
        ? String((err as { message: string }).message)
        : '发送失败'
    ElMessage.error(msg)
  } finally {
    addingUserId.value = null
  }
}

async function open() {
  visible.value = true
  await imStore.loadFriends()
}

defineExpose({ open })
</script>

<style scoped>
.search-row {
  display: flex;
  gap: 8px;
}

.search-hint {
  margin: 8px 0 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.results {
  min-height: 120px;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--border-color);
}

.result-item:last-child {
  border-bottom: none;
}

.result-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.result-name {
  font-weight: 500;
  color: var(--text-primary);
}

.result-sub {
  font-size: 12px;
  color: var(--text-secondary);
}

.result-actions {
  flex-shrink: 0;
}

.status-tag {
  font-size: 12px;
  color: var(--text-secondary);
}

.placeholder-hint {
  text-align: center;
  padding: 32px 16px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
