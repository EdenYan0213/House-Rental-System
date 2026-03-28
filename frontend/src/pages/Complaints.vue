<template>
  <div style="padding: 20px">
    <el-row justify="space-between" align="middle">
      <h2>投诉管理</h2>
      <div>
        <el-button @click="exportCurrent">导出</el-button>
        <el-button type="primary" @click="openCreate">新建投诉</el-button>
      </div>
    </el-row>

    <el-tabs v-model="activeTab" style="margin-top: 8px" @tab-change="onTabChange">
      <el-tab-pane label="待受理" name="pending" />
      <el-tab-pane label="处理中" name="processing" />
      <el-tab-pane label="待确认" name="pending_confirm" />
      <el-tab-pane label="已完成" name="completed" />
      <el-tab-pane label="已关闭" name="closed" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <el-row :gutter="10" style="margin-top: 8px">
      <el-select v-model="query.buildingId" clearable filterable placeholder="楼栋" style="width: 150px">
        <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-select v-model="query.type" clearable placeholder="投诉类型" style="width: 170px">
        <el-option v-for="t in complaintTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="query.priority" clearable placeholder="紧急程度" style="width: 120px">
        <el-option label="一般" value="normal" />
        <el-option label="紧急" value="urgent" />
      </el-select>
      <el-date-picker v-model="query.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 140px" />
      <el-date-picker v-model="query.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 140px" />
      <el-input v-model="query.q" clearable placeholder="搜索租户/房间/标题" style="width: 260px" @keyup.enter="load" />
      <el-button @click="load">查询</el-button>
    </el-row>

    <el-table :data="list" style="margin-top: 12px" border v-loading="loading">
      <el-table-column prop="complaintNo" label="投诉编号" width="180" />
      <el-table-column prop="createdAt" label="投诉时间" width="170" />
      <el-table-column prop="tenantDisplayName" label="租户" width="110" />
      <el-table-column prop="roomNumber" label="房间号" width="90" />
      <el-table-column prop="type" label="类型" width="130" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="紧急程度" width="100">
        <template #default="{ row }">
          <el-tag :type="row.priority === 'urgent' ? 'danger' : 'info'">{{ row.priority === 'urgent' ? '紧急' : '一般' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assignedName" label="处理人" width="120" />
      <el-table-column prop="updatedAt" label="最后更新时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDetail(row)">查看</el-button>
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

    <el-dialog title="新建投诉" v-model="showCreateDialog" width="720px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="租户" required>
          <el-select v-model="createForm.tenantId" filterable clearable placeholder="选择租户" style="width: 100%">
            <el-option v-for="t in tenants" :key="t.id" :label="`${t.name} / ${t.phone}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间" required>
          <el-select v-model="createForm.roomId" filterable placeholder="选择房间" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="r.roomNumber" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉类型" required>
          <el-select v-model="createForm.type" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in complaintTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉标题" required>
          <el-input v-model="createForm.title" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="投诉内容" required>
          <el-input v-model="createForm.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="上传图片">
          <el-upload :http-request="uploadComplaintImage" :show-file-list="false" :before-upload="beforeUpload">
            <el-button>上传图片</el-button>
          </el-upload>
          <div style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px">
            <img
              v-for="(img, idx) in createForm.images"
              :key="idx"
              :src="resolveImageUrl(img)"
              style="width: 76px; height: 76px; object-fit: cover; border-radius: 4px"
            />
          </div>
        </el-form-item>
        <el-form-item label="是否匿名">
          <el-switch v-model="createForm.isAnonymousBool" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="createForm.priority">
            <el-radio label="normal">一般</el-radio>
            <el-radio label="urgent">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showDetailDrawer" size="52%" title="投诉详情">
      <div v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="投诉编号">{{ current.complaintNo }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ current.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="租户">{{ current.tenantDisplayName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ current.tenantPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="房间号">{{ current.roomNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="投诉类型">{{ current.type }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">{{ current.content }}</el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            {{ current.priority === 'urgent' ? '紧急' : '一般' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ current.assignedName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="匿名">{{ current.isAnonymous === 1 ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item v-if="current.closedReason" label="关闭原因" :span="2">{{ current.closedReason }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="imageList(current).length > 0" style="margin-top: 12px">
          <div style="font-weight: 600; margin-bottom: 8px">投诉图片</div>
          <el-image
            v-for="(img, idx) in imageList(current)"
            :key="idx"
            :src="resolveImageUrl(img)"
            :preview-src-list="imagePreviewList(current)"
            style="width: 100px; height: 100px; margin-right: 8px; border-radius: 4px"
            fit="cover"
          />
        </div>

        <div style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">处理记录</div>
          <el-timeline>
            <el-timeline-item
              v-for="log in current.logs || []"
              :key="log.id"
              :timestamp="`${log.createdAt} · ${log.operatorName || '管理员'}`"
              placement="top"
            >
              <div><strong>{{ actionText(log.action) }}</strong></div>
              <div style="margin-top: 4px">{{ log.content }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <el-divider />
        <div style="display: flex; gap: 10px; flex-wrap: wrap">
          <el-button v-if="current.status === 'pending'" type="primary" @click="openAccept">受理</el-button>
          <el-button v-if="current.status === 'pending'" type="danger" @click="closeComplaintFlow">关闭</el-button>

          <el-button v-if="current.status === 'processing'" type="primary" @click="replyComplaintFlow">回复</el-button>
          <el-button v-if="current.status === 'processing'" type="success" @click="completeComplaintFlow">完成处理</el-button>
          <el-button v-if="current.status === 'processing'" type="warning" @click="openReassign">转派</el-button>

          <el-button v-if="current.status === 'pending_confirm'" type="success" @click="confirmComplaintFlow">确认完成</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="受理投诉" v-model="showAcceptDialog" width="520px">
      <el-form :model="acceptForm" label-width="100px">
        <el-form-item label="受理意见">
          <el-input v-model="acceptForm.note" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="acceptForm.assignedTo" clearable placeholder="不选则默认自己" style="width: 100%">
            <el-option v-for="h in handlers" :key="h.id" :label="`${h.name}${h.role ? ' / ' + h.role : ''}`" :value="h.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAcceptDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAccept">确认受理</el-button>
      </template>
    </el-dialog>

    <el-dialog title="转派投诉" v-model="showReassignDialog" width="520px">
      <el-form :model="reassignForm" label-width="100px">
        <el-form-item label="处理人" required>
          <el-select v-model="reassignForm.assignedTo" filterable placeholder="请选择处理人" style="width: 100%">
            <el-option v-for="h in handlers" :key="h.id" :label="`${h.name}${h.role ? ' / ' + h.role : ''}`" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reassignForm.note" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReassignDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReassign">确认转派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acceptComplaint,
  closeComplaint,
  completeComplaint,
  confirmComplaint,
  createComplaint,
  exportModuleData,
  getBuildings,
  getComplaint,
  getComplaintHandlers,
  getComplaints,
  getRooms,
  getTenants,
  reassignComplaint,
  replyComplaint,
  uploadComplaintFile
} from '../api'

const route = useRoute()

const complaintTypes = ['噪音扰民', '卫生环境', '设施问题（非报修）', '服务态度', '其他']

const loading = ref(false)
const activeTab = ref('pending')
const list = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(20)

const buildings = ref([])
const rooms = ref([])
const tenants = ref([])
const handlers = ref([])

const query = ref({
  status: 'pending',
  type: '',
  priority: '',
  buildingId: '',
  startDate: '',
  endDate: '',
  q: ''
})

const showCreateDialog = ref(false)
const createForm = ref({
  tenantId: null,
  roomId: null,
  type: '噪音扰民',
  title: '',
  content: '',
  images: [],
  isAnonymousBool: false,
  priority: 'normal'
})

const showDetailDrawer = ref(false)
const current = ref(null)

const showAcceptDialog = ref(false)
const acceptForm = ref({ note: '', assignedTo: null })

const showReassignDialog = ref(false)
const reassignForm = ref({ assignedTo: null, note: '' })

function statusText(status) {
  const map = {
    pending: '待受理',
    processing: '处理中',
    pending_confirm: '待确认',
    completed: '已完成',
    closed: '已关闭'
  }
  return map[status] || status
}

function statusTagType(status) {
  return {
    pending: 'info',
    processing: 'warning',
    pending_confirm: 'primary',
    completed: 'success',
    closed: 'danger'
  }[status] || 'info'
}

function actionText(action) {
  return {
    submit: '提交投诉',
    accept: '受理',
    reply: '处理回复',
    complete: '完成处理',
    reassign: '转派',
    close: '关闭',
    confirm: '确认完成'
  }[action] || action
}

function onTabChange(name) {
  query.value.status = name === 'all' ? '' : name
  page.value = 0
  load()
}

function onPage(nextPage) {
  page.value = nextPage - 1
  load()
}

function beforeUpload(file) {
  const ext = (file.name || '').split('.').pop()?.toLowerCase() || ''
  const validExt = ['jpg', 'jpeg', 'png']
  if (!validExt.includes(ext)) {
    ElMessage.error('仅支持 jpg/png 图片')
    return false
  }
  const ok = file.size / 1024 / 1024 <= 5
  if (!ok) {
    ElMessage.error('单张图片不超过 5MB')
  }
  return ok
}

function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) return url
  return `/api/files/${url}`
}

function parseImages(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      const arr = JSON.parse(raw)
      return Array.isArray(arr) ? arr : []
    } catch (_) {
      return []
    }
  }
  return []
}

function imageList(item) {
  return parseImages(item?.images)
}

function imagePreviewList(item) {
  return imageList(item).map(v => resolveImageUrl(v))
}

async function uploadComplaintImage(options) {
  try {
    const res = await uploadComplaintFile(options.file)
    const url = res.data?.url
    if (!url) {
      options.onError(new Error('上传失败'))
      return
    }
    createForm.value.images.push(url)
    options.onSuccess(res.data)
    ElMessage.success('上传成功')
  } catch (e) {
    options.onError(e)
    ElMessage.error('上传失败')
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getComplaints({
      status: query.value.status,
      type: query.value.type,
      priority: query.value.priority,
      buildingId: query.value.buildingId,
      startDate: query.value.startDate,
      endDate: query.value.endDate,
      q: query.value.q,
      page: page.value,
      size: size.value
    })
    list.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.value = {
    tenantId: route.query.tenantId ? Number(route.query.tenantId) : null,
    roomId: route.query.roomId ? Number(route.query.roomId) : null,
    type: '噪音扰民',
    title: '',
    content: '',
    images: [],
    isAnonymousBool: false,
    priority: 'normal'
  }
  showCreateDialog.value = true
}

async function submitCreate() {
  if (!createForm.value.roomId || !createForm.value.type || !createForm.value.title || !createForm.value.content) {
    ElMessage.warning('请完整填写房间、类型、标题和内容')
    return
  }
  await createComplaint({
    tenantId: createForm.value.tenantId,
    roomId: createForm.value.roomId,
    type: createForm.value.type,
    title: createForm.value.title,
    content: createForm.value.content,
    images: createForm.value.images,
    isAnonymous: createForm.value.isAnonymousBool ? 1 : 0,
    priority: createForm.value.priority,
    reporterType: 'admin'
  })
  showCreateDialog.value = false
  ElMessage.success('投诉已提交')
  query.value.status = 'pending'
  activeTab.value = 'pending'
  page.value = 0
  await load()
}

async function openDetail(row) {
  const res = await getComplaint(row.id)
  current.value = res.data
  showDetailDrawer.value = true
}

function openAccept() {
  acceptForm.value = { note: '', assignedTo: null }
  showAcceptDialog.value = true
}

async function submitAccept() {
  await acceptComplaint(current.value.id, {
    note: acceptForm.value.note,
    assignedTo: acceptForm.value.assignedTo
  })
  showAcceptDialog.value = false
  ElMessage.success('已受理')
  await refreshCurrent()
  await load()
}

function openReassign() {
  reassignForm.value = { assignedTo: null, note: '' }
  showReassignDialog.value = true
}

async function submitReassign() {
  if (!reassignForm.value.assignedTo) {
    ElMessage.warning('请选择处理人')
    return
  }
  await reassignComplaint(current.value.id, {
    assignedTo: reassignForm.value.assignedTo,
    note: reassignForm.value.note
  })
  showReassignDialog.value = false
  ElMessage.success('转派成功')
  await refreshCurrent()
  await load()
}

async function replyComplaintFlow() {
  const { value } = await ElMessageBox.prompt('请输入处理回复内容', '处理回复', {
    inputType: 'textarea',
    inputPlaceholder: '例如：已联系保洁处理，预计2小时完成'
  })
  await replyComplaint(current.value.id, { content: value || '' })
  ElMessage.success('回复已记录')
  await refreshCurrent()
  await load()
}

async function completeComplaintFlow() {
  const { value } = await ElMessageBox.prompt('请输入最终处理结果', '完成处理', {
    inputType: 'textarea',
    inputPlaceholder: '请输入处理结果'
  })
  await completeComplaint(current.value.id, {
    result: value || '',
    requireConfirm: true
  })
  ElMessage.success('已提交为待确认')
  await refreshCurrent()
  await load()
}

async function closeComplaintFlow() {
  const { value } = await ElMessageBox.prompt('请输入关闭原因', '关闭投诉', {
    inputType: 'textarea',
    inputPlaceholder: '重复提交/无效投诉等'
  })
  await closeComplaint(current.value.id, { reason: value || '' })
  ElMessage.success('已关闭')
  await refreshCurrent()
  await load()
}

async function confirmComplaintFlow() {
  await confirmComplaint(current.value.id)
  ElMessage.success('已确认完成')
  await refreshCurrent()
  await load()
}

async function refreshCurrent() {
  if (!current.value?.id) return
  const res = await getComplaint(current.value.id)
  current.value = res.data
}

async function exportCurrent() {
  try {
    const ids = list.value.map(v => v.id)
    const payload = ids.length > 0 ? { ids } : {}
    const res = await exportModuleData('complaints', payload)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `complaints_${new Date().toISOString().split('T')[0]}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  const [buildingRes, roomRes, tenantRes, handlerRes] = await Promise.all([
    getBuildings(),
    getRooms(),
    getTenants(),
    getComplaintHandlers()
  ])
  buildings.value = buildingRes.data || []
  rooms.value = roomRes.data || []
  tenants.value = tenantRes.data?.content || tenantRes.data || []
  handlers.value = handlerRes.data || []

  const routeStatus = String(route.query.status || '')
  if (routeStatus === 'all') {
    activeTab.value = 'all'
    query.value.status = ''
  } else if (['pending', 'processing', 'pending_confirm', 'completed', 'closed'].includes(routeStatus)) {
    activeTab.value = routeStatus
    query.value.status = routeStatus
  }
  if (route.query.q) {
    query.value.q = String(route.query.q)
  }
  if (route.query.type) {
    query.value.type = String(route.query.type)
  }
  if (route.query.priority) {
    query.value.priority = String(route.query.priority)
  }
  if (route.query.buildingId) {
    query.value.buildingId = Number(route.query.buildingId)
  }

  await load()
  if (route.query.roomId || route.query.tenantId) {
    openCreate()
  }
})
</script>
