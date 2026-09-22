import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useWorkbenchDraft } from '../useWorkbenchDraft'

// --- T589: runtimeConfig override plumbing ---
// Mirror the useStalePollInterval.spec pattern: hoist a mutable public-config
// mock so the public-key overrides are picked up by resolveDraftTtlMs /
// resolveSaveDebounceMs / resolveServerSaveDebounceMs.
type WorkbenchDraftPublicConfig = {
  workbenchDraftTtlMs?: number
  workbenchSaveDebounceMs?: number
  workbenchServerSaveDebounceMs?: number
  [key: string]: unknown
}

const { publicConfig, useRuntimeConfigMock } = vi.hoisted(() => ({
  publicConfig: {
    workbenchDraftTtlMs: undefined,
    workbenchSaveDebounceMs: undefined,
    workbenchServerSaveDebounceMs: undefined,
  } as WorkbenchDraftPublicConfig,
  useRuntimeConfigMock: vi.fn(),
}))

vi.mock('nuxt/app', () => ({
  useRuntimeConfig: () => useRuntimeConfigMock(),
}))

const KEY = 'cbs.nova.draft.c1'

const flush = async () => {
  await vi.advanceTimersByTimeAsync(0)
  await Promise.resolve()
}

describe('useWorkbenchDraft', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.localStorage.clear()
    // Reset the hoisted publicConfig to its undefined baseline so each test
    // gets the documented defaults unless it explicitly overrides.
    publicConfig.workbenchDraftTtlMs = undefined
    publicConfig.workbenchSaveDebounceMs = undefined
    publicConfig.workbenchServerSaveDebounceMs = undefined
    useRuntimeConfigMock.mockReset().mockReturnValue({ public: publicConfig })
  })

  afterEach(() => {
    vi.useRealTimers()
    window.localStorage.clear()
  })

  it('missing key: no restore, empty body, not dirty', () => {
    const { body, dirty, lastSavedAt, restoredFromDraft } = useWorkbenchDraft('c1')

    expect(body.value).toBe('')
    expect(dirty.value).toBe(false)
    expect(lastSavedAt.value).toBeNull()
    expect(restoredFromDraft.value).toBe(false)
  })

  it('saves to localStorage on body change, debounced by 250ms', async () => {
    const { body, lastSavedAt } = useWorkbenchDraft('c1')

    body.value = 'hello'
    expect(window.localStorage.getItem(KEY)).toBeNull()

    await vi.advanceTimersByTimeAsync(249)
    expect(window.localStorage.getItem(KEY)).toBeNull()

    await vi.advanceTimersByTimeAsync(1)
    await flush()

    const raw = window.localStorage.getItem(KEY)
    expect(raw).not.toBeNull()
    const parsed = JSON.parse(raw as string)
    expect(parsed.body).toBe('hello')
    expect(typeof parsed.savedAt).toBe('number')
    expect(lastSavedAt.value).toBe(parsed.savedAt)
  })

  it('debounces rapid successive changes into a single save', async () => {
    const { body } = useWorkbenchDraft('c1')

    body.value = 'a'
    await vi.advanceTimersByTimeAsync(100)
    body.value = 'ab'
    await vi.advanceTimersByTimeAsync(100)
    body.value = 'abc'

    await vi.advanceTimersByTimeAsync(250)
    await flush()

    const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
    expect(parsed.body).toBe('abc')
  })

  it('dirty is true while body differs from the last saved value, false once saved', async () => {
    const { body, dirty } = useWorkbenchDraft('c1')
    expect(dirty.value).toBe(false)

    body.value = 'x'
    expect(dirty.value).toBe(true)

    await vi.advanceTimersByTimeAsync(250)
    await flush()
    expect(dirty.value).toBe(false)
  })

  it('restores body on setup from an existing fresh draft and flags restoredFromDraft', () => {
    window.localStorage.setItem(KEY, JSON.stringify({ body: 'restored body', savedAt: Date.now() }))

    const { body, dirty, lastSavedAt, restoredFromDraft } = useWorkbenchDraft('c1')

    expect(body.value).toBe('restored body')
    expect(dirty.value).toBe(false)
    expect(lastSavedAt.value).toEqual(expect.any(Number))
    expect(restoredFromDraft.value).toBe(true)
  })

  it('TTL: ignores and clears a draft older than 24h', () => {
    const staleSavedAt = Date.now() - (24 * 60 * 60 * 1000 + 1)
    window.localStorage.setItem(KEY, JSON.stringify({ body: 'stale body', savedAt: staleSavedAt }))

    const { body, restoredFromDraft } = useWorkbenchDraft('c1')

    expect(body.value).toBe('')
    expect(restoredFromDraft.value).toBe(false)
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  it('TTL: keeps a draft just under the 24h boundary', () => {
    const freshSavedAt = Date.now() - (24 * 60 * 60 * 1000 - 1000)
    window.localStorage.setItem(KEY, JSON.stringify({ body: 'still fresh', savedAt: freshSavedAt }))

    const { body, restoredFromDraft } = useWorkbenchDraft('c1')

    expect(body.value).toBe('still fresh')
    expect(restoredFromDraft.value).toBe(true)
  })

  it('clearDraft removes the localStorage key and resets state', async () => {
    window.localStorage.setItem(KEY, JSON.stringify({ body: 'restored body', savedAt: Date.now() }))

    const { body, dirty, lastSavedAt, restoredFromDraft, clearDraft } = useWorkbenchDraft('c1')
    expect(body.value).toBe('restored body')

    clearDraft()

    expect(window.localStorage.getItem(KEY)).toBeNull()
    expect(body.value).toBe('')
    expect(dirty.value).toBe(false)
    expect(lastSavedAt.value).toBeNull()
    expect(restoredFromDraft.value).toBe(false)

    // a pending debounced save must not resurrect the key after clearing
    await vi.advanceTimersByTimeAsync(500)
    await flush()
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  it('cancels an in-flight debounced save when clearDraft runs mid-debounce', async () => {
    const { body, clearDraft } = useWorkbenchDraft('c1')

    body.value = 'in flight'
    await vi.advanceTimersByTimeAsync(100)

    clearDraft()

    await vi.advanceTimersByTimeAsync(500)
    await flush()
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  it('reloads the draft when setName is called', async () => {
    window.localStorage.setItem(
      'cbs.nova.draft.c2',
      JSON.stringify({ body: 'construct two draft', savedAt: Date.now() }),
    )

    const { body, restoredFromDraft, setName } = useWorkbenchDraft('c1')
    expect(body.value).toBe('')
    expect(restoredFromDraft.value).toBe(false)

    setName('c2')
    await flush()

    expect(body.value).toBe('construct two draft')
    expect(restoredFromDraft.value).toBe(true)
  })

  it('emits restored, saved and cleared events', async () => {
    window.localStorage.setItem(
      'cbs.nova.draft.c2',
      JSON.stringify({ body: 'restored body', savedAt: Date.now() }),
    )

    const restoredHandler = vi.fn()
    const savedHandler = vi.fn()
    const clearedHandler = vi.fn()

    const { body, clearDraft, setName, onRestored, onSaved, onCleared } = useWorkbenchDraft('c1')
    onRestored(restoredHandler)
    onSaved(savedHandler)
    onCleared(clearedHandler)

    setName('c2')
    await flush()

    expect(restoredHandler).toHaveBeenCalledTimes(1)
    expect(restoredHandler).toHaveBeenLastCalledWith(
      expect.objectContaining({ body: 'restored body' }),
    )

    body.value = 'edited'
    await vi.advanceTimersByTimeAsync(250)
    await flush()

    expect(savedHandler).toHaveBeenCalledTimes(1)
    expect(savedHandler).toHaveBeenLastCalledWith(expect.objectContaining({ body: 'edited' }))

    clearDraft()
    expect(clearedHandler).toHaveBeenCalledTimes(1)
  })

  it('is an SSR no-op when window is unavailable: no throw, no persisted state', async () => {
    const originalWindow = globalThis.window

    // @ts-expect-error simulate SSR — no window global at all
    delete globalThis.window

    try {
      expect(() => {
        const { body, dirty, lastSavedAt, restoredFromDraft, clearDraft } = useWorkbenchDraft('c1')

        expect(body.value).toBe('')
        expect(dirty.value).toBe(false)
        expect(lastSavedAt.value).toBeNull()
        expect(restoredFromDraft.value).toBe(false)

        body.value = 'no window here'
        clearDraft()
      }).not.toThrow()

      await vi.advanceTimersByTimeAsync(500)
    } finally {
      globalThis.window = originalWindow
    }

    // window restored — nothing should have leaked into storage
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  describe('server autosave (T401)', () => {
    function deferred<T>() {
      let resolve!: (value: T) => void
      let reject!: (reason?: unknown) => void
      const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
      })
      return { promise, resolve, reject }
    }

    it('debounces the server save by 3s and sends the latest body', async () => {
      const save = vi.fn().mockResolvedValue(555)
      const { body } = useWorkbenchDraft('c1', { server: { save, load: vi.fn() } })

      body.value = 'a'
      await vi.advanceTimersByTimeAsync(1000)
      body.value = 'ab'
      await vi.advanceTimersByTimeAsync(2999)
      expect(save).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(1)
      await flush()
      await flush()

      expect(save).toHaveBeenCalledTimes(1)
      expect(save).toHaveBeenCalledWith('ab')
    })

    it('echoes the server savedAt into the local copy on success', async () => {
      const save = vi.fn().mockResolvedValue(555)
      const { body, lastSavedAt, autosaveOffline } = useWorkbenchDraft('c1', {
        server: { save, load: vi.fn() },
      })

      body.value = 'hello'
      await vi.advanceTimersByTimeAsync(3000)
      await flush()
      await flush()

      expect(autosaveOffline.value).toBe(false)
      const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
      expect(parsed.body).toBe('hello')
      expect(parsed.savedAt).toBe(555)
      expect(lastSavedAt.value).toBe(555)
    })

    it('keeps the local copy and flags autosaveOffline when the server save fails', async () => {
      const save = vi.fn().mockRejectedValue(new Error('boom'))
      const { body, autosaveOffline } = useWorkbenchDraft('c1', {
        server: { save, load: vi.fn() },
      })

      body.value = 'offline body'
      await vi.advanceTimersByTimeAsync(3000)
      await flush()
      await flush()

      expect(autosaveOffline.value).toBe(true)
      const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
      expect(parsed.body).toBe('offline body')
    })

    it('skips while a save is pending and flushes a trailing save with the newest body', async () => {
      const pending = deferred<number>()
      const save = vi.fn().mockReturnValueOnce(pending.promise).mockResolvedValue(1)
      const { body } = useWorkbenchDraft('c1', { server: { save, load: vi.fn() } })

      body.value = 'first'
      await vi.advanceTimersByTimeAsync(3000)
      await flush()
      expect(save).toHaveBeenCalledTimes(1)
      expect(save).toHaveBeenLastCalledWith('first')

      // Change while the first save is still in flight.
      body.value = 'second'
      await vi.advanceTimersByTimeAsync(3000)
      await flush()
      expect(save).toHaveBeenCalledTimes(1)

      pending.resolve(111)
      await flush()
      await flush()

      expect(save).toHaveBeenCalledTimes(2)
      expect(save).toHaveBeenLastCalledWith('second')
    })

    it('does not re-save a body that was just restored from the server', async () => {
      const save = vi.fn().mockResolvedValue(1)
      const load = vi.fn().mockResolvedValue({ body: 'server body', savedAt: Date.now() - 1000 })
      const { body } = useWorkbenchDraft('c1', { server: { save, load } })
      await flush()
      await flush()

      expect(body.value).toBe('server body')
      await vi.advanceTimersByTimeAsync(5000)
      await flush()
      expect(save).not.toHaveBeenCalled()
    })
  })

  describe('server-vs-local precedence (T401)', () => {
    it('adopts the server draft when it is fresher than the local one', async () => {
      const now = Date.now()
      window.localStorage.setItem(KEY, JSON.stringify({ body: 'local body', savedAt: now - 2000 }))
      const load = vi.fn().mockResolvedValue({ body: 'server body', savedAt: now - 1000 })

      const { body, restoredFromDraft, lastSavedAt } = useWorkbenchDraft('c1', {
        server: { save: vi.fn(), load },
      })
      await flush()
      await flush()

      expect(body.value).toBe('server body')
      expect(restoredFromDraft.value).toBe(true)
      expect(lastSavedAt.value).toBe(now - 1000)
      // local copy is refreshed so the offline fallback matches the server
      const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
      expect(parsed).toEqual({ body: 'server body', savedAt: now - 1000 })
    })

    it('keeps the local draft when it is fresher than the server one', async () => {
      const now = Date.now()
      window.localStorage.setItem(KEY, JSON.stringify({ body: 'local body', savedAt: now - 1000 }))
      const load = vi.fn().mockResolvedValue({ body: 'server body', savedAt: now - 2000 })

      const { body, restoredFromDraft } = useWorkbenchDraft('c1', {
        server: { save: vi.fn(), load },
      })
      await flush()
      await flush()

      expect(body.value).toBe('local body')
      expect(restoredFromDraft.value).toBe(true)
    })

    it('keeps the local draft when savedAt values are equal', async () => {
      const now = Date.now()
      window.localStorage.setItem(KEY, JSON.stringify({ body: 'local body', savedAt: now - 1000 }))
      const load = vi.fn().mockResolvedValue({ body: 'server body', savedAt: now - 1000 })

      const { body } = useWorkbenchDraft('c1', { server: { save: vi.fn(), load } })
      await flush()
      await flush()

      expect(body.value).toBe('local body')
    })

    it('restores from the server when the local draft expired past the TTL', async () => {
      const staleSavedAt = Date.now() - (24 * 60 * 60 * 1000 + 1)
      window.localStorage.setItem(
        KEY,
        JSON.stringify({ body: 'stale body', savedAt: staleSavedAt }),
      )
      const load = vi.fn().mockResolvedValue({ body: 'server body', savedAt: Date.now() })

      const { body, restoredFromDraft } = useWorkbenchDraft('c1', {
        server: { save: vi.fn(), load },
      })
      await flush()
      await flush()

      expect(body.value).toBe('server body')
      expect(restoredFromDraft.value).toBe(true)
      const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
      expect(parsed.body).toBe('server body')
    })

    it('keeps the local draft when the server has none', async () => {
      window.localStorage.setItem(
        KEY,
        JSON.stringify({ body: 'local body', savedAt: Date.now() - 1000 }),
      )
      const load = vi.fn().mockResolvedValue(null)

      const { body } = useWorkbenchDraft('c1', { server: { save: vi.fn(), load } })
      await flush()
      await flush()

      expect(body.value).toBe('local body')
    })

    it('keeps the local draft when the server load fails', async () => {
      window.localStorage.setItem(
        KEY,
        JSON.stringify({ body: 'local body', savedAt: Date.now() - 1000 }),
      )
      const load = vi.fn().mockRejectedValue(new Error('backend down'))

      const { body } = useWorkbenchDraft('c1', { server: { save: vi.fn(), load } })
      await flush()
      await flush()

      expect(body.value).toBe('local body')
    })
  })

  describe('runtimeConfig overrides (T589)', () => {
    it('save debounce: longer public override delays the localStorage write', async () => {
      publicConfig.workbenchSaveDebounceMs = 1500

      const { body } = useWorkbenchDraft('c1')

      body.value = 'late save'
      // previous default 250ms window — must not flush yet
      await vi.advanceTimersByTimeAsync(250)
      expect(window.localStorage.getItem(KEY)).toBeNull()
      // half-way into the overridden window
      await vi.advanceTimersByTimeAsync(1000)
      expect(window.localStorage.getItem(KEY)).toBeNull()
      // cross the overridden boundary
      await vi.advanceTimersByTimeAsync(250)
      await flush()

      const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
      expect(parsed.body).toBe('late save')
    })

    it('save debounce: shorter public override flushes earlier than the default', async () => {
      publicConfig.workbenchSaveDebounceMs = 50

      const { body } = useWorkbenchDraft('c1')

      body.value = 'quick save'
      await vi.advanceTimersByTimeAsync(49)
      expect(window.localStorage.getItem(KEY)).toBeNull()
      await vi.advanceTimersByTimeAsync(1)
      await flush()

      const parsed = JSON.parse(window.localStorage.getItem(KEY) as string)
      expect(parsed.body).toBe('quick save')
    })

    it('TTL: a public override shortens the stale-draft window', () => {
      // 100ms override — anything older is treated as stale.
      publicConfig.workbenchDraftTtlMs = 100
      const staleSavedAt = Date.now() - 200
      window.localStorage.setItem(
        KEY,
        JSON.stringify({ body: 'stale body', savedAt: staleSavedAt }),
      )

      const { body, restoredFromDraft } = useWorkbenchDraft('c1')

      expect(body.value).toBe('')
      expect(restoredFromDraft.value).toBe(false)
      expect(window.localStorage.getItem(KEY)).toBeNull()
    })

    it('TTL: a longer-than-24h public override keeps drafts that the hardcoded TTL would drop', () => {
      // Hardcoded TTL would be 24h; bump it to 48h and check a 25h-old draft
      // survives.
      publicConfig.workbenchDraftTtlMs = 48 * 60 * 60 * 1000
      const olderThanHardcodedTtl = Date.now() - 25 * 60 * 60 * 1000
      window.localStorage.setItem(
        KEY,
        JSON.stringify({ body: 'still alive', savedAt: olderThanHardcodedTtl }),
      )

      const { body, restoredFromDraft } = useWorkbenchDraft('c1')

      expect(body.value).toBe('still alive')
      expect(restoredFromDraft.value).toBe(true)
    })

    it('server save debounce: public override changes the server-fire timing', async () => {
      const save = vi.fn().mockResolvedValue(1)
      publicConfig.workbenchServerSaveDebounceMs = 500

      const { body } = useWorkbenchDraft('c1', { server: { save, load: vi.fn() } })

      body.value = 'ping'
      // the hardcoded default would fire at 3000ms; with the override the
      // server save must already be in flight by 500ms
      await vi.advanceTimersByTimeAsync(499)
      expect(save).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      await flush()
      await flush()

      expect(save).toHaveBeenCalledTimes(1)
      expect(save).toHaveBeenCalledWith('ping')
    })

    it('falls back to defaults when useRuntimeConfig throws (non-Nuxt context)', () => {
      useRuntimeConfigMock.mockImplementation(() => {
        throw new Error('no nuxt context')
      })

      // No throw on setup despite the throw inside useRuntimeConfig.
      expect(() => useWorkbenchDraft('c1')).not.toThrow()
    })

    it('ignores non-positive overrides and uses the documented defaults', async () => {
      publicConfig.workbenchSaveDebounceMs = 0
      publicConfig.workbenchServerSaveDebounceMs = -1
      publicConfig.workbenchDraftTtlMs = 0

      // Debounce 0 must fall through to 250ms — saving must not happen
      // before the default window elapses.
      const save = vi.fn().mockResolvedValue(1)
      const { body } = useWorkbenchDraft('c1', { server: { save, load: vi.fn() } })

      body.value = 'honour default'
      await vi.advanceTimersByTimeAsync(249)
      expect(window.localStorage.getItem(KEY)).toBeNull()
      await vi.advanceTimersByTimeAsync(1)
      await flush()
      expect(JSON.parse(window.localStorage.getItem(KEY) as string).body).toBe('honour default')

      // Server debounce override of -1 must fall through to 3000ms — at
      // ~250ms the server save must still not have fired.
      expect(save).not.toHaveBeenCalled()

      // Advance to just before the default 3000ms server window closes
      // (local save already fired at ~250ms, so 3000ms from the body change
      // lands ~2750ms from now).
      await vi.advanceTimersByTimeAsync(2749)
      expect(save).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      await flush()
      await flush()
      expect(save).toHaveBeenCalledTimes(1)
    })
  })
})
