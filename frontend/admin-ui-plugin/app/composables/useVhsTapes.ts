import { unwrapListWithTotal } from '@cbs/components'
import type {
  VhsLoadTestForm,
  VhsLoadTestReport,
  VhsTapeDetail,
  VhsTapeEventSummary,
  VhsTapeFilters,
  VhsTapeSummary,
} from '@cbs/components/types'
import { $fetch } from 'ofetch'
import { computed, ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'
import { useClientLogger } from './useClientLogger'

const DEFAULT_LIMIT = 25

function parseTapeDetail(raw: string): VhsTapeDetail | null {
  const lines = raw
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  if (lines.length === 0) return null

  let header: Record<string, unknown> = {}
  try {
    header = JSON.parse(lines[0]) as Record<string, unknown>
  } catch {
    return null
  }

  const runId =
    typeof header.source_run_id === 'string'
      ? header.source_run_id
      : typeof header.run_id === 'string'
        ? header.run_id
        : ''

  const events: VhsTapeEventSummary[] = []
  for (let i = 1; i < lines.length; i++) {
    let parsed: Record<string, unknown> | undefined
    try {
      parsed = JSON.parse(lines[i]) as Record<string, unknown>
    } catch {
      continue
    }
    const eventType = String(parsed.event_type ?? parsed.eventType ?? '')
    if (eventType === 'tape_truncated') continue

    const meta = (parsed.call_metadata ?? {}) as Record<string, unknown>
    const timing = (parsed.timing ?? {}) as Record<string, unknown>
    events.push({
      index: Number(parsed.event_index ?? i - 1),
      eventType,
      relativeMs: Number(parsed.relative_ms ?? parsed.relativeMs ?? 0),
      callId: meta.call_id ? String(meta.call_id) : undefined,
      callType: meta.type ? String(meta.type) : undefined,
      target: meta.target ? String(meta.target) : undefined,
      operation: meta.operation ? String(meta.operation) : undefined,
      durationMs: timing.duration_ms != null ? Number(timing.duration_ms) : null,
      hasInput: parsed.input != null,
      input: parsed.input,
      output: parsed.output,
      hasOutput: parsed.output != null,
    })
  }

  return { runId, header, events, raw }
}

export function useVhsTapes() {
  const log = useClientLogger('vhs')

  const tapes = ref<VhsTapeSummary[]>([])
  const total = ref(0)
  const offset = ref(0)
  const limit = ref(DEFAULT_LIMIT)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = ref<VhsTapeFilters>({})

  const detailRunId = ref<string | null>(null)
  const detail = ref<VhsTapeDetail | null>(null)
  const detailLoading = ref(false)
  const detailError = ref<string | null>(null)

  const deleting = ref<Record<string, boolean>>({})

  const loadTestRunning = ref(false)
  const loadTestReport = ref<VhsLoadTestReport | null>(null)
  const loadTestError = ref<string | null>(null)

  const hasPrev = computed(() => offset.value > 0)
  const hasNext = computed(() => offset.value + tapes.value.length < total.value)

  async function load() {
    loading.value = true
    error.value = null
    try {
      const query: Record<string, string> = {
        offset: String(offset.value),
        limit: String(limit.value),
      }
      if (filters.value.route?.trim()) query.route = filters.value.route.trim()
      if (filters.value.since?.trim()) query.since = filters.value.since.trim()

      const result = await $fetch('/api/v1/vhs/tapes', { query })
      const envelope = unwrapListWithTotal<VhsTapeSummary>(result)
      tapes.value = envelope.items
      total.value = envelope.total
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load tapes', { error: message })
      error.value = message
      tapes.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function applyFilters(next: VhsTapeFilters) {
    filters.value = { ...next }
    offset.value = 0
    void load()
  }

  function clearFilters() {
    filters.value = {}
    offset.value = 0
    void load()
  }

  function prev() {
    offset.value = Math.max(0, offset.value - limit.value)
    void load()
  }

  function next() {
    offset.value += limit.value
    void load()
  }

  function setLimit(value: number) {
    limit.value = value
    offset.value = 0
    void load()
  }

  async function openDetail(runId: string) {
    detailRunId.value = runId
    detailLoading.value = true
    detailError.value = null
    detail.value = null
    try {
      const raw = (await $fetch(`/api/v1/vhs/tapes/${encodeURIComponent(runId)}`)) as string
      detail.value = parseTapeDetail(raw)
      if (!detail.value) {
        detailError.value = 'Failed to parse tape contents'
      }
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load tape detail', { runId, error: message })
      detailError.value = message
    } finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    detailRunId.value = null
    detail.value = null
    detailError.value = null
  }

  async function deleteTape(runId: string) {
    deleting.value = { ...deleting.value, [runId]: true }
    try {
      await $fetch(`/api/v1/vhs/tapes/${encodeURIComponent(runId)}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to delete tape', { runId, error: message })
      error.value = message
      throw err
    } finally {
      const next = { ...deleting.value }
      delete next[runId]
      deleting.value = next
    }
  }

  async function runLoadTest(payload: VhsLoadTestForm): Promise<VhsLoadTestReport | null> {
    loadTestRunning.value = true
    loadTestReport.value = null
    loadTestError.value = null
    try {
      const result = (await $fetch('/api/v1/vhs/loadtest', {
        method: 'POST',
        body: {
          tapes: payload.tapes ?? '**/*.vhs.jsonl',
          target: payload.target ?? 'dry-run',
          speed: payload.speed ?? 1,
          concurrency: payload.concurrency ?? 4,
          duration: payload.duration ?? 0,
        },
      })) as VhsLoadTestReport
      loadTestReport.value = result
      return result
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to run load test', { error: message })
      loadTestError.value = message
      return null
    } finally {
      loadTestRunning.value = false
    }
  }

  function clearLoadTestReport() {
    loadTestReport.value = null
    loadTestError.value = null
  }

  return {
    tapes,
    total,
    offset,
    limit,
    loading,
    error,
    filters,
    hasPrev,
    hasNext,
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
    clearLoadTestReport,
  }
}
