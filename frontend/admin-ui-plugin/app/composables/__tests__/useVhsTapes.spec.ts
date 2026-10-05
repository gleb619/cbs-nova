import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest'
import { useVhsTapes } from '../useVhsTapes'

vi.mock('../useClientLogger', () => ({
  useClientLogger: () => ({ error: vi.fn(), info: vi.fn() }),
}))

const fetchMock = vi.mocked($fetch as unknown as Mock)

const TAPE_ENVELOPE = {
  items: [
    {
      run_id: 'run-1',
      route: 'POST /api/v1/dsl/run',
      recorded_at: '2026-09-19T12:00:00Z',
      event_count: 4,
      size_bytes: 2048,
      filename: 'run-1.vhs.jsonl',
      correlation_id: 'corr-1',
    },
    {
      run_id: 'run-2',
      route: 'POST /api/v1/dsl/run',
      recorded_at: '2026-09-19T13:00:00Z',
      event_count: 6,
      size_bytes: 4096,
      filename: 'run-2.vhs.jsonl',
      correlation_id: 'corr-2',
    },
  ],
  total: 2,
}

const TAPE_JSONL = `{"vhs_tape_format_version":"1.0.0","schema_version":"1","recorded_at":"2026-09-19T12:00:00Z","source_run_id":"run-1","correlation_id":"corr-1","route":"POST /api/v1/dsl/run"}
{"event_index":0,"event_type":"run_started","relative_ms":0,"call_metadata":null,"input":null,"output":null,"timing":{"duration_ms":null}}
{"event_index":1,"event_type":"call_start","relative_ms":120,"call_metadata":{"call_id":"c1","type":"http","target":"https://api.example.com/customers/42","operation":"GET"},"input":{"id":"42"},"output":null,"timing":{"duration_ms":null}}
{"event_index":2,"event_type":"call_end","relative_ms":340,"call_metadata":{"call_id":"c1","type":"http","target":"https://api.example.com/customers/42","operation":"GET"},"input":null,"output":{"status":200,"body":{"id":"42"}},"timing":{"duration_ms":220}}
{"event_index":3,"event_type":"run_completed","relative_ms":900,"call_metadata":null,"input":null,"output":{"status":"OK"},"timing":{"duration_ms":900}}`

describe('useVhsTapes', () => {
  beforeEach(() => {
    fetchMock.mockReset()
  })

  it('initial state: empty tapes, loading false, error null', () => {
    const { tapes, total, loading, error } = useVhsTapes()

    expect(tapes.value).toEqual([])
    expect(total.value).toBe(0)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() fetches tapes with offset/limit and populates state', async () => {
    fetchMock.mockResolvedValueOnce(TAPE_ENVELOPE)

    const { tapes, total, loading, error, load } = useVhsTapes()
    await load()

    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
    expect(tapes.value).toHaveLength(2)
    expect(total.value).toBe(2)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/vhs/tapes', {
      query: { offset: '0', limit: '25' },
    })
  })

  it('applyFilters() sends route and since, resets offset to zero', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0 })

    const { filters, offset, applyFilters } = useVhsTapes()
    applyFilters({ route: 'POST /api/foo', since: '2026-01-01T00:00:00Z' })
    await new Promise((r) => setTimeout(r, 0))

    expect(filters.value).toEqual({ route: 'POST /api/foo', since: '2026-01-01T00:00:00Z' })
    expect(offset.value).toBe(0)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/vhs/tapes', {
      query: { offset: '0', limit: '25', route: 'POST /api/foo', since: '2026-01-01T00:00:00Z' },
    })
  })

  it('prev() and next() move offset by limit', async () => {
    fetchMock.mockResolvedValue({ items: [], total: 100 })

    const { offset, prev, next, load } = useVhsTapes()
    await load()

    next()
    await new Promise((r) => setTimeout(r, 0))
    expect(offset.value).toBe(25)

    prev()
    await new Promise((r) => setTimeout(r, 0))
    expect(offset.value).toBe(0)
  })

  it('setLimit() changes page size and resets offset', async () => {
    fetchMock.mockResolvedValue({ items: [], total: 0 })

    const { limit, offset, setLimit } = useVhsTapes()
    setLimit(50)
    await new Promise((r) => setTimeout(r, 0))

    expect(limit.value).toBe(50)
    expect(offset.value).toBe(0)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/vhs/tapes', {
      query: { offset: '0', limit: '50' },
    })
  })

  it('openDetail() fetches tape and builds a step summary, keeping payloads collapsed', async () => {
    fetchMock.mockResolvedValueOnce(TAPE_JSONL)

    const { detail, detailLoading, detailError, openDetail } = useVhsTapes()
    await openDetail('run-1')

    expect(detailLoading.value).toBe(false)
    expect(detailError.value).toBeNull()
    expect(detail.value).not.toBeNull()
    expect(detail.value?.runId).toBe('run-1')
    expect(detail.value?.events).toHaveLength(4)

    const callStart = detail.value?.events.find((e) => e.eventType === 'call_start')
    expect(callStart).toMatchObject({
      index: 1,
      eventType: 'call_start',
      callId: 'c1',
      callType: 'http',
      target: 'https://api.example.com/customers/42',
      operation: 'GET',
      hasInput: true,
      hasOutput: false,
    })
    expect(callStart?.input).toEqual({ id: '42' })

    const callEnd = detail.value?.events.find((e) => e.eventType === 'call_end')
    expect(callEnd).toMatchObject({
      hasInput: false,
      hasOutput: true,
      durationMs: 220,
    })
    expect(callEnd?.output).toEqual({ status: 200, body: { id: '42' } })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/vhs/tapes/run-1')
  })

  it('deleteTape() sends DELETE and reloads the list', async () => {
    fetchMock.mockResolvedValueOnce(undefined)
    fetchMock.mockResolvedValueOnce({ items: [], total: 0 })

    const { deleteTape, deleting } = useVhsTapes()
    await deleteTape('run-1')

    expect(deleting.value['run-1']).toBeUndefined()
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/vhs/tapes/run-1', { method: 'DELETE' })
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/vhs/tapes', {
      query: { offset: '0', limit: '25' },
    })
  })

  it('runLoadTest() POSTs defaults and returns the report', async () => {
    const report = {
      target: 'dry-run',
      tapesLoaded: 2,
      totalCalls: 10,
      successfulCalls: 9,
      failedCalls: 1,
      wallClockMs: 1234,
      tapeSummaries: [],
      rawReports: [],
    }
    fetchMock.mockResolvedValueOnce(report)

    const { runLoadTest, loadTestReport, loadTestRunning } = useVhsTapes()
    const result = await runLoadTest({})

    expect(loadTestRunning.value).toBe(false)
    expect(loadTestReport.value).toEqual(report)
    expect(result).toEqual(report)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/vhs/loadtest', {
      method: 'POST',
      body: { tapes: '**/*.vhs.jsonl', target: 'dry-run', speed: 1, concurrency: 4, duration: 0 },
    })
  })

  it('runLoadTest() error path sets loadTestError and returns null', async () => {
    fetchMock.mockRejectedValueOnce({ message: 'loadtest failed' })

    const { runLoadTest, loadTestReport, loadTestError } = useVhsTapes()
    const result = await runLoadTest({})

    expect(result).toBeNull()
    expect(loadTestReport.value).toBeNull()
    expect(loadTestError.value).toBe('loadtest failed')
  })

  it('load() surfaces errors without throwing', async () => {
    fetchMock.mockRejectedValueOnce({ message: 'backend unreachable' })

    const { load, error, tapes } = useVhsTapes()
    await load()

    expect(error.value).toBe('backend unreachable')
    expect(tapes.value).toEqual([])
  })
})
