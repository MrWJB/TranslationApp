<template>
  <div class="user-management">
    <div class="header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="handleAdd">新增用户</el-button>
    </div>

    <el-table :data="users" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="realName" label="真实姓名" width="150" />
      <el-table-column prop="email" label="邮箱" width="200" />
      <el-table-column prop="phone" label="电话" width="150" />
      <el-table-column prop="isEnabled" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'danger'">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isLocked" label="锁定" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isLocked ? 'warning' : 'success'">
            {{ row.isLocked ? '已锁定' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.username === 'admin'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @size-change="fetchUsers"
      @current-change="fetchUsers"
      style="margin-top: 20px; justify-content: flex-end"
    />

    <!-- User Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username" v-if="!isEdit">
          <el-input v-model="formData.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="formData.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="formData.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="formData.email" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="formData.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="formData.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" v-if="isEdit">
          <el-switch v-model="formData.isEnabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="锁定" v-if="isEdit">
          <el-switch v-model="formData.isLocked" active-text="锁定" inactive-text="正常" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="400px">
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="80px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handlePasswordSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUsers, createUser, updateUser, deleteUser, resetUserPassword, getRoles, formatApiError } from '@/api'
import type { SystemUser, UserCreateRequest, UserUpdateRequest, Role } from '@/types'

const loading = ref(false)
const users = ref<SystemUser[]>([])
const roles = ref<Role[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingUserId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const formData = reactive<UserCreateRequest & UserUpdateRequest>({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  roleIds: [],
  isEnabled: true,
  isLocked: false
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 50, message: '用户名长度为3-50字符', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, max: 100, message: '密码长度为6-100字符', trigger: 'blur' }]
}

const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordUserId = ref<number | null>(null)
const passwordForm = reactive({ newPassword: '' })
const passwordRules: FormRules = {
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, max: 100, message: '密码长度为6-100字符', trigger: 'blur' }]
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getUsers(currentPage.value - 1, pageSize.value)
    users.value = res.content
    total.value = res.totalElements
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const fetchRoles = async () => {
  try {
    roles.value = await getRoles()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '获取角色列表失败')
  }
}

const handleAdd = () => {
  isEdit.value = false
  editingUserId.value = null
  Object.assign(formData, { username: '', password: '', realName: '', email: '', phone: '', roleIds: [], isEnabled: true, isLocked: false })
  dialogVisible.value = true
}

const handleEdit = (row: SystemUser) => {
  isEdit.value = true
  editingUserId.value = row.id
  Object.assign(formData, { username: row.username, realName: row.realName || '', email: row.email || '', phone: row.phone || '', roleIds: row.roleIds || [], isEnabled: row.isEnabled, isLocked: row.isLocked })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    if (isEdit.value && editingUserId.value) {
      await updateUser(editingUserId.value, { realName: formData.realName, email: formData.email, phone: formData.phone, roleIds: formData.roleIds, isEnabled: formData.isEnabled, isLocked: formData.isLocked })
      ElMessage.success('更新成功')
    } else {
      await createUser({ username: formData.username, password: formData.password, realName: formData.realName, email: formData.email, phone: formData.phone, roleIds: formData.roleIds })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchUsers()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '操作失败')
  }
}

const handleDelete = async (row: SystemUser) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？', '确认删除', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (error: unknown) {
    if (error !== 'cancel') ElMessage.error(formatApiError(error) || '删除失败')
  }
}

const handleResetPassword = (row: SystemUser) => {
  passwordUserId.value = row.id
  passwordForm.newPassword = ''
  passwordDialogVisible.value = true
}

const handlePasswordSubmit = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate()
  try {
    await resetUserPassword(passwordUserId.value!, { newPassword: passwordForm.newPassword })
    ElMessage.success('密码重置成功')
    passwordDialogVisible.value = false
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '密码重置失败')
  }
}

onMounted(() => {
  fetchUsers()
  fetchRoles()
})
</script>

<style scoped>
.user-management {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header h2 {
  margin: 0;
}
</style>