import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useLocalStorageState } from '../useLocalStorageState'

describe('useLocalStorageState', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('returns the stored value when localStorage has valid JSON for the key', () => {
    window.localStorage.setItem('theme', JSON.stringify({ mode: 'dark' }))

    const state = useLocalStorageState<{ mode: string }>('theme', { mode: 'light' })

    expect(state.value).toEqual({ mode: 'dark' })
  })

  it('returns defaultValue when the key is absent (null)', () => {
    const state = useLocalStorageState('missing', 'fallback')

    expect(state.value).toBe('fallback')
    expect(window.localStorage.getItem('missing')).toBeNull()
  })

  it('returns defaultValue when stored JSON is corrupt', () => {
    window.localStorage.setItem('broken', '{not valid json')

    const state = useLocalStorageState('broken', 'fallback')

    expect(state.value).toBe('fallback')
  })

  it('uses custom read and write options instead of the JSON defaults', async () => {
    window.localStorage.setItem('custom', 'raw-value')
    const read = vi.fn((raw: string | null) => (raw === null ? 'read-default' : `read:${raw}`))
    const write = vi.fn((value: string) => `write:${value}`)

    const state = useLocalStorageState<string>('custom', 'none', { read, write })

    expect(read).toHaveBeenCalledWith('raw-value')
    expect(state.value).toBe('read:raw-value')

    state.value = 'updated'
    await nextTick()

    expect(write).toHaveBeenCalledWith('updated')
    expect(window.localStorage.getItem('custom')).toBe('write:updated')
  })

  it('persists ref mutations via setItem with a serialized value', async () => {
    const state = useLocalStorageState<number>('count', 0)

    state.value = 42
    await nextTick()

    expect(window.localStorage.getItem('count')).toBe(JSON.stringify(42))
  })

  it('persists nested/deep mutations of the returned ref', async () => {
    const state = useLocalStorageState<{ name: string; filters: string[] }>('filters', {
      name: 'n',
      filters: ['a'],
    })

    state.value.name = 'changed'
    state.value.filters.push('b')
    await nextTick()

    expect(window.localStorage.getItem('filters')).toBe(
      JSON.stringify({ name: 'changed', filters: ['a', 'b'] }),
    )
  })

  it('is SSR-safe: does not access window when typeof window is undefined', async () => {
    vi.stubGlobal('window', undefined)

    const state = useLocalStorageState('ssr', 'server-default')

    expect(state.value).toBe('server-default')

    state.value = 'changed'
    await nextTick()

    expect(state.value).toBe('changed')
  })
})