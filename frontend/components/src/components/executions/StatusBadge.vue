<script setup lang="ts">
import type { ExecutionStatus } from '../../types/execution'

const props = withDefaults(defineProps<{ status: ExecutionStatus; polling?: boolean }>(), {
  polling: false,
})

const styles: Record<ExecutionStatus, string> = {
  Pending: 'bg-gray-200 text-gray-800',
  Running: 'bg-blue-500 text-white animate-pulse',
  Completed: 'bg-green-500 text-white',
  Failed: 'bg-red-500 text-white',
  Compensated: 'bg-orange-500 text-white',
  Stale: 'bg-warning-500 text-white',
}
</script>

<template>
  <span
    data-testid="status-badge"
    :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', styles[props.status]]"
  >
    {{ props.status }}
    <!--
      T199: when this row is being actively polled (i.e. status === 'Stale'
      and the backend has not yet transitioned the run out of Stale), show
      a small spinning indicator next to the label. The class is gated on
      `polling && status === 'Stale'` so the indicator only appears in
      the exact scenario the spec calls out — no false positives on
      other states.
    -->
    <span
      v-if="props.polling && props.status === 'Stale'"
      class="ml-1 inline-block w-3 h-3 border border-white border-t-transparent rounded-full animate-spin"
      aria-hidden="true"
      data-testid="stale-poll-indicator"
      >↻</span
    >
  </span>
</template>
