<template>
  <div style="padding: 20px">
    <h2>系统设置</h2>

    <el-tabs v-model="activeTab" style="margin-top: 20px">
      <el-tab-pane label="个人信息" name="profile">
        <el-card style="max-width: 500px">
          <el-form :model="profileForm" label-width="100px">
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" disabled />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="profileForm.realName" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item label="角色">
              <el-input :value="roleName" disabled />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile" :loading="saving"
                >保存修改</el-button
              >
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <el-card style="max-width: 500px">
          <el-form :model="passwordForm" label-width="100px">
            <el-form-item label="当前密码">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                show-password
              />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="changePassword"
                :loading="changingPwd"
                >修改密码</el-button
              >
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="新手引导" name="guide">
        <el-card style="max-width: 600px">
          <el-form :model="guideForm" label-width="140px">
            <el-form-item label="显示上下文提示">
              <el-switch v-model="guideForm.showContextTips" />
            </el-form-item>
            <el-form-item label="显示任务进度卡">
              <el-switch v-model="guideForm.showTaskCard" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveGuideSettings" :loading="savingGuide">保存设置</el-button>
              <el-button @click="resetOnboarding" :loading="resettingGuide">重置新手引导</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        label="合同到期提醒"
        name="reminder"
        v-if="canManageReminder"
      >
        <el-card style="max-width: 600px">
          <el-form :model="reminderForm" label-width="120px">
            <el-form-item label="启用提醒">
              <el-switch v-model="reminderForm.enabled" />
            </el-form-item>
            <el-form-item label="提前提醒天数">
              <div
                style="
                  display: flex;
                  gap: 10px;
                  flex-wrap: wrap;
                  align-items: center;
                "
              >
                <el-input-number
                  v-model="newDay"
                  :min="1"
                  :max="30"
                  size="small"
                  style="width: 100px"
                />
                <el-button size="small" @click="addReminderDay">添加</el-button>
                <div style="display: flex; gap: 5px; flex-wrap: wrap">
                  <el-tag
                    v-for="(day, index) in reminderDays"
                    :key="day"
                    closable
                    @close="removeReminderDay(index)"
                    style="margin: 2px"
                  >
                    {{ day }}天
                  </el-tag>
                </div>
              </div>
              <div style="color: var(--text-muted); font-size: 12px; margin-top: 5px">
                提前天数最多5个
              </div>
            </el-form-item>
            <el-form-item label="提醒时间">
              <el-time-select
                v-model="reminderForm.reminderTime"
                start="00:00"
                end="23:30"
                step="00:30"
                placeholder="选择时间"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="逾期提醒">
              <el-switch v-model="reminderForm.overdueReminderEnabled" />
              <div style="color: var(--text-muted); font-size: 12px; margin-left: 10px">
                （预留功能）
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="saveReminderSettings"
                :loading="savingReminder"
                >保存设置</el-button
              >
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs" v-if="canViewLogs">
        <Logs />
      </el-tab-pane>

      <el-tab-pane label="撤销管理" name="operationHistory" v-if="canViewLogs">
        <OperationHistory />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import {
  getCurrentUser,
  changePassword as apiChangePassword,
  getReminderSettings,
  updateReminderSettings,
  updateProfile,
  resetGuide,
  getUser,
  setUser,
  hasRole,
  getErrorMessage,
} from "../api";
import { ElMessage } from "element-plus";
import Logs from "./Logs.vue";
import OperationHistory from "./OperationHistory.vue";

const activeTab = ref("profile");
const saving = ref(false);
const changingPwd = ref(false);
const savingReminder = ref(false);
const savingGuide = ref(false);
const resettingGuide = ref(false);
const newDay = ref(3);
const reminderDays = ref([]);

const canManageReminder = computed(
  () => hasRole("ADMIN") || hasRole("SUPER_ADMIN")
);
const canViewLogs = computed(() => hasRole("ADMIN") || hasRole("SUPER_ADMIN"));

const roleName = computed(() => {
  const role = getUser()?.role?.toUpperCase();
  if (role === "SUPER_ADMIN") return "超级管理员";
  if (role === "ADMIN") return "管理员";
  return "普通用户";
});

const profileForm = ref({
  username: "",
  realName: "",
  phone: "",
  email: "",
});

const passwordForm = ref({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});

const reminderForm = ref({
  enabled: true,
  reminderDays: "3,1",
  reminderTime: "09:00",
  overdueReminderEnabled: false,
});

const guideForm = ref({
  showContextTips: true,
  showTaskCard: true,
});

async function loadProfile() {
  try {
    const user = getUser();
    if (user) {
      profileForm.value = {
        username: user.username || "",
        realName: user.realName || "",
        phone: user.phone || "",
        email: user.email || "",
      };
      guideForm.value = {
        showContextTips: user.showContextTips !== false,
        showTaskCard: user.showTaskCard !== false,
      };
    }
  } catch (e) {
    console.error(e);
  }
}

async function saveGuideSettings() {
  savingGuide.value = true;
  try {
    await updateProfile({
      showContextTips: guideForm.value.showContextTips ? "true" : "false",
      showTaskCard: guideForm.value.showTaskCard ? "true" : "false",
    });
    const user = getUser();
    if (user) {
      setUser({
        ...user,
        showContextTips: guideForm.value.showContextTips,
        showTaskCard: guideForm.value.showTaskCard,
      });
    }
    ElMessage.success("引导设置已保存");
  } catch (e) {
    ElMessage.error("保存失败: " + (e.response?.data?.message || e.message));
  } finally {
    savingGuide.value = false;
  }
}

async function resetOnboarding() {
  resettingGuide.value = true;
  try {
    await resetGuide();
    const user = getUser();
    if (user) {
      setUser({
        ...user,
        firstLogin: true,
        showContextTips: true,
        showTaskCard: true,
        dismissedGuideKeys: [],
      });
    }
    guideForm.value = { showContextTips: true, showTaskCard: true };
    ElMessage.success("新手引导已重置，下次进入会重新触发");
  } catch (e) {
    ElMessage.error("重置失败: " + (e.response?.data?.message || e.message));
  } finally {
    resettingGuide.value = false;
  }
}

async function saveProfile() {
  saving.value = true;
  try {
    await updateProfile({
      realName: profileForm.value.realName,
      phone: profileForm.value.phone,
      email: profileForm.value.email,
    });
    setUser({ ...getUser(), ...profileForm.value });
    ElMessage.success("保存成功");
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

async function changePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    return ElMessage.warning("请填写完整");
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    return ElMessage.warning("两次密码输入不一致");
  }
  changingPwd.value = true;
  try {
    await apiChangePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    });
    ElMessage.success("密码修改成功");
    passwordForm.value = {
      oldPassword: "",
      newPassword: "",
      confirmPassword: "",
    };
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "修改失败"));
  } finally {
    changingPwd.value = false;
  }
}

function addReminderDay() {
  if (reminderDays.value.length >= 5) {
    return ElMessage.warning("最多添加5个提醒天数");
  }
  if (reminderDays.value.includes(newDay.value)) {
    return ElMessage.warning("该天数已存在");
  }
  reminderDays.value.push(newDay.value);
  reminderDays.value.sort((a, b) => b - a);
}

function removeReminderDay(index) {
  reminderDays.value.splice(index, 1);
}

async function loadReminderSettings() {
  try {
    const res = await getReminderSettings();
    reminderForm.value = {
      enabled: res.data.enabled ?? true,
      reminderDays: res.data.reminderDays || "3,1",
      reminderTime: res.data.reminderTime || "09:00",
      overdueReminderEnabled: res.data.overdueReminderEnabled ?? false,
    };
    reminderDays.value = (res.data.reminderDays || "3,1")
      .split(",")
      .map((d) => parseInt(d))
      .filter((d) => !isNaN(d));
  } catch (e) {
    console.error(e);
  }
}

async function saveReminderSettings() {
  savingReminder.value = true;
  try {
    reminderForm.value.reminderDays = reminderDays.value.join(",");
    await updateReminderSettings(reminderForm.value);
    ElMessage.success("保存成功");
  } catch (e) {
    ElMessage.error(getErrorMessage(e, "保存失败"));
  } finally {
    savingReminder.value = false;
  }
}

onMounted(() => {
  loadProfile();
  loadReminderSettings();
});
</script>
