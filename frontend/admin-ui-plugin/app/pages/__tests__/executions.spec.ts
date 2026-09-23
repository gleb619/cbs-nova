import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, Suspense, type Ref } from 'vue'
import ExecutionsPage from '../executions/index.vue'
import type { Execution, ExecutionFilters } from '@cbs/components'

interface ExecutionsHarness {
  executions: Ref<Execution[]>
  filters: Ref<ExecutionFilters>
  total: Ref<number>
  page: Ref<number>
  pageSize: number
  loading: Ref<boolean>
  error: Ref<string | null>
  stalePollingIds: Ref<Set<string>>
  cancellingIds: Ref<Set<string>>
  loadExecutions: ReturnType<typeof vi.fn>
  applyFilters: ReturnType<typeof vi.fn>
  setPage: ReturnType<typeof vi.fn>
  startListPolling: ReturnType<typeof vi.fn>
  stopListPolling: ReturnType<typeof vi.fn>
  isCancelling: (id: string) => boolean
  cancelExecution: ReturnType<typeof vi.fn>
}

const { useExecutionsMock, navigateTo } = vi.hoisted(() => {
  const navigateToSpy = vi.fn()
  const useExecutionsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __executionsHarness?: ExecutionsHarness })
      .__executionsHarness
    if (!harness) throw new Error('executions harness not installed yet')
    return harness
  })
  return { useExecutionsMock: useExecutionsMockFn, navigateTo: navigateToSpy }
})

const harness: ExecutionsHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    executions: vue.ref<Execution[]>([]),
    filters: vue.ref<ExecutionFilters>({}),
    total: vue.ref<number>(0),
    page: vue.ref<number>(1),
    pageSize: 20,
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    stalePollingIds: vue.ref<Set<string>>(new Set()),
    cancellingIds: vue.ref<Set<string>>(new Set()),
    loadExecutions: vi.fn(async () => {}),
    applyFilters: vi.fn(),
    setPage: vi.fn(),
    startListPolling: vi.fn(),
    stopListPolling: vi.fn(),
    isCancelling: (_id: string) => false,
    cancelExecution: vi.fn(),
  }
})()

;(globalThis as unknown as { __executionsHarness?: ExecutionsHarness }).__executionsHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useExecutions', () => ({
  useExecutions: useExecutionsMock,
}))

vi.mock('nuxt/app', () => ({
  navigateTo,
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: () => Promise.resolve(), replace: () => Promise.resolve() }),
  useRuntimeConfig: () => ({ public: { stalePollMs: 5000 } }),
}))

// The page imports the following SFCs from @cbs/components. Vue-test-utils
// resolves stub keys against the resolved component name, which comes from
// the source filename (e.g. `ExecutionsExecutionList` -> `ExecutionList`).
// The stubs record props / emit events so we can drive the page without
// pulling in the full component graph.
const ExecutionListProbe = defineComponent({
  name: 'ExecutionList',
  props: ['executions', 'loading', 'stalePollingIds', 'cancellingIds'],
  emits: ['select', 'cancel'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'execution-list' }, [
        h(
          'button',
          {
            'data-testid': 'probe-select-button',
            onClick: () => emit('select', 'exec-1'),
          },
          'Select',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-cancel-button',
            onClick: () => emit('cancel', 'exec-1'),
          },
          'Cancel',
        ),
        h(
          'pre',
          { 'data-testid': 'probe-list-props' },
          JSON.stringify({
            count: Array.isArray(props.executions) ? props.executions.length : 0,
            loading: props.loading,
          }),
        ),
      ])
  },
})

const ExecutionFiltersProbe = defineComponent({
  name: 'ExecutionFilters',
  emits: ['filter'],
  setup(_props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'execution-filters' }, [
        h(
          'button',
          {
            'data-testid': 'probe-emit-filter',
            onClick: () => emit('filter', { status: 'Running' } as ExecutionFilters),
          },
          'Apply',
        ),
      ])
  },
})

const CancelConfirmationModalProbe = defineComponent({
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
  ExecutionList: ExecutionListProbe,
  ExecutionFilters: ExecutionFiltersProbe,
  CancelExecutionConfirmationModal: CancelConfirmationModalProbe,
}

function execution(overrides: Partial<Execution> = {}): Execution {
  return {
    id: 'exec-1',
    entity: 'LoanDsl',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Running',
    startedAt: '2026-09-23T10:00:00Z',
    duration: 1500,
    retries: 0,
    triggeredBy: 'tester',
    ...overrides,
  }
}

function mountPage() {
  const WrappingComponent = defineComponent({
    setup() {
      return () => h(Suspense, null, { default: () => h(ExecutionsPage) })
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

describe('executions/index.vue page wiring', () => {
  beforeEach(() => {
    harness.executions.value = []
    harness.filters.value = {}
    harness.total.value = 0
    harness.page.value = 1
    harness.loading.value = false
    harness.error.value = null
    harness.stalePollingIds.value = new Set()
    harness.cancellingIds.value = new Set()
    harness.loadExecutions.mockReset()
    harness.loadExecutions.mockResolvedValue(undefined)
    harness.applyFilters.mockReset()
    harness.setPage.mockReset()
    harness.startListPolling.mockReset()
    harness.stopListPolling.mockReset()
    harness.cancelExecution.mockReset()
    navigateTo.mockReset()
    window.localStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('calls loadExecutions exactly once on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.loadExecutions).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('passes execution rows from the harness to the list stub', async () => {
    harness.executions.value = [
      execution({ id: 'exec-1' }),
      execution({ id: 'exec-2', status: 'Completed' }),
      execution({ id: 'exec-3', entity: 'OtherDsl', status: 'Failed' }),
    ]

    const wrapper = mountPage()
    await flush()

    const probe = wrapper.findComponent(ExecutionListProbe)
    expect(probe.props('executions')).toHaveLength(3)
    expect(probe.props('executions')?.map((e: Execution) => e.id)).toEqual([
      'exec-1',
      'exec-2',
      'exec-3',
    ])

    wrapper.unmount()
  })

  it('reflects harness loading and stale/cancelling state on the list props', async () => {
    harness.executions.value = [execution({ id: 'exec-1' })]
    harness.loading.value = true
    harness.stalePollingIds.value = new Set(['exec-1'])
    harness.cancellingIds.value = new Set(['exec-1'])

    const wrapper = mountPage()
    await flush()

    const probe = wrapper.findComponent(ExecutionListProbe)
    expect(probe.props('loading')).toBe(true)
    expect(Array.from(probe.props('stalePollingIds') as Set<string>)).toEqual(['exec-1'])
    expect(Array.from(probe.props('cancellingIds') as Set<string>)).toEqual(['exec-1'])

    wrapper.unmount()
  })

  it('clicking Next triggers setPage(2) and a fresh load for page 2', async () => {
    harness.executions.value = Array.from({ length: 20 }, (_, i) =>
      execution({ id: `exec-${i + 1}`, status: 'Completed' }),
    )
    harness.total.value = 42

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="execution-pagination"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-pagination"]').text()).toContain('Page 1 of 3')

    const buttons = wrapper.findAll('[data-testid="execution-pagination"] button')
    const nextButton = buttons[buttons.length - 1]
    if (!nextButton) throw new Error('Next button not found')
    await nextButton.trigger('click')
    await flush()

    expect(harness.setPage).toHaveBeenCalledTimes(1)
    expect(harness.setPage).toHaveBeenCalledWith(2)

    wrapper.unmount()
  })

  it('CSV export URL reflects current filters', async () => {
    harness.filters.value = { status: 'Running', entityName: 'LoanDsl' }

    const wrapper = mountPage()
    await flush()

    const link = wrapper.find('[data-testid="executions-export-csv"]')
    const href = link.attributes('href') ?? ''
    expect(href).toContain('/api/v1/executions/export')
    expect(href).toContain('status=Running')
    expect(href).toContain('entityName=LoanDsl')

    wrapper.unmount()
  })

  it('CSV export URL drops to the bare endpoint when no filters are set', async () => {
    const wrapper = mountPage()
    await flush()

    const href = wrapper.find('[data-testid="executions-export-csv"]').attributes('href') ?? ''
    expect(href).toBe('/api/v1/executions/export')

    wrapper.unmount()
  })

  it('forwards row select to navigateTo with the execution id', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-select-button"]').trigger('click')
    await flush()

    expect(navigateTo).toHaveBeenCalledTimes(1)
    expect(navigateTo).toHaveBeenCalledWith('/executions/exec-1')

    wrapper.unmount()
  })

  it('cancel flow opens the modal and confirm calls cancelExecution with the row id', async () => {
    harness.executions.value = [execution({ id: 'exec-1', status: 'Running' })]
    harness.cancelExecution.mockResolvedValue(execution({ id: 'exec-1', status: 'Cancelled' }))

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="cancel-confirmation-modal"]').exists()).toBe(false)

    await wrapper.find('[data-testid="probe-cancel-button"]').trigger('click')
    await flush()

    expect(wrapper.find('[data-testid="cancel-confirmation-modal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="modal-execution-id"]').text()).toBe('exec-1')

    await wrapper.find('[data-testid="cancel-confirmation-modal-confirm"]').trigger('click')
    await flush()

    expect(harness.cancelExecution).toHaveBeenCalledTimes(1)
    expect(harness.cancelExecution).toHaveBeenCalledWith('exec-1')

    wrapper.unmount()
  })

  it('cancel error surfaces a cancelError banner and keeps the modal open', async () => {
    harness.executions.value = [execution({ id: 'exec-1', status: 'Running' })]
    harness.cancelExecution.mockRejectedValue(new Error('workflow already terminated'))

    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-cancel-button"]').trigger('click')
    await flush()

    await wrapper.find('[data-testid="cancel-confirmation-modal-confirm"]').trigger('click')
    await flush()

    const banners = wrapper.findAll('[data-testid="error-banner"]')
    expect(banners.some((b) => b.text().includes('workflow already terminated'))).toBe(true)
    expect(wrapper.find('[data-testid="cancel-confirmation-modal"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('toggling live polling on starts the interval poller', async () => {
    const wrapper = mountPage()
    await flush()

    const toggle = wrapper.find('[data-testid="executions-live-polling-toggle"]')
    await toggle.setChecked(true)
    await flush()

    expect(harness.startListPolling).toHaveBeenCalled()

    wrapper.unmount()
  })
})