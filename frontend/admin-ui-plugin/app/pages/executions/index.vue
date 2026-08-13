<script setup lang="ts">
import type { ExecutionFilters } from '~/types'

const { executions, loading, loadExecutions, applyFilters, stalePollingIds } = useExecutions()

await loadExecutions()

function onFilter(f: ExecutionFilters) {
  applyFilters(f)
}

function onSelect(id: string) {
  navigateTo(`/executions/${id}`)
}
</script>

<template>
  <div class="p-6 space-y-4">
    <header class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">Executions</h1>
    </header>
    <ExecutionsExecutionFilters @filter="onFilter" />
    <ExecutionsExecutionList
      :executions="executions"
      :loading="loading"
      :stale-polling-ids="stalePollingIds"
      @select="onSelect"
    />
  </div>
</template>
