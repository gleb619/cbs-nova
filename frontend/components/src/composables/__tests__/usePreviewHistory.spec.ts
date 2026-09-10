import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import {
  __resetPreviewHistoryForTests,
  PREVIEW_HISTORY_STORAGE_KEY,
  PREVIEW_HISTORY_STORAGE_NAMESPACE,
  usePreviewHistory,
} from '../usePreviewHistory'

function runInSetup<T>(factory: () => T): T {
  let result: T | undefined
  const Comp = defineComponent({
    setup() {
      result = factory()
      return () => h('div')
    },
  })
  mount(Comp)
  return result as T
}

function storageKey(): string {
  return `${PREVIEW_HISTORY_STORAGE_NAMESPACE}:${PREVIEW_HISTORY_STORAGE_KEY}`
}

describe('usePreviewHistory', () => {
  beforeEach(() => {
    window.localStorage.clear()
    __resetPreviewHistoryForTests()
  })

  it('records an entry and persists it to localStorage', async () => {
    const history = runInSetup(() => usePreviewHistory(() => 'demo'))

    history.record({ name: 'demo', payload: { a: 1 }, status: 'success' })

    expect(history.entries.value).toHaveLength(1)
    expect(history.entries.value[0].payload).toEqual({ a: 1 })
    expect(history.entries.value[0].id).toBeTruthy()
    expect(history.entries.value[0].startedAt).toBeTruthy()

    await nextTick()
    const stored = JSON.parse(window.localStorage.getItem(storageKey()) ?? '[]')
    expect(stored).toHaveLength(1)
    expect(stored[0].name).toBe('demo')
  })

  it('keeps only the newest entries per construct up to the limit', () => {
    const history = runInSetup(() => usePreviewHistory(() => 'demo', { limit: 3 }))

    for (let i = 0; i < 5; i++) {
      history.record({ name: 'demo', payload: { i }, status: 'success' })
    }

    expect(history.entries.value).toHaveLength(3)
    expect(history.entries.value[0].payload).toEqual({ i: 4 })
    expect(history.entries.value[2].payload).toEqual({ i: 2 })
  })

  it('scopes entries per construct name', () => {
    const history = runInSetup(() => usePreviewHistory(() => 'demo'))

    history.record({ name: 'demo', payload: { a: 1 }, status: 'success' })
    history.record({ name: 'other', payload: { b: 2 }, status: 'success' })

    expect(history.entries.value).toHaveLength(1)
    expect(history.entries.value[0].payload).toEqual({ a: 1 })
  })

  it('shares entries between instances', () => {
    const first = runInSetup(() => usePreviewHistory(() => 'demo'))
    const second = runInSetup(() => usePreviewHistory(() => 'demo'))

    first.record({ name: 'demo', payload: { a: 1 }, status: 'success' })

    expect(second.entries.value).toHaveLength(1)
  })

  it('removes a single entry by id', () => {
    const history = runInSetup(() => usePreviewHistory(() => 'demo'))

    const first = history.record({ name: 'demo', payload: { a: 1 }, status: 'success' })
    history.record({ name: 'demo', payload: { b: 2 }, status: 'success' })

    history.remove(first.id)

    expect(history.entries.value).toHaveLength(1)
    expect(history.entries.value[0].payload).toEqual({ b: 2 })
  })

  it('clears only entries for the current construct', async () => {
    const history = runInSetup(() => usePreviewHistory(() => 'demo'))

    history.record({ name: 'demo', payload: { a: 1 }, status: 'success' })
    history.record({ name: 'other', payload: { b: 2 }, status: 'success' })

    history.clear()

    expect(history.entries.value).toHaveLength(0)

    await nextTick()
    const stored = JSON.parse(window.localStorage.getItem(storageKey()) ?? '[]')
    expect(stored).toHaveLength(1)
    expect(stored[0].name).toBe('other')
  })

  it('restores persisted entries after remount', () => {
    const first = runInSetup(() => usePreviewHistory(() => 'demo'))
    first.record({ name: 'demo', payload: { a: 1 }, status: 'success' })

    const second = runInSetup(() => usePreviewHistory(() => 'demo'))
    expect(second.entries.value).toHaveLength(1)
    expect(second.entries.value[0].payload).toEqual({ a: 1 })
  })
})
