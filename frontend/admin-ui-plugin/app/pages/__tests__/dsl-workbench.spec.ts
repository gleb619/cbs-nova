import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import DslWorkbench from '../dsl-workbench.vue'
import { __getBeforeRouteLeaveGuard } from '../../../vitest.vue-router-stub'

// ---------------------------------------------------------------------------
// Mocks for composables imported by the page
// ---------------------------------------------------------------------------

interface ConstructRow {
  name: string
  type: string
  status: string
}

interface WorkbenchStateShape {
  constructs: ConstructRow[]
  selectedName: string | null
  validationErrors: unknown[]
  isDirty: boolean
  isSaving: boolean
  isLoading: boolean
}

interface WorkbenchApiShape {
  state: WorkbenchStateShape
  selectedConstruct: { value: ConstructRow | null }
  loaders: { constructs: { value: boolean } }
  loadConstructs: ReturnType<typeof vi.fn>
  selectConstruct: ReturnType<typeof vi.fn>
  saveConstruct: ReturnType<typeof vi.fn>
  validateConstruct: ReturnType<typeof vi.fn>
  publishConstruct: ReturnType<typeof vi.fn>
  deleteConstruct: ReturnType<typeof vi.fn>
  reloadDefinitions: ReturnType<typeof vi.fn>
  markDirty: ReturnType<typeof vi.fn>
}

const { dslApi, useDslApiMock, useDslWorkbenchMock } = vi.hoisted(() => {
  const api = {
    getDefinitions: vi.fn(),
    preview: vi.fn(),
    reload: vi.fn(),
    run: vi.fn(),
    explain: vi.fn(),
    saveDraft: vi.fn(),
    publishDraft: vi.fn(),
    deleteDraft: vi.fn(),
    searchObjects: vi.fn(),
    validateConstruct: vi.fn(),
    listDrafts: vi.fn(),
    listHelpers: vi.fn(),
    listSchedules: vi.fn(),
    createSchedule: vi.fn(),
    deleteSchedule: vi.fn(),
    readDraft: vi.fn(),
  }
  // `useDslWorkbenchMock` reads the harness lazily from globalThis so the
  // vi.hoisted callback can capture this mock even though the reactive
  // harness is constructed later (after the hoisted block runs).
  const useDslWorkbenchMockFn = vi.fn(() => {
    const harness = (
      globalThis as unknown as { __dslWorkbenchHarness?: WorkbenchApiShape }
    ).__dslWorkbenchHarness
    if (!harness) {
      throw new Error('workbench harness not installed yet')
    }
    return harness
  })
  return {
    dslApi: api,
    useDslApiMock: vi.fn(() => api),
    useDslWorkbenchMock: useDslWorkbenchMockFn,
  }
})

const harness: WorkbenchApiShape = (() => {
  const vue = require('vue') as typeof import('vue')
  const state = vue.reactive<WorkbenchStateShape>({
    constructs: [],
    selectedName: null,
    validationErrors: [],
    isDirty: false,
    isSaving: false,
    isLoading: false,
  })
  const selectedConstructRef = vue.ref<ConstructRow | null>(null)
  const loaderRef = vue.ref(false)
  return {
    state,
    selectedConstruct: selectedConstructRef,
    loaders: { constructs: loaderRef },
    loadConstructs: vi.fn(async () => {
      if (state.constructs.length === 0) {
        state.constructs = [
          { name: 'c1', type: 'Process', status: 'Draft' },
          { name: 'c2', type: 'Helper', status: 'Draft' },
        ]
        state.selectedName = 'c1'
        selectedConstructRef.value = state.constructs[0]
      }
    }),
    selectConstruct: vi.fn((name: string) => {
      state.selectedName = name
      state.validationErrors = []
      state.isDirty = false
      selectedConstructRef.value =
        state.constructs.find((c) => c.name === name) ?? null
    }),
    saveConstruct: vi.fn(async () => {
      state.isDirty = false
    }),
    validateConstruct: vi.fn(async () => []),
    publishConstruct: vi.fn(async () => undefined),
    deleteConstruct: vi.fn(async () => undefined),
    reloadDefinitions: vi.fn(async () => undefined),
    markDirty: vi.fn(() => {
      state.isDirty = true
    }),
  }
})()

;(globalThis as unknown as { __dslWorkbenchHarness?: WorkbenchApiShape }).__dslWorkbenchHarness =
  harness

// vi.mock factories are hoisted; their closures reference the symbols above
// which vi.hoisted captured. The factories run when the SUT first imports
// the mocked modules, at which point all module-level state is defined.
vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: useDslApiMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useDslWorkbench', () => ({
  useDslWorkbench: useDslWorkbenchMock,
}))

// ---------------------------------------------------------------------------
// Component stubs. The page imports many SFCs from @cbs/components; rather
// than mounting the full library we substitute simple placeholders so we can
// drive the page's script-level behavior (lifecycle, guards, emit wiring).
// ---------------------------------------------------------------------------

// Each stub receives the props the page passes and emits any event the page
// listens for, so the page can mount and react to user input without dragging
// in the full @cbs/components SFC graph. The Vue warn about `loading` being
// passed an object instead of a boolean comes from Vue's prop validator on
// the auto-derived stub props; it does not change behaviour and the tests
// assert on outcomes (DOM, mounted listeners, mock calls) rather than the
// warning stream.
const makeStub = (testId: string) =>
  defineComponent({
    name: testId,
    props: [
      'schedules',
      'constructs',
      'selectedName',
      'loading',
      'collapsed',
      'code',
      'preview',
      'explain',
      'open',
      'name',
      'type',
      'description',
      'results',
      'isLoading',
      'error',
      'show',
      'draftName',
      'busy',
      'savedAt',
      'errors',
    ],
    emits: [
      'create',
      'delete',
      'select',
      'delete',
      'update:code',
      'update:collapsed',
      'update:open',
      'update:name',
      'update:type',
      'update:description',
      'search',
      'clear',
      'confirm',
      'cancel',
      'discard',
      'retry',
    ],
    setup(_props, { slots }) {
      return () =>
        h('div', { 'data-testid': testId }, slots.default ? slots.default() : [])
    },
  })

const componentStubs = {
  DropdownMenu: makeStub('DropdownMenu'),
  DslBodyEditor: makeStub('BodyEditor'),
  DslConstructExplorer: makeStub('ConstructExplorer'),
  DslDeleteDraftConfirmationModal: makeStub('DeleteDraftConfirmationModal'),
  DslDraftRestoreBanner: makeStub('DraftRestoreBanner'),
  DslHelperCatalog: makeStub('HelperCatalog'),
  DslHelperSearchPanel: makeStub('HelperSearchPanel'),
  DslMetadataPanel: makeStub('MetadataPanel'),
  DslPlainConstructList: makeStub('PlainConstructList'),
  DslProblemsPanel: makeStub('ProblemsPanel'),
  DslScheduleList: makeStub('DslScheduleList'),
  ErrorBanner: makeStub('ErrorBanner'),
}

function mountPage() {
  return mount(DslWorkbench, {
    global: { stubs: componentStubs },
    attachTo: document.body,
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('dsl-workbench.vue unsaved-changes guard', () => {
  let addSpy: ReturnType<typeof vi.spyOn>
  let removeSpy: ReturnType<typeof vi.spyOn>
  let confirmSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    // Reset the shared workbench state between tests so the markDirty() spy
    // and the captured selectConstruct calls do not bleed across specs.
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    useDslWorkbenchMock.mockClear()
    dslApi.searchObjects.mockReset()
    dslApi.searchObjects.mockResolvedValue([])
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    dslApi.listHelpers.mockReset()
    dslApi.listHelpers.mockResolvedValue({ names: [], helpers: [] })
    dslApi.listSchedules.mockReset()
    dslApi.listSchedules.mockResolvedValue([])
    dslApi.createSchedule.mockReset()
    dslApi.createSchedule.mockResolvedValue({})
    dslApi.deleteSchedule.mockReset()
    dslApi.deleteSchedule.mockResolvedValue({})

    addSpy = vi.spyOn(window, 'addEventListener')
    removeSpy = vi.spyOn(window, 'removeEventListener')
    confirmSpy = vi.spyOn(window, 'confirm')
  })

  afterEach(() => {
    document.body.innerHTML = ''
    addSpy.mockRestore()
    removeSpy.mockRestore()
    confirmSpy.mockRestore()
  })

  it('adds a beforeunload listener on mount and removes it on unmount', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const beforeunloadAdds = addSpy.mock.calls.filter(([type]) => type === 'beforeunload')
    expect(beforeunloadAdds).toHaveLength(1)
    expect(typeof beforeunloadAdds[0]?.[1]).toBe('function')

    wrapper.unmount()

    const beforeunloadRemoves = removeSpy.mock.calls.filter(
      ([type]) => type === 'beforeunload',
    )
    expect(beforeunloadRemoves).toHaveLength(1)
    expect(beforeunloadRemoves[0]?.[1]).toBe(beforeunloadAdds[0]?.[1])
  })

  it('renders the dirty indicator only when state.isDirty is true', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="workbench-dirty-indicator"]').exists()).toBe(false)

    harness.markDirty()
    await nextTick()

    const indicator = wrapper.find('[data-testid="workbench-dirty-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.text()).toContain('unsaved changes')
    expect(indicator.attributes('aria-label')).toBeTruthy()

    await harness.saveConstruct()
    await nextTick()

    expect(wrapper.find('[data-testid="workbench-dirty-indicator"]').exists()).toBe(false)
  })

  it('gates construct switching with a confirm dialog when dirty', async () => {
    const wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    expect(explorer.exists()).toBe(true)

    // Decline: select must NOT be called.
    confirmSpy.mockReturnValueOnce(false)
    await explorer.vm.$emit('select', 'c2')
    await nextTick()
    expect(confirmSpy).toHaveBeenCalledWith(
      'Discard unsaved changes to this construct?',
    )
    expect(harness.selectConstruct).not.toHaveBeenCalled()

    // Accept: select is called and isDirty is cleared inside the composable.
    confirmSpy.mockReturnValueOnce(true)
    await explorer.vm.$emit('select', 'c2')
    await nextTick()
    expect(confirmSpy).toHaveBeenCalledTimes(2)
    expect(harness.selectConstruct).toHaveBeenCalledWith('c2')
    expect(harness.selectConstruct).toHaveBeenCalledTimes(1)
  })

  it('switches constructs without prompting when not dirty', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    expect(explorer.exists()).toBe(true)

    await explorer.vm.$emit('select', 'c2')
    await nextTick()

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(harness.selectConstruct).toHaveBeenCalledWith('c2')
  })

  it('blocks the route-leave guard when dirty + confirm denied, allows when clean', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const guard = __getBeforeRouteLeaveGuard()
    expect(guard).toBeTypeOf('function')

    // Clean: guard returns true (or undefined which is also permissive).
    const cleanResult = guard?.()
    expect(cleanResult).toBeTruthy()
    expect(confirmSpy).not.toHaveBeenCalled()

    // Dirty + decline: guard returns false.
    harness.markDirty()
    confirmSpy.mockReturnValueOnce(false)
    const blocked = guard?.()
    expect(blocked).toBe(false)
    expect(confirmSpy).toHaveBeenCalledWith('You have unsaved changes. Leave anyway?')

    // Dirty + accept: guard returns true.
    confirmSpy.mockReturnValueOnce(true)
    const allowed = guard?.()
    expect(allowed).toBe(true)

    wrapper.unmount()
  })
})

describe('dsl-workbench.vue draft picker', () => {
  beforeEach(() => {
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    useDslWorkbenchMock.mockClear()
    dslApi.searchObjects.mockReset()
    dslApi.searchObjects.mockResolvedValue([])
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    dslApi.listHelpers.mockReset()
    dslApi.listHelpers.mockResolvedValue({ names: [], helpers: [] })
    dslApi.listSchedules.mockReset()
    dslApi.listSchedules.mockResolvedValue([])
    dslApi.createSchedule.mockReset()
    dslApi.createSchedule.mockResolvedValue({})
    dslApi.deleteSchedule.mockReset()
    dslApi.deleteSchedule.mockResolvedValue({})
  })

  it('renders the draft names returned by listDrafts', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
      { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
    ])

    const wrapper = mountPage()
    await flushPromises()

    const picker = wrapper.find('[data-testid="dsl-draft-picker"]')
    expect(picker.exists()).toBe(true)

    const items = wrapper.findAll('[data-testid="dsl-draft-picker-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('alpha')
    expect(items[1].text()).toContain('beta')
    expect(dslApi.listDrafts).toHaveBeenCalled()
  })

  it('clicking a draft item triggers selectConstruct', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountPage()
    await flushPromises()

    const item = wrapper.find('[data-testid="dsl-draft-picker-item"]')
    expect(item.exists()).toBe(true)

    await item.trigger('click')
    await nextTick()

    expect(harness.selectConstruct).toHaveBeenCalledWith('alpha')
  })

  it('shows the empty-state hint when no drafts exist', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([])

    const wrapper = mountPage()
    await flushPromises()

    const picker = wrapper.find('[data-testid="dsl-draft-picker"]')
    expect(picker.exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="dsl-draft-picker-item"]')).toHaveLength(0)
    expect(picker.text()).toContain('No saved drafts yet.')
  })
})