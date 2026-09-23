import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref, Suspense } from 'vue'
import type { ExecutionDetail } from '~/types'
import ExecutionDetailPage from '../[id].vue'

const STASH_KEY = 'cbs.nova.run-again'

// ---------------------------------------------------------------------------
// Mocks for the composables the page consumes.
// ---------------------------------------------------------------------------

const { useExecutionsMock, useExecutionsApiMock, navigateTo, execApi } = vi.hoisted(() => {
  const navigateToSpy = vi.fn()
  const execApi = { getTransactions: vi.fn().mockResolvedValue([]) }
  const useExecutionsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __execDetailHarness?: unknown }).__execDetailHarness
    if (!harness) throw new Error('execution detail harness not installed yet')
    return harness
  })
  const useExecutionsApiMockFn = vi.fn(() => execApi)
  return {
    useExecutionsMock: useExecutionsMockFn,
    useExecutionsApiMock: useExecutionsApiMockFn,
    navigateTo: navigateToSpy,
    execApi,
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

;(globalThis as unknown as { __execDetailHarness?: ExecDetailHarness }).__execDetailHarness =
  harness

vi.mock('@cbs/admin-ui-plugin/composables/useExecutions', () => ({
  useExecutions: useExecutionsMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useExecutionsApi', () => ({
  useExecutionsApi: useExecutionsApiMock,
}))

vi.mock('nuxt/app', () => ({
  useRoute: () => ({ params: { id: 'exec-1' }, query: {} }),
  navigateTo,
  useRuntimeConfig: () => ({
    public: { temporalUiBaseUrl: '', temporalNamespace: 'default' },
  }),
}))

// ---------------------------------------------------------------------------
// Component stubs. The page's SFCs resolve by their short `name`, so the stub
// keys must use those names (e.g. `ExecutionSummary`, `PayloadTab`). The
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
      h('div', { 'data-testid': 'ExecutionSummary' }, slots.actions ? slots.actions() : [])
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
  ExecutionsPayloadTab: makeStub('ExecutionsPayloadTab'),
  ExecutionsMetadataTab: makeStub('ExecutionsMetadataTab'),
  ExecutionsLogsTab: makeStub('ExecutionsLogsTab'),
  ExecutionsTransactionsTab: makeStub('ExecutionsTransactionsTab'),
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

// T461 — the page persists its live-polling toggle/interval via
// useLocalStorageState; mirror the list-page spec by installing a fresh
// controllable localStorage mock before each test.
let storage: Record<string, string> = {}

function installLocalStorageMock() {
  storage = {}
  const target = typeof window !== 'undefined' ? window : globalThis
  Object.defineProperty(target, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => storage[key] ?? null),
      setItem: vi.fn((key: string, value: string) => {
        storage[key] = value
      }),
      removeItem: vi.fn((key: string) => {
        delete storage[key]
      }),
    },
    writable: true,
    configurable: true,
  })
}

describe('executions/[id].vue run-again button', () => {
  beforeEach(() => {
    harness.selectedExecution.value = null
    harness.error.value = null
    harness.loadDetail.mockClear()
    harness.startPolling.mockClear()
    execApi.getTransactions.mockReset()
    execApi.getTransactions.mockResolvedValue([])
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

  // T296 — backend now persists trace in context_json and ExecutionDto.fromDetail
  // surfaces it as a `trace` array. The page renders ExecutionsExecutionTrace
  // whenever `regularSteps.length > 0`.
  it('renders ExecutionsExecutionTrace when the detail payload carries trace steps', async () => {
    harness.selectedExecution.value = detail({
      trace: [
        { id: '0', stepType: 'Helper', name: 'lookup', status: 'Completed', isCompensation: false },
        {
          id: '1',
          stepType: 'Transaction',
          name: 'apply',
          status: 'Completed',
          isCompensation: false,
        },
      ],
    })

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="ExecutionTrace"]').exists()).toBe(true)
    // CompensationLane is always rendered (with an empty steps array when
    // nothing compensated). The ExecutionTrace stub being present is the
    // signal that the page picked up the trace payload.
    expect(wrapper.find('[data-testid="CompensationLane"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('renders CompensationLane with compensation steps when present', async () => {
    harness.selectedExecution.value = detail({
      trace: [
        { id: '0', stepType: 'Helper', name: 'lookup', status: 'Completed', isCompensation: false },
        {
          id: '1',
          stepType: 'Process',
          name: 'rolled back',
          status: 'Compensated',
          isCompensation: true,
        },
      ],
    })

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="ExecutionTrace"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="CompensationLane"]').exists()).toBe(true)

    wrapper.unmount()
  })

  // T296 — the backend has no log source for production runs, so the Logs
  // tab is only rendered when the detail payload carries `logs`.
  it('hides the Logs tab when the detail payload has no logs', async () => {
    harness.selectedExecution.value = detail()

    const wrapper = mountPage()
    await flush()

    // The tab buttons are bare <button> elements with capitalized text.
    const buttons = wrapper.findAll('button')
    const labels = buttons.map((b) => b.text().trim())
    expect(labels).not.toContain('Logs')
    expect(labels).toContain('I/O Payload')
    expect(labels).toContain('Errors')

    wrapper.unmount()
  })

  it('shows the Logs tab when the detail payload carries logs', async () => {
    harness.selectedExecution.value = detail({
      logs: [{ timestamp: '2026-01-01T00:00:00Z', severity: 'info', message: 'hi' }],
    })

    const wrapper = mountPage()
    await flush()

    const buttons = wrapper.findAll('button')
    const labels = buttons.map((b) => b.text().trim())
    expect(labels).toContain('Logs')

    wrapper.unmount()
  })

  describe('executions/[id].vue transactions tab', () => {
    beforeEach(() => {
      harness.selectedExecution.value = null
      harness.error.value = null
      harness.loadDetail.mockClear()
      execApi.getTransactions.mockReset()
      execApi.getTransactions.mockResolvedValue([])
    })

    it('renders the Transactions tab and the TransactionsTab component', async () => {
      harness.selectedExecution.value = detail()

      const wrapper = mountPage()
      await flush()

      const buttons = wrapper.findAll('button')
      const labels = buttons.map((b) => b.text().trim())
      expect(labels).toContain('Transactions')

      const txButton = buttons.find((b) => b.text().trim() === 'Transactions')
      if (txButton) {
        await txButton.trigger('click')
      }
      await flush()

      expect(wrapper.find('[data-testid="ExecutionsTransactionsTab"]').exists()).toBe(true)

      wrapper.unmount()
    })

    it('fetches transactions on tab click instead of watching the active tab', async () => {
      harness.selectedExecution.value = detail()

      const wrapper = mountPage()
      await flush()

      expect(execApi.getTransactions).not.toHaveBeenCalled()

      const buttons = wrapper.findAll('button')
      const txButton = buttons.find((b) => b.text().trim() === 'Transactions')
      if (!txButton) throw new Error('Transactions tab not rendered')
      await txButton.trigger('click')
      await flush()

      expect(execApi.getTransactions).toHaveBeenCalledWith('exec-1')
      expect(wrapper.find('[data-testid="ExecutionsTransactionsTab"]').exists()).toBe(true)

      wrapper.unmount()
    })

    it('fetches transactions only once across repeated tab visits', async () => {
      harness.selectedExecution.value = detail()

      const wrapper = mountPage()
      await flush()

      const clickTab = async (label: string) => {
        const button = wrapper.findAll('button').find((b) => b.text().trim() === label)
        if (!button) throw new Error(`${label} tab not rendered`)
        await button.trigger('click')
        await flush()
      }

      await clickTab('Transactions')
      await clickTab('I/O Payload')
      await clickTab('Transactions')

      expect(execApi.getTransactions).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })
  })

  describe('executions/[id].vue live polling controls (T461)', () => {
    beforeEach(() => {
      installLocalStorageMock()
      harness.selectedExecution.value = null
      harness.error.value = null
      harness.loadDetail.mockClear()
      harness.startPolling.mockClear()
      harness.stopPolling.mockClear()
    })

    afterEach(() => {
      document.body.innerHTML = ''
      vi.useRealTimers()
    })

    function findToggleButton(wrapper: ReturnType<typeof mountPage>) {
      return wrapper.find('[data-testid="execution-detail-live-polling-toggle"]')
    }

    function findIntervalSelectEl(wrapper: ReturnType<typeof mountPage>) {
      return wrapper.find('[data-testid="execution-detail-live-polling-interval"]')
        .element as HTMLSelectElement
    }

    it('starts polling on mount for a Running execution with fresh storage (default on + resolved interval)', async () => {
      harness.selectedExecution.value = detail({ status: 'Running' })

      const wrapper = mountPage()
      await flush()

      expect(harness.startPolling).toHaveBeenCalledWith('exec-1', 5000)
      expect(harness.stopPolling).not.toHaveBeenCalled()
      expect(findToggleButton(wrapper).attributes('aria-pressed')).toBe('true')
      expect(findIntervalSelectEl(wrapper).value).toBe('5000')

      wrapper.unmount()
    })

    it('does not start polling on mount when the execution is not Running', async () => {
      harness.selectedExecution.value = detail({ status: 'Completed' })

      const wrapper = mountPage()
      await flush()

      expect(harness.startPolling).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('does not start polling on mount when the persisted toggle is off', async () => {
      storage['executions.detail.livePolling.enabled'] = 'false'
      harness.selectedExecution.value = detail({ status: 'Running' })

      const wrapper = mountPage()
      await flush()

      expect(harness.startPolling).not.toHaveBeenCalled()
      expect(findToggleButton(wrapper).attributes('aria-pressed')).toBe('false')

      wrapper.unmount()
    })

    it('stops polling and persists the toggle when switched off', async () => {
      harness.selectedExecution.value = detail({ status: 'Running' })

      const wrapper = mountPage()
      await flush()

      harness.stopPolling.mockClear()

      await findToggleButton(wrapper).trigger('click')
      await flush()

      expect(harness.stopPolling).toHaveBeenCalled()
      expect(harness.startPolling).toHaveBeenCalledTimes(1) // only the mount call
      expect(storage['executions.detail.livePolling.enabled']).toBe('false')
      expect(findToggleButton(wrapper).attributes('aria-pressed')).toBe('false')

      wrapper.unmount()
    })

    it('starts polling with the persisted interval when switched on', async () => {
      storage['executions.detail.livePolling.enabled'] = 'false'
      storage['executions.detail.livePolling.intervalMs'] = '10000'
      harness.selectedExecution.value = detail({ status: 'Running' })

      const wrapper = mountPage()
      await flush()

      expect(harness.startPolling).not.toHaveBeenCalled()

      await findToggleButton(wrapper).trigger('click')
      await flush()

      expect(harness.startPolling).toHaveBeenCalledWith('exec-1', 10000)
      expect(storage['executions.detail.livePolling.enabled']).toBe('true')

      wrapper.unmount()
    })

    it('restarts polling with the new interval when changed while polling is on', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true })
      harness.selectedExecution.value = detail({ status: 'Running' })

      const wrapper = mountPage()
      await flush()

      expect(harness.startPolling).toHaveBeenCalledWith('exec-1', 5000)

      harness.startPolling.mockClear()
      harness.stopPolling.mockClear()

      const select = findIntervalSelectEl(wrapper)
      select.value = '10000'
      await select.dispatchEvent(new Event('change', { bubbles: true }))
      await flush()

      expect(harness.stopPolling).toHaveBeenCalled()
      expect(harness.startPolling).toHaveBeenCalledWith('exec-1', 10000)
      expect(storage['executions.detail.livePolling.intervalMs']).toBe('10000')

      wrapper.unmount()
    })

    it('persists the interval across reload (unmount + remount)', async () => {
      harness.selectedExecution.value = detail({ status: 'Running' })

      const wrapper = mountPage()
      await flush()

      const select = findIntervalSelectEl(wrapper)
      select.value = '10000'
      await select.dispatchEvent(new Event('change', { bubbles: true }))
      await flush()

      wrapper.unmount()
      harness.startPolling.mockClear()

      const wrapper2 = mountPage()
      await flush()

      expect(harness.startPolling).toHaveBeenCalledWith('exec-1', 10000)
      expect(findIntervalSelectEl(wrapper2).value).toBe('10000')

      wrapper2.unmount()
    })
  })

  describe('executions/[id].vue tab availability fallback', () => {
    beforeEach(() => {
      harness.selectedExecution.value = null
      harness.error.value = null
      harness.loadDetail.mockClear()
    })

    it('falls back to the I/O Payload tab when the selected tab disappears', async () => {
      harness.selectedExecution.value = detail({
        logs: [{ timestamp: '2026-01-01T00:00:00Z', severity: 'info', message: 'hi' }],
      })

      const wrapper = mountPage()
      await flush()

      const logsButton = wrapper.findAll('button').find((b) => b.text().trim() === 'Logs')
      if (!logsButton) throw new Error('Logs tab not rendered')
      await logsButton.trigger('click')
      await flush()

      expect(wrapper.find('[data-testid="ExecutionsLogsTab"]').exists()).toBe(true)

      // The refreshed payload no longer carries logs — the Logs tab vanishes
      // and the visible panel falls back to I/O Payload instead of going blank.
      harness.selectedExecution.value = detail()
      await flush()

      expect(wrapper.find('[data-testid="ExecutionsLogsTab"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ExecutionsPayloadTab"]').exists()).toBe(true)

      wrapper.unmount()
    })
  })
})
