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
} = useExecutions()

await loadExecutions()
startListPolling()

const pageCount = computed(() => Math.ceil(total.value / pageSize))

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
