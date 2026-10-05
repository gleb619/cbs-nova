<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  node: Record<string, unknown>
  depth: number
}>()

const isOpen = ref(props.depth <= 1)

const name = computed(() => (typeof props.node.name === 'string' ? props.node.name : ''))
const description = computed(() =>
  typeof props.node.description === 'string' ? props.node.description : undefined,
)

function childNodes(): Record<string, unknown>[] {
  const children = props.node.children
  return Array.isArray(children)
    ? children.filter((c): c is Record<string, unknown> => typeof c === 'object' && c !== null)
    : []
}

function callCount(): number {
  const counts =
    typeof props.node.callCounts === 'object' && props.node.callCounts !== null
      ? (props.node.callCounts as Record<string, unknown>)
      : {}
  return Object.values(counts).reduce((sum, n) => sum + (typeof n === 'number' ? n : 0), 0)
}

const indentStyle = computed(() => ({
  marginLeft: `${Math.min(props.depth * 12, 48)}px`,
}))
</script>

<template>
  <div
    class="border-l border-line"
    data-testid="hierarchy-report-node"
    :style="indentStyle"
  >
    <button
      type="button"
      class="w-full flex items-center gap-2 py-1 px-1 text-left hover:bg-surface rounded"
      data-testid="hierarchy-report-node-toggle"
      @click="isOpen = !isOpen"
    >
      <span class="text-xs text-ink-muted w-3">{{ isOpen ? '▾' : '▸' }}</span>
      <span class="text-sm font-medium text-ink">{{ name }}</span>
      <span v-if="description" class="text-xs text-ink-muted truncate">{{ description }}</span>
      <span
        class="ml-auto text-xs rounded px-1.5 py-0.5 bg-accent-500/10 text-accent-600"
        data-testid="hierarchy-report-node-count"
      >
        {{ callCount() }} call{{ callCount() === 1 ? '' : 's' }}
      </span>
    </button>

    <div v-if="isOpen" class="flex flex-col gap-1 mt-1 pr-1">
      <HierarchyReportNode
        v-for="(child, idx) in childNodes()"
        :key="idx"
        :node="child"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>
