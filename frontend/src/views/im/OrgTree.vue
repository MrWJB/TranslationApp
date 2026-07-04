<template>
  <div class="org-tree">
    <div class="list-header">
      <el-input
        v-model="keyword"
        placeholder="搜索成员"
        clearable
        prefix-icon="Search"
        @keyup.enter="searchMembers"
        @clear="clearSearch"
      />
      <el-button type="primary" :loading="searching" @click="searchMembers">搜索</el-button>
      <el-button type="primary" plain @click="openAddFriend?.()">添加好友</el-button>
    </div>
    <p v-if="!searchResults.length && !deptUsers.length" class="org-hint">
      点击左侧部门查看成员，或搜索成员后点击「添加好友」
    </p>
    <el-scrollbar class="list-body">
      <el-tree
        v-if="!searchResults.length"
        :data="imStore.departments"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        default-expand-all
        highlight-current
        @node-click="onDeptClick"
      />
      <div v-else class="search-results">
        <div class="section-title">搜索结果</div>
        <MemberRow
          v-for="user in searchResults"
          :key="user.id"
          :user="user"
          :adding-user-id="addingUserId"
          :sent-user-ids="sentUserIds"
          @chat="emit('chat', $event)"
          @add-friend="onAddFriend"
        />
      </div>
      <div v-if="deptUsers.length" class="dept-users">
        <div class="section-title">{{ selectedDeptName }} 成员</div>
        <MemberRow
          v-for="user in deptUsers"
          :key="user.id"
          :user="user"
          :adding-user-id="addingUserId"
          :sent-user-ids="sentUserIds"
          @chat="emit('chat', $event)"
          @add-friend="onAddFriend"
        />
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { inject, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDepartmentUsers, searchUsers } from '@/api/im'
import { useImStore } from '@/stores/im'
import type { Department, ImUserSummary } from '@/types/im'
import MemberRow from './MemberRow.vue'

const emit = defineEmits<{ chat: [userId: number] }>()
const imStore = useImStore()
const openAddFriend = inject<(() => void) | undefined>('openAddFriendDialog', undefined)

const keyword = ref('')
const deptUsers = ref<ImUserSummary[]>([])
const searchResults = ref<ImUserSummary[]>([])
const selectedDeptName = ref('')
const addingUserId = ref<number | null>(null)
const searching = ref(false)
const sentUserIds = ref(new Set<number>())

async function onAddFriend(user: ImUserSummary) {
  addingUserId.value = user.id
  try {
    await imStore.addFriend(user.id)
    sentUserIds.value.add(user.id)
    ElMessage.success(`已向 ${user.realName || user.username} 发送好友申请`)
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

async function onDeptClick(dept: Department) {
  selectedDeptName.value = dept.name
  searchResults.value = []
  keyword.value = ''
  deptUsers.value = await getDepartmentUsers(dept.id)
}

function clearSearch() {
  searchResults.value = []
}

async function searchMembers() {
  const q = keyword.value.trim()
  if (!q) {
    searchResults.value = []
    return
  }
  searching.value = true
  deptUsers.value = []
  try {
    searchResults.value = await searchUsers(q)
  } catch {
    ElMessage.error('搜索失败')
    searchResults.value = []
  } finally {
    searching.value = false
  }
}
</script>

<style scoped>
.org-tree {
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

.org-hint {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--table-row-hover);
  border-bottom: 1px solid var(--border-color);
}

.list-body {
  flex: 1;
  padding: 8px;
}

.section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  padding: 8px 4px;
  margin-top: 4px;
}

.dept-users {
  margin-top: 8px;
}
</style>
