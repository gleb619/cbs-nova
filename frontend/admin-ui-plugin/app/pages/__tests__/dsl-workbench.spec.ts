import { resetSavedDraftsState, useSavedDrafts } from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { __setRouteQuery } from '../../../vitest.nuxt-app-stub'
import { __getBeforeRouteLeaveGuard } from '../../../vitest.vue-router-stub'
import { DSL_TEMPLATES } from '../../utils/dslTemplates'
import DslWorkbench from '../dsl-workbench.vue'

// Track every mounted page so we can tear down global window listeners
// (e.g. the Ctrl+S handler from @vueuse/core useEventListener) between tests.
const mountedWrappers: ReturnType<typeof mountPage>[] = []

afterEach(() => {
  // Unmount in reverse order to avoid stale listeners interfering with later tests.
  while (mountedWrappers.length) {
    const wrapper = mountedWrappers.pop()
    try {
      wrapper.unmount()
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
  updateDescription: ReturnType<typeof vi.fn>
  markDirty: ReturnType<typeof vi.fn>
  markClean: ReturnType<typeof vi.fn>
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
    readDslFile: vi.fn(),
    writeDslFile: vi.fn(),
  }
  const useDslWorkbenchMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __dslWorkbenchHarness?: WorkbenchApiShape })
      .__dslWorkbenchHarness
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

  function createRefLikeReactive<T extends object>(target: T): T & { value: T } {
    const reactiveTarget = vue.reactive(target)
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
      state.isDirty = false
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
      state.isDirty = false
      selectedConstructRef.value = newConstruct
    }),
    saveConstruct: vi.fn(async () => {
      state.isDirty = false
    }),
    validateConstruct: vi.fn(async () => []),
    publishConstruct: vi.fn(async () => undefined),
    deleteConstruct: vi.fn(async () => undefined),
    reloadDefinitions: vi.fn(async () => undefined),
    updateDescription: vi.fn(async () => undefined),
    markDirty: vi.fn(() => {
      state.isDirty = true
    }),
    markClean: vi.fn(() => {
      state.isDirty = false
    }),
  }
})()

;(globalThis as unknown as { __dslWorkbenchHarness?: WorkbenchApiShape }).__dslWorkbenchHarness =
  harness

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

  it('mirrors the workbench selection into the shared store', async () => {
    mountPage()
    await flushPromises()

    harness.state.selectedName = 'alpha'
    await nextTick()

    expect(useSavedDrafts().selectedName.value).toBe('alpha')
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

    // Trigger save through the Actions menu.
    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'save' })
    await nextTick()

    expect(findEditorSaveStatus(wrapper)).toBe('saving')

    resolveSave?.()
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('saved')
  })

  it('reports error status when save fails', async () => {
    harness.saveConstruct.mockRejectedValueOnce(new Error('server error'))

    const wrapper = mountPage()
    await flushPromises()

    harness.markDirty()
    await nextTick()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'save' })
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
    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'save' })
    await flushPromises()

    expect(findEditorSaveStatus(wrapper)).toBe('error')

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    await editor.vm.$emit('save', 'code')
    await nextTick()

    expect(findEditorSaveStatus(wrapper)).toBe('saving')
    expect(harness.saveConstruct).toHaveBeenCalledTimes(2)

    resolveRetry?.()
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

  it('shows Save File action for file-backed constructs and Save Draft for drafts', async () => {
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
    let items = dropdown.props('items') as { label: string; value: string }[]
    expect(items.find((i) => i.value === 'save')?.label).toBe('Save File')

    await explorer.vm.$emit('select', 'DraftOne')
    await flushPromises()

    items = dropdown.props('items') as { label: string; value: string }[]
    expect(items.find((i) => i.value === 'save')?.label).toBe('Save Draft')
  })

  it('passes editor content to saveConstruct when saving a file-backed construct', async () => {
    harness.state.constructs = [
      { name: 'LoanDsl', type: 'Process', status: 'Published', filePath: 'LoanDsl.java' },
    ]
    harness.saveConstruct.mockImplementationOnce(async () => {
      harness.state.isDirty = false
    })

    const wrapper = mountPage()
    await flushPromises()

    const explorer = wrapper.findComponent({ name: 'ConstructExplorer' })
    await explorer.vm.$emit('select', 'LoanDsl')
    await flushPromises()

    const editor = wrapper.findComponent({ name: 'BodyEditor' })
    await editor.vm.$emit('update:code', 'public class LoanDsl { }')
    await nextTick()

    const dropdown = wrapper.findComponent({ name: 'DropdownMenu' })
    await dropdown.vm.$emit('select', { value: 'save' })
    await flushPromises()

    expect(harness.saveConstruct).toHaveBeenCalledWith('public class LoanDsl { }')
  })
})
