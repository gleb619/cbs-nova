<script setup lang="ts">
import type { Execution } from '../../types/execution'

const props = defineProps<{
  metadata: Record<string, unknown> | undefined
  execution: Execution
}>()

interface Row {
  key: string
  value: string
}

const rows = computed<Row[]>(() => {
  const out: Row[] = [
    { key: 'Correlation ID', value: props.execution.correlationId ?? '—' },
    { key: 'Workflow ID', value: props.execution.workflowId ?? '—' },
    { key: 'Mode', value: props.execution.mode },
    { key: 'Entity Type', value: props.execution.entityType },
    { key: 'Retries', value: String(props.execution.retries ?? 0) },
  ]
  if (props.metadata) {
    for (const [k, v] of Object.entries(props.metadata)) {
      if (k === 'version' || k === 'retrySettings' || k === 'retryPolicy') {
        out.push({ key: k, value: typeof v === 'string' ? v : JSON.stringify(v) })
      }
    }
  }
  return out
})
</script>

<template>
  <div class="bg-white border border-gray-200 rounded-lg p-4">
    <table class="min-w-full text-sm">
      <tbody>
        <tr v-for="row in rows" :key="row.key" class="border-t border-gray-100 first:border-t-0">
          <th class="text-left text-xs uppercase text-gray-500 py-2 pr-4 w-48">{{ row.key }}</th>
          <td class="py-2 font-mono text-xs text-gray-800 break-all">{{ row.value }}</td>
        </tr>
        <tr v-if="rows.length === 0">
          <td colspan="2" class="py-4 text-center text-sm text-gray-500">No metadata available.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
