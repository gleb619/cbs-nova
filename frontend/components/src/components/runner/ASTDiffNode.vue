<script setup lang="ts">
import type { ASTDiffNode } from '../../composables/usePreviewDiff'

defineOptions({ name: 'ASTDiffNode' })

const props = defineProps<{
  node: ASTDiffNode
  depth?: number
}>()

const currentDepth = computed(() => props.depth ?? 0)
const isOpen = ref(currentDepth.value <= 1)

const statusBadge: Record<ASTDiffNode['status'], string> = {
  same: 'bg-gray-100 text-gray-600',
  added: 'bg-green-100 text-green-700',
  removed: 'bg-red-100 text-red-700',
  modified: 'bg-yellow-100 text-yellow-800',
}

const statusIcon: Record<ASTDiffNode['status'], string> = {
  same: '=',
  added: '+',
  removed: '−',
  modified: '~',
}

const statusLabel: Record<ASTDiffNode['status'], string> = {
  same: 'unchanged',
  added: 'added',
  removed: 'removed',
  modified: 'modified',
}

const indentClass = computed(() => {
  const steps = Math.min(currentDepth.value * 4, 16)
  return steps === 0 ? '' : `ml-${steps}`
})

const borderClass = computed(() => {
  switch (props.node.status) {
    case 'added':
      return 'border-green-400'
    case 'removed':
      return 'border-red-400'
    case 'modified':
      return 'border-yellow-400'
    default:
      return 'border-gray-200'
  }
})

const childSummary = computed(() => {
  const c = props.node.children.length
  if (c === 0) return ''
  return `${c} child${c === 1 ? '' : 'ren'}`
})
</script>

<template>
  <div :class="['border-l-2 pl-3', borderClass, indentClass]" data-testid="ast-diff-node" :data-status="node.status">
    <button
      type="button"
      class="w-full flex items-center gap-2 py-1 text-left hover:bg-neutral-50 rounded px-1"
      @click="isOpen = !isOpen"
    >
      <span
        class="text-xs font-mono rounded px-1.5 py-0.5 shrink-0 w-5 text-center"
        :class="statusBadge[node.status]"
        :aria-label="statusLabel[node.status]"
        data-testid="ast-diff-status"
      >
        {{ statusIcon[node.status] }}
      </span>
      <span class="text-xs font-semibold uppercase tracking-wide text-gray-600">
        {{ node.kind }}
      </span>
      <span class="text-sm font-medium text-neutral-800">{{ node.name }}</span>
      <span class="text-xs text-gray-400 ml-auto" v-if="!isOpen && childSummary">{{ childSummary }}</span>
    </button>

    <div v-if="isOpen" class="flex flex-col gap-1 mt-1">
      <div
        v-if="node.propertyChanges.length > 0"
        class="text-xs text-gray-600 ml-2"
        data-testid="ast-diff-property-changes"
      >
        <div
          v-for="change in node.propertyChanges"
          :key="change.key"
          class="flex items-start gap-2 font-mono"
        >
          <span class="text-gray-400">{{ change.key }}:</span>
          <span class="text-red-700 line-through break-all">{{ JSON.stringify(change.lhs) }}</span>
          <span class="text-gray-400">→</span>
          <span class="text-green-700 break-all">{{ JSON.stringify(change.rhs) }}</span>
        </div>
      </div>

      <ASTDiffNode
        v-for="(child, idx) in node.children"
        :key="idx"
        :node="child"
        :depth="currentDepth + 1"
      />
    </div>
  </div>
</template>
