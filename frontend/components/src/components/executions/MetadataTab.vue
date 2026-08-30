<script setup lang="ts">
import { computed, ref } from 'vue'

import type { Execution } from '../../types/execution'

const props = withDefaults(
  defineProps<{
    metadata: Record<string, unknown> | undefined
    execution: Execution
    /**
     * T302: optional pre-built Temporal Web UI deep-link for the execution's
     * workflow id. When present (non-null) and the execution has a workflowId,
     * the Workflow ID row renders a "View in Temporal" anchor + copy button.
     */
    workflowLink?: string | null
  }>(),
  { workflowLink: null },
)

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

// T302: clipboard feedback ("Copied!" flash) for the workflow-id copy button.
const copied = ref(false)
let copyResetHandle: ReturnType<typeof setTimeout> | null = null

async function copyWorkflowId() {
  const id = props.execution.workflowId
  if (!id) return
  const clipboard = (globalThis as { navigator?: { clipboard?: { writeText: (s: string) => Promise<void> } } }).navigator?.clipboard
  if (!clipboard) return
  try {
    await clipboard.writeText(id)
    copied.value = true
    if (copyResetHandle) clearTimeout(copyResetHandle)
    copyResetHandle = setTimeout(() => {
      copied.value = false
      copyResetHandle = null
    }, 1500)
  } catch {
    // clipboard write can fail in non-secure contexts; silently no-op.
  }
}

function isWorkflowRow(row: Row): boolean {
  return row.key === 'Workflow ID'
}
</script>

<template>
  <div data-testid="executions-metadata-tab" class="bg-white border border-gray-200 rounded-lg p-4">
    <table class="min-w-full text-sm">
      <caption class="sr-only">
        Execution metadata
      </caption>
      <tbody>
        <tr v-for="row in rows" :key="row.key" class="border-t border-gray-100 first:border-t-0">
          <th scope="row" class="text-left text-xs uppercase text-gray-500 py-2 pr-4 w-48">
            {{ row.key }}
          </th>
          <td
            :data-testid="`executions-metadata-field-${row.key}`"
            class="py-2 font-mono text-xs text-gray-800 break-all"
          >
            <span>{{ row.value }}</span>
            <template v-if="isWorkflowRow(row) && props.execution.workflowId">
              <a
                v-if="props.workflowLink"
                :href="props.workflowLink"
                target="_blank"
                rel="noopener noreferrer"
                data-testid="temporal-workflow-link"
                class="ml-2 inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 hover:underline font-mono"
              >
                View in Temporal ↗
              </a>
              <button
                type="button"
                data-testid="workflow-id-copy"
                class="ml-2 inline-flex items-center text-xs text-gray-500 hover:text-gray-800 border border-gray-200 rounded px-1.5 py-0.5"
                :aria-label="copied ? 'Workflow ID copied' : 'Copy workflow ID'"
                @click="copyWorkflowId"
              >
                {{ copied ? 'Copied!' : 'Copy' }}
              </button>
            </template>
          </td>
        </tr>
        <tr v-if="rows.length === 0">
          <td colspan="2" class="py-4 text-center text-sm text-gray-500">No metadata available.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>