<script setup lang="ts">
import { useExecutions } from '@cbs/admin-ui-plugin/composables/useExecutions'
import {
  ErrorBanner,
  ExecutionsCancelConfirmationModal,
  ExecutionsExecutionFilters,
  ExecutionsExecutionList,
} from '@cbs/components'
import { navigateTo } from 'nuxt/app'
import { computed, ref } from 'vue'

import type { ExecutionFilters } from '~/types'

const {
  executions,
  filters,
  loading,
  error,
  loadExecutions,
  applyFilters,
  stalePollingIds,
  cancellingIds,
  isCancelling,
  cancelExecution,
  total,
  page,
  pageSize,
  setPage,
  startListPolling,
  stopListPolling,
} = useExecutions()

await loadExecutions()
// Polling is user-controlled via isPollingEnabled toggle; default off.

const pageCount = computed(() => Math.ceil(total.value / pageSize))
const isPollingEnabled = ref(false)
function onTogglePolling(enabled: boolean) {
  isPollingEnabled.value = enabled
  if (enabled) startListPolling()
  else stopListPolling()
}

const exportUrl = computed(() => {
  const params = new URLSearchParams()
  const f = filters.value as ExecutionFilters
  if (f.status) params.set('status', f.status)
  if (f.mode) params.set('mode', f.mode)
  if (f.entityName) params.set('entityName', f.entityName)
  if (f.correlationId) params.set('correlationId', f.correlationId)
  const query = params.toString()
  return query ? `/api/v1/executions/export?${query}` : '/api/v1/executions/export'
})

const cancelTargetId = ref<string | null>(null)
const cancelError = ref<string | null>(null)
const showCancelModal = computed(() => cancelTargetId.value !== null)
const cancellingTarget = computed(() =>
  cancelTargetId.value ? isCancelling(cancelTargetId.value) : false,
)

function onFilter(f: ExecutionFilters) {
  applyFilters(f)
}

function onSelect(id: string) {
  navigateTo(`/executions/${id}`)
}

function onCancel(id: string) {
  cancelError.value = null
  cancelTargetId.value = id
}

function onCancelModalDismiss() {
  if (cancellingTarget.value) return
  cancelTargetId.value = null
}

async function onCancelConfirm() {
  const id = cancelTargetId.value
  if (!id) return
  try {
    await cancelExecution(id)
    cancelTargetId.value = null
    cancelError.value = null
  } catch (err) {
    cancelError.value = (err as Error)?.message ?? 'Failed to cancel execution'
  }
}

function onDismissCancelError() {
  cancelError.value = null
}

function prevPage() {
  if (page.value > 1) setPage(page.value - 1)
}

function nextPage() {
  if (page.value < pageCount.value) setPage(page.value + 1)
}
</script>

<template>
  <div class="p-6 space-y-4">
    <header class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-neutral-900">Executions</h1>
      <div class="flex items-center gap-4">
        <label class="inline-flex items-center cursor-pointer select-none">
          <input
            type="checkbox"
            :checked="isPollingEnabled"
            class="sr-only peer"
            data-testid="executions-live-polling-toggle"
            @change="onTogglePolling(($event.target as HTMLInputElement).checked)"
          >
          <div
            class="relative w-11 h-6 bg-neutral-300 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-300 rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-neutral-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"
          ></div>
          <span class="ms-3 text-sm font-medium text-neutral-900"> Live updates </span>
        </label>
        <a
          :href="exportUrl"
          download
          data-testid="executions-export-csv"
          class="px-3 py-1.5 rounded border text-sm font-medium transition-colors border-neutral-300 bg-white text-neutral-800 hover:bg-neutral-100"
        >
          Export CSV
        </a>
      </div>
    </header>
    <ExecutionsExecutionFilters @filter="onFilter" />
    <ErrorBanner v-if="error" :message="error" @retry="loadExecutions" />
    <ErrorBanner
      v-if="cancelError"
      :message="cancelError"
      :retry-label="'Dismiss'"
      @retry="onDismissCancelError"
    />
    <ExecutionsExecutionList
      :executions="executions"
      :loading="loading"
      :stale-polling-ids="stalePollingIds"
      :cancelling-ids="cancellingIds"
      @select="onSelect"
      @cancel="onCancel"
    />
    <div
      v-if="total > 0"
      class="flex items-center justify-between gap-4 pt-2"
      data-testid="execution-pagination"
    >
      <span class="text-sm text-neutral-700">
        Page {{ page }} of {{ pageCount }} ({{ total }}
        total)
      </span>
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="px-3 py-1.5 rounded border text-sm font-medium transition-colors"
          :class="
            page <= 1
              ? 'border-neutral-200 bg-neutral-100 text-neutral-500 cursor-not-allowed'
              : 'border-neutral-300 bg-white text-neutral-800 hover:bg-neutral-100'
          "
          :disabled="page <= 1"
          @click="prevPage"
        >
          Previous
        </button>
        <button
          type="button"
          class="px-3 py-1.5 rounded border text-sm font-medium transition-colors"
          :class="
            page >= pageCount
              ? 'border-neutral-200 bg-neutral-100 text-neutral-500 cursor-not-allowed'
              : 'border-neutral-300 bg-white text-neutral-800 hover:bg-neutral-100'
          "
          :disabled="page >= pageCount"
          @click="nextPage"
        >
          Next
        </button>
      </div>
    </div>

    <ExecutionsCancelConfirmationModal
      :show="showCancelModal"
      :execution-id="cancelTargetId ?? undefined"
      :busy="cancellingTarget"
      @confirm="onCancelConfirm"
      @cancel="onCancelModalDismiss"
    />
  </div>
</template>
