<template>
  <div class="role-management">
    <div class="header">
      <h2>角色管理</h2>
      <el-button type="primary" @click="handleAdd">新增角色</el-button>
    </div>

    <el-table :data="roles" v-loading="loading" stripe>
      <el-table-column prop="name" label="角色名称" width="150" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="isSystem" label="系统角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isSystem ? 'info' : 'success'">
            {{ row.isSystem ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="权限数" width="100">
        <template #default="{ row }">
          {{ row.permissionIds?.length || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="菜单数" width="100">
        <template #default="{ row }">
          {{ row.menuIds?.length || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)" :disabled="row.isSystem">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.isSystem">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="600px">
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" />
        </el-form-item>
        <el-form-item label="权限">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            :props="{ label: 'name', children: 'children' }"
            show-checkbox
            node-key="id"
            default-expand-all
            :checked-keys="formData.permissionIds"
          />
        </el-form-item>
        <p class="form-hint">菜单将根据已选权限自动生成，无需单独配置。</p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getRoles, createRole, updateRole, deleteRole, getPermissionTree, formatApiError } from '@/api'
import type { Role, RoleCreateRequest, RoleUpdateRequest, Permission } from '@/types'

const loading = ref(false)
const roles = ref<Role[]>([])
const permissionTree = ref<Permission[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingRoleId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const permissionTreeRef = ref()

const formData = reactive<RoleCreateRequest & RoleUpdateRequest>({
  name: '',
  description: '',
  permissionIds: [],
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

const fetchRoles = async (): Promise<void> => {
  loading.value = true
  try {
    roles.value = await getRoles()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '获取角色列表失败')
  } finally {
    loading.value = false
  }
}

const fetchPermissionTree = async (): Promise<void> => {
  try {
    permissionTree.value = await getPermissionTree()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '获取权限树失败')
  }
}

const handleAdd = (): void => {
  isEdit.value = false
  editingRoleId.value = null
  Object.assign(formData, { name: '', description: '', permissionIds: [] })
  dialogVisible.value = true
}

const handleEdit = (row: Role): void => {
  isEdit.value = true
  editingRoleId.value = row.id
  Object.assign(formData, {
    name: row.name,
    description: row.description || '',
    permissionIds: row.permissionIds || [],
  })
  dialogVisible.value = true
}

const handleSubmit = async (): Promise<void> => {
  if (!formRef.value) return
  await formRef.value.validate()

  const checkedPermissions = permissionTreeRef.value?.getCheckedKeys() || []

  try {
    if (isEdit.value && editingRoleId.value) {
      await updateRole(editingRoleId.value, {
        name: formData.name,
        description: formData.description,
        permissionIds: checkedPermissions,
      })
      ElMessage.success('更新成功')
    } else {
      await createRole({
        name: formData.name,
        description: formData.description,
        permissionIds: checkedPermissions,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchRoles()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '操作失败')
  }
}

const handleDelete = async (row: Role): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要删除该角色吗？', '确认删除', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    await fetchRoles()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(formatApiError(error) || '删除失败')
    }
  }
}

onMounted(() => {
  void fetchRoles()
  void fetchPermissionTree()
})
</script>

<style scoped>
.role-management {
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
.form-hint {
  margin: 0 0 8px 80px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
