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
  testId?: string
}

const rows = computed<Row[]>(() => {
  const out: Row[] = [
    { key: 'Correlation ID', value: props.execution.correlationId ?? '—' },
    { key: 'Workflow ID', value: props.execution.workflowId ?? '—' },
    { key: 'Triggered by', value: props.execution.triggeredBy ?? '—', testId: 'metadata-triggered-by' },
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

// T302: clipboard feedback ("Copied!" flash) for copy buttons.
const copiedWorkflow = ref(false)
const copiedCorrelation = ref(false)
let copyResetHandle: ReturnType<typeof setTimeout> | null = null

async function copyValue(value: string, target: 'workflow' | 'correlation') {
  const clipboard = (globalThis as { navigator?: { clipboard?: { writeText: (s: string) => Promise<void> } } }).navigator?.clipboard
  if (!clipboard || !value) return
  try {
    await clipboard.writeText(value)
    if (target === 'workflow') {
      copiedWorkflow.value = true
    } else {
      copiedCorrelation.value = true
    }
    if (copyResetHandle) clearTimeout(copyResetHandle)
    copyResetHandle = setTimeout(() => {
      copiedWorkflow.value = false
      copiedCorrelation.value = false
      copyResetHandle = null
    }, 1500)
  } catch {
    // clipboard write can fail in non-secure contexts; silently no-op.
  }
}

function isWorkflowRow(row: Row): boolean {
  return row.key === 'Workflow ID'
}

function isCorrelationRow(row: Row): boolean {
  return row.key === 'Correlation ID'
}

function rowTestId(row: Row): string {
  return row.testId ?? `executions-metadata-field-${row.key}`
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
            :data-testid="rowTestId(row)"
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
                :aria-label="copiedWorkflow ? 'Workflow ID copied' : 'Copy workflow ID'"
                @click="copyValue(props.execution.workflowId!, 'workflow')"
              >
                {{ copiedWorkflow ? 'Copied!' : 'Copy' }}
              </button>
            </template>
            <template v-if="isCorrelationRow(row) && props.execution.correlationId">
              <button
                type="button"
                data-testid="correlation-id-copy"
                class="ml-2 inline-flex items-center text-xs text-gray-500 hover:text-gray-800 border border-gray-200 rounded px-1.5 py-0.5"
                :aria-label="copiedCorrelation ? 'Correlation ID copied' : 'Copy correlation ID'"
                @click="copyValue(props.execution.correlationId, 'correlation')"
              >
                {{ copiedCorrelation ? 'Copied!' : 'Copy' }}
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
