<template>
  <div class="department-management">
    <div class="header">
      <h2>部门管理</h2>
      <el-button type="primary" @click="openDialog()">新增部门</el-button>
    </div>

    <el-table :data="flatDepartments" v-loading="loading" stripe row-key="id">
      <el-table-column prop="name" label="部门名称" />
      <el-table-column prop="parentId" label="上级部门 ID" width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="memberCount" label="成员数" width="100" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑部门' : '新增部门'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="部门名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="imStore.departments"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="无（顶级部门）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDepartment, deleteDepartment, updateDepartment } from '@/api/im'
import { useImStore } from '@/stores/im'
import type { Department } from '@/types/im'

const imStore = useImStore()
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  parentId: null as number | null,
  sortOrder: 0,
})

function flatten(depts: Department[], result: Department[] = []): Department[] {
  for (const d of depts) {
    result.push(d)
    if (d.children?.length) flatten(d.children, result)
  }
  return result
}

const flatDepartments = computed(() => flatten(imStore.departments))

function openDialog(row?: Department) {
  isEdit.value = !!row
  editingId.value = row?.id ?? null
  form.name = row?.name ?? ''
  form.parentId = row?.parentId ?? null
  form.sortOrder = row?.sortOrder ?? 0
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }
  try {
    if (isEdit.value && editingId.value) {
      await updateDepartment(editingId.value, {
        name: form.name,
        parentId: form.parentId,
        sortOrder: form.sortOrder,
      })
    } else {
      await createDepartment({
        name: form.name,
        parentId: form.parentId,
        sortOrder: form.sortOrder,
      })
    }
    dialogVisible.value = false
    await imStore.loadDepartments()
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(row: Department) {
  await ElMessageBox.confirm(`确定删除部门「${row.name}」？`, '确认')
  try {
    await deleteDepartment(row.id)
    await imStore.loadDepartments()
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await imStore.loadDepartments()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.department-management {
  padding: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  color: var(--text-primary);
}
</style>
