import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import WorkbenchDrawers from '../WorkbenchDrawers.vue'

// The four drawer bodies are sibling components the drawers embed; stubbing
// keeps this spec focused on the drawer open/close wiring. CbsDrawer itself
// stays real so data-testid and the close button wiring are exercised.
function stubPanel(name: string, emitEvent?: string) {
  return defineComponent({
    name,
    emits: emitEvent ? [emitEvent] : [],
    setup(_props, { emit }) {
      return () =>
        h(
          'div',
          {
            'data-testid': `${name}-stub`,
            onClick: () => emitEvent && emit(emitEvent as never),
          },
          name,
        )
    },
  })
}

function mountDrawers(modelProps: Record<string, boolean> = {}) {
  return mount(WorkbenchDrawers, {
    props: {
      selectedName: 'Demo',
      loadHelpersPage: vi.fn().mockResolvedValue({ items: [] }),
      listPublishHistory: vi.fn().mockResolvedValue([]),
      getHistoryEntry: vi.fn().mockResolvedValue({}),
      getHistoryDiff: vi.fn().mockResolvedValue({}),
      restorePublishHistory: vi.fn().mockResolvedValue({ ok: true }),
      fetchDiagnostics: vi.fn().mockResolvedValue({}),
      fetchDefinitionTests: vi.fn().mockResolvedValue({}),
      saveDefinitionTests: vi.fn().mockResolvedValue({ ok: true }),
      runDefinitionTests: vi.fn().mockResolvedValue({}),
      ...modelProps,
    },
    global: {
      stubs: {
        // Stub keys must match the components' inferred SFC names
        // (filename-based), not the @cbs/components export aliases.
        HelperCatalog: stubPanel('HelperCatalog'),
        DslHistoryPanel: stubPanel('DslHistoryPanel', 'restored'),
        DiagnosticsHistoryPanel: stubPanel('DiagnosticsHistoryPanel'),
        DefinitionTestsPanel: stubPanel('DefinitionTestsPanel'),
      },
    },
    attachTo: document.body,
  })
}

describe('WorkbenchDrawers', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('renders no drawers when all models are closed', () => {
    const wrapper = mountDrawers()
    expect(document.querySelector('[data-testid="helper-catalog-drawer"]')).toBeNull()
    expect(document.querySelector('[data-testid="history-drawer"]')).toBeNull()
    expect(document.querySelector('[data-testid="diagnostics-drawer"]')).toBeNull()
    expect(document.querySelector('[data-testid="tests-drawer"]')).toBeNull()
    wrapper.unmount()
  })

  it('shows the helper catalog drawer when helperCatalogOpen is true', async () => {
    const wrapper = mountDrawers({ helperCatalogOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="helper-catalog-drawer"]')).not.toBeNull()
    })
    expect(document.querySelector('[data-testid="HelperCatalog-stub"]')).not.toBeNull()
    wrapper.unmount()
  })

  it('shows the history drawer when historyPanelOpen is true', async () => {
    const wrapper = mountDrawers({ historyPanelOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="history-drawer"]')).not.toBeNull()
    })
    expect(document.querySelector('[data-testid="DslHistoryPanel-stub"]')).not.toBeNull()
    wrapper.unmount()
  })

  it('shows the diagnostics drawer when diagnosticsPanelOpen is true', async () => {
    const wrapper = mountDrawers({ diagnosticsPanelOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="diagnostics-drawer"]')).not.toBeNull()
    })
    wrapper.unmount()
  })

  it('shows the tests drawer when testsPanelOpen is true', async () => {
    const wrapper = mountDrawers({ testsPanelOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="tests-drawer"]')).not.toBeNull()
    })
    wrapper.unmount()
  })

  it('emits update:helperCatalogOpen false when the helper catalog close button is clicked', async () => {
    const wrapper = mountDrawers({ helperCatalogOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="helper-catalog-drawer"]')).not.toBeNull()
    })

    const closeButton = document.querySelector(
      '[data-testid="helper-catalog-drawer"] [data-testid="drawer-close-button"]',
    ) as HTMLElement
    closeButton.click()
    await vi.waitFor(() => {
      expect(wrapper.emitted('update:helperCatalogOpen')?.flat()).toContain(false)
    })
    wrapper.unmount()
  })

  it('emits update:testsPanelOpen false when the tests drawer close button is clicked', async () => {
    const wrapper = mountDrawers({ testsPanelOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="tests-drawer"]')).not.toBeNull()
    })

    const closeButton = document.querySelector(
      '[data-testid="tests-drawer"] [data-testid="drawer-close-button"]',
    ) as HTMLElement
    closeButton.click()
    await vi.waitFor(() => {
      expect(wrapper.emitted('update:testsPanelOpen')?.flat()).toContain(false)
    })
    wrapper.unmount()
  })

  it('emits history-restored when the history panel emits restored', async () => {
    const wrapper = mountDrawers({ historyPanelOpen: true })
    await vi.waitFor(() => {
      expect(document.querySelector('[data-testid="DslHistoryPanel-stub"]')).not.toBeNull()
    })

    ;(document.querySelector('[data-testid="DslHistoryPanel-stub"]') as HTMLElement).click()
    expect(wrapper.emitted('history-restored')).toHaveLength(1)
    wrapper.unmount()
  })
})
