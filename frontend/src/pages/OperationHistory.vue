<template>
  <div>
    <el-row style="margin-bottom: 15px" :gutter="10">
      <el-input
        v-model="usernameFilter"
        placeholder="操作人"
        clearable
        style="width: 140px"
      />
      <el-input
        v-model="moduleFilter"
        placeholder="模块"
        clearable
        style="width: 140px"
      />
      <el-input
        v-model="keywordFilter"
        placeholder="对象/操作关键词"
        clearable
        style="width: 180px"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="yyyy-MM-dd"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
      <el-button @click="loadHistory">查询</el-button>
    </el-row>

    <el-table :data="rows" style="width: 100%" v-loading="loading">
      <el-table-column label="操作时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="操作人" width="120" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作类型" width="120" />
      <el-table-column
        prop="targetName"
        label="目标对象"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column label="状态" width="120">
        <template #default>
          <el-tag type="warning">可撤销</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)"
            >查看详情</el-button
          >
          <el-button
            link
            type="warning"
            :disabled="!canRecover(row)"
            @click="recover(row)"
            >撤销</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 16px"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="onPageChange"
    />

    <el-dialog v-model="detailVisible" title="操作快照详情" width="900px">
      <el-row :gutter="12">
        <el-col :span="12">
          <div style="font-weight: 600; margin-bottom: 8px">操作前</div>
          <pre class="json-box">{{ prettyJson(detailBefore) }}</pre>
        </el-col>
        <el-col :span="12">
          <div style="font-weight: 600; margin-bottom: 8px">操作后</div>
          <pre class="json-box">{{ prettyJson(detailAfter) }}</pre>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { formatDateTime, formatRelative } from '../utils/format'
import { ref, onMounted, onUnmounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getOperationSnapshots,
  getOperationSnapshotDetail,
  recoverOperationSnapshot,
  getErrorMessage,
} from "../api";

const loading = ref(false);
const rows = ref([]);
const usernameFilter = ref("");
const moduleFilter = ref("");
const keywordFilter = ref("");
const dateRange = ref([]);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);

const detailVisible = ref(false);
const detailBefore = ref(null);
const detailAfter = ref(null);
let pollTimer = null;

function isExpired(row) {
  if (!row?.expiredAt) return true;
  return new Date(row.expiredAt).getTime() < Date.now();
}

function canRecover(row) {
  return !row?.isRecovered && !isExpired(row);
}

function prettyJson(data) {
  if (data === null || data === undefined) return "-";
  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data);
  }
}

async function loadHistory() {
  loading.value = true;
  try {
    const res = await getOperationSnapshots({
      username: usernameFilter.value,
      module: moduleFilter.value,
      keyword: keywordFilter.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value - 1,
      size: pageSize.value,
    });
    rows.value = res.data.content || [];
    total.value = res.data.totalElements || rows.value.length;
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "加载操作历史失败"));
  } finally {
    loading.value = false;
  }
}

function onPageChange(val) {
  page.value = val;
  loadHistory();
}

async function showDetail(row) {
  try {
    const res = await getOperationSnapshotDetail(row.id);
    detailBefore.value = res.data.beforeData;
    detailAfter.value = res.data.afterData;
    detailVisible.value = true;
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "加载详情失败"));
  }
}

async function recover(row) {
  try {
    await ElMessageBox.confirm(
      "确定恢复到该操作前的状态吗？后续对该数据的修改可能会丢失。",
      "恢复确认",
      {
        type: "warning",
        confirmButtonText: "确定恢复",
        cancelButtonText: "取消",
      }
    );
    await recoverOperationSnapshot(row.id);
    ElMessage.success("撤销成功");
    await loadHistory();
  } catch (e) {
    if (e === "cancel") return;
    ElMessage.error(getErrorMessage(e, "恢复失败"));
  }
}

onMounted(() => {
  loadHistory();
  // Keep this page in sync with backend cleanup and new operations.
  pollTimer = setInterval(loadHistory, 30 * 60 * 1000);
});

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});
</script>

<style scoped>
.json-box {
  margin: 0;
  min-height: 260px;
  max-height: 460px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
