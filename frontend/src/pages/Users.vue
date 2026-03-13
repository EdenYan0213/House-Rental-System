<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>用户管理</h2>
      <el-button type="primary" @click="handleAdd" v-if="isSuperAdmin">新增用户</el-button>
    </el-row>

    <el-row style="margin-top: 16px">
      <el-input v-model="searchForm.username" placeholder="用户名" clearable style="width: 150px; margin-right: 10px" />
      <el-input v-model="searchForm.realName" placeholder="姓名" clearable style="width: 150px; margin-right: 10px" />
      <el-select v-model="searchForm.role" placeholder="角色" clearable style="width: 120px; margin-right: 10px">
        <el-option label="超级管理员" value="SUPER_ADMIN" />
        <el-option label="管理员" value="ADMIN" />
        <el-option label="普通用户" value="USER" />
      </el-select>
      <el-button type="primary" @click="loadData">搜索</el-button>
    </el-row>

    <el-table :data="tableData" style="width: 100%; margin-top: 16px" v-loading="loading">
      <el-table-column prop="username" label="用户名" min-width="100" />
      <el-table-column prop="realName" label="姓名" min-width="80" />
      <el-table-column prop="phone" label="手机号" min-width="110" />
      <el-table-column prop="email" label="邮箱" min-width="150" />
      <el-table-column label="角色" min-width="90">
        <template #default="{ row }">
          <el-tag :type="getRoleTagType(row.role)" size="small">{{ getRoleName(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="70">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" min-width="130">
        <template #default="{ row }">
          <span>{{ row.lastLoginTime || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220" fixed="right" v-if="isSuperAdmin">
        <template #default="{ row }">
          <span class="action-btns">
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleResetPassword(row)" v-if="row.role !== 'SUPER_ADMIN'">重置</el-button>
            <el-button size="small" :type="row.enabled ? 'danger' : 'success'" @click="handleToggleStatus(row)" v-if="row.role !== 'SUPER_ADMIN'">{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" v-if="row.role !== 'SUPER_ADMIN'">删除</el-button>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="60" v-else>
        <template #default="{ row }">
          <el-tag size="small" type="info">查看</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="tableData.length > 0"
      style="margin-top: 16px"
      :current-page="pagination.page"
      :page-size="pagination.size"
      :total="pagination.total || tableData.length"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, createUser, updateUser, deleteUser, enableUser, disableUser, resetUserPassword, getUser } from '@/api'

const isSuperAdmin = computed(() => getUser()?.role === 'SUPER_ADMIN')

const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const searchForm = reactive({
  username: '',
  realName: '',
  role: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  role: 'USER'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const dialogTitle = computed(() => isEdit.value ? '编辑用户' : '新增用户')

function getRoleName(role) {
  const map = { SUPER_ADMIN: '超级管理员', ADMIN: '管理员', USER: '普通用户' }
  return map[role] || role
}

function getRoleTagType(role) {
  const map = { SUPER_ADMIN: 'danger', ADMIN: 'warning', USER: 'info' }
  return map[role] || 'info'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUsers({
      username: searchForm.username || undefined,
      realName: searchForm.realName || undefined,
      role: searchForm.role || undefined,
      page: pagination.page,
      size: pagination.size
    })
    tableData.value = res.data.records || res.data || []
    pagination.total = res.data.total || 0
  } catch (err) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  if (!isSuperAdmin.value) return
  isEdit.value = false
  Object.assign(form, { id: null, username: '', password: '', realName: '', phone: '', email: '', role: 'USER' })
  dialogVisible.value = true
}

function handleEdit(row) {
  if (!isSuperAdmin.value) return
  isEdit.value = true
  Object.assign(form, { ...row, password: '' })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) {
        await updateUser(form.id, form)
        ElMessage.success('更新成功')
      } else {
        await createUser(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (err) {
      ElMessage.error(err.response?.data?.message || '操作失败')
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户 "${row.username}" 吗？`, '警告', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.response?.data?.message || '删除失败')
    }
  }
}

async function handleToggleStatus(row) {
  try {
    if (row.enabled) {
      await disableUser(row.id)
      ElMessage.success('已禁用')
    } else {
      await enableUser(row.id)
      ElMessage.success('已启用')
    }
    loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '操作失败')
  }
}

async function handleResetPassword(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.{6,}/,
      inputErrorMessage: '密码至少6位'
    })
    await resetUserPassword(row.id, value)
    ElMessage.success('密码重置成功')
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.response?.data?.message || '操作失败')
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.action-btns {
  white-space: nowrap;
}
.action-btns .el-button {
  margin-right: 2px;
}
</style>
