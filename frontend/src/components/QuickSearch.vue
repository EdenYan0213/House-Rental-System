<template>
  <el-dialog
    v-model="visible"
    :show-close="false"
    width="600px"
    top="12vh"
    class="quick-search-dialog"
    @opened="focusInput"
  >
    <el-input
      ref="inputRef"
      v-model="keyword"
      placeholder="搜索房间号 / 租户姓名 / 合同号 / 手机号…"
      size="large"
      :prefix-icon="Search"
      clearable
      @input="onSearch"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="selectCurrent"
      @keydown.esc="visible = false"
    />

    <div class="qs-body">
      <div v-if="loading" class="qs-hint">搜索中…</div>
      <template v-else-if="items.length">
        <div
          v-for="(item, i) in items"
          :key="item.key"
          class="qs-item"
          :class="{ active: i === activeIndex }"
          @mouseenter="activeIndex = i"
          @click="go(item)"
        >
          <el-tag size="small" :type="item.tagType" effect="light" class="qs-tag">{{ item.tag }}</el-tag>
          <span class="qs-title">{{ item.title }}</span>
          <span class="qs-sub">{{ item.sub }}</span>
        </div>
      </template>
      <div v-else class="qs-hint">
        {{ keyword ? '没有匹配结果' : '输入关键词开始搜索' }}
      </div>
    </div>

    <div class="qs-footer">
      <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
      <span><kbd>Enter</kbd> 打开</span>
      <span><kbd>Esc</kbd> 关闭</span>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { getRooms, getTenants, getContracts } from '@/api'
import { debounce } from '@/utils/debounce'

const visible = ref(false)
const keyword = ref('')
const loading = ref(false)
const items = ref([])
const activeIndex = ref(0)
const inputRef = ref(null)

const router = useRouter()

const MAX_PER_TYPE = 5

function open() {
  visible.value = true
  keyword.value = ''
  items.value = []
  activeIndex.value = 0
}

function close() {
  visible.value = false
}

async function focusInput() {
  await nextTick()
  inputRef.value?.focus?.()
}

// 汇总四类结果，按类型分组展示
async function runSearch(kw) {
  const q = (kw || '').trim()
  if (!q) {
    items.value = []
    return
  }
  loading.value = true
  const result = []
  try {
    const [roomsRes, tenantsRes, contractsRes] = await Promise.allSettled([
      getRooms({ page: 0, size: 200 }),
      getTenants({ q }),
      getContracts({ q, page: 0, size: 50 })
    ])

    // 房间：前端按房间号过滤（后端不支持号码检索）
    if (roomsRes.status === 'fulfilled') {
      const data = roomsRes.value.data
      const rooms = Array.isArray(data) ? data : (data?.content || [])
      rooms
        .filter(r => String(r.roomNumber || '').toLowerCase().includes(q.toLowerCase()))
        .slice(0, MAX_PER_TYPE)
        .forEach(r => {
          result.push({
            key: `room-${r.id}`,
            tag: '房间',
            tagType: 'success',
            title: r.roomNumber,
            sub: `${r.layout || ''} ${r.area ? r.area + 'm²' : ''} · ${r.status || ''}`,
            to: `/buildings/${r.buildingId}/rooms/${r.id}/detail`
          })
        })
    }

    if (tenantsRes.status === 'fulfilled') {
      const list = tenantsRes.value.data?.content || tenantsRes.value.data || []
      list.slice(0, MAX_PER_TYPE).forEach(t => {
        result.push({
          key: `tenant-${t.id}`,
          tag: '租户',
          tagType: 'primary',
          title: t.name,
          sub: t.phone || '',
          to: `/tenants?q=${encodeURIComponent(t.name || '')}`
        })
      })
    }

    if (contractsRes.status === 'fulfilled') {
      const list = contractsRes.value.data?.content || contractsRes.value.data || []
      list.slice(0, MAX_PER_TYPE).forEach(c => {
        result.push({
          key: `contract-${c.id}`,
          tag: '合同',
          tagType: 'warning',
          title: c.contractNo,
          sub: `${c.tenantName || ''} · ${c.roomNumber || ''}`,
          to: `/contracts?highlight=${c.id}`
        })
      })
    }

    // 直接输入纯数字时，额外给出「查看该月账单」入口
    if (/^\d{4}-\d{2}$/.test(q)) {
      result.unshift({
        key: `bills-${q}`,
        tag: '账单',
        tagType: 'danger',
        title: `${q} 账单`,
        sub: '查看该月所有账单',
        to: `/bills?month=${q}`
      })
    }
  } finally {
    items.value = result
    activeIndex.value = 0
    loading.value = false
  }
}

const debouncedSearch = debounce(runSearch, 260)

function onSearch() {
  activeIndex.value = 0
  debouncedSearch(keyword.value)
}

function move(delta) {
  if (!items.value.length) return
  activeIndex.value = (activeIndex.value + delta + items.value.length) % items.value.length
}

function selectCurrent() {
  const item = items.value[activeIndex.value]
  if (item) go(item)
}

function go(item) {
  close()
  router.push(item.to)
}

watch(visible, v => {
  if (!v) debouncedSearch.cancel?.()
})

defineExpose({ open, close })
</script>

<style scoped>
.quick-search-dialog :deep(.el-dialog__header) {
  display: none;
}

.quick-search-dialog :deep(.el-dialog__body) {
  padding: 16px 16px 8px;
}

.qs-body {
  margin-top: 12px;
  max-height: 380px;
  overflow-y: auto;
}

.qs-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.qs-item.active {
  background: var(--el-fill-color-light);
}

.qs-tag {
  flex-shrink: 0;
}

.qs-title {
  font-weight: 600;
  color: var(--text-main);
  min-width: 90px;
}

.qs-sub {
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qs-hint {
  padding: 28px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

.qs-footer {
  display: flex;
  gap: 16px;
  padding: 10px 4px 2px;
  border-top: 1px solid var(--border-color);
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

kbd {
  display: inline-block;
  padding: 1px 6px;
  margin: 0 2px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-family: inherit;
  font-size: 11px;
}
</style>
