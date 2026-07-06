<template>
  <div class="menu-management">
    <div class="header">
      <h2>菜单管理</h2>
      <el-button type="primary" @click="handleAdd(null)">新增菜单</el-button>
    </div>

    <el-table :data="menuTree" v-loading="loading" stripe row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
      <el-table-column prop="name" label="菜单名称" width="200" />
      <el-table-column prop="icon" label="图标" width="100" />
      <el-table-column prop="path" label="路由路径" width="200" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="isVisible" label="显示" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isVisible ? 'success' : 'info'">
            {{ row.isVisible ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isEnabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'danger'">
            {{ row.isEnabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="handleAdd(row.id)">添加子菜单</el-button>
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '新增菜单'" width="500px">
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuTree"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            check-strictly
            clearable
            placeholder="选择上级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="formData.icon" placeholder="如: HomeFilled, Document" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="formData.path" placeholder="如: /dashboard" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="formData.componentPath" placeholder="如: views/Dashboard.vue" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="显示">
          <el-switch v-model="formData.isVisible" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="formData.isEnabled" active-text="是" inactive-text="否" />
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
import { getMenuTree, createMenu, updateMenu, deleteMenu, formatApiError } from '@/api'
import type { Menu, MenuCreateRequest, MenuUpdateRequest } from '@/types'

const loading = ref(false)
const menuTree = ref<Menu[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingMenuId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const formData = reactive<MenuCreateRequest & MenuUpdateRequest>({
  name: '',
  parentId: undefined,
  icon: '',
  path: '',
  componentPath: '',
  sortOrder: 0,
  isVisible: true,
  isEnabled: true,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
}

const fetchMenuTree = async (): Promise<void> => {
  loading.value = true
  try {
    menuTree.value = await getMenuTree()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '获取菜单树失败')
  } finally {
    loading.value = false
  }
}

const handleAdd = (parentId: number | null): void => {
  isEdit.value = false
  editingMenuId.value = null
  Object.assign(formData, {
    name: '',
    parentId: parentId || undefined,
    icon: '',
    path: '',
    componentPath: '',
    sortOrder: 0,
    isVisible: true,
    isEnabled: true,
  })
  dialogVisible.value = true
}

const handleEdit = (row: Menu): void => {
  isEdit.value = true
  editingMenuId.value = row.id
  Object.assign(formData, {
    name: row.name,
    parentId: row.parentId,
    icon: row.icon || '',
    path: row.path || '',
    componentPath: row.componentPath || '',
    sortOrder: row.sortOrder,
    isVisible: row.isVisible,
    isEnabled: row.isEnabled,
  })
  dialogVisible.value = true
}

const handleSubmit = async (): Promise<void> => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    if (isEdit.value && editingMenuId.value) {
      await updateMenu(editingMenuId.value, {
        name: formData.name,
        parentId: formData.parentId,
        icon: formData.icon,
        path: formData.path,
        componentPath: formData.componentPath,
        sortOrder: formData.sortOrder,
        isVisible: formData.isVisible,
        isEnabled: formData.isEnabled,
      })
      ElMessage.success('更新成功')
    } else {
      await createMenu({
        name: formData.name,
        parentId: formData.parentId,
        icon: formData.icon,
        path: formData.path,
        componentPath: formData.componentPath,
        sortOrder: formData.sortOrder,
        isVisible: formData.isVisible,
        isEnabled: formData.isEnabled,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchMenuTree()
  } catch (error: unknown) {
    ElMessage.error(formatApiError(error) || '操作失败')
  }
}

const handleDelete = async (row: Menu): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要删除该菜单吗？', '确认删除', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    await fetchMenuTree()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(formatApiError(error) || '删除失败')
    }
  }
}

onMounted(() => {
  void fetchMenuTree()
})
</script>

<style scoped>
.menu-management {
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
