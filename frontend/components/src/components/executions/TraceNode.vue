<script setup lang="ts">
import type { TraceStep } from '../../types/execution'

const props = defineProps<{ step: TraceStep; depth: number }>()

const iconMap: Record<string, string> = {
  Process: '🔷',
  Transaction: '🔶',
  Function: '🔹',
  Helper: '🛠',
}

function formatDuration(ms?: number) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
</script>

<template>
  <div
    :data-testid="`execution-trace-node-${props.step.id}`"
    :style="{ marginLeft: `${props.depth * 1.25}rem` }"
    :class="['flex items-center gap-2 py-1.5 px-2 rounded text-sm',
                props.step.isCompensation ? 'border border-dashed border-orange-300 bg-orange-50' : 'hover:bg-gray-50']"
  >
    <span class="text-base">{{ iconMap[props.step.stepType] ?? '•' }}</span>
    <span
      :class="['font-mono text-xs px-1.5 py-0.5 rounded',
                   props.step.isCompensation ? 'text-orange-700' : 'text-gray-600 bg-gray-100']"
      :data-testid="`execution-trace-node-step-type-${props.step.id}`"
    >
      {{ props.step.stepType }}
    </span>
    <span class="font-medium text-gray-900 truncate" :title="props.step.name"
      >{{ props.step.name }}</span
    >
    <ExecutionsStatusBadge :status="props.step.status" />
    <span
      class="ml-auto text-xs text-gray-500"
      :data-testid="`execution-trace-node-duration-${props.step.id}`"
      >{{ formatDuration(props.step.duration) }}</span
    >
  </div>
</template>
