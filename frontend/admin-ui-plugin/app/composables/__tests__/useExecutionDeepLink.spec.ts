import type { Mock } from 'vitest'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useRoute, useRouter } from 'nuxt/app'

vi.mock('nuxt/app', () => ({
  useRoute: vi.fn(),
  useRouter: vi.fn(),
}))

import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { type ExecutionDetailTab, useExecutionDeepLink } from '../useExecutionDeepLink'

function buildHarness(
  initialQuery: Record<string, string | string[]>,
  allTabs: readonly ExecutionDetailTab[] = [
    'payload',
    'metadata',
    'logs',
    'errors',
    'transactions',
  ],
) {
  const replace = vi.fn(() => Promise.resolve())
  const route = reactive({ query: initialQuery })

  vi.mocked(useRoute as Mock).mockReturnValue(route as ReturnType<typeof useRoute>)
  vi.mocked(useRouter as Mock).mockReturnValue({ replace } as ReturnType<typeof useRouter>)

  const availableTabs = ref(allTabs)
  let result: ReturnType<typeof useExecutionDeepLink> | undefined

  const Comp = defineComponent({
    setup() {
      result = useExecutionDeepLink({ availableTabs })
      return () => h('div')
    },
  })

  mount(Comp)

  return {
    result: result!,
    replace,
    route,
    availableTabs,
  }
}

describe('useExecutionDeepLink — param parsing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sets activeTab from ?activeTab=', () => {
    const { result } = buildHarness({ activeTab: 'logs' })
    expect(result.activeTab.value).toBe('logs')
    expect(result.visibleTab.value).toBe('logs')
  })

  it('falls back to payload for an invalid ?activeTab=', () => {
    const { result } = buildHarness({ activeTab: 'bogus' })
    expect(result.activeTab.value).toBe('payload')
    expect(result.visibleTab.value).toBe('payload')
  })

  it('falls back to payload when ?activeTab= is absent', () => {
    const { result } = buildHarness({})
    expect(result.activeTab.value).toBe('payload')
    expect(result.visibleTab.value).toBe('payload')
  })

  it('falls back to payload when ?activeTab= is empty', () => {
    const { result } = buildHarness({ activeTab: '' })
    expect(result.activeTab.value).toBe('payload')
    expect(result.visibleTab.value).toBe('payload')
  })

  it('uses the first value for array params (?activeTab=a&activeTab=b)', () => {
    const { result } = buildHarness({ activeTab: ['errors', 'logs'] })
    expect(result.activeTab.value).toBe('errors')
  })

  it('falls back to payload when the requested tab is unavailable', () => {
    const { result } = buildHarness({ activeTab: 'logs' }, ['payload', 'metadata', 'errors'])
    expect(result.activeTab.value).toBe('logs')
    expect(result.visibleTab.value).toBe('payload')
  })
})

describe('useExecutionDeepLink — URL update', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('updates the URL with replace when the tab changes', async () => {
    const { result, replace } = buildHarness({})
    result.activeTab.value = 'errors'
    await nextTick()
    expect(replace).toHaveBeenCalledTimes(1)
    expect(replace).toHaveBeenCalledWith({ query: { activeTab: 'errors' } })
  })

  it('preserves other query params when replacing', async () => {
    const { result, replace } = buildHarness({ foo: 'bar', activeTab: 'payload' })
    result.activeTab.value = 'metadata'
    await nextTick()
    expect(replace).toHaveBeenCalledWith({ query: { foo: 'bar', activeTab: 'metadata' } })
  })

  it('does not replace the URL on mount when the query already matches', () => {
    const { replace } = buildHarness({ activeTab: 'payload' })
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('useExecutionDeepLink — route query changes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('restores the tab when the route query changes', async () => {
    const { result, route, replace } = buildHarness({ activeTab: 'payload' })
    route.query.activeTab = 'logs'
    await nextTick()
    expect(result.activeTab.value).toBe('logs')
    expect(result.visibleTab.value).toBe('logs')
    expect(replace).not.toHaveBeenCalled()
  })

  it('falls back to payload when the new route query is invalid', async () => {
    const { result, route, replace } = buildHarness({ activeTab: 'errors' })
    route.query.activeTab = 'bogus'
    await nextTick()
    expect(result.activeTab.value).toBe('payload')
    expect(result.visibleTab.value).toBe('payload')
    expect(replace).toHaveBeenCalledWith({ query: { activeTab: 'payload' } })
  })
})
