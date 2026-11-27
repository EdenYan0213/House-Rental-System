<template>
  <PullRefresh :on-refresh="load">
    <div class="mobile-page" v-loading="loading">
      <header class="m-head">
        <div class="head-row">
          <div>
            <h1 class="m-head-title">租户管理</h1>
            <p class="m-head-sub">
              共 <span class="m-num count">{{ filtered.length }}</span> 位
              <template v-if="filtered.length !== tenants.length"> / {{ tenants.length }} 位</template>
            </p>
          </div>
          <el-button
            v-if="canAdd"
            type="primary"
            :icon="Plus"
            class="head-btn"
            @click="openCreate"
          >新建</el-button>
        </div>
      </header>

      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索姓名或手机号"
        :prefix-icon="Search"
      />

      <div v-if="!loading && filtered.length === 0" class="m-card empty-state">
        <el-icon :size="32" class="empty-icon"><User /></el-icon>
        <p class="empty-title">{{ tenants.length === 0 ? '暂无租户' : '没有匹配的租户' }}</p>
        <p class="empty-desc">{{ canAdd ? '点击右上角「新建」添加租户。' : '换个关键词试试。' }}</p>
      </div>

      <div v-else class="tenant-list">
        <article v-for="t in filtered" :key="t.id" class="m-card tenant-card">
          <div class="tenant-main">
            <div class="tenant-top">
              <span class="tenant-name">{{ t.name || '-' }}</span>
              <span v-if="t.isBlacklisted" class="tenant-tag is-black">黑名单</span>
              <span v-else-if="t.activeContract" class="tenant-tag is-active">在租</span>
            </div>
            <div class="tenant-phone">
              <el-icon :size="13"><Iphone /></el-icon>
              <span>{{ t.phone || '-' }}</span>
            </div>
            <div v-if="t.emergencyContact || t.emergencyPhone" class="tenant-emerg">
              紧急联系人：{{ t.emergencyContact || '-' }} {{ t.emergencyPhone || '' }}
            </div>
            <div v-if="t.remark" class="tenant-remark">{{ t.remark }}</div>
          </div>

          <div v-if="canEdit" class="tenant-ops">
            <button type="button" class="op-btn" @click.stop="openEdit(t)">编辑</button>
          </div>
        </article>
      </div>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editId ? '编辑租户' : '新建租户'"
      width="92%"
      align-center
    >
      <el-form :model="form" label-position="top" class="create-form">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" placeholder="如：张三" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" inputmode="numeric" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.idCard" placeholder="选填，加密存储" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="紧急联系人">
              <el-input v-model="form.emergencyContact" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急联系电话">
              <el-input v-model="form.emergencyPhone" placeholder="选填" inputmode="numeric" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ editId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </PullRefresh>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, User, Iphone } from '@element-plus/icons-vue'
import PullRefresh from '@/components/PullRefresh.vue'
import {
  getTenants, createTenant, updateTenant,
  canAddTenant, canEditTenant, getErrorMessage
} from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const loading = ref(false)
const tenants = ref([])
const keyword = ref('')

const canAdd = canAddTenant()
const canEdit = canEditTenant()

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return tenants.value
  return tenants.value.filter(t =>
    String(t.name || '').toLowerCase().includes(q) ||
    String(t.phone || '').includes(q)
  )
})

/* ---------- 新建 / 编辑 ---------- */
const formVisible = ref(false)
const submitting = ref(false)
const editId = ref(null)
const emptyForm = () => ({
  name: '', phone: '', idCard: '', emergencyContact: '', emergencyPhone: '', remark: ''
})
const form = ref(emptyForm())

function openCreate() {
  editId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEdit(t) {
  editId.value = t.id
  form.value = {
    name: t.name || '',
    phone: t.phone || '',
    // 身份证是加密存储的，列表返回的是掩码；留空表示不修改
    idCard: '',
    emergencyContact: t.emergencyContact || '',
    emergencyPhone: t.emergencyPhone || '',
    remark: t.remark || ''
  }
  formVisible.value = true
}

async function submitForm() {
  const name = form.value.name.trim()
  const phone = form.value.phone.trim()
  if (!name) return ElMessage.warning('请填写姓名')
  if (!/^\d{11}$/.test(phone)) return ElMessage.warning('请填写 11 位手机号')

  submitting.value = true
  try {
    // 只提交填了值的字段：后端 update 是部分更新，
    // 空值会被忽略，避免把已有的加密身份证清掉
    const payload = { name, phone }
    if (form.value.idCard.trim()) payload.idCard = form.value.idCard.trim()
    if (form.value.emergencyContact.trim()) payload.emergencyContact = form.value.emergencyContact.trim()
    if (form.value.emergencyPhone.trim()) payload.emergencyPhone = form.value.emergencyPhone.trim()
    if (form.value.remark.trim()) payload.remark = form.value.remark.trim()

    if (editId.value) {
      await updateTenant(editId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createTenant(payload)
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, editId.value ? '保存失败' : '创建失败'))
  } finally {
    submitting.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getTenants()
    tenants.value = unwrapList(res.data)
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '加载租户失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-btn {
  flex: 0 0 auto;
  min-height: 36px;
  border-radius: 9px;
}

.count {
  color: var(--accent-3);
  font-weight: 700;
  font-size: 15px;
}

.tenant-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: calc(56px + var(--m-safe-bottom));
}

.tenant-card {
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 0;
  overflow: hidden;
}

.tenant-main {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
}

.tenant-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tenant-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.tenant-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
}

.tenant-tag.is-active {
  color: var(--accent-3);
  background: var(--accent-1);
  border: 1px solid var(--accent-2);
}

.tenant-tag.is-black {
  color: var(--danger-color);
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger-color) 30%, transparent);
}

.tenant-phone {
  margin-top: 5px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--accent-3);
}

.tenant-emerg {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-muted);
}

.tenant-remark {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.tenant-ops {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 10px 12px;
  border-left: 1px solid var(--border-color);
  flex: 0 0 auto;
}

.op-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-main);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  font-family: inherit;
  min-height: 34px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.op-btn:active {
  background: var(--accent-1);
  border-color: var(--accent-2);
}

.create-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 4px;
}

.create-form :deep(.el-input__wrapper) {
  min-height: var(--m-tap);
  border-radius: 10px;
}

.full-width {
  width: 100%;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  color: var(--text-muted);
  opacity: 0.4;
}

.empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
