<template>
  <div>
    <a
      v-if="isExternalLinkNode(item) && externalUrl"
      class="toc-node is-external"
      :href="externalUrl"
      target="_blank"
      rel="noopener noreferrer"
      @click.stop
    >
      <span class="toc-arrow-placeholder"></span>
      <span class="toc-title">{{ item.title }}</span>
      <el-icon class="toc-external-icon"><Link /></el-icon>
    </a>
    <el-tooltip
      v-else-if="item.available === false && unavailableTooltip"
      :content="unavailableTooltip"
      placement="right"
      :show-after="400"
    >
      <div
        class="toc-node"
        :class="nodeClass"
        @click="handleClick"
      >
        <span v-if="hasChildren" class="toc-arrow" :class="{ expanded: expanded }">
          <el-icon><ArrowRight /></el-icon>
        </span>
        <span v-else class="toc-arrow-placeholder"></span>
        <span class="toc-title">{{ item.title }}</span>
        <el-tag v-if="unavailableBadge" size="small" type="info" class="toc-badge">{{ unavailableBadge }}</el-tag>
      </div>
    </el-tooltip>
    <div
      v-else
      class="toc-node"
      :class="nodeClass"
      @click="handleClick"
    >
      <span v-if="hasChildren" class="toc-arrow" :class="{ expanded: expanded }">
        <el-icon><ArrowRight /></el-icon>
      </span>
      <span v-else class="toc-arrow-placeholder"></span>
      <span class="toc-title">{{ item.title }}</span>
    </div>
    <transition name="expand">
      <ul v-if="hasChildren && expanded" class="toc-children">
        <li v-for="child in item.children" :key="child.id ?? child.localPath ?? child.title" class="toc-item">
          <toc-recursive :item="child" :active-id="activeId" @select="$emit('select', $event)" />
        </li>
      </ul>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ArrowRight, Link } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { TocItem } from '@/types'
import { tocNodeContainsId } from '@/utils/tocNavigation'
import {
  isNavFolderNode,
  isExternalLinkNode,
  resolveExternalUrl,
  openExternalTocLink,
  resolveUnavailableReason,
  unavailableReasonLabel,
  unavailableReasonMessage,
} from '@/utils/tocUnavailable'

const props = defineProps<{
  item: TocItem
  activeId: number | null
}>()

const emit = defineEmits<{
  select: [item: TocItem]
}>()

const expanded = ref(false)
const hasChildren = computed(() => props.item.children && props.item.children.length > 0)
const externalUrl = computed(() => resolveExternalUrl(props.item))

const unavailableReason = computed(() =>
  props.item.available === false ? resolveUnavailableReason(props.item) : null,
)

const unavailableTooltip = computed(() =>
  unavailableReason.value ? unavailableReasonMessage(props.item, unavailableReason.value) : '',
)

const unavailableBadge = computed(() =>
  unavailableReason.value ? unavailableReasonLabel(unavailableReason.value) : '',
)

const nodeClass = computed(() => ({
  'is-active': props.activeId === props.item.id,
  'has-children': hasChildren.value,
  'is-unavailable': props.item.available === false && !isNavFolderNode(props.item) && !isExternalLinkNode(props.item),
  'is-nav-folder': isNavFolderNode(props.item),
  'is-external': isExternalLinkNode(props.item),
}))

watch(
  () => props.activeId,
  (activeId) => {
    if (tocNodeContainsId(props.item, activeId)) {
      expanded.value = true
    }
  },
  { immediate: true },
)

const handleClick = () => {
  if (isExternalLinkNode(props.item)) {
    openExternalTocLink(props.item)
    return
  }

  if (hasChildren.value) {
    expanded.value = !expanded.value
  }

  if (props.item.available === false) {
    const reason = unavailableReason.value
    if (reason === 'nav-folder') {
      return
    }
    if (reason) {
      ElMessage.info(unavailableReasonMessage(props.item, reason))
    }
    return
  }

  emit('select', props.item)
}
</script>

<style scoped>
.toc-node.is-external {
  color: var(--primary-color);
  opacity: 0.92;
  text-decoration: none;
}

.toc-external-icon {
  flex-shrink: 0;
  font-size: 12px;
  opacity: 0.75;
}

.toc-node.is-external:hover {
  background: var(--input-bg);
}

.toc-node.is-unavailable {
  color: var(--text-secondary);
  opacity: 0.55;
  cursor: help;
}

.toc-node.is-unavailable:hover {
  background: transparent;
  color: var(--text-secondary);
}

.toc-node.is-nav-folder {
  opacity: 0.85;
}

.toc-node.is-nav-folder:hover {
  background: var(--input-bg);
  color: var(--primary-color);
}

.toc-node {
  display: flex;
  align-items: center;
  padding: 6px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary);
  transition: all 0.2s;
  border-left: 3px solid transparent;
  user-select: none;
  gap: 4px;
}

.toc-node:hover {
  background: var(--input-bg);
  color: var(--primary-color);
}

.toc-node.is-active {
  background: var(--input-bg);
  color: var(--primary-color);
  border-left-color: var(--primary-color);
  font-weight: 500;
}

.toc-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.toc-arrow.expanded {
  transform: rotate(90deg);
}

.toc-arrow-placeholder {
  width: 16px;
  flex-shrink: 0;
}

.toc-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toc-badge {
  flex-shrink: 0;
  transform: scale(0.85);
}

.toc-children {
  list-style: none;
  margin: 0;
  padding-left: 12px;
}

.toc-item {
  list-style: none;
}

.toc-children .toc-node {
  font-size: 12px;
  padding: 5px 16px;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 2000px;
}
</style>
