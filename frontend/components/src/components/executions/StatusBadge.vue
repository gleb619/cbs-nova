<script setup lang="ts">
import type { ExecutionStatus } from '../../types/execution'

const props = defineProps<{ status: ExecutionStatus }>()

const styles: Record<ExecutionStatus, string> = {
  Pending: 'bg-gray-200 text-gray-800',
  Running: 'bg-blue-500 text-white animate-pulse',
  Completed: 'bg-green-500 text-white',
  Failed: 'bg-red-500 text-white',
  Compensated: 'bg-orange-500 text-white',
}
</script>

<template>
  <span
    :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', styles[props.status]]"
  >
    {{ props.status }}
  </span>
</template>
