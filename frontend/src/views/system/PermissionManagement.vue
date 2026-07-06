<template>
  <div class="permission-management">
    <div class="header">
      <h2>权限管理</h2>
      <el-button type="primary" @click="handleAdd(null)">新增权限</el-button>
    </div>

    <el-table :data="permissionTree" v-loading="loading" stripe row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
      <el-table-column prop="name" label="权限名称" width="200" />
      <el-table-column prop="code" label="权限编码" width="200" />
      <el-table-column prop="moduleName" label="所属模块" width="150" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="handleAdd(row.id)">添加子权限</el-button>
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Permission Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑权限' : '新增权限'" width="500px">
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="权限编码" prop="code" v-if="!isEdit">
          <el-input v-model="formData.code" />
        </el-form-item>
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" />
        </el-form-item>
        <el-form-item label="所属模块">
          <el-input v-model="formData.moduleName" />
        </el-form-item>
        <el-form-item label="上级权限">
          <el-tree-select
            v-model="formData.parentId"
            :data="permissionTree"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            check-strictly
            clearable
            placeholder="选择上级权限"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" />
        </el-form-item>
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
import { getPermissionTree, createPermission, updatePermission, deletePermission, formatApiError } from '@/api'
import type { Permission, PermissionCreateRequest, PermissionUpdateRequest } from '@/types'

const loading = ref(false)
const permissionTree = ref<Permission[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingPermissionId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const formData = reactive<PermissionCreateRequest & PermissionUpdateRequest>({
  code: '',
  name: '',
  description: '',
  moduleName: '',
  parentId: undefined,
  sortOrder: 0
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }]
}

const fetchPermissions = async () => {
  loading.value = true
  try {
    permissionTree.value = await getPermissionTree()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '获取权限列表失败')
  } finally {
    loading.value = false
  }
}

const handleAdd = (parentId: number | null) => {
  isEdit.value = false
  editingPermissionId.value = null
  Object.assign(formData, { code: '', name: '', description: '', moduleName: '', parentId: parentId || undefined, sortOrder: 0 })
  dialogVisible.value = true
}

const handleEdit = (row: Permission) => {
  isEdit.value = true
  editingPermissionId.value = row.id
  Object.assign(formData, { code: row.code, name: row.name, description: row.description || '', moduleName: row.moduleName || '', parentId: row.parentId, sortOrder: row.sortOrder })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    if (isEdit.value && editingPermissionId.value) {
      await updatePermission(editingPermissionId.value, { name: formData.name, description: formData.description, moduleName: formData.moduleName, parentId: formData.parentId, sortOrder: formData.sortOrder })
      ElMessage.success('更新成功')
    } else {
      await createPermission({ code: formData.code, name: formData.name, description: formData.description, moduleName: formData.moduleName, parentId: formData.parentId, sortOrder: formData.sortOrder })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchPermissions()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '操作失败')
  }
}

const handleDelete = async (row: Permission) => {
  try {
    await ElMessageBox.confirm('确定要删除该权限吗？', '确认删除', { type: 'warning' })
    await deletePermission(row.id)
    ElMessage.success('删除成功')
    fetchPermissions()
  } catch (error: unknown) {
    if (error !== 'cancel') ElMessage.error(formatApiError(error) || '删除失败')
  }
}

onMounted(() => {
  fetchPermissions()
})
</script>

<style scoped>
.permission-management {
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