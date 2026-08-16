import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useWorkbenchDraft } from '../useWorkbenchDraft'

const KEY = 'cbs.nova.draft.c1'

const flush = async () => {
  await vi.advanceTimersByTimeAsync(0)
  await Promise.resolve()
}

describe('useWorkbenchDraft', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.localStorage.clear()
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

  it('reloads the draft when a reactive name ref changes', async () => {
    window.localStorage.setItem(
      'cbs.nova.draft.c2',
      JSON.stringify({ body: 'construct two draft', savedAt: Date.now() }),
    )

    const name = ref('c1')
    const { body, restoredFromDraft } = useWorkbenchDraft(name)
    expect(body.value).toBe('')
    expect(restoredFromDraft.value).toBe(false)

    name.value = 'c2'
    await flush()

    expect(body.value).toBe('construct two draft')
    expect(restoredFromDraft.value).toBe(true)
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
})
