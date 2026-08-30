import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import RunnerPage from '../runner.vue'

// ---------------------------------------------------------------------------
// Harness for the composables the page consumes. The page reads
// selectedDefinition / formData from `useRunner()` and drives them via
// `selectDefinition`/`setMode`, so we mock the composable with a reactive
// module-scope harness and assert on its refs directly.
// ---------------------------------------------------------------------------

const STASH_KEY = 'cbs.nova.run-again'

type RunnerMode = 'preview' | 'run' | 'explain'
type RunnerStatus = 'idle' | 'loading' | 'success' | 'failed' | 'running'

interface RunnerHarness {
  selectedDefinition: Ref<string | null>
  mode: Ref<RunnerMode>
  status: Ref<RunnerStatus>
  formData: Ref<Record<string, unknown>>
  output: Ref<unknown>
  baselineOutput: Ref<unknown>
  showConfirmModal: Ref<boolean>
  selectDefinition: ReturnType<typeof vi.fn>
  setMode: ReturnType<typeof vi.fn>
  submit: ReturnType<typeof vi.fn>
  confirmRun: ReturnType<typeof vi.fn>
  resetOutput: ReturnType<typeof vi.fn>
  compareWithPrevious: ReturnType<typeof vi.fn>
  clearBaseline: ReturnType<typeof vi.fn>
}

const { useRunnerMock, useDslApiMock, dslApi } = vi.hoisted(() => {
  const useRunnerMockFn = vi.fn(() => {
    const harness = (
      globalThis as unknown as { __runnerHarness?: RunnerHarness }
    ).__runnerHarness
    if (!harness) throw new Error('runner harness not installed yet')
    return harness
  })
  const api = { getDefinitions: vi.fn() }
  const useDslApiMockFn = vi.fn(() => api)
  return { useRunnerMock: useRunnerMockFn, useDslApiMock: useDslApiMockFn, dslApi: api }
})

const harness: RunnerHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  const selectedDefinition = vue.ref<string | null>(null)
  const mode = vue.ref<RunnerMode>('preview')
  const status = vue.ref<RunnerStatus>('idle')
  const formData = vue.ref<Record<string, unknown>>({})
  return {
    selectedDefinition,
    mode,
    status,
    formData,
    output: vue.ref<unknown>(null),
    baselineOutput: vue.ref<unknown>(null),
    showConfirmModal: vue.ref(false),
    selectDefinition: vi.fn((name: string | null) => {
      selectedDefinition.value = name
    }),
    setMode: vi.fn((m: RunnerMode) => {
      mode.value = m
    }),
    submit: vi.fn(),
    confirmRun: vi.fn(),
    resetOutput: vi.fn(() => {
      status.value = 'idle'
    }),
    compareWithPrevious: vi.fn(),
    clearBaseline: vi.fn(),
  }
})()

;(globalThis as unknown as { __runnerHarness?: RunnerHarness }).__runnerHarness = harness

// The page imports `getDefinitions` from useDslApi and resolves `definitions`
// from `loadDefinitions()`; wire the api mock so the page can select and then
// consume the stash.
vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: useDslApiMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useRunner', () => ({
  useRunner: useRunnerMock,
}))

// Control the route the page derives its query params (name/mode) from.
vi.mock('nuxt/app', () => ({
  useRoute: () => ({ params: {}, query: { name: 'c1', mode: 'run' } }),
  useRouter: () => ({ replace: () => Promise.resolve(), push: () => Promise.resolve() }),
}))

// ---------------------------------------------------------------------------
// Component stubs — the page imports many SFCs from @cbs/components; we
// substitute simple placeholders so we can drive the page's script-level flow
// without dragging in the full @cbs/components SFC graph.
// ---------------------------------------------------------------------------

const makeStub = (testId: string) =>
  defineComponent({
    name: testId,
    setup() {
      return () => h('div', { 'data-testid': testId })
    },
  })

// @cbs/components runner SFCs declare their component `name` without the
// `Runner` import-alias prefix (e.g. `RunnerOutputPanel` -> `OutputPanel`), and
// vue-test-utils matches `global.stubs` keys against that resolved name.
const componentStubs = {
  DefinitionSelector: makeStub('DefinitionSelector'),
  InputForm: makeStub('InputForm'),
  ModeSwitcher: makeStub('ModeSwitcher'),
  OutputPanel: makeStub('OutputPanel'),
  RunConfirmationModal: makeStub('RunConfirmationModal'),
  StatusIndicator: makeStub('StatusIndicator'),
}

const DEFINITIONS = [
  { name: 'c1', type: 'Process', inputSchema: { foo: { type: 'string' } } },
  { name: 'c2', type: 'Process', inputSchema: {} },
]

function mountPage() {
  return mount(RunnerPage, {
    global: { stubs: componentStubs },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}

describe('runner.vue run-again handoff', () => {
  beforeEach(() => {
    harness.selectedDefinition.value = null
    harness.mode.value = 'preview'
    harness.status.value = 'idle'
    harness.formData.value = {}
    harness.selectDefinition.mockClear()
    harness.setMode.mockClear()
    dslApi.getDefinitions.mockReset()
    dslApi.getDefinitions.mockResolvedValue(DEFINITIONS)
    window.sessionStorage.clear()
  })

  afterEach(() => {
    window.sessionStorage.clear()
  })

  it('pre-fills formData from a matching run-again stash', async () => {
    window.sessionStorage.setItem(STASH_KEY, JSON.stringify({ name: 'c1', input: { foo: 'bar' } }))

    const wrapper = mountPage()
    await flush()

    expect(harness.formData.value).toEqual({ foo: 'bar' })
    // one-shot handoff: consumed, so the key is gone
    expect(window.sessionStorage.getItem(STASH_KEY)).toBeNull()

    wrapper.unmount()
  })

  it('ignores the stash when its name does not match the selected definition', async () => {
    window.sessionStorage.setItem(STASH_KEY, JSON.stringify({ name: 'c2', input: { foo: 'bar' } }))

    const wrapper = mountPage()
    await flush()

    // definition 'c1' is selected from the query; the stash targets 'c2'
    expect(harness.selectedDefinition.value).toBe('c1')
    expect(harness.formData.value).toEqual({})
    // stale stash is discarded
    expect(window.sessionStorage.getItem(STASH_KEY)).toBeNull()

    wrapper.unmount()
  })

  it('keeps formData empty on the normal flow when there is no stash', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.selectedDefinition.value).toBe('c1')
    expect(harness.formData.value).toEqual({})

    wrapper.unmount()
  })
})
