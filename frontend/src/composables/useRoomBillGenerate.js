import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { generateBillForRoom, getBuildings, getRooms, getErrorMessage } from '@/api'

/**
 * 「按房间生成账单」弹窗的级联选择与提交。
 *
 * <p>从 `Bills.vue` 抽出。交互是三级级联：楼栋 → 楼层 → 房间，
 * 并且楼层/房间在同楼栋内本地过滤，只有切楼栋才重新请求。
 *
 * <p>原实现里加载失败是 `catch { 列表 = [] }`，用户只看到空下拉、
 * 不知道是「没数据」还是「请求失败」，这里改为显式提示。
 *
 * @param onSuccess 生成成功后的回调（刷新列表与汇总）
 */
export function useRoomBillGenerate(onSuccess) {
  const visible = ref(false)
  const generating = ref(false)

  const buildingOptions = ref([])
  const floorOptions = ref([])
  const allRooms = ref([])
  const roomOptions = ref([])

  const form = reactive({
    buildingId: null,
    floor: null,
    roomId: null,
    month: ''
  })

  function formatRoomOption(room) {
    const building = room?.buildingName ? `${room.buildingName}-` : ''
    return `${building}${room?.roomNumber || room?.id}`
  }

  async function loadBuildings() {
    try {
      const res = await getBuildings()
      buildingOptions.value = res.data || []
    } catch (e) {
      buildingOptions.value = []
      ElMessage.error(getErrorMessage(e, '楼栋列表加载失败'))
    }
  }

  async function loadRooms() {
    try {
      const params = { size: 1000 }
      if (form.buildingId) {
        params.buildingId = form.buildingId
      }
      const res = await getRooms(params)
      allRooms.value = res.data?.content || res.data || []
    } catch (e) {
      allRooms.value = []
      ElMessage.error(getErrorMessage(e, '房间列表加载失败'))
    }
  }

  function refreshFloorOptions() {
    const set = new Set(
      (allRooms.value || [])
        .map(r => r.floor)
        .filter(v => v !== null && v !== undefined)
    )
    floorOptions.value = Array.from(set).sort((a, b) => a - b)
  }

  /** 楼层选定后在本地过滤房间，不再发请求。 */
  function refreshRoomOptions() {
    if (form.floor === null || form.floor === undefined) {
      roomOptions.value = []
      return
    }
    roomOptions.value = (allRooms.value || []).filter(r => r.floor === form.floor)
  }

  async function onBuildingChange() {
    form.floor = null
    form.roomId = null
    roomOptions.value = []
    await loadRooms()
    refreshFloorOptions()
  }

  function onFloorChange() {
    form.roomId = null
    refreshRoomOptions()
  }

  /** 打开弹窗：重置为初始状态，并预填当前筛选的月份。 */
  async function open(defaultMonth) {
    form.buildingId = null
    form.floor = null
    form.roomId = null
    form.month = defaultMonth || ''
    allRooms.value = []
    floorOptions.value = []
    roomOptions.value = []

    await loadBuildings()
    await loadRooms()
    visible.value = true
  }

  async function submit() {
    if (!form.buildingId) {
      ElMessage.warning('请选择楼栋')
      return
    }
    if (form.floor === null || form.floor === undefined) {
      ElMessage.warning('请选择楼层')
      return
    }
    if (!form.roomId) {
      ElMessage.warning('请选择房间')
      return
    }

    generating.value = true
    try {
      await generateBillForRoom(form.roomId, form.month)
      ElMessage.success('按房间生成账单成功')
      visible.value = false
      await onSuccess?.()
    } catch (e) {
      ElMessage.error(getErrorMessage(e, '按房间生成失败'))
    } finally {
      generating.value = false
    }
  }

  return {
    visible,
    generating,
    buildingOptions,
    floorOptions,
    roomOptions,
    form,
    formatRoomOption,
    onBuildingChange,
    onFloorChange,
    open,
    submit
  }
}
