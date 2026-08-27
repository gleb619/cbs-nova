<script setup lang="ts">
import { computed, ref } from 'vue'

import type { CallKind, CallNode } from '../../types/runner'

const props = defineProps<{
  node: CallNode
  depth: number
}>()

const isOpen = ref(props.depth <= 1)

const kindBorderClasses: Record<CallKind, string> = {
  PROCESS: 'border-blue-500',
  TRANSACTION: 'border-green-500',
  HELPER: 'border-purple-500',
  FUNCTION: 'border-yellow-500',
}

const kindBadgeClasses: Record<CallKind, string> = {
  PROCESS: 'bg-blue-100 text-blue-700',
  TRANSACTION: 'bg-green-100 text-green-700',
  HELPER: 'bg-purple-100 text-purple-700',
  FUNCTION: 'bg-yellow-100 text-yellow-700',
}

const indentClass = computed(() => {
  const steps = Math.min(props.depth * 4, 16)
  return steps === 0 ? '' : `ml-${steps}`
})

const childCount = computed(() => props.node.children.length)
const externalCount = computed(() => props.node.externalCalls.length)
</script>

<template>
  <div
    class="border-l-2 pl-3"
    :class="[kindBorderClasses[node.kind], indentClass]"
    data-testid="call-tree-node"
  >
    <button
      type="button"
      class="w-full flex items-center gap-2 py-1 text-left hover:bg-neutral-100 rounded px-1"
      data-testid="call-tree-node-toggle"
      @click="isOpen = !isOpen"
    >
      <span
        class="text-xs font-semibold rounded px-1.5 py-0.5 uppercase tracking-wide"
        :class="kindBadgeClasses[node.kind]"
      >
        {{ node.kind }}
      </span>
      <span class="text-sm font-medium text-neutral-800">{{ node.name }}</span>
      <span v-if="node.success" class="text-success-600 text-sm" role="img" aria-label="success"
        >✓</span
      >
      <span v-else class="text-error-600 text-sm" role="img" aria-label="failure">✗</span>
    </button>

    <div v-if="!isOpen" class="pl-1 text-xs text-neutral-500">
      {{ childCount }}
      child{{ childCount === 1 ? '' : 'ren' }}
      <span v-if="externalCount > 0">
        · {{ externalCount }} external call{{ externalCount === 1 ? '' : 's' }}
      </span>
    </div>

    <div v-else class="flex flex-col gap-2 mt-1">
      <ExternalCallsBadge v-if="node.externalCalls.length > 0" :calls="node.externalCalls" />
      <CallTreeNode
        v-for="(child, idx) in node.children"
        :key="idx"
        :node="child"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>
