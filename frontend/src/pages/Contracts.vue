<template>
  <div class="contracts-page" style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>合同管理</h2>
      <div>
        <el-button @click="exportCurrent">导出</el-button>
        <el-button
          type="primary"
          @click="$router.push('/contracts/new')"
          v-if="canAdd"
          >新建合同</el-button
        >
      </div>
    </el-row>

    <el-row
      v-if="selectedContractIds.length > 0"
      style="margin-top: 12px"
      :gutter="10"
    >
      <el-tag type="info"
        >已选择 {{ selectedContractIds.length }} 份合同</el-tag
      >
      <el-button type="warning" @click="openBatchRenew" v-if="canRenew"
        >批量续租</el-button
      >
      <el-button type="danger" @click="openBatchTerminate" v-if="canTerminate"
        >批量退租</el-button
      >
    </el-row>

    <el-row style="margin-top: 16px" :gutter="10">
      <el-select
        v-model="statusFilter"
        placeholder="合同状态"
        clearable
        style="width: 150px"
        @change="loadContracts"
      >
        <el-option label="全部" value="" />
        <el-option label="待生效" value="PENDING" />
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已到期" value="EXPIRED" />
        <el-option label="已解约" value="TERMINATED" />
      </el-select>
      <el-select
        v-model="expiringFilter"
        placeholder="即将到期"
        clearable
        style="width: 150px"
        @change="loadContracts"
      >
        <el-option label="全部" value="" />
        <el-option label="3天内" value="3" />
        <el-option label="7天内" value="7" />
        <el-option label="30天内" value="30" />
      </el-select>
      <el-input
        v-model="searchQuery"
        placeholder="合同编号/房间号/租户姓名"
        clearable
        style="width: 250px"
        @keyup.enter="loadContracts"
      />
      <el-button icon="Search" @click="loadContracts">搜索</el-button>
    </el-row>

    <el-table
      :data="contracts"
      style="width: 100%; margin-top: 16px"
      v-loading="loading"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="contractNo" label="合同编号" width="170" />
      <el-table-column prop="roomNumber" label="房间号" width="100" />
      <el-table-column prop="tenantName" label="租户姓名" width="120" />
      <el-table-column prop="startDate" label="起租日期" width="110" />
      <el-table-column prop="endDate" label="结束日期" width="110" />
      <el-table-column label="剩余天数" width="120">
        <template #default="{ row }">
          <el-tooltip :content="'到期日期: ' + row.endDate" placement="top">
            <el-tag :type="getDaysLeftType(row.endDate)">
              {{ getDaysLeftText(row.endDate) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="monthlyRent" label="月租金" width="100">
        <template #default="{ row }"
          >¥{{ row.monthlyRent?.toLocaleString() }}</template
        >
      </el-table-column>
      <el-table-column prop="deposit" label="押金" width="100">
        <template #default="{ row }"
          >¥{{ row.deposit?.toLocaleString() }}</template
        >
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{
            getStatusText(row.status)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <div class="contract-actions">
            <el-button size="small" link type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" link type="primary" @click="goRepair(row)">报修</el-button>
            <el-button size="small" link type="warning" @click="renew(row)" :disabled="row.status !== 'ACTIVE'" v-if="canRenew">续租</el-button>
            <el-button size="small" link type="danger" @click="terminate(row)" :disabled="row.status !== 'ACTIVE'" v-if="canTerminate">解约</el-button>
          </div>
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
      @current-change="loadContracts"
    />

    <el-dialog title="合同详情" v-model="showDetail" width="700px" top="5vh">
      <div v-if="isExpiring(currentContract)" style="margin-bottom: 16px">
        <el-alert type="warning" :closable="false">
          <template #title>
            <span
              >合同将于 {{ getDaysLeft(currentContract.endDate) }} 天后到期
            </span>
            <el-button
              type="primary"
              size="small"
              style="margin-left: 10px"
              @click="renew(currentContract)"
              >立即续租</el-button
            >
          </template>
        </el-alert>
      </div>
      <el-descriptions :column="2" border v-if="currentContract">
        <el-descriptions-item label="合同编号">{{
          currentContract.contractNo
        }}</el-descriptions-item>
        <el-descriptions-item label="房间号">{{
          currentContract.roomNumber
        }}</el-descriptions-item>
        <el-descriptions-item label="租户姓名">{{
          currentContract.tenantName
        }}</el-descriptions-item>
        <el-descriptions-item label="租户电话">{{
          currentContract.tenantPhone
        }}</el-descriptions-item>
        <el-descriptions-item label="起租日期">{{
          currentContract.startDate
        }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{
          currentContract.endDate
        }}</el-descriptions-item>
        <el-descriptions-item label="月租金"
          >¥{{
            currentContract.monthlyRent?.toLocaleString()
          }}</el-descriptions-item
        >
        <el-descriptions-item label="押金"
          >¥{{
            currentContract.deposit?.toLocaleString()
          }}</el-descriptions-item
        >
        <el-descriptions-item label="付款方式">{{
          currentContract.paymentMethod
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentContract.status)">{{
            getStatusText(currentContract.status)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          currentContract.remark || "-"
        }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="currentContract?.attachmentUrl" style="margin-top: 15px">
        <el-link
          :href="currentContract.attachmentUrl"
          target="_blank"
          type="primary"
          >查看附件</el-link
        >
      </div>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog title="解约" v-model="showTerminate" width="450px">
      <el-form :model="terminateForm" label-width="80px">
        <el-form-item label="解约日期" required>
          <el-date-picker
            v-model="terminateForm.terminateDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="解约原因">
          <el-input v-model="terminateForm.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="terminateForm.addToBlacklist"
            >将此租户加入黑名单</el-checkbox
          >
        </el-form-item>
        <el-form-item
          v-if="terminateForm.addToBlacklist"
          label="加入原因"
          required
        >
          <el-select
            v-model="terminateForm.blacklistReasonType"
            placeholder="请选择原因"
            style="width: 100%"
            @change="handleBlacklistReasonChange"
          >
            <el-option label="欠费" value="欠费" />
            <el-option label="扰民" value="扰民" />
            <el-option label="房屋损坏" value="房屋损坏" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="
            terminateForm.addToBlacklist &&
            terminateForm.blacklistReasonType === '其他'
          "
          label="详情"
        >
          <el-input
            v-model="terminateForm.blacklistCustomReason"
            placeholder="请输入具体原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTerminate = false">取消</el-button>
        <el-button type="danger" @click="confirmTerminate">确认解约</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量续租" v-model="showBatchRenew" width="560px">
      <el-form :model="batchRenewForm" label-width="120px">
        <el-form-item label="续租结束日期">
          <el-date-picker
            v-model="batchRenewForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="续租月数(可选)">
          <el-input-number
            v-model="batchRenewForm.durationMonths"
            :min="1"
            :max="60"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="租金调整方式">
          <el-radio-group v-model="batchRenewForm.rentMode">
            <el-radio label="FIXED">固定金额</el-radio>
            <el-radio label="PERCENT">百分比</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="batchRenewForm.rentMode === 'FIXED'"
          label="固定金额"
        >
          <el-input-number
            v-model="batchRenewForm.fixedAmount"
            :step="100"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-else label="百分比(%)">
          <el-input-number
            v-model="batchRenewForm.percent"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="batchRenewForm.inheritDeposit"
            >沿用原押金</el-checkbox
          >
          <el-checkbox
            v-model="batchRenewForm.inheritPaymentMethod"
            style="margin-left: 12px"
            >沿用付款方式</el-checkbox
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchRenew = false">取消</el-button>
        <el-button
          type="primary"
          :loading="batchLoading"
          @click="submitBatchRenew"
          >确认续租</el-button
        >
      </template>
    </el-dialog>

    <el-dialog title="批量退租" v-model="showBatchTerminate" width="520px">
      <el-form :model="batchTerminateForm" label-width="120px">
        <el-form-item label="统一退租日期" required>
          <el-date-picker
            v-model="batchTerminateForm.terminateDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="退租原因">
          <el-input
            v-model="batchTerminateForm.reason"
            type="textarea"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchTerminate = false">取消</el-button>
        <el-button
          type="danger"
          :loading="batchLoading"
          @click="submitBatchTerminate"
          >确认退租</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import {
  addToBlacklist,
  batchRenewContracts,
  batchTerminateContracts,
  canAddContract,
  canRenewContract,
  canTerminateContract,
  exportModuleData,
  getContracts,
  terminateContract,
} from "../api";
import { ElMessage, ElMessageBox } from "element-plus";
import { showUndoToast } from "../utils/undo";

const router = useRouter();
const route = useRoute();

const canAdd = computed(() => canAddContract());
const canRenew = computed(() => canRenewContract());
const canTerminate = computed(() => canTerminateContract());
const contracts = ref([]);
const loading = ref(false);
const statusFilter = ref("");
const expiringFilter = ref("");
const searchQuery = ref("");
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const showDetail = ref(false);
const showTerminate = ref(false);
const currentContract = ref(null);
const terminateForm = ref({ terminateDate: "", reason: "" });
const selectedContractIds = ref([]);
const showBatchRenew = ref(false);
const showBatchTerminate = ref(false);
const batchLoading = ref(false);
const batchRenewForm = ref({
  endDate: "",
  durationMonths: 12,
  rentMode: "FIXED",
  fixedAmount: 0,
  percent: 0,
  inheritDeposit: true,
  inheritPaymentMethod: true,
});
const batchTerminateForm = ref({ terminateDate: "", reason: "" });

function getStatusType(status) {
  const map = {
    PENDING: "warning",
    ACTIVE: "success",
    EXPIRED: "info",
    TERMINATED: "danger",
  };
  return map[status] || "info";
}

function getStatusText(status) {
  const map = {
    PENDING: "待生效",
    ACTIVE: "进行中",
    EXPIRED: "已到期",
    TERMINATED: "已解约",
  };
  return map[status] || status;
}

function getDaysLeft(endDate) {
  if (!endDate) return 999;
  const end = new Date(endDate);
  const today = new Date();
  return Math.ceil((end - today) / (1000 * 60 * 60 * 24));
}

function getDaysLeftText(endDate) {
  const days = getDaysLeft(endDate);
  if (days < 0) return `已逾期${Math.abs(days)}天`;
  if (days === 0) return "今天到期";
  return `${days}天后`;
}

function getDaysLeftType(endDate) {
  const days = getDaysLeft(endDate);
  if (days < 0) return "danger";
  if (days <= 2) return "warning";
  if (days <= 7) return "success";
  return "info";
}

function isExpiring(contract) {
  if (!contract || contract.status !== "ACTIVE") return false;
  return (
    getDaysLeft(contract.endDate) <= 3 && getDaysLeft(contract.endDate) >= 0
  );
}

async function loadContracts() {
  loading.value = true;
  try {
    const params = {
      status: statusFilter.value,
      q: searchQuery.value,
      page: page.value - 1,
      size: pageSize.value,
    };
    if (expiringFilter.value) {
      params.expiring = expiringFilter.value;
    }
    const res = await getContracts(params);
    contracts.value = res.data.content || res.data || [];
    total.value = res.data.totalElements || contracts.value.length;
  } catch (e) {
    ElMessage.error("加载失败");
  } finally {
    loading.value = false;
  }
}

function onSelectionChange(rows) {
  selectedContractIds.value = (rows || []).map((r) => r.id);
}

function viewDetail(row) {
  currentContract.value = row;
  showDetail.value = true;
}

function renew(row) {
  router.push({ path: "/contracts/new", query: { renewFrom: row.id } });
}

function goRepair(row) {
  const q = new URLSearchParams();
  if (row.roomId) q.set("roomId", row.roomId);
  if (row.tenantId) q.set("tenantId", row.tenantId);
  router.push(`/repairs/orders?${q.toString()}`);
}

function openBatchRenew() {
  if (selectedContractIds.value.length === 0) {
    ElMessage.warning("请先勾选合同");
    return;
  }
  showBatchRenew.value = true;
}

function openBatchTerminate() {
  if (selectedContractIds.value.length === 0) {
    ElMessage.warning("请先勾选合同");
    return;
  }
  batchTerminateForm.value.terminateDate = new Date()
    .toISOString()
    .split("T")[0];
  showBatchTerminate.value = true;
}

async function submitBatchRenew() {
  batchLoading.value = true;
  try {
    const payload = {
      contractIds: selectedContractIds.value,
      endDate: batchRenewForm.value.endDate,
      durationMonths: batchRenewForm.value.durationMonths,
      rentMode: batchRenewForm.value.rentMode,
      fixedAmount: batchRenewForm.value.fixedAmount,
      percent: batchRenewForm.value.percent,
      inheritDeposit: batchRenewForm.value.inheritDeposit,
      inheritPaymentMethod: batchRenewForm.value.inheritPaymentMethod,
    };
    const res = await batchRenewContracts(payload);
    ElMessage.success(
      `批量续租完成：新增${res.data.createdCount}份，跳过${res.data.skippedCount}份`
    );
    showBatchRenew.value = false;
    selectedContractIds.value = [];
    await loadContracts();
  } catch (e) {
    ElMessage.error(e.response?.data || e.message);
  } finally {
    batchLoading.value = false;
  }
}

async function submitBatchTerminate() {
  if (!batchTerminateForm.value.terminateDate) {
    ElMessage.warning("请选择退租日期");
    return;
  }
  batchLoading.value = true;
  try {
    const payload = {
      contractIds: selectedContractIds.value,
      terminateDate: batchTerminateForm.value.terminateDate,
      reason: batchTerminateForm.value.reason,
      allowForce: false,
    };
    const res = await batchTerminateContracts(payload);
    ElMessage.success(
      `批量退租完成：成功${res.data.terminatedCount}份，阻止${res.data.blockedCount}份`
    );
    showBatchTerminate.value = false;
    selectedContractIds.value = [];
    await loadContracts();
  } catch (e) {
    ElMessage.error(e.response?.data || e.message);
  } finally {
    batchLoading.value = false;
  }
}

async function exportCurrent() {
  try {
    const payload =
      selectedContractIds.value.length > 0
        ? { ids: selectedContractIds.value }
        : {};
    const res = await exportModuleData("contracts", payload);
    const blob = new Blob([res.data], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `contracts_${new Date().toISOString().split("T")[0]}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error("导出失败");
  }
}

function terminate(row) {
  currentContract.value = row;
  terminateForm.value = {
    terminateDate: "",
    reason: "",
    addToBlacklist: false,
    blacklistReasonType: "",
    blacklistCustomReason: "",
  };
  showTerminate.value = true;
}

function handleBlacklistReasonChange() {
  terminateForm.value.blacklistCustomReason = "";
}

async function confirmTerminate() {
  if (!terminateForm.value.terminateDate) {
    return ElMessage.warning("请选择解约日期");
  }
  try {
    await ElMessageBox.confirm("确定要解约吗？操作后可在短时间内撤销。", "确认解约", {
      type: "warning",
    });
    const res = await terminateContract(
      currentContract.value.id,
      terminateForm.value
    );

    if (terminateForm.value.addToBlacklist) {
      const reason =
        terminateForm.value.blacklistReasonType === "其他"
          ? terminateForm.value.blacklistCustomReason
          : terminateForm.value.blacklistReasonType;
      if (reason) {
        try {
          await addToBlacklist(currentContract.value.tenantId, {
            reason,
            adminId: 1,
          });
        } catch (e) {
          console.error("加入黑名单失败", e);
        }
      }
    }

    ElMessage.success("解约成功");
    showUndoToast(res?.data, loadContracts);
    showTerminate.value = false;
    showDetail.value = false;
    loadContracts();
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  }
}

onMounted(() => {
  if (route.query.expiring === "true") {
    expiringFilter.value = "3";
  }
  loadContracts();
});
</script>

<style scoped>
/* single-line table rows and inline buttons */
.el-table .cell,
.el-table .el-table__cell,
.el-table td .cell {
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
}
.contract-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  white-space: nowrap;
}
.contract-actions .el-button {
  margin-right: 0;
  padding: 4px 8px;
  font-size: 12px;
}

/* stronger left-shift: reduce padding for 起租日期 和 紧随列 (tenantName 与 startDate 更靠近) */
.contracts-page .el-table__header-wrapper table th:nth-child(5),
.contracts-page .el-table__body-wrapper table td:nth-child(5),
.contracts-page .el-table__header-wrapper table th:nth-child(6),
.contracts-page .el-table__body-wrapper table td:nth-child(6) {
  padding-left: 4px !important;
  padding-right: 4px !important;
}
</style>
