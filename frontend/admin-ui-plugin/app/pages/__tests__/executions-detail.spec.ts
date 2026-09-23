import type { ExecutionDetail } from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref, Suspense } from 'vue'
import ExecutionDetailPage from '../executions/[id].vue'

interface DetailHarness {
  selectedExecution: Ref<ExecutionDetail | null>
  error: Ref<string | null>
  loadDetail: ReturnType<typeof vi.fn>
  startPolling: ReturnType<typeof vi.fn>
  stopPolling: ReturnType<typeof vi.fn>
  isStalePolling: (id: string) => boolean
  isCancelling: (id: string) => boolean
  cancelExecution: ReturnType<typeof vi.fn>
}

const { useExecutionsMock, useExecutionsApiMock, navigateTo } = vi.hoisted(() => {
  const navigateToSpy = vi.fn()
  const useExecutionsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __detailHarness?: DetailHarness }).__detailHarness
    if (!harness) throw new Error('execution detail harness not installed yet')
    return harness
  })
  const useExecutionsApiMockFn = vi.fn(() => ({
    list: vi.fn(async () => []),
    get: vi.fn(async (id: string) => ({ id, status: 'Completed' })),
    cancel: vi.fn(async (id: string) => ({ id, status: 'Cancelled' })),
    getTransactions: vi.fn(async () => []),
  }))
  return {
    useExecutionsMock: useExecutionsMockFn,
    useExecutionsApiMock: useExecutionsApiMockFn,
    navigateTo: navigateToSpy,
  }
})

const harness: DetailHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    selectedExecution: vue.ref<ExecutionDetail | null>(null),
    error: vue.ref<string | null>(null),
    loadDetail: vi.fn(async () => {}),
    startPolling: vi.fn(),
    stopPolling: vi.fn(),
    isStalePolling: (_id: string) => false,
    isCancelling: (_id: string) => false,
    cancelExecution: vi.fn(),
  }
})()

;(globalThis as unknown as { __detailHarness?: DetailHarness }).__detailHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useExecutions', () => ({
  useExecutions: useExecutionsMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useExecutionsApi', () => ({
  useExecutionsApi: useExecutionsApiMock,
}))

vi.mock('nuxt/app', () => ({
  navigateTo,
  useRoute: () => ({ params: { id: 'exec-1' }, query: {} }),
  useRouter: () => ({ push: () => Promise.resolve(), replace: () => Promise.resolve() }),
  useRuntimeConfig: () => ({
    public: { stalePollMs: 5000, temporalUiBaseUrl: '', temporalNamespace: 'default' },
  }),
}))

// Stub the SFCs imported from @cbs/components. The keys are the source
// filenames (e.g. `ExecutionsExecutionSummary` -> `ExecutionSummary`).
const ExecutionSummaryProbe = defineComponent({
  name: 'ExecutionSummary',
  props: ['execution'],
  setup(props, { slots }) {
    const exec = props.execution as ExecutionDetail
    return () =>
      h('div', { 'data-testid': 'execution-summary' }, [
        h('span', { 'data-testid': 'summary-status' }, exec?.status ?? ''),
        h('span', { 'data-testid': 'summary-entity' }, exec?.entity ?? ''),
        h('span', { 'data-testid': 'summary-mode' }, exec?.mode ?? ''),
        h('span', { 'data-testid': 'summary-started-at' }, exec?.startedAt ?? ''),
        slots.actions ? slots.actions() : null,
      ])
  },
})

const makeStub = (testId: string) =>
  defineComponent({
    name: testId,
    setup(_props, { slots }) {
      return () => h('div', { 'data-testid': testId }, slots.default?.() ?? [])
    },
  })

const CancelButtonProbe = defineComponent({
  name: 'CancelExecutionConfirmationModal',
  props: ['executionId', 'busy'],
  emits: ['confirm', 'cancel'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'cancel-confirmation-modal' }, [
        h(
          'span',
          { 'data-testid': 'modal-execution-id' },
          (props.executionId as string | undefined) ?? '',
        ),
        h(
          'button',
          {
            'data-testid': 'cancel-confirmation-modal-confirm',
            onClick: () => emit('confirm'),
          },
          'Confirm',
        ),
        h(
          'button',
          {
            'data-testid': 'cancel-confirmation-modal-cancel',
            onClick: () => emit('cancel'),
          },
          'Cancel',
        ),
      ])
  },
})

const componentStubs = {
  ExecutionSummary: ExecutionSummaryProbe,
  ExecutionTrace: makeStub('ExecutionTrace'),
  CompensationLane: makeStub('CompensationLane'),
  ExecutionTimeline: makeStub('ExecutionTimeline'),
  PayloadTab: makeStub('PayloadTab'),
  MetadataTab: makeStub('MetadataTab'),
  TransactionsTab: makeStub('TransactionsTab'),
  LogsTab: makeStub('LogsTab'),
  ErrorsTab: makeStub('ErrorsTab'),
  CancelExecutionConfirmationModal: CancelButtonProbe,
  ErrorBanner: defineComponent({
    name: 'ErrorBanner',
    props: ['message'],
    emits: ['retry'],
    setup(props, { emit }) {
      return () =>
        h('div', { 'data-testid': 'error-banner' }, [
          h('span', { 'data-testid': 'error-message' }, props.message as string),
          h(
            'button',
            { 'data-testid': 'error-banner-retry', onClick: () => emit('retry') },
            'Retry',
          ),
        ])
    },
  }),
}

function detailExecution(overrides: Partial<ExecutionDetail> = {}): ExecutionDetail {
  return {
    id: 'exec-1',
    entity: 'LoanDsl',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Completed',
    startedAt: '2026-09-23T10:00:00Z',
    completedAt: '2026-09-23T10:00:05Z',
    duration: 5000,
    retries: 0,
    triggeredBy: 'tester',
    correlationId: 'corr-1',
    workflowId: 'wf-1',
    ...overrides,
  }
}

function mountPage() {
  const WrappingComponent = defineComponent({
    setup() {
      return () => h(Suspense, null, { default: () => h(ExecutionDetailPage) })
    },
  })
  return mount(WrappingComponent, {
    global: {
      stubs: componentStubs,
      components: {
        ExecutionsExecutionSummary: ExecutionSummaryProbe,
        ExecutionsCancelConfirmationModal: CancelButtonProbe,
        ExecutionsPayloadTab: makeStub('PayloadTab'),
        ExecutionsMetadataTab: makeStub('MetadataTab'),
        ExecutionsTransactionsTab: makeStub('TransactionsTab'),
        ExecutionsLogsTab: makeStub('LogsTab'),
        ExecutionsErrorsTab: makeStub('ErrorsTab'),
      },
    },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}

describe('executions/[id].vue page wiring', () => {
  beforeEach(() => {
    harness.selectedExecution.value = null
    harness.error.value = null
    harness.loadDetail.mockReset()
    harness.loadDetail.mockResolvedValue(undefined)
    harness.startPolling.mockReset()
    harness.stopPolling.mockReset()
    harness.cancelExecution.mockReset()
    navigateTo.mockReset()
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('calls loadDetail with the route param id on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.loadDetail).toHaveBeenCalledTimes(1)
    expect(harness.loadDetail).toHaveBeenCalledWith('exec-1')

    wrapper.unmount()
  })

  it('renders status, definition and timings from the detail payload', async () => {
    harness.selectedExecution.value = detailExecution({
      status: 'Running',
      entity: 'OnboardingDsl',
      mode: 'PREVIEW',
      startedAt: '2026-09-23T11:00:00Z',
      duration: 2500,
    })

    const wrapper = mountPage()
    await flush()

    const summary = wrapper.find('[data-testid="execution-summary"]')
    expect(summary.exists()).toBe(true)
    expect(wrapper.find('[data-testid="summary-status"]').text()).toBe('Running')
    expect(wrapper.find('[data-testid="summary-entity"]').text()).toBe('OnboardingDsl')
    expect(wrapper.find('[data-testid="summary-mode"]').text()).toBe('PREVIEW')
    expect(wrapper.find('[data-testid="summary-started-at"]').text()).toBe('2026-09-23T11:00:00Z')

    wrapper.unmount()
  })

  it('starts detail polling when the run is Running and live polling is enabled (default ON)', async () => {
    harness.selectedExecution.value = detailExecution({ status: 'Running' })

    const wrapper = mountPage()
    await flush()

    expect(harness.startPolling).toHaveBeenCalledTimes(1)
    expect(harness.startPolling).toHaveBeenCalledWith('exec-1', expect.any(Number))

    wrapper.unmount()
  })

  it('does not start detail polling when the run is in a terminal state', async () => {
    harness.selectedExecution.value = detailExecution({ status: 'Completed' })

    const wrapper = mountPage()
    await flush()

    expect(harness.startPolling).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('renders an error state when the run cannot be loaded', async () => {
    harness.error.value = 'Not Found'
    harness.selectedExecution.value = null

    const wrapper = mountPage()
    await flush()

    const banner = wrapper.find('[data-testid="error-banner"]')
    expect(banner.exists()).toBe(true)
    expect(banner.find('[data-testid="error-message"]').text()).toBe('Not Found')

    await banner.find('[data-testid="error-banner-retry"]').trigger('click')
    await flush()

    expect(harness.loadDetail).toHaveBeenCalledWith('exec-1')

    wrapper.unmount()
  })

  it('toggling live polling off calls stopPolling', async () => {
    harness.selectedExecution.value = detailExecution({ status: 'Running' })

    const wrapper = mountPage()
    await flush()

    harness.startPolling.mockClear()

    await wrapper.find('[data-testid="execution-detail-live-polling-toggle"]').trigger('click')
    await flush()

    expect(harness.stopPolling).toHaveBeenCalled()

    wrapper.unmount()
  })
})
