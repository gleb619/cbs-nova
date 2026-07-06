<script setup lang="ts">
import type { ExecutionDetail } from '~/types/execution'

const props = defineProps<{ execution: ExecutionDetail }>()

function formatDate(s?: string) {
  if (!s) return '—'
  return new Date(s).toLocaleString()
}

function formatDuration(ms?: number) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
</script>

<template>
  <div class="bg-white border border-gray-200 rounded-lg p-4">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold text-gray-900">
          {{ props.execution.entity }}
          <span class="text-sm font-normal text-gray-500">({{ props.execution.entityType }})</span>
        </h1>
        <div class="mt-1 flex flex-wrap items-center gap-3 text-xs text-gray-600">
          <span>Mode: <span class="font-mono">{{ props.execution.mode }}</span></span>
          <span>Started: {{ formatDate(props.execution.startedAt) }}</span>
          <span>Duration: {{ formatDuration(props.execution.duration) }}</span>
        </div>
      </div>
      <ExecutionsStatusBadge :status="props.execution.status" />
    </div>
    <div class="mt-3 grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
      <div>
        <span class="text-gray-500">Correlation ID: </span>
        <span class="font-mono">{{ props.execution.correlationId ?? '—' }}</span>
      </div>
      <div>
        <span class="text-gray-500">Workflow ID: </span>
        <span class="font-mono">{{ props.execution.workflowId ?? '—' }}</span>
      </div>
    </div>
  </div>
</template>
