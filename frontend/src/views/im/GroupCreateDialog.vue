<template>
  <el-dialog v-model="visible" title="创建群聊" width="480px" @close="reset">
    <el-form :model="form" label-width="80px">
      <el-form-item label="群名称">
        <el-input v-model="form.name" placeholder="请输入群名称" />
      </el-form-item>
      <el-form-item label="成员">
        <el-select
          v-model="form.memberIds"
          multiple
          filterable
          placeholder="选择好友"
          style="width: 100%"
          :loading="friendsLoading"
          no-data-text="暂无好友，请先在「组织」中搜索成员并添加好友"
        >
          <el-option
            v-for="f in imStore.friends"
            :key="f.userId"
            :label="f.remark || f.realName || f.username"
            :value="f.userId"
          />
        </el-select>
        <p v-if="!friendsLoading && !imStore.friends.length" class="empty-hint">
          点击通讯录或消息页右上角「添加好友」搜索成员；对方同意后即可在此选择成员创建群聊。
        </p>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useImStore } from '@/stores/im'

const imStore = useImStore()
const visible = ref(false)
const loading = ref(false)
const friendsLoading = ref(false)
const form = reactive({ name: '', memberIds: [] as number[] })

async function open() {
  visible.value = true
  friendsLoading.value = true
  try {
    await imStore.loadFriends()
  } finally {
    friendsLoading.value = false
  }
}

function reset() {
  form.name = ''
  form.memberIds = []
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入群名称')
    return
  }
  if (!form.memberIds.length) {
    ElMessage.warning('请选择至少一名成员')
    return
  }
  loading.value = true
  try {
    await imStore.createGroup(form.name.trim(), form.memberIds)
    visible.value = false
    reset()
    ElMessage.success('群聊创建成功')
  } catch {
    ElMessage.error('创建失败')
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.empty-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
</style>
