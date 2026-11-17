<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>维修工单</h2>
      <el-button type="primary" @click="openCreate">发起报修</el-button>
    </el-row>

    <el-row :gutter="10" style="margin-top: 12px">
      <el-select
        v-model="query.status"
        clearable
        placeholder="状态"
        style="width: 140px"
      >
        <el-option label="待分配" value="pending" />
        <el-option label="维修中" value="processing" />
        <el-option label="待验收" value="pending_accept" />
        <el-option label="已完成" value="completed" />
        <el-option label="已取消" value="cancelled" />
      </el-select>
      <el-date-picker
        v-model="query.startDate"
        value-format="YYYY-MM-DD"
        placeholder="开始日期"
        style="width: 140px"
      />
      <el-date-picker
        v-model="query.endDate"
        value-format="YYYY-MM-DD"
        placeholder="结束日期"
        style="width: 140px"
      />
      <el-select
        v-model="query.workerId"
        clearable
        filterable
        placeholder="维修工"
        style="width: 150px"
      >
        <el-option
          v-for="w in workers"
          :key="w.id"
          :label="w.name"
          :value="w.id"
        />
      </el-select>
      <el-button @click="load">查询</el-button>
    </el-row>

    <el-table
      :data="orders"
      v-loading="loading"
      style="margin-top: 12px"
      border
    >
      <el-table-column prop="orderNo" label="工单号" width="170" />
      <el-table-column prop="roomNumber" label="房间号" width="100" />
      <el-table-column prop="tenantName" label="租户" width="120" />
      <el-table-column prop="faultType" label="故障类型" width="100" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }"
          ><el-tag :type="statusType(row.status)">{{
            statusText(row.status)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="workerName" label="维修工" width="120" />
      <el-table-column prop="createdAt" label="报修时间" width="170" />
      <el-table-column label="操作" min-width="430">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">详情</el-button>
          <el-button
            size="small"
            type="primary"
            v-if="row.status === 'pending'"
            @click="openAssign(row)"
            >分配维修工</el-button
          >
          <el-button
            size="small"
            type="warning"
            v-if="row.status === 'processing'"
            @click="openComplete(row)"
            >完成维修</el-button
          >
          <el-button
            size="small"
            type="success"
            v-if="row.status === 'pending_accept'"
            @click="accept(row)"
            >验收通过</el-button
          >
          <el-button
            size="small"
            v-if="row.status === 'pending_accept'"
            @click="reject(row)"
            >驳回</el-button
          >
          <el-button
            size="small"
            type="danger"
            v-if="row.status !== 'cancelled' && row.status !== 'completed'"
            @click="cancel(row)"
            >取消</el-button
          >
          <el-button
            size="small"
            type="success"
            v-if="
              row.status === 'completed' &&
              row.isTenantCharge === 1 &&
              !row.billItemId
            "
            @click="generateBill(row)"
            >生成账单</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px"
      :current-page="page + 1"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="onPage"
    />

    <el-dialog title="发起报修" v-model="showCreateDialog" width="640px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="楼栋" required>
          <el-select
            v-model="createForm.buildingId"
            filterable
            placeholder="请选择楼栋"
            style="width: 100%"
            @change="onCreateBuildingChange"
          >
            <el-option
              v-for="b in buildings"
              :key="b.id"
              :label="b.name"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="房间" required>
          <el-select
            v-model="createForm.roomId"
            filterable
            placeholder="请先选择楼栋"
            style="width: 100%"
            @change="onCreateRoomChange"
            :disabled="!createForm.buildingId"
          >
            <el-option
              v-for="r in filteredRooms"
              :key="r.id"
              :label="r.roomNumber"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="故障类型" required>
          <el-select v-model="createForm.faultType" style="width: 100%">
            <el-option label="水电" value="水电" />
            <el-option label="门窗" value="门窗" />
            <el-option label="家电" value="家电" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联器具">
          <el-select
            v-model="createForm.applianceId"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="a in roomAppliances"
              :key="a.id"
              :label="a.applianceName"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述"
          ><el-input v-model="createForm.description" type="textarea" :rows="4"
        /></el-form-item>
        <el-form-item label="图片">
          <el-upload
            :http-request="uploadRepairImage"
            :show-file-list="false"
            :before-upload="beforeUpload"
          >
            <el-button>上传图片</el-button>
          </el-upload>
          <div
            style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px"
          >
            <img
              v-for="(img, idx) in createForm.images"
              :key="idx"
              :src="resolveImageUrl(img)"
              style="width: 70px; height: 70px; object-fit: cover"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog title="分配维修工" v-model="showAssignDialog" width="420px">
      <el-select
        v-model="assignWorkerId"
        style="width: 100%"
        placeholder="请选择维修工"
      >
        <el-option
          v-for="w in enabledWorkers"
          :key="w.id"
          :label="w.name"
          :value="w.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="showAssignDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <el-dialog title="完成维修" v-model="showCompleteDialog" width="560px">
      <el-form :model="completeForm" label-width="100px">
        <el-form-item label="维修结果"
          ><el-input
            v-model="completeForm.repairResult"
            type="textarea"
            :rows="3"
        /></el-form-item>
        <el-form-item label="材料费"
          ><el-input-number
            v-model="completeForm.materialFee"
            :min="0"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="人工费"
          ><el-input-number
            v-model="completeForm.laborFee"
            :min="0"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="租户承担"
          ><el-switch v-model="completeForm.isTenantChargeBool"
        /></el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="completeForm.feeRemark" type="textarea" :rows="2"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCompleteDialog = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer title="工单详情" v-model="showDetailDrawer" size="45%">
      <el-descriptions :column="1" border v-if="currentOrder">
        <el-descriptions-item label="工单号">{{
          currentOrder.orderNo
        }}</el-descriptions-item>
        <el-descriptions-item label="房间号">{{
          currentOrder.roomNumber
        }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{
          currentOrder.tenantName || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="故障类型">{{
          currentOrder.faultType
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{
          statusText(currentOrder.status)
        }}</el-descriptions-item>
        <el-descriptions-item label="维修工">{{
          currentOrder.workerName || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="故障描述">{{
          currentOrder.description || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="维修结果">{{
          currentOrder.repairResult || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="费用"
          >材料费: {{ currentOrder.materialFee || 0 }} / 人工费:
          {{ currentOrder.laborFee || 0 }} / 合计:
          {{ currentOrder.totalFee || 0 }}</el-descriptions-item
        >
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { showUndoToast } from "../utils/undo";
import {
  getRepairOrders,
  getRepairWorkers,
  getBuildings,
  getRooms,
  getRoomAppliances,
  createRepairOrder,
  assignRepairWorker,
  completeRepairOrder,
  acceptRepairOrder,
  rejectRepairOrder,
  cancelRepairOrder,
  generateRepairBill,
  uploadFile,
  getErrorMessage,
} from "../api";
import { repairStatusText, repairStatusTag } from "../utils/labels";

const route = useRoute();
const loading = ref(false);
const orders = ref([]);
const workers = ref([]);
const buildings = ref([]);
const allRooms = ref([]);
const roomAppliances = ref([]);
const query = ref({
  status: "",
  startDate: "",
  endDate: "",
  buildingId: "",
  workerId: "",
});
const page = ref(0);
const size = ref(20);
const total = ref(0);

const showCreateDialog = ref(false);
const showAssignDialog = ref(false);
const showCompleteDialog = ref(false);
const showDetailDrawer = ref(false);
const currentOrder = ref(null);

const createForm = ref({
  buildingId: null,
  roomId: null,
  tenantId: null,
  faultType: "水电",
  description: "",
  applianceId: null,
  images: [],
  reporterType: "admin",
});
const assignWorkerId = ref(null);
const completeForm = ref({
  repairResult: "",
  materialFee: 0,
  laborFee: 0,
  isTenantChargeBool: false,
  feeRemark: "",
});

const enabledWorkers = computed(() =>
  workers.value.filter((w) => w.status === 1)
);
const filteredRooms = computed(() => {
  if (!createForm.value.buildingId) return [];
  return allRooms.value.filter(
    (r) => Number(r.buildingId) === Number(createForm.value.buildingId)
  );
});

function statusText(s) {
  return repairStatusText(s);
}

function statusType(s) {
  return repairStatusTag(s);
}

function beforeUpload(file) {
  const okType = file.type?.startsWith("image/");
  const okSize = file.size / 1024 / 1024 < 10;
  if (!okType) ElMessage.error("请上传图片文件");
  if (!okSize) ElMessage.error("图片不能超过10MB");
  return okType && okSize;
}

function resolveImageUrl(url) {
  if (!url) return "";
  if (
    url.startsWith("http://") ||
    url.startsWith("https://") ||
    url.startsWith("/")
  )
    return url;
  return `/api/files/${url}`;
}

async function uploadRepairImage(options) {
  try {
    const res = await uploadFile(options.file);
    const url = res.data?.url;
    if (url) {
      createForm.value.images.push(url);
      options.onSuccess(res.data);
      ElMessage.success("上传成功");
    } else {
      options.onError(new Error("上传失败"));
    }
  } catch (e) {
    options.onError(e);
    ElMessage.error("上传失败");
  }
}

async function load() {
  loading.value = true;
  try {
    const [orderRes, workerRes, buildingRes, roomRes] = await Promise.all([
      getRepairOrders({ ...query.value, page: page.value, size: size.value }),
      getRepairWorkers(),
      getBuildings(),
      getRooms(),
    ]);
    orders.value = orderRes.data.content || [];
    total.value = orderRes.data.totalElements || 0;
    workers.value = workerRes.data || [];
    buildings.value = buildingRes?.data || [];
    allRooms.value = roomRes.data || [];
  } finally {
    loading.value = false;
  }
}

function onPage(p) {
  page.value = p - 1;
  load();
}

function openCreate() {
  const presetRoomId = route.query.roomId ? Number(route.query.roomId) : null;
  const presetRoom = presetRoomId
    ? allRooms.value.find((r) => Number(r.id) === presetRoomId)
    : null;
  createForm.value = {
    buildingId: presetRoom ? Number(presetRoom.buildingId) : null,
    roomId: presetRoomId,
    tenantId: route.query.tenantId ? Number(route.query.tenantId) : null,
    faultType: "水电",
    description: "",
    applianceId: route.query.applianceId
      ? Number(route.query.applianceId)
      : null,
    images: [],
    reporterType: "admin",
  };
  showCreateDialog.value = true;
  if (createForm.value.roomId) {
    onCreateRoomChange(createForm.value.roomId);
  } else {
    roomAppliances.value = [];
  }
}

function onCreateBuildingChange() {
  createForm.value.roomId = null;
  createForm.value.applianceId = null;
  roomAppliances.value = [];
}

async function onCreateRoomChange(roomId) {
  const res = await getRoomAppliances(roomId);
  roomAppliances.value = res.data || [];
}

async function submitCreate() {
  if (
    !createForm.value.buildingId ||
    !createForm.value.roomId ||
    !createForm.value.faultType
  ) {
    ElMessage.warning("请先选择楼栋、房间并填写故障类型");
    return;
  }
  const payload = { ...createForm.value };
  delete payload.buildingId;
  await createRepairOrder(payload);
  showCreateDialog.value = false;
  ElMessage.success("工单已创建");
  await load();
}

function showDetail(row) {
  currentOrder.value = row;
  showDetailDrawer.value = true;
}

function openAssign(row) {
  currentOrder.value = row;
  assignWorkerId.value = null;
  showAssignDialog.value = true;
}

async function submitAssign() {
  if (!assignWorkerId.value) {
    ElMessage.warning("请选择维修工");
    return;
  }
  await assignRepairWorker(currentOrder.value.id, {
    workerId: assignWorkerId.value,
  });
  showAssignDialog.value = false;
  ElMessage.success("分配成功");
  await load();
}

function openComplete(row) {
  currentOrder.value = row;
  completeForm.value = {
    repairResult: "",
    materialFee: 0,
    laborFee: 0,
    isTenantChargeBool: false,
    feeRemark: "",
  };
  showCompleteDialog.value = true;
}

async function submitComplete() {
  const payload = {
    repairResult: completeForm.value.repairResult,
    materialFee: completeForm.value.materialFee,
    laborFee: completeForm.value.laborFee,
    isTenantCharge: completeForm.value.isTenantChargeBool ? 1 : 0,
    feeRemark: completeForm.value.feeRemark,
  };
  await completeRepairOrder(currentOrder.value.id, payload);
  showCompleteDialog.value = false;
  ElMessage.success("已提交待验收");
  await load();
}

async function accept(row) {
  await acceptRepairOrder(row.id);
  ElMessage.success("验收通过");
  await load();
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt("请输入驳回原因", "驳回验收", {
    inputPlaceholder: "原因",
  });
  await rejectRepairOrder(row.id, { reason: value || "" });
  ElMessage.success("已驳回");
  await load();
}

async function cancel(row) {
  const { value } = await ElMessageBox.prompt("请输入取消原因", "取消工单", {
    inputPlaceholder: "原因",
  });
  const res = await cancelRepairOrder(row.id, { reason: value || "" });
  ElMessage.success("已取消");
  showUndoToast(res?.data, load);
  await load();
}

async function generateBill(row) {
  await generateRepairBill(row.id);
  ElMessage.success("已生成维修账单项");
  await load();
}

onMounted(async () => {
  await load();
  if (route.query.roomId || route.query.tenantId) {
    openCreate();
  }
});
</script>
