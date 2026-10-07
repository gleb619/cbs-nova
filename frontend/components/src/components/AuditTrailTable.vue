<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AuditDto } from '../types/audit'
import ErrorBanner from './ErrorBanner.vue'

const props = defineProps<{
  entries: AuditDto[]
  loading: boolean
  error: string | null
  actionFilter: string
  total: number
  offset: number
  limit: number
  hasPrev: boolean
  hasNext: boolean
}>()

const emit = defineEmits<{
  'update:actionFilter': [value: string]
  applyActionFilter: []
  clearActionFilter: []
  prev: []
  next: []
  refresh: []
}>()

const expandedIds = ref<Set<number>>(new Set())

const rangeText = computed(() => {
  if (props.entries.length === 0) return ''
  const start = props.offset + 1
  const end = props.offset + props.entries.length
  return `${start}–${end} of ${props.total}`
})

const emptyMessage = computed(() => {
  if (props.actionFilter.trim()) return 'No audit entries match the current action filter.'
  return 'No audit entries recorded.'
})

function updateActionFilter(value: string) {
  emit('update:actionFilter', value)
}

function onApplyActionFilter() {
  emit('applyActionFilter')
}

function onClearActionFilter() {
  emit('clearActionFilter')
}

function onRefresh() {
  emit('refresh')
}

function onPrev() {
  emit('prev')
}

function onNext() {
  emit('next')
}

function formatRelativeTime(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime()
  if (Number.isNaN(elapsed)) return iso
  const seconds = Math.max(0, Math.round(elapsed / 1000))
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.round(hours / 24)}d ago`
}

function outcomeClass(outcome: string): string {
  const normalized = outcome.trim().toUpperCase()
  if (normalized === 'SUCCESS') return 'bg-green-100 text-green-800'
  if (normalized === 'FAILURE' || normalized === 'FAILED' || normalized === 'ERROR')
    return 'bg-red-100 text-red-800'
  return 'bg-gray-100 text-gray-800'
}

function _formatDetails(details: string | null | undefined): string {
  if (!details) return ''
  try {
    return JSON.stringify(JSON.parse(details), null, 2)
  } catch {
    return details
  }
}

function isExpanded(id: number): boolean {
  return expandedIds.value.has(id)
}

function toggleExpanded(id: number) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

function hasDetails(entry: AuditDto): boolean {
  return Boolean(entry.detailsJson)
}
</script>

<template>
  <div class="flex flex-col h-full" data-testid="audit-trail-panel">
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="text-base font-semibold text-gray-900">Audit trail</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Control-plane actions recorded by the engine, newest first.
      </p>
    </div>

    <div class="px-4 py-2 border-b border-gray-200 flex flex-wrap items-center gap-2">
      <form class="flex items-center gap-1" @submit.prevent="onApplyActionFilter">
        <label for="audit-action-filter" class="sr-only">Filter by action</label>
        <input
          id="audit-action-filter"
          :value="actionFilter"
          type="text"
          placeholder="Filter by action"
          class="px-2 py-1 text-sm border border-gray-300 rounded"
          data-testid="audit-trail-filter-action"
          @input="updateActionFilter(($event.target as HTMLInputElement).value)"
        >
        <button
          type="submit"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="audit-trail-filter-apply"
        >
          Apply
        </button>
        <button
          v-if="actionFilter.trim()"
          type="button"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="audit-trail-filter-clear"
          @click="onClearActionFilter"
        >
          Clear
        </button>
      </form>
      <button
        type="button"
        class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
        :disabled="loading"
        data-testid="audit-trail-refresh"
        @click="onRefresh"
      >
        Refresh
      </button>
    </div>

    <div class="flex-1 overflow-auto">
      <div v-if="loading" class="px-4 py-3 text-sm text-gray-500" data-testid="audit-trail-loading">
        Loading audit trail…
      </div>
      <div v-else-if="error" class="px-4 py-3" data-testid="audit-trail-error">
        <ErrorBanner :message="error" retry-label="Retry" @retry="onRefresh" />
      </div>
      <div
        v-else-if="entries.length === 0"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="audit-trail-empty"
      >
        {{ emptyMessage }}
      </div>
      <table v-else class="w-full text-sm" data-testid="audit-trail-table">
        <thead>
          <tr class="text-left text-xs uppercase text-gray-500 border-b border-gray-200">
            <th class="px-4 py-2 font-semibold">When</th>
            <th class="px-2 py-2 font-semibold">Actor</th>
            <th class="px-2 py-2 font-semibold">Action</th>
            <th class="px-2 py-2 font-semibold">Target</th>
            <th class="px-2 py-2 font-semibold">Correlation</th>
            <th class="px-2 py-2 font-semibold">Outcome</th>
            <th class="px-2 py-2 font-semibold">Details</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <template v-for="entry in entries" :key="entry.id">
            <tr class="align-top" data-testid="audit-trail-row">
              <td class="px-4 py-2 whitespace-nowrap text-gray-500" :title="entry.occurredAt">
                {{ formatRelativeTime(entry.occurredAt) }}
              </td>
              <td class="px-2 py-2 text-gray-900" data-testid="audit-trail-actor">
                {{ entry.actor }}
              </td>
              <td class="px-2 py-2 text-gray-900" data-testid="audit-trail-action">
                {{ entry.action }}
              </td>
              <td class="px-2 py-2 text-gray-600 break-all" data-testid="audit-trail-target">
                {{ entry.target }}
              </td>
              <td class="px-2 py-2 text-gray-600" data-testid="audit-trail-correlation">
                {{ entry.correlationId ?? '—' }}
              </td>
              <td class="px-2 py-2">
                <span
                  class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                  :class="outcomeClass(entry.outcome)"
                  data-testid="audit-trail-status"
                >
                  {{ entry.outcome }}
                </span>
              </td>
              <td class="px-2 py-2">
                <button
                  v-if="hasDetails(entry)"
                  type="button"
                  class="px-2 py-0.5 text-xs rounded border border-gray-300 hover:bg-gray-100"
                  data-testid="audit-trail-details-toggle"
                  @click="toggleExpanded(entry.id)"
                >
                  {{ isExpanded(entry.id) ? 'Collapse' : 'Expand' }}
                </button>
                <span v-else class="text-xs text-gray-400">—</span>
              </td>
            </tr>
            <tr v-if="isExpanded(entry.id)" data-testid="audit-trail-details-row">
              <td colspan="7" class="px-4 py-2">
                <pre
                  class="p-2 bg-gray-50 border border-gray-200 rounded font-mono text-xs whitespace-pre-wrap break-words overflow-auto max-h-[40vh]"
                  data-testid="audit-trail-details"
                >{{ _formatDetails(entry.detailsJson) }}</pre>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div
      class="px-4 py-2 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500"
      data-testid="audit-trail-pager"
    >
      <span data-testid="audit-trail-range">{{ rangeText }}</span>
      <span class="flex items-center gap-1">
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!hasPrev || loading"
          data-testid="audit-trail-pager-prev"
          @click="onPrev"
        >
          Prev
        </button>
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!hasNext || loading"
          data-testid="audit-trail-pager-next"
          @click="onNext"
        >
          Next
        </button>
      </span>
    </div>
  </div>
</template>
