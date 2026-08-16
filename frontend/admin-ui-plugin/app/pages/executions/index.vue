<script setup lang="ts">
import { computed } from 'vue'
import { navigateTo } from 'nuxt/app'
import { useExecutions } from '@cbs/admin-ui-plugin/composables/useExecutions'
import { ExecutionsExecutionFilters, ExecutionsExecutionList } from '@cbs/components'

import type { ExecutionFilters } from '~/types'

const {
  executions,
  loading,
  loadExecutions,
  applyFilters,
  stalePollingIds,
  total,
  page,
  pageSize,
  setPage,
} = useExecutions()

await loadExecutions()

const pageCount = computed(() => Math.ceil(total.value / pageSize))

function onFilter(f: ExecutionFilters) {
  applyFilters(f)
}

function onSelect(id: string) {
  navigateTo(`/executions/${id}`)
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
    <ExecutionsExecutionList
      :executions="executions"
      :loading="loading"
      :stale-polling-ids="stalePollingIds"
      @select="onSelect"
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
  </div>
</template>
