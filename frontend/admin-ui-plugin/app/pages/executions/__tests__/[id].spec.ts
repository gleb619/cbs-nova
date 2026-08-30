import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, Suspense, type Ref } from 'vue'
import ExecutionDetailPage from '../[id].vue'
import type { ExecutionDetail } from '~/types'

const STASH_KEY = 'cbs.nova.run-again'

// ---------------------------------------------------------------------------
// Mocks for the composables the page consumes.
// ---------------------------------------------------------------------------

const { useExecutionsMock, useDslApiMock, navigateTo, dslApi } = vi.hoisted(() => {
  const navigateToSpy = vi.fn()
  const api = { getProcessDiagram: vi.fn() }
  const useExecutionsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __execDetailHarness?: unknown }).__execDetailHarness
    if (!harness) throw new Error('execution detail harness not installed yet')
    return harness
  })
  const useDslApiMockFn = vi.fn(() => api)
  return {
    useExecutionsMock: useExecutionsMockFn,
    useDslApiMock: useDslApiMockFn,
    navigateTo: navigateToSpy,
    dslApi: api,
  }
})

interface ExecDetailHarness {
  selectedExecution: Ref<ExecutionDetail | null>
  error: Ref<string | null>
  loadDetail: ReturnType<typeof vi.fn>
  startPolling: ReturnType<typeof vi.fn>
  stopPolling: ReturnType<typeof vi.fn>
  isStalePolling: ReturnType<typeof vi.fn>
  isCancelling: ReturnType<typeof vi.fn>
  cancelExecution: ReturnType<typeof vi.fn>
}

const harness: ExecDetailHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    selectedExecution: vue.ref<ExecutionDetail | null>(null),
    error: vue.ref<string | null>(null),
    loadDetail: vi.fn(async () => {}),
    startPolling: vi.fn(),
    stopPolling: vi.fn(),
    isStalePolling: vi.fn(() => false),
    isCancelling: vi.fn(() => false),
    cancelExecution: vi.fn(),
  }
})()

;(globalThis as unknown as { __execDetailHarness?: ExecDetailHarness }).__execDetailHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: useDslApiMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useExecutions', () => ({
  useExecutions: useExecutionsMock,
}))

vi.mock('nuxt/app', () => ({
  useRoute: () => ({ params: { id: 'exec-1' }, query: {} }),
  navigateTo,
}))

// ---------------------------------------------------------------------------
// Component stubs. The page's SFCs resolve by their short `name`, so the stub
// keys must use those names (e.g. `ExecutionSummary`, `DiagramTab`). The
// summary stub renders the `actions` slot so the run-again button mounts.
// ---------------------------------------------------------------------------

const makeStub = (name: string) =>
  defineComponent({
    name,
    setup() {
      return () => h('div', { 'data-testid': name })
    },
  })

const summaryStub = defineComponent({
  name: 'ExecutionSummary',
  props: ['execution'],
  setup(_props, { slots }) {
    return () =>
      h(
        'div',
        { 'data-testid': 'ExecutionSummary' },
        slots.actions ? slots.actions() : [],
      )
  },
})

const componentStubs = {
  ExecutionSummary: summaryStub,
  ExecutionTrace: makeStub('ExecutionTrace'),
  CompensationLane: makeStub('CompensationLane'),
  CancelExecutionConfirmationModal: makeStub('CancelExecutionConfirmationModal'),
  ErrorBanner: makeStub('ErrorBanner'),
  // These tab components are NOT imported by the page (auto-registered by the
  // Nuxt components dir), so they resolve by their template tag name.
  ExecutionsDiagramTab: makeStub('ExecutionsDiagramTab'),
  ExecutionsPayloadTab: makeStub('ExecutionsPayloadTab'),
  ExecutionsMetadataTab: makeStub('ExecutionsMetadataTab'),
  ExecutionsLogsTab: makeStub('ExecutionsLogsTab'),
  ExecutionsErrorsTab: makeStub('ExecutionsErrorsTab'),
}

const detail = (overrides: Partial<ExecutionDetail> = {}): ExecutionDetail => ({
  id: 'exec-1',
  entity: 'c1',
  entityType: 'Process',
  mode: 'RUN',
  status: 'Completed',
  startedAt: '2026-01-01T00:00:00Z',
  input: { foo: 'bar' },
  output: null,
  trace: [],
  ...overrides,
})

function mountPage() {
  // [id].vue uses top-level `await` in `<script setup>` (async setup), which
  // only renders inside a `<Suspense>` boundary — vue-test-utils won't mount
  // one implicitly, so we wrap the page.
  const WrappingComponent = defineComponent({
    setup() {
      return () => h(Suspense, null, { default: () => h(ExecutionDetailPage) })
    },
  })
  return mount(WrappingComponent, {
    global: { stubs: componentStubs },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}
describe('executions/[id].vue run-again button', () => {
  beforeEach(() => {
    harness.selectedExecution.value = null
    harness.error.value = null
    harness.loadDetail.mockClear()
    harness.startPolling.mockClear()
    dslApi.getProcessDiagram.mockReset()
    dslApi.getProcessDiagram.mockResolvedValue({ diagram: 'graph TD' })
    navigateTo.mockClear()
    window.sessionStorage.clear()
  })

  afterEach(() => {
    window.sessionStorage.clear()
  })

  it('renders the Run again button when the execution has an entity', async () => {
    harness.selectedExecution.value = detail()

    const wrapper = mountPage()
    await flush()

    const button = wrapper.find('[data-testid="run-again-button"]')
    expect(button.exists()).toBe(true)
    expect(button.text()).toBe('Run again')

    wrapper.unmount()
  })

  it('hides the Run again button when the execution has no entity', async () => {
    harness.selectedExecution.value = detail({ entity: '' })

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="run-again-button"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('clicking Run again stashes the input and navigates with the correct query', async () => {
    harness.selectedExecution.value = detail()

    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="run-again-button"]').trigger('click')
    await nextTick()

    // navigate to the runner with name + mapped mode (RUN -> run)
    expect(navigateTo).toHaveBeenCalledWith({
      path: '/runner',
      query: { name: 'c1', mode: 'run' },
    })

    // input stashed for the runner to consume
    expect(window.sessionStorage.getItem(STASH_KEY)).toBe(
      JSON.stringify({ name: 'c1', input: { foo: 'bar' } }),
    )

    wrapper.unmount()
  })

  it('maps EXPLAIN mode to the runner explain mode', async () => {
    harness.selectedExecution.value = detail({ mode: 'EXPLAIN' })

    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="run-again-button"]').trigger('click')
    await nextTick()

    expect(navigateTo).toHaveBeenCalledWith({
      path: '/runner',
      query: { name: 'c1', mode: 'explain' },
    })

    wrapper.unmount()
  })
})
