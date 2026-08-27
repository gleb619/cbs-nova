import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import { createNamespacedLocalStorageState, useLocalStorageState } from '../useLocalStorageState'

function runInSetup<T>(factory: () => Ref<T>): Ref<T> {
  let state: Ref<T> | undefined
  const Comp = defineComponent({
    setup() {
      state = factory()
      return () => h('div')
    },
  })
  mount(Comp)
  return state as Ref<T>
}

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

    const state = runInSetup(() =>
      useLocalStorageState<{ mode: string }>('theme', { mode: 'light' }),
    )

    expect(state.value).toEqual({ mode: 'dark' })
  })

  it('returns defaultValue when the key is absent (null)', () => {
    const state = runInSetup(() => useLocalStorageState('missing', 'fallback'))

    expect(state.value).toBe('fallback')
    expect(window.localStorage.getItem('missing')).toBeNull()
  })

  it('returns defaultValue when stored JSON is corrupt', () => {
    window.localStorage.setItem('broken', '{not valid json')

    const state = runInSetup(() => useLocalStorageState('broken', 'fallback'))

    expect(state.value).toBe('fallback')
  })

  it('uses custom read and write options instead of the JSON defaults', async () => {
    window.localStorage.setItem('custom', 'raw-value')
    const read = vi.fn((raw: string | null) => (raw === null ? 'read-default' : `read:${raw}`))
    const write = vi.fn((value: string) => `write:${value}`)

    const state = runInSetup(() => useLocalStorageState<string>('custom', 'none', { read, write }))

    expect(read).toHaveBeenCalledWith('raw-value')
    expect(state.value).toBe('read:raw-value')

    state.value = 'updated'
    await nextTick()

    expect(write).toHaveBeenCalledWith('updated')
    expect(window.localStorage.getItem('custom')).toBe('write:updated')
  })

  it('persists ref mutations via setItem with a serialized value', async () => {
    const state = runInSetup(() => useLocalStorageState<number>('count', 0))

    state.value = 42
    await nextTick()

    expect(window.localStorage.getItem('count')).toBe(JSON.stringify(42))
  })

  it('persists nested/deep mutations of the returned ref', async () => {
    const state = runInSetup(() =>
      useLocalStorageState<{ name: string; filters: string[] }>('filters', {
        name: 'n',
        filters: ['a'],
      }),
    )

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

  it('prefixes the storage key with namespace when provided', async () => {
    const state = runInSetup(() =>
      useLocalStorageState<boolean>('flag', false, { namespace: 'app:dashboard' }),
    )

    state.value = true
    await nextTick()

    expect(window.localStorage.getItem('app:dashboard:flag')).toBe('true')
    expect(window.localStorage.getItem('flag')).toBeNull()
  })

  it('reads a previously stored namespaced value', () => {
    window.localStorage.setItem('app:dashboard:theme', JSON.stringify('dark'))

    const state = runInSetup(() =>
      useLocalStorageState<string>('theme', 'light', { namespace: 'app:dashboard' }),
    )

    expect(state.value).toBe('dark')
  })
})

describe('createNamespacedLocalStorageState', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('creates a scoped composable that reuses the same namespace', async () => {
    const usePageStorage = createNamespacedLocalStorageState('app:page')

    const sidebarOpen = runInSetup(() => usePageStorage('sidebar-open', true))
    const sidebarOpenAlt = runInSetup(() => usePageStorage('sidebar-open', false))

    expect(sidebarOpen.value).toBe(true)

    sidebarOpen.value = false
    await nextTick()

    expect(sidebarOpenAlt.value).toBe(false)
    expect(window.localStorage.getItem('app:page:sidebar-open')).toBe('false')
  })
})
