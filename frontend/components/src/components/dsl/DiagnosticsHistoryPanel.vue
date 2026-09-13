<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLocalStorageState } from '../../composables/useLocalStorageState'
import type { DiagnosticsPage, PersistedCompileDiagnostic } from '../../types/dsl'
import ErrorBanner from '../ErrorBanner.vue'

const PAGE_SIZE = 25

const props = defineProps<{
  fetchPage: (params: {
    definition?: string
    limit: number
    offset: number
  }) => Promise<DiagnosticsPage>
}>()

const definitionFilter = useLocalStorageState('workbench.diagnostics.filter.definition', '')
const severityFilter = useLocalStorageState('workbench.diagnostics.filter.severity', '')

const definitionInput = ref(definitionFilter.value)
const page = ref<DiagnosticsPage | null>(null)
const offset = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

const rows = computed<PersistedCompileDiagnostic[]>(() => {
  const items = page.value?.items ?? []
  const severity = severityFilter.value.trim().toLowerCase()
  if (!severity) return items
  return items.filter((item) => item.severity.toLowerCase() === severity)
})

const rangeText = computed(() => {
  if (!page.value || page.value.items.length === 0) return ''
  const start = page.value.offset + 1
  const end = page.value.offset + page.value.items.length
  return `${start}–${end} of ${page.value.total}`
})

const canGoPrev = computed(() => offset.value > 0)
const canGoNext = computed(() => {
  if (!page.value) return false
  return page.value.offset + page.value.items.length < page.value.total
})

async function load(targetOffset: number) {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    page.value = await props.fetchPage({
      definition: definitionFilter.value,
      limit: PAGE_SIZE,
      offset: targetOffset,
    })
    offset.value = targetOffset
  } catch (err) {
    error.value = (err as Error).message
    page.value = null
  } finally {
    loading.value = false
  }
}

function applyDefinitionFilter() {
  definitionFilter.value = definitionInput.value.trim()
  void load(0)
}

function clearDefinitionFilter() {
  definitionInput.value = ''
  applyDefinitionFilter()
}

function refresh() {
  void load(offset.value)
}

function goPrev() {
  if (!canGoPrev.value) return
  void load(Math.max(0, offset.value - PAGE_SIZE))
}

function goNext() {
  if (!canGoNext.value) return
  void load(offset.value + PAGE_SIZE)
}

const severityStyles: Record<string, string> = {
  error: 'bg-red-100 text-red-800',
  warning: 'bg-yellow-100 text-yellow-800',
  info: 'bg-sky-100 text-sky-800',
}

function severityClass(severity: string): string {
  return severityStyles[severity.toLowerCase()] ?? 'bg-gray-100 text-gray-800'
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

function formatLocation(item: PersistedCompileDiagnostic): string {
  if (!item.file) return ''
  return [item.file, item.line, item.column]
    .filter((part) => part !== null && part !== undefined)
    .join(':')
}

onMounted(() => {
  void load(0)
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="diagnostics-panel">
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="text-base font-semibold text-gray-900">Diagnostics history</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Compile diagnostics recorded on reload, publish and draft validation.
      </p>
    </div>

    <div class="px-4 py-2 border-b border-gray-200 flex flex-wrap items-center gap-2">
      <form class="flex items-center gap-1" @submit.prevent="applyDefinitionFilter">
        <input
          v-model="definitionInput"
          type="text"
          placeholder="Filter by definition"
          class="px-2 py-1 text-sm border border-gray-300 rounded"
          data-testid="diagnostics-filter-definition"
        >
        <button
          type="submit"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="diagnostics-filter-apply"
        >
          Apply
        </button>
        <button
          v-if="definitionFilter"
          type="button"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="diagnostics-filter-clear"
          @click="clearDefinitionFilter"
        >
          Clear
        </button>
      </form>
      <select
        v-model="severityFilter"
        class="px-2 py-1 text-sm border border-gray-300 rounded"
        data-testid="diagnostics-filter-severity"
      >
        <option value="">All severities</option>
        <option value="error">Error</option>
        <option value="warning">Warning</option>
        <option value="info">Info</option>
      </select>
      <button
        type="button"
        class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
        :disabled="loading"
        data-testid="diagnostics-refresh"
        @click="refresh"
      >
        Refresh
      </button>
    </div>

    <div class="flex-1 overflow-auto">
      <div v-if="loading" class="px-4 py-3 text-sm text-gray-500" data-testid="diagnostics-loading">
        Loading diagnostics…
      </div>
      <div v-else-if="error" class="px-4 py-3" data-testid="diagnostics-error">
        <ErrorBanner :message="error" retry-label="Retry" @retry="refresh" />
      </div>
      <div
        v-else-if="rows.length === 0"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="diagnostics-empty"
      >
        No diagnostics recorded.
      </div>
      <table v-else class="w-full text-sm" data-testid="diagnostics-table">
        <thead>
          <tr class="text-left text-xs uppercase text-gray-500 border-b border-gray-200">
            <th class="px-4 py-2 font-semibold">When</th>
            <th class="px-2 py-2 font-semibold">Source</th>
            <th class="px-2 py-2 font-semibold">Definition</th>
            <th class="px-2 py-2 font-semibold">Severity</th>
            <th class="px-2 py-2 font-semibold">Location</th>
            <th class="px-2 py-2 font-semibold">Message</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="item in rows" :key="item.id" class="align-top" data-testid="diagnostics-row">
            <td class="px-4 py-2 whitespace-nowrap text-gray-500" :title="item.occurredAt">
              {{ formatRelativeTime(item.occurredAt) }}
            </td>
            <td class="px-2 py-2">
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700"
                data-testid="diagnostics-source-badge"
              >
                {{ item.source }}
              </span>
            </td>
            <td class="px-2 py-2 font-medium text-gray-900">{{ item.definition }}</td>
            <td class="px-2 py-2">
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                :class="severityClass(item.severity)"
                data-testid="diagnostics-severity-badge"
              >
                {{ item.severity }}
              </span>
            </td>
            <td class="px-2 py-2 text-gray-600">
              <span v-if="formatLocation(item)">{{ formatLocation(item) }}</span>
              <span v-if="item.code" class="ml-1 text-gray-400">[{{ item.code }}]</span>
            </td>
            <td class="px-2 py-2 text-gray-700 max-w-xs break-words">{{ item.message }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      class="px-4 py-2 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500"
      data-testid="diagnostics-pager"
    >
      <span data-testid="diagnostics-range">{{ rangeText }}</span>
      <span class="flex items-center gap-1">
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!canGoPrev || loading"
          data-testid="diagnostics-pager-prev"
          @click="goPrev"
        >
          Prev
        </button>
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!canGoNext || loading"
          data-testid="diagnostics-pager-next"
          @click="goNext"
        >
          Next
        </button>
      </span>
    </div>
  </div>
</template>
