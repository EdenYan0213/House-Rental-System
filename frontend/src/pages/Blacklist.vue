<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <div>
        <el-button @click="$router.push('/tenants')">返回租户管理</el-button>
        <h2 style="display: inline-block; margin-left: 10px">黑名单列表</h2>
      </div>
      <el-button type="danger" @click="openAddDialog">手动加入黑名单</el-button>
    </el-row>

    <el-row style="margin-top: 16px" :gutter="10">
      <el-input
        v-model="searchQuery"
        placeholder="按姓名或手机号搜索"
        clearable
        style="width: 250px"
        @change="loadBlacklist"
      />
      <el-select
        v-model="reasonFilter"
        placeholder="按原因筛选"
        clearable
        style="width: 150px"
        @change="loadBlacklist"
      >
        <el-option label="欠费" value="欠费" />
        <el-option label="扰民" value="扰民" />
        <el-option label="房屋损坏" value="房屋损坏" />
        <el-option label="其他" value="其他" />
      </el-select>
      <el-button icon="Search" @click="loadBlacklist">搜索</el-button>
    </el-row>

    <el-table
      :data="blacklist"
      style="width: 100%; margin-top: 16px"
      v-loading="loading"
    >
      <el-table-column prop="name" label="租户姓名" width="150">
        <template #default="{ row }">
          <el-link type="primary" @click="viewTenant(row)">{{
            row.name
          }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="blacklistReason" label="加入原因" />
      <el-table-column label="加入时间" width="120">
        <template #default="{ row }">
          {{ formatDate(row.blacklistedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button
            size="small"
            type="success"
            @click="removeFromBlacklist(row)"
            >移出黑名单</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="手动加入黑名单" v-model="showAddDialog" width="450px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="选择租户" required>
          <el-select
            v-model="form.tenantId"
            filterable
            placeholder="搜索租户"
            style="width: 100%"
          >
            <el-option
              v-for="t in tenants"
              :key="t.id"
              :label="t.name + ' - ' + t.phone"
              :value="t.id"
              :disabled="t.isBlacklisted"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" required>
          <el-select
            v-model="form.reasonType"
            placeholder="请选择原因"
            style="width: 100%"
            @change="handleReasonChange"
          >
            <el-option label="欠费" value="欠费" />
            <el-option label="扰民" value="扰民" />
            <el-option label="房屋损坏" value="房屋损坏" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.reasonType === '其他'" label="详情">
          <el-input v-model="form.customReason" placeholder="请输入具体原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmAdd" :loading="adding"
          >确认加入</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  getBlacklist,
  addToBlacklist,
  removeFromBlacklist as apiRemoveFromBlacklist,
  getTenants,
  getErrorMessage,
} from "../api";
import { ElMessage, ElMessageBox } from "element-plus";
import { showUndoToast } from "../utils/undo";

const router = useRouter();
const blacklist = ref([]);
const tenants = ref([]);
const loading = ref(false);
const showAddDialog = ref(false);
const adding = ref(false);
const searchQuery = ref("");
const reasonFilter = ref("");
const form = ref({
  tenantId: null,
  reasonType: "",
  customReason: "",
});

function formatDate(dateStr) {
  if (!dateStr) return "";
  return dateStr.split("T")[0];
}

async function loadBlacklist() {
  loading.value = true;
  try {
    const res = await getBlacklist({
      q: searchQuery.value,
      reason: reasonFilter.value,
    });
    blacklist.value = res.data || [];
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "加载失败"));
  } finally {
    loading.value = false;
  }
}

async function loadTenants() {
  try {
    const res = await getTenants({});
    tenants.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
}

function viewTenant(row) {
  router.push({ path: "/tenants", query: { id: row.id } });
}

function openAddDialog() {
  form.value = { tenantId: null, reasonType: "", customReason: "" };
  loadTenants();
  showAddDialog.value = true;
}

function handleReasonChange() {
  form.value.customReason = "";
}

async function confirmAdd() {
  if (!form.value.tenantId) {
    return ElMessage.warning("请选择租户");
  }
  if (!form.value.reasonType) {
    return ElMessage.warning("请选择原因");
  }
  const reason =
    form.value.reasonType === "其他"
      ? form.value.customReason
      : form.value.reasonType;
  if (!reason) {
    return ElMessage.warning("请填写原因");
  }
  adding.value = true;
  try {
    await addToBlacklist(form.value.tenantId, { reason, adminId: 1 });
    ElMessage.success("已加入黑名单");
    showAddDialog.value = false;
    loadBlacklist();
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "操作失败"));
  } finally {
    adding.value = false;
  }
}

async function removeFromBlacklist(row) {
  try {
    await ElMessageBox.confirm("确定将该租户移出黑名单吗？", "提示", {
      type: "warning",
    });
    const res = await apiRemoveFromBlacklist(row.id);
    ElMessage.success("已移出黑名单");
    showUndoToast(res?.data, loadBlacklist);
    loadBlacklist();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(getErrorMessage(e, "操作失败"));
  }
}

onMounted(loadBlacklist);
</script>
