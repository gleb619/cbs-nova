import type { LocationQuery } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkbenchDeepLink } from '../useWorkbenchDeepLink'

function buildHarness(routeQuery: LocationQuery) {
  const setExplorerFilter = vi.fn()
  const safeSelectConstruct = vi.fn()
  const selectConstruct = vi.fn()

  const harness = useWorkbenchDeepLink({
    routeQuery,
    setExplorerFilter,
    safeSelectConstruct,
    selectConstruct,
  })
  return { ...harness, setExplorerFilter, safeSelectConstruct, selectConstruct }
}

describe('useWorkbenchDeepLink — resolveDeepLink', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/workbench')
  })

  it('selects the construct named in ?objectName= and strips the param', () => {
    const { resolveDeepLink, safeSelectConstruct } = buildHarness({ objectName: 'alpha' })
    resolveDeepLink()
    expect(safeSelectConstruct).toHaveBeenCalledWith('alpha')
    expect(new URL(window.location.href).searchParams.has('objectName')).toBe(false)
  })

  it('does nothing when ?objectName= is absent', () => {
    const { resolveDeepLink, safeSelectConstruct } = buildHarness({})
    resolveDeepLink()
    expect(safeSelectConstruct).not.toHaveBeenCalled()
  })

  it('does nothing when ?objectName= is empty', () => {
    const { resolveDeepLink, safeSelectConstruct } = buildHarness({ objectName: '' })
    resolveDeepLink()
    expect(safeSelectConstruct).not.toHaveBeenCalled()
  })

  it('uses the first value for array params (?objectName=a&objectName=b)', () => {
    const { resolveDeepLink, safeSelectConstruct } = buildHarness({
      objectName: ['alpha', 'beta'],
    })
    resolveDeepLink()
    expect(safeSelectConstruct).toHaveBeenCalledWith('alpha')
    expect(safeSelectConstruct).toHaveBeenCalledTimes(1)
  })
})

describe('useWorkbenchDeepLink — applyObjectName', () => {
  it('writes the explorer filter when ?objectName= is non-empty', () => {
    const { applyObjectName, setExplorerFilter } = buildHarness({ objectName: 'alpha' })
    applyObjectName()
    expect(setExplorerFilter).toHaveBeenCalledWith('alpha')
  })

  it('skips the filter write when ?objectName= is absent or empty', () => {
    for (const routeQuery of [{}, { objectName: '' }, { objectName: null }] as LocationQuery[]) {
      const { applyObjectName, setExplorerFilter } = buildHarness(routeQuery)
      applyObjectName()
      expect(setExplorerFilter).not.toHaveBeenCalled()
    }
  })

  it('uses the first value for array params', () => {
    const { applyObjectName, setExplorerFilter } = buildHarness({
      objectName: ['alpha', 'beta'],
    })
    applyObjectName()
    expect(setExplorerFilter).toHaveBeenCalledWith('alpha')
  })
})

describe('useWorkbenchDeepLink — applyActiveTab', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('emits cbs:body-editor:set-tab with the tab for a valid ?activeTab=', () => {
    const listener = vi.fn()
    window.addEventListener('cbs:body-editor:set-tab', listener)
    const { applyActiveTab } = buildHarness({ activeTab: 'preview' })
    applyActiveTab()
    expect(listener).toHaveBeenCalledTimes(1)
    const event = listener.mock.calls[0][0] as CustomEvent
    expect(event.detail).toBe('preview')
    window.removeEventListener('cbs:body-editor:set-tab', listener)
  })

  it('ignores an invalid tab name (BODY_EDITOR_TABS guard)', () => {
    const listener = vi.fn()
    window.addEventListener('cbs:body-editor:set-tab', listener)
    const { applyActiveTab } = buildHarness({ activeTab: 'bogus' })
    applyActiveTab()
    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener('cbs:body-editor:set-tab', listener)
  })

  it('does nothing when ?activeTab= is absent or empty', () => {
    const listener = vi.fn()
    window.addEventListener('cbs:body-editor:set-tab', listener)
    for (const routeQuery of [{}, { activeTab: '' }] as LocationQuery[]) {
      const { applyActiveTab } = buildHarness(routeQuery)
      applyActiveTab()
    }
    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener('cbs:body-editor:set-tab', listener)
  })
})

describe('useWorkbenchDeepLink — applyDraft', () => {
  it('returns true and selects the draft when ?draft= is non-empty', () => {
    const { applyDraft, selectConstruct } = buildHarness({ draft: 'helperOne' })
    expect(applyDraft()).toBe(true)
    expect(selectConstruct).toHaveBeenCalledWith('helperOne')
  })

  it('returns false without selecting when ?draft= is absent', () => {
    const { applyDraft, selectConstruct } = buildHarness({})
    expect(applyDraft()).toBe(false)
    expect(selectConstruct).not.toHaveBeenCalled()
  })

  it('returns false without selecting when ?draft= is empty', () => {
    const { applyDraft, selectConstruct } = buildHarness({ draft: '' })
    expect(applyDraft()).toBe(false)
    expect(selectConstruct).not.toHaveBeenCalled()
  })
})
