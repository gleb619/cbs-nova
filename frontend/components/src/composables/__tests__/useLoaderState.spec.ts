import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { createNamespacedLoaderState, useLoaderState } from '../useLoaderState'

describe('useLoaderState', () => {
  it('starts false', () => {
    const loader = useLoaderState('demo')

    expect(loader.value).toBe(false)
  })

  it('returns same ref for same key', async () => {
    const a = useLoaderState('shared')
    const b = useLoaderState('shared')

    a.value = true
    await nextTick()

    expect(a).toBe(b)
    expect(b.value).toBe(true)
  })

  it('isolates keys under default namespace', () => {
    const one = useLoaderState('one')
    const two = useLoaderState('two')

    one.value = true

    expect(two.value).toBe(false)
  })

  it('isolates namespaced keys from global keys', () => {
    const globalLoader = useLoaderState('token')
    const namespacedLoader = useLoaderState('token', { namespace: 'app:auth' })

    globalLoader.value = true

    expect(namespacedLoader.value).toBe(false)
  })

  it('isolates keys across namespaces', () => {
    const a = useLoaderState('list', { namespace: 'app:a' })
    const b = useLoaderState('list', { namespace: 'app:b' })

    a.value = true

    expect(b.value).toBe(false)
  })

  it('exposes start/stop helpers', () => {
    const loader = useLoaderState('helpers')

    loader.start()
    expect(loader.value).toBe(true)

    loader.stop()
    expect(loader.value).toBe(false)
  })
})

describe('createNamespacedLoaderState', () => {
  it('creates a scoped composable that shares state', async () => {
    const usePageLoader = createNamespacedLoaderState('app:page')

    const sidebar = usePageLoader('sidebar')
    const sidebarAlt = usePageLoader('sidebar')

    expect(sidebar.value).toBe(false)

    sidebar.start()
    await nextTick()

    expect(sidebarAlt.value).toBe(true)
    expect(sidebar.value).toBe(true)
  })

  it('keeps different namespaces independent', () => {
    const usePageALoader = createNamespacedLoaderState('app:a')
    const usePageBLoader = createNamespacedLoaderState('app:b')

    const a = usePageALoader('table')
    const b = usePageBLoader('table')

    a.start()

    expect(b.value).toBe(false)
  })
})
