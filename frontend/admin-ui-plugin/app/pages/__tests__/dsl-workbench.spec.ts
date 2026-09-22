import { resetSavedDraftsState, useSavedDrafts } from '@cbs/components'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { __setRouteQuery } from '../../../vitest.nuxt-app-stub'
import { __getBeforeRouteLeaveGuard } from '../../../vitest.vue-router-stub'
import { createEmitter } from '../../utils/createEmitter'
import { DSL_TEMPLATES } from '../../utils/dslTemplates'
import DslWorkbench from '../dsl-workbench.vue'

// Track every mounted page so we can tear down global window listeners
// (e.g. the Ctrl+S handler from @vueuse/core useEventListener) between tests.
const mountedWrappers: VueWrapper[] = []

afterEach(() => {
  // Unmount in reverse order to avoid stale listeners interfering with later tests.
  while (mountedWrappers.length) {
    const wrapper = mountedWrappers.pop()
    try {
      wrapper?.unmount()
    } catch {
      /* already unmounted */
    }
  }
  document.body.innerHTML = ''
})

// ---------------------------------------------------------------------------
// Mocks for composables imported by the page
// ---------------------------------------------------------------------------

interface ConstructRow {
  name: string
  type: string
  status: string
  filePath?: string
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
  createConstruct: ReturnType<typeof vi.fn>
  saveConstruct: ReturnType<typeof vi.fn>
  validateConstruct: ReturnType<typeof vi.fn>
  publishConstruct: ReturnType<typeof vi.fn>
  deleteConstruct: ReturnType<typeof vi.fn>
  reloadDefinitions: ReturnType<typeof vi.fn>
  markDirty: ReturnType<typeof vi.fn>
  markClean: ReturnType<typeof vi.fn>
  onDirty: (handler: () => void) => () => void
  onClean: (handler: () => void) => () => void
}

interface ApprovalsHarnessShape {
  items: { value: unknown[] }
  load: ReturnType<typeof vi.fn>
  submit: ReturnType<typeof vi.fn>
}

const { dslApi, useDslApiMock, useDslWorkbenchMock, useApprovalsMock } = vi.hoisted(() => {
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
    readDslFile: vi.fn(),
    writeDslFile: vi.fn(),
    listPublishHistory: vi.fn(),
    getHistoryEntry: vi.fn(),
    getHistoryDiff: vi.fn(),
    restorePublishHistory: vi.fn(),
    fetchDiagnostics: vi.fn(),
    fetchDefinitionTests: vi.fn(),
    saveDefinitionTests: vi.fn(),
    runDefinitionTests: vi.fn(),
  }
  const useDslWorkbenchMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __dslWorkbenchHarness?: WorkbenchApiShape })
      .__dslWorkbenchHarness
    if (!harness) {
      throw new Error('workbench harness not installed yet')
    }
    return harness
  })
  const useApprovalsMockFn = vi.fn(() => {
    const approvals = (globalThis as unknown as { __approvalsHarness?: ApprovalsHarnessShape })
      .__approvalsHarness
    if (!approvals) {
      throw new Error('approvals harness not installed yet')
    }
    return approvals
  })
  return {
    dslApi: api,
    useDslApiMock: vi.fn(() => api),
    useDslWorkbenchMock: useDslWorkbenchMockFn,
    useApprovalsMock: useApprovalsMockFn,
  }
})

const harness: WorkbenchApiShape = (() => {
  const vue = require('vue') as typeof import('vue')

  function createRefLikeReactive<T extends object>(target: T): T & { value: T } {
    const reactiveTarget = vue.reactive(target) as any
    return new Proxy(reactiveTarget, {
      get(t, key) {
        if (key === 'value') return t
        return t[key as keyof T]
      },
      set(t, key, value) {
        if (key === 'value') return true
        t[key as keyof T] = value
        return true
      },
    }) as T & { value: T }
  }

  const dirtyEmitter = createEmitter<{ dirty: undefined; clean: undefined }>()
  const setDirty = (value: boolean) => {
    if (state.isDirty === value) return
    state.isDirty = value
    dirtyEmitter.emit(value ? 'dirty' : 'clean')
  }
  const state = createRefLikeReactive<WorkbenchStateShape>({
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
      setDirty(false)
      selectedConstructRef.value = state.constructs.find((c) => c.name === name) ?? null
    }),
    createConstruct: vi.fn((name: string, type?: string) => {
      const newConstruct: ConstructRow = {
        name,
        type: type ?? 'Helper',
        status: 'Draft',
      }
      state.constructs = [...state.constructs, newConstruct]
      state.selectedName = name
      state.validationErrors = []
      setDirty(false)
      selectedConstructRef.value = newConstruct
    }),
    saveConstruct: vi.fn(async () => {
      setDirty(false)
    }),
    validateConstruct: vi.fn(async () => []),
    publishConstruct: vi.fn(async () => undefined),
    deleteConstruct: vi.fn(async () => undefined),
    reloadDefinitions: vi.fn(async () => undefined),
    markDirty: vi.fn(() => {
      setDirty(true)
    }),
    markClean: vi.fn(() => {
      setDirty(false)
    }),
    onDirty: (handler: () => void) => dirtyEmitter.on('dirty', handler),
    onClean: (handler: () => void) => dirtyEmitter.on('clean', handler),
  }
})()

;(globalThis as unknown as { __dslWorkbenchHarness?: WorkbenchApiShape }).__dslWorkbenchHarness =
  harness

const approvalsHarness: ApprovalsHarnessShape = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    items: vue.ref<unknown[]>([]),
    load: vi.fn(async () => undefined),
    submit: vi.fn(async () => undefined),
  }
})()

;(globalThis as unknown as { __approvalsHarness?: ApprovalsHarnessShape }).__approvalsHarness =
  approvalsHarness

vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: useDslApiMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useApprovals', () => ({
  useApprovals: useApprovalsMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useDslWorkbench', () => ({
  useDslWorkbench: useDslWorkbenchMock,
}))

// ---------------------------------------------------------------------------
// Component stubs. The page imports many SFCs from @cbs/components; rather
// than mounting the full library we substitute simple placeholders so we can
// drive the page's script-level behavior (lifecycle, guards, emit wiring).
// ---------------------------------------------------------------------------

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
      'saveStatus',
      'lastSavedAt',
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
      'templates',
      'items',
      'label',
      'align',
      'refresh',
      'validate',
    ],
    emits: [
      'create',
      'delete',
      'select',
      'delete',
      'update:code',
      'update:collapsed',
      'save',
      'update:open',
      'update:name',
      'update:type',
      'search',
      'clear',
      'confirm',
      'cancel',
      'discard',
      'retry',
    ],
    setup(_props, { slots }) {
      return () => h('div', { 'data-testid': testId }, slots.default ? slots.default() : [])
    },
  })

const makeConstructExplorerStub = () =>
  defineComponent({
    name: 'ConstructExplorer',
    props: ['constructs', 'selectedName', 'loading', 'collapsed'],
    emits: ['select', 'update:collapsed'],
    setup(props, { slots, emit }) {
      return () =>
        h(
          'div',
          { 'data-testid': 'ConstructExplorer' },
          slots.default
            ? slots.default({
                constructs: props.constructs ?? [],
                selectedName: props.selectedName ?? null,
                onSelect: (name: string) => emit('select', name),
              })
            : [],
        )
    },
  })

const componentStubs = {
  DropdownMenu: makeStub('DropdownMenu'),
  BodyEditor: makeStub('BodyEditor'),
  ConstructExplorer: makeConstructExplorerStub(),
  DeleteDraftConfirmationModal: makeStub('DeleteDraftConfirmationModal'),
  DraftRestoreBanner: makeStub('DraftRestoreBanner'),
  HelperCatalog: makeStub('HelperCatalog'),
  HelperSearchPanel: makeStub('HelperSearchPanel'),
  MetadataPanel: makeStub('MetadataPanel'),
  PlainConstructList: makeStub('PlainConstructList'),
  ProblemsPanel: makeStub('ProblemsPanel'),
  ScheduleList: makeStub('ScheduleList'),
  DslTemplateGallery: makeStub('DslTemplateGallery'),
  ErrorBanner: makeStub('ErrorBanner'),
}

function mountPage() {
  const wrapper = mount(DslWorkbench, {
    global: { stubs: componentStubs },
    attachTo: document.body,
  })
  mountedWrappers.push(wrapper)
  return wrapper
}

type WorkbenchWrapper = ReturnType<typeof mountPage>

// The Misc dropdown is the second DropdownMenu on the page (first is Actions).
// We bypass DOM clicks since DropdownMenu is component-stubbed in tests and
// drive behaviour through the same `select` event real clicks produce.
async function openHelpersMenuItem(
  wrapper: WorkbenchWrapper,
  value: 'objects' | 'helpers' | 'history' | 'diagnostics' | 'tests',
) {
  const dropdowns = wrapper.findAllComponents({ name: 'DropdownMenu' })
  const misc = dropdowns.find((node) => node.props('label') === 'Misc')
  expect(misc, 'Misc dropdown').toBeTruthy()
  const items = misc?.props('items') as Array<{ value: string }> | undefined
  expect(items?.some((item) => item.value === value)).toBe(true)
  await misc?.vm.$emit('select', { value })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('dsl-workbench.vue unsaved-changes guard', () => {
  let addSpy: ReturnType<typeof vi.spyOn>
  let removeSpy: ReturnType<typeof vi.spyOn>
  let confirmSpy: ReturnType<typeof vi.spyOn>

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
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('class LoanDsl {}')
    dslApi.writeDslFile.mockReset()
    dslApi.writeDslFile.mockResolvedValue({ ok: true })

    addSpy = vi.spyOn(window, 'addEventListener')
    removeSpy = vi.spyOn(window, 'removeEventListener')
    confirmSpy = vi.spyOn(window, 'confirm') as any
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

    const beforeunloadRemoves = removeSpy.mock.calls.filter(([type]) => type === 'beforeunload')
    expect(beforeunloadRemoves).toHaveLength(1)
    expect(beforeunloadRemoves[0]?.[1]).toBe(beforeunloadAdds[0]?.[1])
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
    expect(confirmSpy).toHaveBeenCalledWith('Discard unsaved changes to this construct?')
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

describe('dsl-workbench.vue saved drafts store', () => {
  beforeEach(() => {
    resetSavedDraftsState()
    __setRouteQuery({})
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
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('class LoanDsl {}')
    dslApi.writeDslFile.mockReset()
    dslApi.writeDslFile.mockResolvedValue({ ok: true })
  })

  afterEach(() => {
    __setRouteQuery({})
  })

  it('publishes the drafts returned by listDrafts into the shared store', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
      { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
    ])

    mountPage()
    await flushPromises()

    expect(dslApi.listDrafts).toHaveBeenCalled()
    expect(useSavedDrafts().drafts.value.map((d) => d.name)).toEqual(['alpha', 'beta'])
  })

  it('no longer renders the drafts panel in the sidebar', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="dsl-draft-picker"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="dsl-saved-drafts"]').exists()).toBe(false)
  })

  it('selects a construct when the store dispatches a pick from the navbar widget', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    mountPage()
    await flushPromises()

    expect(useSavedDrafts().select('alpha')).toBe(true)
    expect(harness.selectConstruct).toHaveBeenCalledWith('alpha')
  })

  it('mirrors the workbench selection into the shared store as selection changes', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // loadConstructs auto-selects the first construct on mount.
    expect(useSavedDrafts().selectedName.value).toBe('c1')

    // Switching constructs through the explorer updates the shared store
    // without any watcher on the workbench state.
    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'c2')
    await nextTick()

    expect(useSavedDrafts().selectedName.value).toBe('c2')
  })

  it('reloads publish history when the selected construct changes', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await openHelpersMenuItem(wrapper, 'history')
    await flushPromises()

    expect(dslApi.listPublishHistory).toHaveBeenCalledWith('c1')

    // The history panel is keyed by construct name, so switching constructs
    // remounts it and loads the other construct's history.
    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'c2')
    await flushPromises()

    expect(dslApi.listPublishHistory).toHaveBeenCalledTimes(2)
    expect(dslApi.listPublishHistory).toHaveBeenLastCalledWith('c2')
  })

  it('stops handling picks once the page unmounts', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.unmount()

    expect(useSavedDrafts().select('alpha')).toBe(false)
    expect(harness.selectConstruct).not.toHaveBeenCalled()
  })

  it('selects the draft named in the route query on mount', async () => {
    __setRouteQuery({ draft: 'beta' })

    mountPage()
    await flushPromises()

    expect(harness.selectConstruct).toHaveBeenCalledWith('beta')
  })
})

describe('dsl-workbench.vue new definition flow', () => {
  beforeEach(() => {
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    harness.createConstruct.mockClear()
    harness.markDirty.mockClear()
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
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('class LoanDsl {}')
    dslApi.writeDslFile.mockReset()
    dslApi.writeDslFile.mockResolvedValue({ ok: true })
  })

  it('opens the new-definition panel when the New button is clicked', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="workbench-new-definition"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="DslTemplateGallery"]').exists()).toBe(false)

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    expect(wrapper.find('[data-testid="workbench-new-name"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="DslTemplateGallery"]').exists()).toBe(true)
  })

  it('picking a template + valid name creates the construct and marks dirty', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    const gallery = wrapper.findComponent({ name: 'DslTemplateGallery' })
    await gallery.vm.$emit('select', DSL_TEMPLATES[0])
    await nextTick()

    const input = wrapper.find('[data-testid="workbench-new-name"]')
    await input.setValue('NewProcess')
    await nextTick()

    await wrapper.find('[data-testid="workbench-new-create"]').trigger('click')
    await nextTick()

    expect(harness.createConstruct).toHaveBeenCalledWith('NewProcess', 'Process')
    expect(harness.markDirty).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="workbench-new-name-error"]').exists()).toBe(false)

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    expect(editor.props('code')).toBe(DSL_TEMPLATES[0].body)
  })

  it('shows an inline error for an invalid name and blocks creation', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    const gallery = wrapper.findComponent({ name: 'DslTemplateGallery' })
    await gallery.vm.$emit('select', DSL_TEMPLATES[0])
    await nextTick()

    const input = wrapper.find('[data-testid="workbench-new-name"]')
    await input.setValue('bad name!')
    await nextTick()

    const error = wrapper.find('[data-testid="workbench-new-name-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('letters, numbers, dots, dashes and underscores')

    const create = wrapper.find('[data-testid="workbench-new-create"]')
    expect(create.attributes('disabled')).toBeDefined()
    await create.trigger('click')

    expect(harness.createConstruct).not.toHaveBeenCalled()
    expect(harness.markDirty).not.toHaveBeenCalled()
  })

  it('shows a collision error when the name matches an existing construct', async () => {
    harness.state.constructs = [{ name: 'ExistingOne', type: 'Process', status: 'Draft' }]

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    const gallery = wrapper.findComponent({ name: 'DslTemplateGallery' })
    await gallery.vm.$emit('select', DSL_TEMPLATES[0])
    await nextTick()

    const input = wrapper.find('[data-testid="workbench-new-name"]')
    await input.setValue('ExistingOne')
    await nextTick()

    const error = wrapper.find('[data-testid="workbench-new-name-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('already exists')

    const create = wrapper.find('[data-testid="workbench-new-create"]')
    expect(create.attributes('disabled')).toBeDefined()
    await create.trigger('click')

    expect(harness.createConstruct).not.toHaveBeenCalled()
  })

  it('shows a collision error when the name matches an existing draft', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'DraftOne', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    const gallery = wrapper.findComponent({ name: 'DslTemplateGallery' })
    await gallery.vm.$emit('select', DSL_TEMPLATES[0])
    await nextTick()

    const input = wrapper.find('[data-testid="workbench-new-name"]')
    await input.setValue('DraftOne')
    await nextTick()

    const error = wrapper.find('[data-testid="workbench-new-name-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('already exists')

    const create = wrapper.find('[data-testid="workbench-new-create"]')
    expect(create.attributes('disabled')).toBeDefined()
  })

  it('closes the panel on cancel without creating anything', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    expect(wrapper.find('[data-testid="workbench-new-name"]').exists()).toBe(true)

    await wrapper.find('[data-testid="workbench-new-cancel"]').trigger('click')
    await nextTick()

    expect(wrapper.find('[data-testid="workbench-new-name"]').exists()).toBe(false)
    expect(harness.createConstruct).not.toHaveBeenCalled()
  })
})

describe('dsl-workbench.vue save-status pill and Ctrl+S', () => {
  beforeEach(() => {
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    harness.createConstruct.mockClear()
    harness.markDirty.mockClear()
    harness.saveConstruct.mockClear()
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
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('class LoanDsl {}')
    dslApi.writeDslFile.mockReset()
    dslApi.writeDslFile.mockResolvedValue({ ok: true })
  })

  function findEditorSaveStatus(wrapper: ReturnType<typeof mountPage>) {
    return wrapper.findComponent({ name: 'BodyEditor' }).props('saveStatus')
  }

  it('reports dirty save status to the editor when the draft is dirty', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('idle')

    harness.markDirty()
    await nextTick()

    expect(findEditorSaveStatus(wrapper)).toBe('dirty')
  })

  it('reports saving then saved status while saveConstruct runs', async () => {
    let resolveSave: (() => void) | null = null
    harness.saveConstruct.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveSave = resolve
        }),
    )

    const wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    // Save is owned by the Code tab toolbar now; trigger it via the editor event.
    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    await editor.vm.$emit('save', 'code')
    await nextTick()

    expect(findEditorSaveStatus(wrapper)).toBe('saving')

    if (resolveSave) {
      ;(resolveSave as any)()
    }
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('saved')
  })

  it('reports error status when save fails', async () => {
    harness.saveConstruct.mockRejectedValueOnce(new Error('server error'))

    const wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    await editor.vm.$emit('save', 'code')
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('error')
  })

  it('editor save event re-attempts saving after a failure', async () => {
    let resolveRetry: (() => void) | null = null
    harness.saveConstruct.mockRejectedValueOnce(new Error('server error')).mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveRetry = resolve
        }),
    )

    const wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    await editor.vm.$emit('save', 'code')
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('error')

    await editor.vm.$emit('save', 'code')
    await nextTick()

    expect(findEditorSaveStatus(wrapper)).toBe('saving')
    expect(harness.saveConstruct).toHaveBeenCalledTimes(2)

    if (resolveRetry) {
      ;(resolveRetry as any)()
    }
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('saved')
  })

  it('fires save on Ctrl+S when dirty', async () => {
    const _wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    const event = new KeyboardEvent('keydown', {
      key: 's',
      ctrlKey: true,
      bubbles: true,
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(harness.saveConstruct).toHaveBeenCalled()
  })

  it('fires save on Cmd+S when dirty', async () => {
    const _wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    const event = new KeyboardEvent('keydown', {
      key: 'S',
      metaKey: true,
      bubbles: true,
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(harness.saveConstruct).toHaveBeenCalled()
  })

  it('does not fire save on Ctrl+S when clean', async () => {
    const _wrapper = mountPage()
    await flushPromises()

    const event = new KeyboardEvent('keydown', {
      key: 's',
      ctrlKey: true,
      bubbles: true,
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(harness.saveConstruct).not.toHaveBeenCalled()
  })

  it('does not fire save on Ctrl+S when the new-definition modal is open', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    await nextTick()

    harness.markDirty()
    await nextTick()

    const event = new KeyboardEvent('keydown', {
      key: 's',
      ctrlKey: true,
      bubbles: true,
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(harness.saveConstruct).not.toHaveBeenCalled()
  })

  it('does not fire save on Ctrl+S when the delete confirmation modal is open', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // Open the delete modal by emitting delete from the construct list inside the explorer.
    const plainList = wrapper.findComponent({ name: 'PlainConstructList' })
    await plainList.vm.$emit('delete', 'c1')
    await nextTick()

    harness.markDirty()
    await nextTick()

    const event = new KeyboardEvent('keydown', {
      key: 's',
      ctrlKey: true,
      bubbles: true,
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(harness.saveConstruct).not.toHaveBeenCalled()
  })

  it('does not fire save when the event is already defaultPrevented', async () => {
    const _wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    const event = new KeyboardEvent('keydown', {
      key: 's',
      ctrlKey: true,
      bubbles: true,
      cancelable: true,
    })
    event.preventDefault()
    window.dispatchEvent(event)
    await flushPromises()

    expect(harness.saveConstruct).not.toHaveBeenCalled()
  })
})

describe('dsl-workbench.vue file-backed construct', () => {
  beforeEach(() => {
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    harness.saveConstruct.mockClear()
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
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('public class LoanDsl {}')
    dslApi.writeDslFile.mockReset()
    dslApi.writeDslFile.mockResolvedValue({ ok: true })
  })

  it('loads source file content when a file-backed construct is selected', async () => {
    harness.state.constructs = [
      { name: 'LoanDsl', type: 'Process', status: 'Published', filePath: 'LoanDsl.java' },
    ]

    const wrapper = mountPage()
    await flushPromises()

    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'LoanDsl')
    await flushPromises()

    expect(dslApi.readDslFile).toHaveBeenCalledWith('LoanDsl')
    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    expect(editor.props('code')).toBe('public class LoanDsl {}')
  })

  it('does not expose Refresh, Validate or Save actions in the Actions dropdown', async () => {
    harness.state.constructs = [
      { name: 'LoanDsl', type: 'Process', status: 'Published', filePath: 'LoanDsl.java' },
      { name: 'DraftOne', type: 'Helper', status: 'Draft' },
    ]

    const wrapper = mountPage()
    await flushPromises()

    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'LoanDsl')
    await flushPromises()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    const fileBackedItems = dropdown.props('items') as { value: string }[]
    expect(fileBackedItems.find((i) => i.value === 'refresh')).toBeUndefined()
    expect(fileBackedItems.find((i) => i.value === 'validate')).toBeUndefined()
    expect(fileBackedItems.find((i) => i.value === 'save')).toBeUndefined()

    await explorer.vm.$emit('select', 'DraftOne')
    await flushPromises()

    const draftItems = dropdown.props('items') as { value: string }[]
    expect(draftItems.find((i) => i.value === 'refresh')).toBeUndefined()
    expect(draftItems.find((i) => i.value === 'validate')).toBeUndefined()
    expect(draftItems.find((i) => i.value === 'save')).toBeUndefined()
  })

  it('wires BodyEditor validate callback to validateConstruct', async () => {
    harness.state.constructs = [{ name: 'DraftOne', type: 'Helper', status: 'Draft' }]
    harness.validateConstruct.mockClear()

    const wrapper = mountPage()
    await flushPromises()

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    const validate = editor.props('validate') as () => Promise<void>
    expect(validate).toBeTypeOf('function')

    await validate()
    await flushPromises()

    expect(harness.validateConstruct).toHaveBeenCalledTimes(1)
  })

  it('wires BodyEditor refresh callback to reloadDefinitions and refreshes drafts', async () => {
    harness.state.constructs = [{ name: 'DraftOne', type: 'Helper', status: 'Draft' }]
    harness.reloadDefinitions.mockClear()
    dslApi.listDrafts.mockClear()
    dslApi.listDrafts.mockResolvedValue([])

    const wrapper = mountPage()
    await flushPromises()

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    const refresh = editor.props('refresh') as () => Promise<void>
    expect(refresh).toBeTypeOf('function')

    await refresh()
    await flushPromises()

    expect(harness.reloadDefinitions).toHaveBeenCalledTimes(1)
    expect(dslApi.listDrafts).toHaveBeenCalled()
  })

  it('forwards busy state to BodyEditor when the workbench is saving', async () => {
    harness.state.isSaving = true

    const wrapper = mountPage()
    await flushPromises()

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    expect(editor.props('busy')).toBe(true)

    harness.state.isSaving = false
    await nextTick()
    expect(editor.props('busy')).toBe(false)
  })
})

describe('dsl-workbench.vue history panel persistence', () => {
  let storage: Record<string, string> = {}

  function installLocalStorageMock() {
    storage = {}
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage[key] ?? null),
      setItem: vi.fn((key: string, value: string) => {
        storage[key] = value
      }),
      removeItem: vi.fn((key: string) => {
        delete storage[key]
      }),
    })
  }

  beforeEach(() => {
    installLocalStorageMock()
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
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('class LoanDsl {}')
    dslApi.writeDslFile.mockReset()
    dslApi.writeDslFile.mockResolvedValue({ ok: true })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('defaults history panel to closed with fresh localStorage', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(document.querySelector('[data-testid="history-drawer"]')).toBeNull()
    expect(storage['cbs-nova:dsl-workbench:history-panel-open']).toBeUndefined()

    wrapper.unmount()
  })

  it('persists history panel open state across reload', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await openHelpersMenuItem(wrapper, 'history')
    await nextTick()

    expect(document.querySelector('[data-testid="history-drawer"]')).not.toBeNull()
    expect(storage['cbs-nova:dsl-workbench:history-panel-open']).toBe('true')

    wrapper.unmount()

    const wrapper2 = mountPage()
    await flushPromises()

    expect(document.querySelector('[data-testid="history-drawer"]')).not.toBeNull()

    wrapper2.unmount()
  })
})

describe('dsl-workbench.vue definition tests panel', () => {
  beforeEach(() => {
    localStorage.removeItem('cbs-nova:dsl-workbench:tests-panel-open')
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    useDslWorkbenchMock.mockClear()
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    dslApi.readDslFile.mockReset()
    dslApi.readDslFile.mockResolvedValue('class LoanDsl {}')
    dslApi.fetchDefinitionTests.mockReset()
    dslApi.fetchDefinitionTests.mockResolvedValue([])
  })

  it('opens the tests drawer and loads the selected construct cases', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await openHelpersMenuItem(wrapper, 'tests')
    await flushPromises()

    expect(document.querySelector('[data-testid="tests-drawer"]')).not.toBeNull()
    expect(dslApi.fetchDefinitionTests).toHaveBeenCalledWith('c1')

    // The panel is keyed by construct name, so switching constructs remounts
    // it and loads the other construct's test cases.
    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'c2')
    await flushPromises()

    expect(dslApi.fetchDefinitionTests).toHaveBeenCalledTimes(2)
    expect(dslApi.fetchDefinitionTests).toHaveBeenLastCalledWith('c2')

    wrapper.unmount()
  })
})

describe('dsl-workbench.vue submit for approval (T568)', () => {
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
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    approvalsHarness.items.value = []
    approvalsHarness.load.mockClear()
    approvalsHarness.submit.mockReset()
    approvalsHarness.submit.mockResolvedValue(undefined)
  })

  it('offers a Submit for approval action next to Publish', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // The Actions dropdown is the first DropdownMenu on the page.
    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    const items = dropdown.props('items') as Array<{ label: string; value: string }>
    const submitItem = items.find((i) => i.value === 'submit-approval')
    expect(submitItem?.label).toBe('Submit for approval')

    wrapper.unmount()
  })

  it('submits the selected construct when the action runs and reloads approvals', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'submit-approval' })
    await flushPromises()

    expect(approvalsHarness.submit).toHaveBeenCalledTimes(1)
    expect(approvalsHarness.submit).toHaveBeenCalledWith('c1')
    // Approvals list refreshes after a successful submission.
    expect(approvalsHarness.load).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('loads change requests on mount', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(approvalsHarness.load).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('shows the Pending approval badge only for a PENDING request on the selected construct', async () => {
    approvalsHarness.items.value = [{ definitionName: 'c2', status: 'PENDING' }]

    const wrapper = mountPage()
    await flushPromises()

    // Default selection is c1 — no pending request for it.
    expect(wrapper.find('[data-testid="workbench-pending-approval"]').exists()).toBe(false)

    approvalsHarness.items.value = [
      { definitionName: 'c1', status: 'PENDING' },
      { definitionName: 'c2', status: 'APPROVED' },
    ]
    await flushPromises()

    expect(wrapper.find('[data-testid="workbench-pending-approval"]').exists()).toBe(true)

    wrapper.unmount()
  })
})

describe('dsl-workbench.vue share link action', () => {
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
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    window.localStorage.clear()
  })

  it('exposes a Share Link item in the Actions dropdown', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    const items = dropdown.props('items') as Array<{
      label: string
      value: string
      disabled?: boolean
    }>
    const share = items.find((i) => i.value === 'share-link')
    expect(share?.label).toBe('Share Link')
    // Default harness auto-selects 'c1', so the action is enabled.
    expect(share?.disabled).toBe(false)

    wrapper.unmount()
  })

  it('disables the Share Link item when no construct is selected', async () => {
    // Prevent the harness from auto-populating / auto-selecting on mount.
    harness.loadConstructs.mockImplementationOnce(async () => undefined)
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.selectedConstruct.value = null

    const wrapper = mountPage()
    await flushPromises()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    const items = dropdown.props('items') as Array<{
      label: string
      value: string
      disabled?: boolean
    }>
    const share = items.find((i) => i.value === 'share-link')
    expect(share?.disabled).toBe(true)

    wrapper.unmount()
  })

  it('copies a deep-link URL with the selected object and active tab to the clipboard', async () => {
    window.localStorage.setItem('cbs-nova:body-editor:active-tab', JSON.stringify('preview'))

    const writeText = vi.fn(async () => undefined)
    const clipboardSpy = vi
      .spyOn(navigator, 'clipboard', 'get')
      .mockReturnValue({ writeText } as unknown as Clipboard)

    const wrapper = mountPage()
    await flushPromises()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'share-link' })
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    const link = writeText.mock.calls[0]?.[0] as string
    const parsed = new URL(link)
    expect(parsed.pathname).toBe(window.location.pathname)
    expect(parsed.searchParams.get('objectName')).toBe('c1')
    expect(parsed.searchParams.get('activeTab')).toBe('preview')

    clipboardSpy.mockRestore()
    wrapper.unmount()
  })

  it('falls back to the structure tab when localStorage has no record', async () => {
    const writeText = vi.fn(async () => undefined)
    const clipboardSpy = vi
      .spyOn(navigator, 'clipboard', 'get')
      .mockReturnValue({ writeText } as unknown as Clipboard)

    const wrapper = mountPage()
    await flushPromises()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'share-link' })
    await flushPromises()

    const link = writeText.mock.calls[0]?.[0] as string
    expect(new URL(link).searchParams.get('activeTab')).toBe('structure')

    clipboardSpy.mockRestore()
    wrapper.unmount()
  })
})

describe('dsl-workbench.vue selected-construct header label', () => {
  beforeEach(() => {
    __setRouteQuery({})
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    useDslWorkbenchMock.mockClear()
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    dslApi.listHelpers.mockReset()
    dslApi.listHelpers.mockResolvedValue({ names: [], helpers: [] })
  })

  afterEach(() => {
    __setRouteQuery({})
  })

  const headerLabel = (wrapper: WorkbenchWrapper) =>
    wrapper.find('[data-testid="workbench-selected-construct-label"]').text().trim()

  it('shows the construct name when no filePath is associated', async () => {
    harness.state.constructs = [{ name: 'BatchProcessing', type: 'Process', status: 'Draft' }]
    harness.state.selectedName = 'BatchProcessing'
    harness.selectedConstruct.value = harness.state.constructs[0] ?? null

    const wrapper = mountPage()
    await flushPromises()

    expect(headerLabel(wrapper)).toBe('/ BatchProcessing')
  })

  it('shows the file basename (not the construct name) when filePath is set', async () => {
    harness.state.constructs = [
      {
        name: 'BatchProcessing',
        type: 'Process',
        status: 'Published',
        filePath: 'dsl/BatchProcessingDsl.java',
      },
    ]
    harness.state.selectedName = 'BatchProcessing'
    harness.selectedConstruct.value = harness.state.constructs[0] ?? null

    const wrapper = mountPage()
    await flushPromises()

    const label = wrapper.find('[data-testid="workbench-selected-construct-label"]')
    expect(label.text().trim()).toBe('/ BatchProcessingDsl.java')
    // Title carries the full path so hover reveals the directory location.
    expect(label.attributes('title')).toBe('dsl/BatchProcessingDsl.java')
  })

  it('keeps showing the basename when the user switches between file-backed constructs', async () => {
    harness.state.constructs = [
      {
        name: 'BatchProcessing',
        type: 'Process',
        status: 'Published',
        filePath: 'dsl/BatchProcessingDsl.java',
      },
      {
        name: 'LoanFlow',
        type: 'Process',
        status: 'Published',
        filePath: 'examples/LoanFlowDsl.java',
      },
    ]
    harness.state.selectedName = 'BatchProcessing'
    harness.selectedConstruct.value = harness.state.constructs[0] ?? null

    const wrapper = mountPage()
    await flushPromises()

    expect(headerLabel(wrapper)).toBe('/ BatchProcessingDsl.java')

    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'LoanFlow')
    await nextTick()

    expect(headerLabel(wrapper)).toBe('/ LoanFlowDsl.java')
  })

  it('falls back to the construct name after deleting a file-backed construct', async () => {
    harness.state.constructs = [
      {
        name: 'BatchProcessing',
        type: 'Process',
        status: 'Published',
        filePath: 'dsl/BatchProcessingDsl.java',
      },
      { name: 'helper-one', type: 'Helper', status: 'Draft' },
    ]
    harness.state.selectedName = 'BatchProcessing'
    harness.selectedConstruct.value = harness.state.constructs[0] ?? null

    const wrapper = mountPage()
    await flushPromises()

    expect(headerLabel(wrapper)).toBe('/ BatchProcessingDsl.java')

    // Simulate the harness swapping selection to a non-file-backed construct
    // (mirrors what `selectConstruct` does after the user picks a different one).
    harness.selectConstruct('helper-one')
    await nextTick()

    expect(headerLabel(wrapper)).toBe('/ helper-one')
  })
})

// ---------------------------------------------------------------------------
// Header hotkeys: Ctrl/⌘ + Alt + <N|A|M> open the three header buttons
// (New / Actions / Misc). The HotkeyTooltip wrappers carry the shortcut
// label; the global keydown handler drives the action.
// ---------------------------------------------------------------------------

describe('dsl-workbench.vue header hotkeys (New / Actions / Misc)', () => {
  beforeEach(() => {
    harness.state.constructs = []
    harness.state.selectedName = null
    harness.state.validationErrors = []
    harness.state.isDirty = false
    harness.state.isSaving = false
    harness.state.isLoading = false
    harness.selectedConstruct.value = null
    harness.loaders.constructs.value = false
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    dslApi.listHelpers.mockReset()
    dslApi.listHelpers.mockResolvedValue({ names: [], helpers: [] })
  })

  function fireKeydown(opts: {
    key: string
    ctrlKey?: boolean
    metaKey?: boolean
    altKey?: boolean
    shiftKey?: boolean
    target?: EventTarget | null
  }) {
    const event = new KeyboardEvent('keydown', {
      key: opts.key,
      ctrlKey: opts.ctrlKey ?? false,
      metaKey: opts.metaKey ?? false,
      altKey: opts.altKey ?? false,
      shiftKey: opts.shiftKey ?? false,
      bubbles: true,
      cancelable: true,
    })
    if (opts.target) {
      Object.defineProperty(event, 'target', { value: opts.target })
    }
    window.dispatchEvent(event)
    return event
  }

  it('renders HotkeyTooltip wrappers for the three header buttons', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="workbench-hotkey-new"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workbench-hotkey-actions"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workbench-hotkey-misc"]').exists()).toBe(true)

    // HotkeyTooltip falls through attrs onto its root <span>; the inner
    // DropdownMenu is stub-replaced in tests (renders with its component
    // name as the testid — verify both stubs land inside the wrappers).
    expect(
      wrapper
        .find('[data-testid="workbench-hotkey-actions"] [data-testid="DropdownMenu"]')
        .exists(),
    ).toBe(true)
    expect(
      wrapper.find('[data-testid="workbench-hotkey-misc"] [data-testid="DropdownMenu"]').exists(),
    ).toBe(true)
  })

  it('shows the platform-aware shortcut label inside each tooltip wrapper', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // The tooltip itself only renders when useHotkeyOverlay.visible is true.
    // Force-enable it (Alt press) so the label text is observable.
    const altDown = new KeyboardEvent('keydown', { key: 'Alt', bubbles: true })
    window.dispatchEvent(altDown)

    await nextTick()

    const newTooltip = wrapper.find(
      '[data-testid="workbench-hotkey-new"] [data-testid="hotkey-tooltip"]',
    )
    const actionsTooltip = wrapper.find(
      '[data-testid="workbench-hotkey-actions"] [data-testid="hotkey-tooltip"]',
    )
    const miscTooltip = wrapper.find(
      '[data-testid="workbench-hotkey-misc"] [data-testid="hotkey-tooltip"]',
    )

    expect(newTooltip.exists()).toBe(true)
    expect(actionsTooltip.exists()).toBe(true)
    expect(miscTooltip.exists()).toBe(true)

    const expectedMod = /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')
      ? '⌘'
      : 'Ctrl'
    expect(newTooltip.text()).toBe(`${expectedMod}+Alt+N`)
    expect(actionsTooltip.text()).toBe(`${expectedMod}+Alt+A`)
    expect(miscTooltip.text()).toBe(`${expectedMod}+Alt+M`)

    window.dispatchEvent(new KeyboardEvent('keyup', { key: 'Alt', bubbles: true }))
  })

  it('Ctrl+Alt+N opens the new-definition modal', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('#workbench-new-title').exists()).toBe(false)

    const event = fireKeydown({ key: 'n', ctrlKey: true, altKey: true })
    await nextTick()

    expect(event.defaultPrevented).toBe(true)
    expect(wrapper.find('#workbench-new-title').exists()).toBe(true)
  })

  it('⌘+Alt+N opens the new-definition modal on macOS-style user agents', async () => {
    const originalPlatform = navigator.platform
    Object.defineProperty(navigator, 'platform', { value: 'MacIntel', configurable: true })
    Object.defineProperty(navigator, 'userAgent', {
      value: 'Mozilla/5.0 (Macintosh)',
      configurable: true,
    })

    try {
      const wrapper = mountPage()
      await flushPromises()

      const event = fireKeydown({ key: 'n', metaKey: true, altKey: true })
      await nextTick()

      expect(event.defaultPrevented).toBe(true)
      expect(wrapper.find('#workbench-new-title').exists()).toBe(true)
    } finally {
      Object.defineProperty(navigator, 'platform', { value: originalPlatform, configurable: true })
    }
  })

  it('does not open the modal when Ctrl is pressed without Alt', async () => {
    const wrapper = mountPage()
    await flushPromises()

    fireKeydown({ key: 'n', ctrlKey: true })
    await nextTick()

    expect(wrapper.find('#workbench-new-title').exists()).toBe(false)
  })

  it('does not open the modal when the user is typing in an input', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const input = document.createElement('input')
    document.body.appendChild(input)

    fireKeydown({ key: 'n', ctrlKey: true, altKey: true, target: input })
    await nextTick()

    expect(wrapper.find('#workbench-new-title').exists()).toBe(false)
    input.remove()
  })

  it('Ctrl+Alt+A and Ctrl+Alt+M dispatch clicks on the dropdown triggers without errors', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // The dropdowns are stub-replaced in tests, so the trigger button is not
    // rendered — the handler should silently no-op (verify no throw).
    const aEvent = fireKeydown({ key: 'a', ctrlKey: true, altKey: true })
    const mEvent = fireKeydown({ key: 'm', ctrlKey: true, altKey: true })
    await nextTick()

    expect(aEvent.defaultPrevented).toBe(true)
    expect(mEvent.defaultPrevented).toBe(true)
    expect(wrapper.find('#workbench-new-title').exists()).toBe(false)
  })

  it('does not intercept Ctrl+Alt+N while a modal is already open', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // Open the New modal manually, then confirm Ctrl+Alt+N is a no-op
    // (no double-open, no errors).
    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    expect(wrapper.find('#workbench-new-title').exists()).toBe(true)

    const event = fireKeydown({ key: 'n', ctrlKey: true, altKey: true })
    await nextTick()

    expect(event.defaultPrevented).toBe(false)
    expect(wrapper.find('#workbench-new-title').exists()).toBe(true)
  })
})
