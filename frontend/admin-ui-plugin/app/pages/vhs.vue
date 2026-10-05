<script setup lang="ts">
import { useVhsTapes } from '@cbs/admin-ui-plugin/composables/useVhsTapes'
import { useToast, VhsTapePanel } from '@cbs/components'
import type { VhsLoadTestForm, VhsLoadTestReport } from '@cbs/components/types'
import { onMounted } from 'vue'

const {
  tapes,
  total,
  offset,
  limit,
  hasPrev,
  hasNext,
  loading,
  error,
  filters,
  detail,
  detailLoading,
  detailError,
  deleting,
  loadTestRunning,
  loadTestReport,
  loadTestError,
  load,
  applyFilters,
  clearFilters,
  prev,
  next,
  setLimit,
  openDetail,
  closeDetail,
  deleteTape,
  runLoadTest,
} = useVhsTapes()

const toast = useToast()

onMounted(() => {
  void load()
})

async function onDelete(runId: string) {
  try {
    await deleteTape(runId)
    toast.success('Tape deleted')
  } catch {
    toast.error(error.value || 'Failed to delete tape')
  }
}

async function onLoadTest(payload: VhsLoadTestForm) {
  const report: VhsLoadTestReport | null = await runLoadTest(payload)
  if (report) {
    toast.success(`Load test complete: ${report.tapesLoaded} tapes, ${report.totalCalls} calls`)
  } else if (loadTestError.value) {
    toast.error(loadTestError.value)
  }
}
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col" data-testid="vhs-page">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">VHS Tapes</h1>
      <p class="text-sm text-neutral-600">
        Recorded execution tapes for replay, inspection and load testing.
      </p>
    </header>

    <VhsTapePanel
      :tapes="tapes"
      :total="total"
      :offset="offset"
      :limit="limit"
      :has-prev="hasPrev"
      :has-next="hasNext"
      :loading="loading"
      :error="error"
      :filters="filters"
      :detail="detail"
      :detail-loading="detailLoading"
      :detail-error="detailError"
      :deleting="deleting"
      :load-test-running="loadTestRunning"
      :load-test-report="loadTestReport"
      :load-test-error="loadTestError"
      @refresh="load"
      @apply-filters="applyFilters"
      @clear-filters="clearFilters"
      @prev="prev"
      @next="next"
      @update:limit="setLimit"
      @open-detail="openDetail"
      @close-detail="closeDetail"
      @delete="onDelete"
      @loadtest="onLoadTest"
    />
  </div>
</template>
