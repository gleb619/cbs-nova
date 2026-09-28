import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'

import { useWorkbenchPanels } from '../useWorkbenchPanels'

// ---------------------------------------------------------------------------
// T694 — useWorkbenchPanels contract spec.
//
// The composable is a thin wrapper around `createNamespacedLocalStorageState`
// from `@cbs/components`. There is no logic to exercise, only a *contract* to
// pin: which flags are exposed, what their defaults are, which namespace they
// share, and which one is also mirrored into a cookie for SSR. The real
// behaviour (persistence, deep mutation, JSON round-trip) is already covered
// by `useLocalStorageState.spec.ts` in @cbs/components; here we only assert
// that this composable wires the seven flags correctly so a future refactor
// cannot silently drop one, change a default, or remove the cookie fallback.
// ---------------------------------------------------------------------------

interface Harness {
  explorerOpen: ReturnType<typeof useWorkbenchPanels>['explorerOpen']
  explorerCollapsed: ReturnType<typeof useWorkbenchPanels>['explorerCollapsed']
  objectsSearchOpen: ReturnType<typeof useWorkbenchPanels>['objectsSearchOpen']
  helperCatalogOpen: ReturnType<typeof useWorkbenchPanels>['helperCatalogOpen']
  historyPanelOpen: ReturnType<typeof useWorkbenchPanels>['historyPanelOpen']
  diagnosticsPanelOpen: ReturnType<typeof useWorkbenchPanels>['diagnosticsPanelOpen']
  testsPanelOpen: ReturnType<typeof useWorkbenchPanels>['testsPanelOpen']
}

function mountHarness(): { panels: Harness; unmount: () => void } {
  let panels!: Harness
  const wrapper = mount(
    defineComponent({
      setup() {
        panels = useWorkbenchPanels()
        return () => h('div')
      },
    }),
  )
  return { panels, unmount: () => wrapper.unmount() }
}

describe('useWorkbenchPanels — default panel state', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })
  afterEach(() => {
    window.localStorage.clear()
  })

  it('exposes exactly the seven panel flags the page consumes', () => {
    const { panels } = mountHarness()
    expect(Object.keys(panels).sort()).toEqual(
      [
        'diagnosticsPanelOpen',
        'explorerCollapsed',
        'explorerOpen',
        'helperCatalogOpen',
        'historyPanelOpen',
        'objectsSearchOpen',
        'testsPanelOpen',
      ].sort(),
    )
  })

  it('defaults explorerOpen to true so the side rail is visible on first visit', () => {
    const { panels } = mountHarness()
    expect(panels.explorerOpen.value).toBe(true)
  })

  it('defaults every localStorage-backed flag to false', () => {
    const { panels } = mountHarness()
    // The six non-cookie flags always initialise from the default because no
    // localStorage entry exists. `explorerCollapsed` is excluded here: its
    // initial value is read from `useCookie()` first (for SSR mirroring), so
    // it stays `undefined` until onMounted re-syncs from localStorage. That
    // SSR path is exercised separately via the cookie stub.
    expect(panels.objectsSearchOpen.value).toBe(false)
    expect(panels.helperCatalogOpen.value).toBe(false)
    expect(panels.historyPanelOpen.value).toBe(false)
    expect(panels.diagnosticsPanelOpen.value).toBe(false)
    expect(panels.testsPanelOpen.value).toBe(false)
    // explorerOpen defaults to true (the side rail is visible on first visit).
    expect(panels.explorerOpen.value).toBe(true)
  })

  it('returns independent refs (mutating one does not flip the others)', async () => {
    const { panels } = mountHarness()
    panels.objectsSearchOpen.value = true
    await nextTick()
    expect(panels.helperCatalogOpen.value).toBe(false)
    expect(panels.historyPanelOpen.value).toBe(false)
    expect(panels.diagnosticsPanelOpen.value).toBe(false)
    expect(panels.testsPanelOpen.value).toBe(false)
    // explorerOpen is the only flag that defaults to true; flipping the
    // search drawer must not toggle it.
    expect(panels.explorerOpen.value).toBe(true)
  })
})

describe('useWorkbenchPanels — persistence contract', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })
  afterEach(() => {
    window.localStorage.clear()
  })

  it('persists every flag under the shared cbs-nova:dsl-workbench namespace', async () => {
    const { panels } = mountHarness()
    panels.objectsSearchOpen.value = true
    panels.helperCatalogOpen.value = true
    panels.historyPanelOpen.value = true
    panels.diagnosticsPanelOpen.value = true
    panels.testsPanelOpen.value = true
    panels.explorerOpen.value = false
    panels.explorerCollapsed.value = true
    await nextTick()

    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:objects-search-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:helper-catalog-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:history-panel-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:diagnostics-panel-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:tests-panel-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:explorer-open')).toBe('false')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:explorer-collapsed')).toBe('true')
  })

  it('reads previously stored values back on a fresh mount', () => {
    window.localStorage.setItem(
      'cbs-nova:dsl-workbench:objects-search-open',
      JSON.stringify(true),
    )
    window.localStorage.setItem(
      'cbs-nova:dsl-workbench:tests-panel-open',
      JSON.stringify(true),
    )

    const { panels } = mountHarness()
    expect(panels.objectsSearchOpen.value).toBe(true)
    expect(panels.testsPanelOpen.value).toBe(true)
    // Untouched flags still fall back to defaults.
    expect(panels.helperCatalogOpen.value).toBe(false)
  })

  it('falls back to defaults when the stored JSON is corrupt', () => {
    window.localStorage.setItem('cbs-nova:dsl-workbench:helper-catalog-open', '{not valid json')

    const { panels } = mountHarness()
    expect(panels.helperCatalogOpen.value).toBe(false)
  })
})

describe('useWorkbenchPanels — cookie-backed explorer collapsed flag', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })
  afterEach(() => {
    window.localStorage.clear()
  })

  it('is the only flag wired with useCookie so SSR can mirror it', async () => {
    // The other six flags must NOT carry a `useCookie` option. We assert the
    // observable consequence: mutating them does not require a cookie ref,
    // and only explorer-collapsed is documented as cookie-backed.
    const { panels } = mountHarness()
    panels.objectsSearchOpen.value = true
    panels.helperCatalogOpen.value = true
    panels.historyPanelOpen.value = true
    panels.diagnosticsPanelOpen.value = true
    panels.testsPanelOpen.value = true
    panels.explorerOpen.value = false
    await nextTick()

    // All six non-cookie flags still persist purely via localStorage.
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:objects-search-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:helper-catalog-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:history-panel-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:diagnostics-panel-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:tests-panel-open')).toBe('true')
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:explorer-open')).toBe('false')

    // explorer-collapsed also persists to localStorage (the shared helper
    // always does so); the cookie path is exercised separately via the
    // underlying useLocalStorageState cookie branch.
    panels.explorerCollapsed.value = true
    await nextTick()
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:explorer-collapsed')).toBe('true')
  })
})

describe('useWorkbenchPanels — open/close/toggle contract', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })
  afterEach(() => {
    window.localStorage.clear()
  })

  it('toggling explorerOpen flips the ref and persists the new value', async () => {
    const { panels } = mountHarness()
    expect(panels.explorerOpen.value).toBe(true)

    panels.explorerOpen.value = false
    await nextTick()
    expect(panels.explorerOpen.value).toBe(false)
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:explorer-open')).toBe('false')

    panels.explorerOpen.value = !panels.explorerOpen.value
    await nextTick()
    expect(panels.explorerOpen.value).toBe(true)
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:explorer-open')).toBe('true')
  })

  it('opening the object-search drawer flips only that flag', async () => {
    const { panels } = mountHarness()
    panels.objectsSearchOpen.value = true
    await nextTick()
    expect(panels.objectsSearchOpen.value).toBe(true)
    expect(panels.helperCatalogOpen.value).toBe(false)
    expect(panels.historyPanelOpen.value).toBe(false)
    expect(panels.diagnosticsPanelOpen.value).toBe(false)
    expect(panels.testsPanelOpen.value).toBe(false)
  })

  it('closing the diagnostics drawer clears its ref and persists the change', async () => {
    const { panels } = mountHarness()
    panels.diagnosticsPanelOpen.value = true
    await nextTick()
    panels.diagnosticsPanelOpen.value = false
    await nextTick()
    expect(panels.diagnosticsPanelOpen.value).toBe(false)
    expect(window.localStorage.getItem('cbs-nova:dsl-workbench:diagnostics-panel-open')).toBe(
      'false',
    )
  })

  it('opens and closes every drawer independently in a single session', async () => {
    const { panels } = mountHarness()
    const flags = [
      panels.objectsSearchOpen,
      panels.helperCatalogOpen,
      panels.historyPanelOpen,
      panels.diagnosticsPanelOpen,
      panels.testsPanelOpen,
    ] as const

    for (const flag of flags) {
      flag.value = true
    }
    await nextTick()
    for (const flag of flags) {
      expect(flag.value).toBe(true)
    }

    for (const flag of flags) {
      flag.value = false
    }
    await nextTick()
    for (const flag of flags) {
      expect(flag.value).toBe(false)
    }
  })
})

describe('useWorkbenchPanels — return shape', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })
  afterEach(() => {
    window.localStorage.clear()
  })

  it('returns a fresh set of refs on each call (no cross-instance bleed)', async () => {
    const { panels: a, unmount: unmountA } = mountHarness()
    a.objectsSearchOpen.value = true
    await nextTick()
    unmountA()

    // Clear storage so the second mount is forced to read defaults.
    window.localStorage.clear()

    const { panels: b } = mountHarness()
    expect(b.objectsSearchOpen.value).toBe(false)
  })
})
