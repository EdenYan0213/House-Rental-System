<template>
  <div class="mobile-page" v-loading="loading">
    <div class="head">
      <div class="title">楼栋信息</div>
      <el-button text @click="load">刷新</el-button>
    </div>

    <el-empty v-if="!loading && buildings.length === 0" description="暂无楼栋" />

    <el-card v-for="item in buildings" :key="item.id" class="panel" shadow="never" @click="openRooms(item)">
      <div class="row top">
        <div class="name">{{ item.name || '-' }}</div>
        <el-tag size="small" type="info">查看房间</el-tag>
      </div>
      <div class="muted">地址：{{ item.address || '-' }}</div>
      <div class="muted">水价：{{ money(item.waterUnitPrice) }} / 电价：{{ money(item.electricUnitPrice) }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBuildings } from '@/api'
import { unwrapList } from '@/utils/mobileApi'

const router = useRouter()
const loading = ref(false)
const buildings = ref([])

function money(v) {
  return Number(v || 0).toFixed(2)
}

function openRooms(building) {
  if (!building?.id) return
  router.push(`/mobile/buildings/${building.id}/rooms`)
}

async function load() {
  loading.value = true
  try {
    const res = await getBuildings()
    buildings.value = unwrapList(res.data)
  } catch (_) {
    ElMessage.error('加载楼栋失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mobile-page {
  padding: 14px;
  max-width: 760px;
  margin: 0 auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
}

.panel {
  margin-bottom: 10px;
  border: 1px solid #e8efff;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.top {
  margin-bottom: 6px;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.muted {
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 4px;
}
</style>
