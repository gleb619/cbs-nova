<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLocalStorageState } from '../composables/useLocalStorageState'
import type { DomainEvent, DomainEventPage, DomainEventQuery } from '../types/events'
import ErrorBanner from './ErrorBanner.vue'

const PAGE_SIZE = 25

const KNOWN_EVENT_TYPES = [
  'RunStarted',
  'RunCompleted',
  'RunFailed',
  'RunCancelled',
  'RunStale',
  'DraftSaved',
  'DraftPublished',
  'ReloadFailed',
]

const SINCE_PRESETS = [
  { value: 'all', label: 'Any time', ms: null },
  { value: '1h', label: 'Last hour', ms: 60 * 60 * 1000 },
  { value: '24h', label: 'Last 24 hours', ms: 24 * 60 * 60 * 1000 },
  { value: '7d', label: 'Last 7 days', ms: 7 * 24 * 60 * 60 * 1000 },
] as const

type SincePreset = (typeof SINCE_PRESETS)[number]['value']

const props = defineProps<{
  fetchEvents: (query: DomainEventQuery) => Promise<DomainEventPage>
  executionLink?: (aggregateId: string) => string | null
  linkComponent?: unknown
}>()

const anchorComponent = props.linkComponent ?? 'a'

const typeFilter = useLocalStorageState('activity.filter.eventType', '')
const aggregateIdFilter = useLocalStorageState('activity.filter.aggregateId', '')
const correlationIdFilter = useLocalStorageState('activity.filter.correlationId', '')
const sinceFilter = useLocalStorageState<SincePreset>('activity.filter.since', 'all')

const aggregateIdInput = ref(aggregateIdFilter.value)
const correlationIdInput = ref(correlationIdFilter.value)

const page = ref<DomainEventPage | null>(null)
const offset = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const expandedIds = ref<Set<number>>(new Set())

const rangeText = computed(() => {
  if (!page.value || page.value.items.length === 0) return ''
  const start = (page.value.offset ?? 0) + 1
  const end = (page.value.offset ?? 0) + page.value.items.length
  return `${start}–${end} of ${page.value.total ?? 0}`
})

const canGoPrev = computed(() => offset.value > 0)
const canGoNext = computed(() => {
  if (!page.value) return false
  return (page.value.offset ?? 0) + page.value.items.length < (page.value.total ?? 0)
})

function sinceInstant(): string | undefined {
  const preset = SINCE_PRESETS.find((entry) => entry.value === sinceFilter.value)
  if (!preset?.ms) return undefined
  return new Date(Date.now() - preset.ms).toISOString()
}

function eventGroup(eventType: string): string {
  return /^[A-Z][a-z]*/.exec(eventType)?.[0].toLowerCase() ?? ''
}

const groupStyles: Record<string, string> = {
  run: 'bg-sky-100 text-sky-800',
  draft: 'bg-green-100 text-green-800',
  reload: 'bg-red-100 text-red-800',
}

function badgeClass(eventType: string): string {
  return groupStyles[eventGroup(eventType)] ?? 'bg-gray-100 text-gray-800'
}

function isRunAggregate(event: DomainEvent): boolean {
  const aggregateType = event.aggregateType.toLowerCase()
  return aggregateType === 'run' || aggregateType === 'execution'
}

function runLink(event: DomainEvent): string | null {
  if (!isRunAggregate(event)) return null
  return props.executionLink?.(event.aggregateId) ?? null
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

// biome-ignore lint/correctness/noUnusedVariables: used in the template
function formatPayload(payload: unknown): string {
  if (payload === undefined) return ''
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
}

async function load(targetOffset: number) {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    const query: DomainEventQuery = { limit: PAGE_SIZE, offset: targetOffset }
    if (typeFilter.value.trim()) query.type = typeFilter.value.trim()
    if (aggregateIdFilter.value.trim()) query.aggregateId = aggregateIdFilter.value.trim()
    if (correlationIdFilter.value.trim()) query.correlationId = correlationIdFilter.value.trim()
    const since = sinceInstant()
    if (since) query.since = since
    page.value = await props.fetchEvents(query)
    offset.value = targetOffset
    expandedIds.value = new Set()
  } catch (err) {
    error.value = (err as Error).message
    page.value = null
  } finally {
    loading.value = false
  }
}

function applyTypeFilter() {
  typeFilter.value = typeFilter.value.trim()
  void load(0)
}

function applyAggregateIdFilter() {
  aggregateIdFilter.value = aggregateIdInput.value.trim()
  void load(0)
}

function applyCorrelationIdFilter() {
  correlationIdFilter.value = correlationIdInput.value.trim()
  void load(0)
}

function filterByCorrelation(correlationId: string) {
  correlationIdFilter.value = correlationId
  correlationIdInput.value = correlationId
  void load(0)
}

function clearCorrelationFilter() {
  correlationIdInput.value = ''
  applyCorrelationIdFilter()
}

function applySinceFilter() {
  void load(0)
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

function toggleExpanded(id: number) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
}

function isExpanded(id: number): boolean {
  return expandedIds.value.has(id)
}

// Phase 1 is pull-only with a manual refresh button: no auto-polling. A live
// feed would need a user-controllable toggle + interval selector per the
// polling rule, and SSE streaming is a documented follow-up.
onMounted(() => {
  void load(0)
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="activity-feed">
    <div class="px-4 py-2 border-b border-gray-200 flex flex-wrap items-center gap-2">
      <input
        v-model="typeFilter"
        type="text"
        list="activity-event-types"
        placeholder="Event type"
        class="px-2 py-1 text-sm border border-gray-300 rounded"
        data-testid="activity-filter-type"
        @change="applyTypeFilter"
      >
      <datalist id="activity-event-types">
        <option v-for="knownType in KNOWN_EVENT_TYPES" :key="knownType" :value="knownType" />
      </datalist>

      <form class="flex items-center gap-1" @submit.prevent="applyAggregateIdFilter">
        <input
          v-model="aggregateIdInput"
          type="text"
          placeholder="Aggregate ID"
          class="px-2 py-1 text-sm border border-gray-300 rounded"
          data-testid="activity-filter-aggregate"
        >
        <button
          type="submit"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="activity-filter-aggregate-apply"
        >
          Apply
        </button>
      </form>

      <form class="flex items-center gap-1" @submit.prevent="applyCorrelationIdFilter">
        <input
          v-model="correlationIdInput"
          type="text"
          placeholder="Correlation ID"
          class="px-2 py-1 text-sm border border-gray-300 rounded"
          data-testid="activity-filter-correlation"
        >
        <button
          type="submit"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="activity-filter-correlation-apply"
        >
          Apply
        </button>
        <button
          v-if="correlationIdFilter"
          type="button"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="activity-filter-correlation-clear"
          @click="clearCorrelationFilter"
        >
          Clear
        </button>
      </form>

      <select
        v-model="sinceFilter"
        class="px-2 py-1 text-sm border border-gray-300 rounded"
        data-testid="activity-filter-since"
        @change="applySinceFilter"
      >
        <option v-for="preset in SINCE_PRESETS" :key="preset.value" :value="preset.value">
          {{ preset.label }}
        </option>
      </select>

      <button
        type="button"
        class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
        :disabled="loading"
        data-testid="activity-refresh"
        @click="refresh"
      >
        Refresh
      </button>
    </div>

    <div class="flex-1 overflow-auto">
      <div v-if="loading" class="px-4 py-3 text-sm text-gray-500" data-testid="activity-loading">
        Loading activity…
      </div>
      <div v-else-if="error" class="px-4 py-3" data-testid="activity-error">
        <ErrorBanner :message="error" retry-label="Retry" @retry="refresh" />
      </div>
      <div
        v-else-if="!page || page.items.length === 0"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="activity-empty"
      >
        No activity recorded.
      </div>

      <ul v-else class="divide-y divide-gray-100" data-testid="activity-list">
        <li
          v-for="event in page.items"
          :key="event.id"
          class="px-4 py-2"
          data-testid="activity-row"
        >
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <span
              class="text-gray-500 whitespace-nowrap"
              :title="event.createdAt"
              data-testid="activity-time"
            >
              {{ formatRelativeTime(event.createdAt) }}
            </span>
            <span
              class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
              :class="badgeClass(event.eventType)"
              data-testid="activity-event-badge"
            >
              {{ event.eventType }}
            </span>
            <span class="text-gray-600" data-testid="activity-aggregate">
              {{ event.aggregateType }}
              /
              <component
                :is="anchorComponent"
                v-if="runLink(event)"
                :to="runLink(event)"
                :href="runLink(event)"
                class="text-primary-600 hover:text-primary-700"
                data-testid="activity-aggregate-link"
              >
                {{ event.aggregateId }}
              </component>
              <template v-else>{{ event.aggregateId }}</template>
            </span>
            <button
              v-if="event.correlationId"
              type="button"
              class="text-xs text-gray-500 hover:text-gray-700 underline decoration-dotted"
              :title="`Filter by correlation ${event.correlationId}`"
              data-testid="activity-correlation"
              @click="filterByCorrelation(event.correlationId as string)"
            >
              {{ event.correlationId }}
            </button>
            <button
              v-if="event.payload !== undefined"
              type="button"
              class="ml-auto px-2 py-0.5 text-xs rounded border border-gray-300 hover:bg-gray-100"
              data-testid="activity-payload-toggle"
              @click="toggleExpanded(event.id)"
            >
              {{ isExpanded(event.id) ? 'Collapse' : 'Payload' }}
            </button>
          </div>
          <pre
            v-if="isExpanded(event.id)"
            class="mt-2 p-2 bg-gray-50 border border-gray-200 rounded font-mono text-xs whitespace-pre-wrap break-words overflow-auto max-h-[40vh]"
            data-testid="activity-payload"
          >{{ formatPayload(event.payload) }}</pre>
        </li>
      </ul>
    </div>

    <div
      class="px-4 py-2 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500"
      data-testid="activity-pager"
    >
      <span data-testid="activity-range">{{ rangeText }}</span>
      <span class="flex items-center gap-1">
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!canGoPrev || loading"
          data-testid="activity-pager-prev"
          @click="goPrev"
        >
          Prev
        </button>
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!canGoNext || loading"
          data-testid="activity-pager-next"
          @click="goNext"
        >
          Next
        </button>
      </span>
    </div>
  </div>
</template>
