<script setup lang="ts">
import { computed } from 'vue'

import type { RunnerStatus } from '../../types/runner'

const props = defineProps<{ status: RunnerStatus }>()

const styleMap: Record<RunnerStatus, { label: string; classes: string; animate: boolean }> = {
  idle: { label: 'Idle', classes: 'bg-gray-200 text-gray-700', animate: false },
  loading: { label: 'Loading', classes: 'bg-yellow-100 text-yellow-800', animate: true },
  running: { label: 'Running', classes: 'bg-blue-100 text-blue-800', animate: true },
  success: { label: 'Success', classes: 'bg-green-100 text-green-800', animate: false },
  failed: { label: 'Failed', classes: 'bg-red-100 text-red-800', animate: false },
}

const current = computed(() => styleMap[props.status])
</script>

<template>
  <span
    :class="['inline-flex items-center gap-2 px-2.5 py-1 rounded-full text-xs font-medium', current.classes]"
    role="status"
    data-testid="status-indicator"
    :aria-label="`Runner status: ${current.label}`"
  >
    <span
      :class="['inline-block w-2 h-2 rounded-full', current.animate ? 'animate-pulse' : '', current.classes.replace('100', '500').split(' ')[0]]"
    />
    <span data-testid="status-indicator-text">{{ current.label }}</span>
  </span>
</template>
