import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { useManifestGuard } from '../useManifestGuard'

describe('useManifestGuard', () => {
  const fetchMock = vi.mocked($fetch as unknown as import('vitest').Mock)

  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue([])
  })

  async function flushReady(guard: ReturnType<typeof useManifestGuard>) {
    await vi.waitFor(() => expect(guard.ready.value).toBe(true))
  }

  it('fetches GET /api/v1/dsl/manifest/guard once per session', async () => {
    fetchMock.mockResolvedValueOnce([{ id: 'workbench-publish', allowed: true }])

    const guard = useManifestGuard()
    // A second invocation within the same session must reuse the in-flight fetch.
    useManifestGuard()
    await flushReady(guard)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/manifest/guard')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('fails closed while ready is false (no enabled-button flash)', async () => {
    // Never-resolving fetch: the verdict has not arrived yet.
    fetchMock.mockReturnValueOnce(new Promise(() => {}))

    const guard = useManifestGuard()

    expect(guard.ready.value).toBe(false)
    expect(guard.allowed('workbench-publish')).toBe(false)
  })

  it('returns allowed=true for an allowed piece and false for a denied one', async () => {
    fetchMock.mockResolvedValueOnce([
      { id: 'workbench-publish', allowed: true },
      { id: 'admin-only-button', allowed: false, reason: 'role' },
    ])

    const guard = useManifestGuard()
    await flushReady(guard)

    expect(guard.allowed('workbench-publish')).toBe(true)
    expect(guard.allowed('admin-only-button')).toBe(false)
  })

  it('treats a piece absent from the manifest snapshot as not governed (allowed once ready)', async () => {
    fetchMock.mockResolvedValueOnce([{ id: 'workbench-publish', allowed: true }])

    const guard = useManifestGuard()
    await flushReady(guard)

    expect(guard.allowed('unlisted-button')).toBe(true)
  })

  it.each([
    ['a network error', new Error('fetch failed')],
    ['a 401', Object.assign(new Error('Unauthorized'), { response: { status: 401 } })],
    ['a 403', Object.assign(new Error('Forbidden'), { response: { status: 403 } })],
  ])('fails closed on %s: every piece denied, no exception thrown', async (_label, error) => {
    fetchMock.mockRejectedValueOnce(error)

    const guard = useManifestGuard()
    await flushReady(guard)

    expect(guard.ready.value).toBe(true)
    expect(guard.allowed('workbench-publish')).toBe(false)
    expect(guard.allowed('any-other-piece')).toBe(false)
  })

  it.each([
    ['a non-array payload', { id: 'workbench-publish', allowed: true }],
    ['an entry with a non-boolean allowed flag', [{ id: 'workbench-publish', allowed: 'yes' }]],
    ['a non-object entry', ['workbench-publish']],
  ])('fails closed on malformed payload (%s)', async (_label, payload) => {
    fetchMock.mockResolvedValueOnce(payload)

    const guard = useManifestGuard()
    await flushReady(guard)

    expect(guard.ready.value).toBe(true)
    expect(guard.allowed('workbench-publish')).toBe(false)
  })

  it('bound button stays disabled while loading and when denied (:disabled="!allowed(id)")', async () => {
    fetchMock.mockResolvedValueOnce([{ id: 'workbench-publish', allowed: false }])

    const guard = useManifestGuard()
    // Bound as :disabled="!allowed('workbench-publish')" — assert the contract a button
    // relies on, not just the raw verdict map.
    const disabled = computed(() => !guard.allowed('workbench-publish'))

    expect(disabled.value).toBe(true) // fail closed before ready

    await flushReady(guard)
    expect(disabled.value).toBe(true) // denied → still disabled
  })

  it('bound button is enabled once an allowed verdict arrives', async () => {
    fetchMock.mockResolvedValueOnce([{ id: 'workbench-publish', allowed: true }])

    const guard = useManifestGuard()
    const disabled = computed(() => !guard.allowed('workbench-publish'))
    await flushReady(guard)

    expect(disabled.value).toBe(false)
  })
})
