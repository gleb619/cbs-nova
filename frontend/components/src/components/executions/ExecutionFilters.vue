<script setup lang="ts">
import { reactive } from 'vue'

import type { ExecutionFilters } from '../../types/execution'

const emit = defineEmits<{ filter: [filters: ExecutionFilters] }>()

const local = reactive<ExecutionFilters>({
  status: undefined,
  mode: undefined,
  entityName: '',
  from: '',
  to: '',
  correlationId: '',
})

function apply() {
  const payload: ExecutionFilters = {}
  if (local.status) payload.status = local.status
  if (local.mode) payload.mode = local.mode
  if (local.entityName) payload.entityName = local.entityName
  if (local.from) payload.from = local.from
  if (local.to) payload.to = local.to
  if (local.correlationId) payload.correlationId = local.correlationId
  emit('filter', payload)
}

function reset() {
  local.status = undefined
  local.mode = undefined
  local.entityName = ''
  local.from = ''
  local.to = ''
  local.correlationId = ''
  emit('filter', {})
}
</script>

<template>
  <div
    data-testid="execution-filters"
    class="bg-white border border-gray-200 rounded-lg p-4 space-y-3"
  >
    <div class="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-3">
      <div>
        <label for="filter-status" class="block text-xs font-medium text-gray-600 mb-1"
          >Status</label
        >
        <select
          id="filter-status"
          v-model="local.status"
          data-testid="execution-filter-status"
          class="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
          <option :value="undefined">Any</option>
          <option value="Pending">Pending</option>
          <option value="Running">Running</option>
          <option value="Completed">Completed</option>
          <option value="Failed">Failed</option>
          <option value="Compensated">Compensated</option>
          <option value="Stale">Stale</option>
        </select>
      </div>
      <div>
        <label for="filter-mode" class="block text-xs font-medium text-gray-600 mb-1">Mode</label>
        <select
          id="filter-mode"
          v-model="local.mode"
          data-testid="execution-filter-mode"
          class="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
          <option :value="undefined">Any</option>
          <option value="PREVIEW">PREVIEW</option>
          <option value="RUN">RUN</option>
          <option value="EXPLAIN">EXPLAIN</option>
        </select>
      </div>
      <div>
        <label for="filter-entity" class="block text-xs font-medium text-gray-600 mb-1"
          >Entity</label
        >
        <input
          id="filter-entity"
          v-model="local.entityName"
          type="text"
          placeholder="Entity name"
          data-testid="execution-filter-entity"
          class="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
      </div>
      <div>
        <label for="filter-from" class="block text-xs font-medium text-gray-600 mb-1">From</label>
        <input
          id="filter-from"
          v-model="local.from"
          type="date"
          class="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
      </div>
      <div>
        <label for="filter-to" class="block text-xs font-medium text-gray-600 mb-1">To</label>
        <input
          id="filter-to"
          v-model="local.to"
          type="date"
          class="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
      </div>
      <div>
        <label for="filter-correlation" class="block text-xs font-medium text-gray-600 mb-1"
          >Correlation ID</label
        >
        <input
          id="filter-correlation"
          v-model="local.correlationId"
          type="text"
          placeholder="corr-id"
          class="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
      </div>
    </div>
    <div class="flex gap-2">
      <button
        type="button"
        data-testid="execution-filter-apply"
        class="px-3 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
        @click="apply"
      >
        Apply
      </button>
      <button
        type="button"
        data-testid="execution-filter-reset"
        class="px-3 py-1.5 bg-gray-200 text-gray-700 text-sm rounded hover:bg-gray-300"
        @click="reset"
      >
        Reset
      </button>
    </div>
  </div>
</template>
