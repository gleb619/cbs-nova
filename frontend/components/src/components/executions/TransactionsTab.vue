<script setup lang="ts">
import { ref } from 'vue'
import type { TransactionExecutionDto } from '../../types/execution'
import ErrorBanner from '../ErrorBanner.vue'

const props = defineProps<{
  transactions: TransactionExecutionDto[] | undefined
  loading: boolean
  error: string | null
}>()

const expanded = ref<Set<number>>(new Set())

function toggle(idx: number) {
  if (expanded.value.has(idx)) {
    expanded.value.delete(idx)
  } else {
    expanded.value.add(idx)
  }
  expanded.value = new Set(expanded.value)
}

function formatTime(s: string): string {
  return new Date(s).toLocaleString()
}

function formatJson(v: unknown): string {
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}
</script>

<template>
  <div data-testid="executions-transactions-tab">
    <div
      v-if="props.loading"
      class="text-center py-12 text-sm text-gray-500"
      data-testid="executions-transactions-loading"
    >
      Loading transactions…
    </div>
    <ErrorBanner v-else-if="props.error" :message="props.error" />
    <div
      v-else-if="!props.transactions || props.transactions.length === 0"
      class="bg-white border border-gray-200 rounded-lg p-12 text-center text-sm text-gray-500"
      data-testid="executions-transactions-empty"
    >
      No transactions recorded for this run.
    </div>
    <div v-else class="space-y-3">
      <div
        v-for="(tx, idx) in props.transactions"
        :key="idx"
        data-testid="executions-transaction-row"
        class="bg-white border border-gray-200 rounded-lg p-4"
      >
        <div class="flex flex-wrap items-center gap-3 text-sm">
          <span class="font-semibold text-gray-800">{{ tx.transactionName }}</span>
          <span class="text-xs text-gray-500">{{ formatTime(tx.executedAt) }}</span>
          <button
            v-if="tx.input !== undefined"
            type="button"
            class="ml-auto text-xs text-blue-600 hover:underline"
            data-testid="executions-transaction-toggle-input"
            @click="toggle(idx)"
          >
            {{ expanded.has(idx) ? 'Hide input' : 'Show input' }}
          </button>
        </div>
        <pre
          v-if="expanded.has(idx)"
          data-testid="executions-transaction-input"
          class="mt-2 text-xs bg-gray-50 border border-gray-200 rounded p-3 overflow-auto max-h-64 whitespace-pre"
        ><code>{{ formatJson(tx.input) }}</code></pre>
      </div>
    </div>
  </div>
</template>
