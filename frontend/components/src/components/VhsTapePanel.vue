<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type {
  VhsLoadTestForm,
  VhsLoadTestReport,
  VhsTapeDetail,
  VhsTapeFilters,
  VhsTapeSummary,
} from '../types'
import CbsDrawer from './CbsDrawer.vue'
import ErrorBanner from './ErrorBanner.vue'
import VhsDeleteConfirmationModal from './vhs/VhsDeleteConfirmationModal.vue'

const props = defineProps<{
  tapes: VhsTapeSummary[]
  total: number
  offset: number
  limit: number
  hasPrev: boolean
  hasNext: boolean
  loading: boolean
  error: string | null
  filters: VhsTapeFilters
  detail: VhsTapeDetail | null
  detailLoading: boolean
  detailError: string | null
  deleting: Record<string, boolean>
  loadTestRunning: boolean
  loadTestReport: VhsLoadTestReport | null
  loadTestError: string | null
}>()

const emit = defineEmits<{
  'update:filters': [filters: VhsTapeFilters]
  'apply-filters': []
  'clear-filters': []
  refresh: []
  prev: []
  next: []
  'update:limit': [limit: number]
  'open-detail': [runId: string]
  'close-detail': []
  delete: [runId: string]
  loadtest: [payload: VhsLoadTestForm]
}>()

const localFilters = reactive<{ route: string; since: string }>({
  route: props.filters.route ?? '',
  since: props.filters.since ?? '',
})

const pendingDeleteRunId = ref<string | null>(null)
const pendingDeleteRoute = computed(() => {
  if (!pendingDeleteRunId.value) return undefined
  return (
    props.tapes.find((t) => t.run_id === pendingDeleteRunId.value)?.route ??
    pendingDeleteRunId.value
  )
})

const loadTestForm = reactive<Required<VhsLoadTestForm>>({
  tapes: '**/*.vhs.jsonl',
  target: 'dry-run',
  speed: 1,
  concurrency: 4,
  duration: 0,
})

const drawerOpen = computed({
  get: () => props.detail !== null || props.detailLoading,
  set: (value) => {
    if (!value) emit('close-detail')
  },
})

const rangeText = computed(() => {
  if (props.tapes.length === 0) return ''
  const start = props.offset + 1
  const end = props.offset + props.tapes.length
  return `${start}–${end} of ${props.total}`
})

function onApplyFilters() {
  emit('update:filters', {
    route: localFilters.route.trim() || undefined,
    since: localFilters.since.trim() || undefined,
  })
  emit('apply-filters')
}

function onClearFilters() {
  localFilters.route = ''
  localFilters.since = ''
  emit('update:filters', {})
  emit('clear-filters')
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

function onLimitChange(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  emit('update:limit', value)
}

function onView(runId: string) {
  emit('open-detail', runId)
}

function onDeleteClick(runId: string) {
  pendingDeleteRunId.value = runId
}

function onConfirmDelete() {
  if (!pendingDeleteRunId.value) return
  emit('delete', pendingDeleteRunId.value)
  pendingDeleteRunId.value = null
}

function onCancelDelete() {
  pendingDeleteRunId.value = null
}

function onLoadTestSubmit() {
  emit('loadtest', {
    tapes: loadTestForm.tapes.trim() || undefined,
    target: loadTestForm.target,
    speed: loadTestForm.speed,
    concurrency: loadTestForm.concurrency,
    duration: loadTestForm.duration,
  })
}

function formatBytes(bytes?: number | null) {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}

function formatDate(iso?: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

function formatDuration(ms?: number | null) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

function headerField(key: string): unknown {
  return props.detail?.header[key]
}
</script>

<template>
  <div
    data-testid="vhs-tape-panel"
    class="flex flex-col h-full bg-white border border-neutral-200 rounded-lg overflow-hidden"
  >
    <div class="px-4 py-3 border-b border-neutral-200">
      <h2 class="text-base font-semibold text-neutral-900">VHS Tapes</h2>
      <p class="text-xs text-neutral-500 mt-0.5">
        Recorded execution tapes for replay, inspection and load testing.
      </p>
    </div>

    <div class="px-4 py-2 border-b border-neutral-200 flex flex-wrap items-center gap-2">
      <form class="flex items-center gap-2" @submit.prevent="onApplyFilters">
        <label for="vhs-filter-route" class="sr-only">Filter by route</label>
        <input
          id="vhs-filter-route"
          v-model="localFilters.route"
          data-testid="vhs-filter-route"
          type="text"
          placeholder="Route"
          class="px-2 py-1 text-sm border border-neutral-300 rounded"
        >
        <label for="vhs-filter-since" class="sr-only">Recorded since</label>
        <input
          id="vhs-filter-since"
          v-model="localFilters.since"
          data-testid="vhs-filter-since"
          type="datetime-local"
          class="px-2 py-1 text-sm border border-neutral-300 rounded"
        >
        <button
          type="submit"
          data-testid="vhs-apply-filters"
          class="px-2 py-1 text-sm rounded border border-neutral-300 hover:bg-neutral-100"
        >
          Apply
        </button>
        <button
          v-if="localFilters.route || localFilters.since"
          type="button"
          data-testid="vhs-clear-filters"
          class="px-2 py-1 text-sm rounded border border-neutral-300 hover:bg-neutral-100"
          @click="onClearFilters"
        >
          Clear
        </button>
      </form>
      <button
        type="button"
        data-testid="vhs-refresh"
        class="px-2 py-1 text-sm rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50"
        :disabled="loading"
        @click="onRefresh"
      >
        Refresh
      </button>
    </div>

    <div class="flex-1 overflow-auto p-4 space-y-4">
      <ErrorBanner
        v-if="error"
        :message="error"
        data-testid="vhs-error-banner"
        @retry="onRefresh"
      />

      <div v-if="loading" class="space-y-3" data-testid="vhs-loading">
        <div v-for="i in 5" :key="i" class="h-10 bg-neutral-100 rounded animate-pulse" />
      </div>

      <div
        v-else-if="tapes.length === 0"
        class="text-sm text-neutral-500 italic text-center py-8"
        data-testid="vhs-empty"
      >
        No tapes found.
      </div>

      <table
        v-else
        data-testid="vhs-tape-table"
        class="min-w-full text-sm border border-neutral-200 rounded-lg overflow-hidden"
      >
        <caption class="sr-only">
          VHS tapes
        </caption>
        <thead class="bg-neutral-50 text-left text-xs uppercase text-neutral-600">
          <tr>
            <th scope="col" class="px-3 py-2">Run ID</th>
            <th scope="col" class="px-3 py-2">Route</th>
            <th scope="col" class="px-3 py-2">Recorded</th>
            <th scope="col" class="px-3 py-2">Events</th>
            <th scope="col" class="px-3 py-2">Size</th>
            <th scope="col" class="px-3 py-2 text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="tape in tapes"
            :key="tape.run_id"
            class="border-t border-neutral-100 hover:bg-neutral-50"
            :data-testid="`vhs-tape-row-${tape.run_id}`"
          >
            <td class="px-3 py-2 font-mono text-xs text-neutral-800">{{ tape.run_id }}</td>
            <td class="px-3 py-2 text-neutral-800">{{ tape.route }}</td>
            <td class="px-3 py-2 text-xs text-neutral-600">{{ formatDate(tape.recorded_at) }}</td>
            <td class="px-3 py-2 text-xs text-neutral-600">{{ tape.event_count }}</td>
            <td class="px-3 py-2 text-xs text-neutral-600">{{ formatBytes(tape.size_bytes) }}</td>
            <td class="px-3 py-2 text-right">
              <div class="flex items-center justify-end gap-2">
                <button
                  type="button"
                  class="px-2 py-1 text-xs font-medium rounded border border-neutral-300 hover:bg-neutral-100"
                  :data-testid="`vhs-tape-view-${tape.run_id}`"
                  @click="onView(tape.run_id)"
                >
                  View
                </button>
                <button
                  type="button"
                  class="px-2 py-1 text-xs font-medium rounded border border-error-300 text-error-700 hover:bg-error-50"
                  :disabled="deleting[tape.run_id]"
                  :data-testid="`vhs-tape-delete-${tape.run_id}`"
                  @click="onDeleteClick(tape.run_id)"
                >
                  {{ deleting[tape.run_id] ? 'Deleting…' : 'Delete' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="tapes.length > 0" class="flex items-center justify-between gap-3">
        <div class="text-xs text-neutral-600">{{ rangeText }}</div>
        <div class="flex items-center gap-2">
          <label for="vhs-limit" class="text-xs text-neutral-600">Page size</label>
          <select
            id="vhs-limit"
            :value="limit"
            data-testid="vhs-limit"
            class="px-2 py-1 text-sm border border-neutral-300 rounded"
            @change="onLimitChange"
          >
            <option :value="10">10</option>
            <option :value="25">25</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button
            type="button"
            data-testid="vhs-prev"
            class="px-2 py-1 text-sm rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50"
            :disabled="!hasPrev"
            @click="onPrev"
          >
            Previous
          </button>
          <button
            type="button"
            data-testid="vhs-next"
            class="px-2 py-1 text-sm rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50"
            :disabled="!hasNext"
            @click="onNext"
          >
            Next
          </button>
        </div>
      </div>

      <details class="border border-neutral-200 rounded-lg" data-testid="vhs-loadtest-details">
        <summary class="px-4 py-3 text-sm font-medium text-neutral-900 cursor-pointer">
          Load test
        </summary>
        <div class="p-4 border-t border-neutral-200 space-y-4">
          <form
            data-testid="vhs-loadtest-form"
            class="grid grid-cols-1 md:grid-cols-3 gap-3"
            @submit.prevent="onLoadTestSubmit"
          >
            <div class="flex flex-col gap-1">
              <label for="vhs-loadtest-tapes" class="text-xs font-medium text-neutral-700"
                >Tape glob</label
              >
              <input
                id="vhs-loadtest-tapes"
                v-model="loadTestForm.tapes"
                data-testid="vhs-loadtest-tapes"
                type="text"
                class="px-2 py-1 text-sm border border-neutral-300 rounded"
              >
            </div>
            <div class="flex flex-col gap-1">
              <label for="vhs-loadtest-target" class="text-xs font-medium text-neutral-700"
                >Target</label
              >
              <select
                id="vhs-loadtest-target"
                v-model="loadTestForm.target"
                data-testid="vhs-loadtest-target"
                class="px-2 py-1 text-sm border border-neutral-300 rounded"
              >
                <option value="dry-run">dry-run</option>
                <option value="local">local</option>
              </select>
            </div>
            <div class="flex flex-col gap-1">
              <label for="vhs-loadtest-speed" class="text-xs font-medium text-neutral-700"
                >Speed</label
              >
              <input
                id="vhs-loadtest-speed"
                v-model.number="loadTestForm.speed"
                data-testid="vhs-loadtest-speed"
                type="number"
                step="0.1"
                min="0.1"
                class="px-2 py-1 text-sm border border-neutral-300 rounded"
              >
            </div>
            <div class="flex flex-col gap-1">
              <label for="vhs-loadtest-concurrency" class="text-xs font-medium text-neutral-700"
                >Concurrency</label
              >
              <input
                id="vhs-loadtest-concurrency"
                v-model.number="loadTestForm.concurrency"
                data-testid="vhs-loadtest-concurrency"
                type="number"
                min="1"
                class="px-2 py-1 text-sm border border-neutral-300 rounded"
              >
            </div>
            <div class="flex flex-col gap-1">
              <label for="vhs-loadtest-duration" class="text-xs font-medium text-neutral-700"
                >Duration cap (ms)</label
              >
              <input
                id="vhs-loadtest-duration"
                v-model.number="loadTestForm.duration"
                data-testid="vhs-loadtest-duration"
                type="number"
                min="0"
                class="px-2 py-1 text-sm border border-neutral-300 rounded"
              >
            </div>
            <div class="flex items-end">
              <button
                type="submit"
                data-testid="vhs-loadtest-submit"
                class="px-3 py-1.5 text-sm rounded bg-primary-600 text-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-primary-700"
                :disabled="loadTestRunning"
              >
                {{ loadTestRunning ? 'Running…' : 'Run load test' }}
              </button>
            </div>
          </form>

          <div v-if="loadTestError" class="text-sm text-error-700" data-testid="vhs-loadtest-error">
            {{ loadTestError }}
          </div>

          <div v-if="loadTestReport" data-testid="vhs-loadtest-report" class="space-y-3">
            <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
              <div class="p-2 rounded bg-neutral-50 border border-neutral-200">
                <div class="text-xs text-neutral-500">Tapes loaded</div>
                <div class="font-semibold text-neutral-900">{{ loadTestReport.tapesLoaded }}</div>
              </div>
              <div class="p-2 rounded bg-neutral-50 border border-neutral-200">
                <div class="text-xs text-neutral-500">Total calls</div>
                <div class="font-semibold text-neutral-900">{{ loadTestReport.totalCalls }}</div>
              </div>
              <div class="p-2 rounded bg-neutral-50 border border-neutral-200">
                <div class="text-xs text-neutral-500">Successful</div>
                <div class="font-semibold text-success-700">
                  {{ loadTestReport.successfulCalls }}
                </div>
              </div>
              <div class="p-2 rounded bg-neutral-50 border border-neutral-200">
                <div class="text-xs text-neutral-500">Failed</div>
                <div class="font-semibold text-error-700">{{ loadTestReport.failedCalls }}</div>
              </div>
            </div>

            <table class="min-w-full text-sm border border-neutral-200 rounded-lg overflow-hidden">
              <caption class="sr-only">
                Load test tape summaries
              </caption>
              <thead class="bg-neutral-50 text-left text-xs uppercase text-neutral-600">
                <tr>
                  <th scope="col" class="px-3 py-2">Tape</th>
                  <th scope="col" class="px-3 py-2">Calls</th>
                  <th scope="col" class="px-3 py-2">Success</th>
                  <th scope="col" class="px-3 py-2">Failed</th>
                  <th scope="col" class="px-3 py-2">P50</th>
                  <th scope="col" class="px-3 py-2">P95</th>
                  <th scope="col" class="px-3 py-2">P99</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="summary in loadTestReport.tapeSummaries"
                  :key="summary.tapeName"
                  class="border-t border-neutral-100"
                >
                  <td class="px-3 py-2 font-mono text-xs">{{ summary.tapeName }}</td>
                  <td class="px-3 py-2">{{ summary.totalCalls }}</td>
                  <td class="px-3 py-2 text-success-700">{{ summary.successfulCalls }}</td>
                  <td class="px-3 py-2 text-error-700">{{ summary.failedCalls }}</td>
                  <td class="px-3 py-2">{{ formatDuration(summary.p50Ms) }}</td>
                  <td class="px-3 py-2">{{ formatDuration(summary.p95Ms) }}</td>
                  <td class="px-3 py-2">{{ formatDuration(summary.p99Ms) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </details>
    </div>

    <CbsDrawer
      v-model:open="drawerOpen"
      title="Tape detail"
      aria-label="Tape detail"
      test-id="vhs-detail-drawer"
      close-label="Close tape detail"
      width-class="w-[40rem] max-w-full"
    >
      <div class="flex-1 overflow-y-auto p-4 space-y-4">
        <div v-if="detailLoading" class="space-y-3">
          <div v-for="i in 4" :key="i" class="h-4 bg-neutral-100 rounded animate-pulse" />
        </div>

        <ErrorBanner
          v-else-if="detailError"
          :message="detailError"
          data-testid="vhs-detail-error"
          @retry="detail ? emit('open-detail', detail.runId) : undefined"
        />

        <template v-else-if="detail">
          <div class="space-y-1 text-sm">
            <div class="flex justify-between">
              <span class="text-neutral-500">Run ID</span>
              <span class="font-mono text-neutral-900">{{ detail.runId }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-neutral-500">Format version</span>
              <span class="text-neutral-900">{{ headerField('vhs_tape_format_version') }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-neutral-500">Schema version</span>
              <span class="text-neutral-900">{{ headerField('schema_version') }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-neutral-500">Route</span>
              <span class="text-neutral-900">{{ headerField('route') }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-neutral-500">Recorded at</span>
              <span class="text-neutral-900">{{
                formatDate(String(headerField('recorded_at') ?? ''))
              }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-neutral-500">Events</span>
              <span class="text-neutral-900">{{ detail.events.length }}</span>
            </div>
          </div>

          <table class="min-w-full text-sm border border-neutral-200 rounded-lg overflow-hidden">
            <caption class="sr-only">
              Tape events
            </caption>
            <thead class="bg-neutral-50 text-left text-xs uppercase text-neutral-600">
              <tr>
                <th scope="col" class="px-3 py-2">#</th>
                <th scope="col" class="px-3 py-2">Type</th>
                <th scope="col" class="px-3 py-2">Target</th>
                <th scope="col" class="px-3 py-2">Duration</th>
                <th scope="col" class="px-3 py-2">Payloads</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="event in detail.events"
                :key="event.index"
                class="border-t border-neutral-100"
              >
                <td class="px-3 py-2 text-xs text-neutral-600">{{ event.index }}</td>
                <td class="px-3 py-2">
                  <span
                    class="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-neutral-100 text-neutral-800"
                  >
                    {{ event.eventType }}
                  </span>
                </td>
                <td class="px-3 py-2 text-xs">
                  <div v-if="event.target" class="text-neutral-800">{{ event.target }}</div>
                  <div v-if="event.operation" class="text-neutral-500">{{ event.operation }}</div>
                </td>
                <td class="px-3 py-2 text-xs">{{ formatDuration(event.durationMs) }}</td>
                <td class="px-3 py-2 text-xs">
                  <div class="flex flex-col gap-1">
                    <details v-if="event.hasInput">
                      <summary class="cursor-pointer text-neutral-600 hover:text-neutral-900">
                        Input
                      </summary>
                      <pre
                        class="mt-1 p-2 bg-neutral-50 rounded text-xs overflow-auto max-h-40"
                      >{{ JSON.stringify(event.input, null, 2) }}</pre>
                    </details>
                    <details v-if="event.hasOutput">
                      <summary class="cursor-pointer text-neutral-600 hover:text-neutral-900">
                        Output
                      </summary>
                      <pre
                        class="mt-1 p-2 bg-neutral-50 rounded text-xs overflow-auto max-h-40"
                      >{{ JSON.stringify(event.output, null, 2) }}</pre>
                    </details>
                    <span v-if="!event.hasInput && !event.hasOutput" class="text-neutral-400"
                      >—</span
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <details class="border border-neutral-200 rounded-lg">
            <summary class="px-3 py-2 text-xs font-medium text-neutral-700 cursor-pointer">
              Raw tape (collapsed by default)
            </summary>
            <pre class="p-3 text-xs overflow-auto max-h-96 bg-neutral-50">{{ detail.raw }}</pre>
          </details>
        </template>
      </div>
    </CbsDrawer>

    <VhsDeleteConfirmationModal
      :show="pendingDeleteRunId !== null"
      :run-id="pendingDeleteRunId ?? undefined"
      :route="pendingDeleteRoute"
      :busy="pendingDeleteRunId ? deleting[pendingDeleteRunId] : false"
      @confirm="onConfirmDelete"
      @cancel="onCancelDelete"
    />
  </div>
</template>
