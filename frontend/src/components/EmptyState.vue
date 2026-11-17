<template>
  <div class="empty-state" :class="{ compact }">
    <el-icon class="empty-icon" :size="compact ? 34 : 52">
      <component :is="iconComponent" />
    </el-icon>
    <div class="empty-title">{{ title }}</div>
    <div v-if="description" class="empty-desc">{{ description }}</div>
    <div v-if="showActions" class="empty-actions">
      <el-button v-if="primaryText" type="primary" :icon="Plus" @click="$emit('primary')">
        {{ primaryText }}
      </el-button>
      <el-button v-if="secondaryText" @click="$emit('secondary')">
        {{ secondaryText }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Plus, Document, Box, Bell, Tools, User, Wallet, Money } from '@element-plus/icons-vue'

/**
 * 统一空态：替代默认的「暂无数据」，提供图标、说明与引导操作。
 *
 * 用法：
 *   <EmptyState
 *     icon="document" title="还没有账单"
 *     description="生成账单后即可在这里收款与管理"
 *     primary-text="生成账单" @primary="handleGenerate"
 *   />
 */
const props = defineProps({
  // document | box | bell | tools | user | wallet | money
  icon: { type: String, default: 'box' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  primaryText: { type: String, default: '' },
  secondaryText: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})

defineEmits(['primary', 'secondary'])

const ICONS = { document: Document, box: Box, bell: Bell, tools: Tools, user: User, wallet: Wallet, money: Money }

const iconComponent = computed(() => ICONS[props.icon] || Box)
const showActions = computed(() => !!props.primaryText || !!props.secondaryText)
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 20px;
  text-align: center;
}

.empty-state.compact {
  padding: 28px 16px;
}

.empty-icon {
  color: var(--text-muted);
  opacity: 0.45;
  margin-bottom: 14px;
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 6px;
}

.empty-desc {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-muted);
  max-width: 380px;
}

.empty-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
</style>
