import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref, Suspense } from 'vue'
import type { Execution, ExecutionDetail, ExecutionFilters } from '~/types'
import ExecutionsListPage from '../index.vue'

// ---------------------------------------------------------------------------
// Harness for `useExecutions()` consumed by the executions list page.
// ---------------------------------------------------------------------------

interface ExecutionsHarness {
  executions: Ref<Execution[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  loadExecutions: ReturnType<typeof vi.fn>
  applyFilters: ReturnType<typeof vi.fn>
  stalePollingIds: Ref<Set<string>>
  cancellingIds: Ref<Set<string>>
  isCancelling: ReturnType<typeof vi.fn>
  cancelExecution: ReturnType<typeof vi.fn>
  total: Ref<number>
  page: Ref<number>
  pageSize: number
  setPage: ReturnType<typeof vi.fn>
  startListPolling: ReturnType<typeof vi.fn>
  stopListPolling: ReturnType<typeof vi.fn>
}

const { useExecutionsMock, navigateTo } = vi.hoisted(() => {
  const navigateToSpy = vi.fn()
  const useExecutionsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __executionsHarness?: ExecutionsHarness })
      .__executionsHarness
    if (!harness) throw new Error('executions harness not installed yet')
    return harness
  })
  return {
    useExecutionsMock: useExecutionsMockFn,
    navigateTo: navigateToSpy,
  }
})

const harness: ExecutionsHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  const cancellingIds = vue.ref<Set<string>>(new Set())
  return {
    executions: vue.ref<Execution[]>([]),
    filters: vue.ref<ExecutionFilters>({}),
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    loadExecutions: vi.fn(),
    applyFilters: vi.fn(),
    stalePollingIds: vue.ref<Set<string>>(new Set()),
    cancellingIds,
    isCancelling: vi.fn((id: string) => cancellingIds.value.has(id)),
    cancelExecution: vi.fn(),
    total: vue.ref(0),
    page: vue.ref(1),
    pageSize: 20,
    setPage: vi.fn(),
    startListPolling: vi.fn(),
    stopListPolling: vi.fn(),
  }
})()

;(globalThis as unknown as { __executionsHarness?: ExecutionsHarness }).__executionsHarness =
  harness

vi.mock('@cbs/admin-ui-plugin/composables/useExecutions', () => ({
  useExecutions: useExecutionsMock,
}))

vi.mock('nuxt/app', () => ({
  navigateTo,
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: () => Promise.resolve(), replace: () => Promise.resolve() }),
  useRuntimeConfig: () => ({ public: {} }),
}))

// ---------------------------------------------------------------------------
// Component stubs. The page imports SFCs from @cbs/components via aliases.
// The resolved component names come from the source filenames (e.g.
// `ExecutionsExecutionList` resolves to `ExecutionList`), so stubs are keyed
// by the actual component name.
// ---------------------------------------------------------------------------

const filtersStub = defineComponent({
  name: 'ExecutionFilters',
  emits: ['filter'],
  setup(_props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'execution-filters' }, [
        h(
          'button',
          {
            'data-testid': 'apply-filter-button',
            onClick: () => emit('filter', { status: 'Running', entityName: 'alpha' }),
          },
          'Filter',
        ),
      ])
  },
})

const listStub = defineComponent({
  name: 'ExecutionList',
  props: ['executions', 'loading', 'stalePollingIds', 'cancellingIds'],
  setup(_props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'execution-list' }, [
        h(
          'button',
          { 'data-testid': 'select-button', onClick: () => emit('select', 'exec-1') },
          'Select',
        ),
        h(
          'button',
          {
            'data-testid': 'cancel-button',
            onClick: () => emit('cancel', 'exec-running'),
          },
          'Cancel',
        ),
      ])
  },
})

const cancelModalStub = defineComponent({
  name: 'CancelExecutionConfirmationModal',
  props: ['executionId', 'busy'],
  setup(_props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'cancel-modal' }, [
        h(
          'button',
          { 'data-testid': 'confirm-cancel-button', onClick: () => emit('confirm') },
          'Confirm',
        ),
        h(
          'button',
          { 'data-testid': 'modal-dismiss-button', onClick: () => emit('cancel') },
          'Dismiss',
        ),
      ])
  },
})

const errorBannerStub = defineComponent({
  name: 'ErrorBanner',
  props: ['message', 'retryLabel'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'error-banner' }, [
        h('span', { 'data-testid': 'error-message' }, props.message as string),
        h(
          'button',
          { 'data-testid': 'retry-button', onClick: () => emit('retry') },
          (props.retryLabel as string) || 'Retry',
        ),
      ])
  },
})

const componentStubs = {
  ExecutionFilters: filtersStub,
  ExecutionList: listStub,
  CancelExecutionConfirmationModal: cancelModalStub,
  ErrorBanner: errorBannerStub,
}

function mountPage() {
  // The page uses top-level `await loadExecutions()` in `<script setup>`,
  // so it must render inside a `<Suspense>` boundary under vue-test-utils.
  const WrappingComponent = defineComponent({
    setup() {
      return () => h(Suspense, null, { default: () => h(ExecutionsListPage) })
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

function findToggle(wrapper: ReturnType<typeof mountPage>) {
  return wrapper.find('[data-testid="executions-live-polling-toggle"]').element as HTMLInputElement
}

function findIntervalSelect(wrapper: ReturnType<typeof mountPage>) {
  return wrapper.find('[data-testid="executions-live-polling-interval"]')
    .element as HTMLSelectElement
}

describe('executions/index.vue list page', () => {
  beforeEach(() => {
    installLocalStorageMock()
    harness.executions.value = []
    harness.filters.value = {}
    harness.loading.value = false
    harness.error.value = null
    harness.loadExecutions.mockClear()
    harness.applyFilters.mockClear()
    harness.stalePollingIds.value = new Set()
    harness.cancellingIds.value = new Set()
    harness.isCancelling.mockClear()
    harness.cancelExecution.mockClear()
    harness.total.value = 0
    harness.page.value = 1
    harness.setPage.mockClear()
    harness.startListPolling.mockClear()
    harness.stopListPolling.mockClear()
    navigateTo.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('renders rows from executions ref and passes loading flag', async () => {
    harness.executions.value = [
      {
        id: 'exec-1',
        entity: 'alpha',
        entityType: 'Process',
        mode: 'RUN',
        status: 'Completed',
        startedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'exec-2',
        entity: 'beta',
        entityType: 'Helper',
        mode: 'RUN',
        status: 'Running',
        startedAt: '2026-01-01T00:00:00Z',
      },
    ]
    harness.loading.value = true

    const wrapper = mountPage()
    await flush()

    const list = wrapper.findComponent({ name: 'ExecutionList' })
    expect(list.exists()).toBe(true)
    expect(list.props('executions')).toEqual(harness.executions.value)
    expect(list.props('loading')).toBe(true)

    wrapper.unmount()
  })

  it('applies filters when ExecutionFilters emits filter', async () => {
    const wrapper = mountPage()
    await flush()

    const filters = wrapper.findComponent({ name: 'ExecutionFilters' })
    const filter: ExecutionFilters = { status: 'Running', entityName: 'alpha' }
    await filters.vm.$emit('filter', filter)
    await flush()

    expect(harness.applyFilters).toHaveBeenCalledWith(filter)

    wrapper.unmount()
  })

  it('opens cancel modal on cancel event for a running execution', async () => {
    const wrapper = mountPage()
    await flush()

    const list = wrapper.findComponent({ name: 'ExecutionList' })
    await list.vm.$emit('cancel', 'exec-running')
    await flush()

    const modal = wrapper.findComponent({ name: 'CancelExecutionConfirmationModal' })
    expect(modal.exists()).toBe(true)
    expect(modal.props('executionId')).toBe('exec-running')

    wrapper.unmount()
  })

  it('confirms cancel, awaits cancelExecution and closes modal on success', async () => {
    harness.cancelExecution.mockResolvedValue({} as ExecutionDetail)

    const wrapper = mountPage()
    await flush()

    const list = wrapper.findComponent({ name: 'ExecutionList' })
    await list.vm.$emit('cancel', 'exec-running')
    await flush()

    const modal = wrapper.findComponent({ name: 'CancelExecutionConfirmationModal' })
    await modal.vm.$emit('confirm')
    await flush()

    expect(harness.cancelExecution).toHaveBeenCalledWith('exec-running')
    expect(wrapper.findComponent({ name: 'CancelExecutionConfirmationModal' }).exists()).toBe(false)

    wrapper.unmount()
  })

  it('surfaces cancelExecution error in a second ErrorBanner and keeps modal open', async () => {
    harness.cancelExecution.mockRejectedValueOnce(new Error('cancel failed'))

    const wrapper = mountPage()
    await flush()

    const list = wrapper.findComponent({ name: 'ExecutionList' })
    await list.vm.$emit('cancel', 'exec-running')
    await flush()

    const modal = wrapper.findComponent({ name: 'CancelExecutionConfirmationModal' })
    await modal.vm.$emit('confirm')
    await flush()

    expect(harness.cancelExecution).toHaveBeenCalledWith('exec-running')

    const banners = wrapper.findAll('[data-testid="error-banner"]')
    const cancelBanner = banners.find(
      (b) => b.find('[data-testid="error-message"]').text() === 'cancel failed',
    )
    if (!cancelBanner) throw new Error('cancel error banner not rendered')
    expect(cancelBanner.find('[data-testid="retry-button"]').text()).toBe('Dismiss')

    // cancelTargetId is only cleared on success
    expect(modal.exists()).toBe(true)

    wrapper.unmount()
  })

  it('does not start list polling on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.startListPolling).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('starts list polling with the selected interval when live updates toggle is enabled', async () => {
    const wrapper = mountPage()
    await flush()

    const toggle = findToggle(wrapper)
    toggle.checked = true
    await toggle.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    expect(harness.startListPolling).toHaveBeenCalledWith(5000)

    wrapper.unmount()
  })

  it('stops list polling when live updates toggle is disabled', async () => {
    const wrapper = mountPage()
    await flush()

    const toggle = findToggle(wrapper)
    toggle.checked = true
    await toggle.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()
    harness.startListPolling.mockClear()
    harness.stopListPolling.mockClear()

    toggle.checked = false
    await toggle.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    expect(harness.stopListPolling).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('renders the interval selector with preset options and enables it only when polling is on', async () => {
    const wrapper = mountPage()
    await flush()

    const select = wrapper.find('[data-testid="executions-live-polling-interval"]')
    expect(select.exists()).toBe(true)
    expect(select.element.disabled).toBe(true)

    const options = select.findAll('option')
    expect(options.map((o) => ({ value: o.element.value, text: o.text() }))).toEqual([
      { value: '2000', text: '2s' },
      { value: '5000', text: '5s' },
      { value: '10000', text: '10s' },
      { value: '30000', text: '30s' },
    ])

    const toggle = findToggle(wrapper)
    toggle.checked = true
    await toggle.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    expect(select.element.disabled).toBe(false)

    wrapper.unmount()
  })

  it('defaults toggle to off and interval to stalePollMs with fresh localStorage', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.startListPolling).not.toHaveBeenCalled()
    expect(findToggle(wrapper).checked).toBe(false)

    const select = findIntervalSelect(wrapper)
    expect(select.value).toBe('5000')
    expect(select.disabled).toBe(true)

    wrapper.unmount()
  })

  it('restarts list polling with the new interval when changed while polling is on', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })

    const wrapper = mountPage()
    await flush()

    const toggle = findToggle(wrapper)
    toggle.checked = true
    await toggle.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    harness.startListPolling.mockClear()
    harness.stopListPolling.mockClear()

    const select = findIntervalSelect(wrapper)
    select.value = '10000'
    await select.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    expect(harness.stopListPolling).toHaveBeenCalled()
    expect(harness.startListPolling).toHaveBeenCalledWith(10000)

    wrapper.unmount()
  })

  it('restores persisted toggle and interval on mount and starts polling', async () => {
    storage['executions.livePolling.enabled'] = 'true'
    storage['executions.livePolling.intervalMs'] = '10000'

    const wrapper = mountPage()
    await flush()

    expect(harness.startListPolling).toHaveBeenCalledWith(10000)
    expect(findToggle(wrapper).checked).toBe(true)

    const select = findIntervalSelect(wrapper)
    expect(select.value).toBe('10000')
    expect(select.disabled).toBe(false)

    wrapper.unmount()
  })

  it('persists toggle and interval across reload', async () => {
    const wrapper = mountPage()
    await flush()

    const toggle = findToggle(wrapper)
    toggle.checked = true
    await toggle.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    const select = findIntervalSelect(wrapper)
    select.value = '10000'
    await select.dispatchEvent(new Event('change', { bubbles: true }))
    await flush()

    expect(storage['executions.livePolling.enabled']).toBe('true')
    expect(storage['executions.livePolling.intervalMs']).toBe('10000')

    harness.startListPolling.mockClear()

    wrapper.unmount()

    const wrapper2 = mountPage()
    await flush()

    expect(harness.startListPolling).toHaveBeenCalledWith(10000)
    expect(findToggle(wrapper2).checked).toBe(true)
    expect(findIntervalSelect(wrapper2).value).toBe('10000')

    wrapper2.unmount()
  })

  it('renders error banner and retries loadExecutions on retry', async () => {
    harness.error.value = 'list load failed'

    const wrapper = mountPage()
    await flush()

    const banners = wrapper.findAll('[data-testid="error-banner"]')
    const errorBanner = banners.find(
      (b) => b.find('[data-testid="error-message"]').text() === 'list load failed',
    )
    if (!errorBanner) throw new Error('load error banner not rendered')

    await errorBanner.find('[data-testid="retry-button"]').trigger('click')
    await flush()

    // once on mount, once on retry
    expect(harness.loadExecutions).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('navigates to execution detail when select is emitted', async () => {
    const wrapper = mountPage()
    await flush()

    const list = wrapper.findComponent({ name: 'ExecutionList' })
    await list.vm.$emit('select', 'exec-1')
    await flush()

    expect(navigateTo).toHaveBeenCalledWith('/executions/exec-1')

    wrapper.unmount()
  })

  it('renders pagination info and handles Next', async () => {
    harness.total.value = 100
    harness.page.value = 2

    const wrapper = mountPage()
    await flush()

    const pagination = wrapper.find('[data-testid="execution-pagination"]')
    expect(pagination.exists()).toBe(true)
    expect(pagination.text()).toContain('Page 2 of 5')
    expect(pagination.text()).toContain('100 total')

    const next = pagination.findAll('button').find((b) => b.text() === 'Next')
    if (!next) throw new Error('Next button not rendered')
    await next.trigger('click')
    await flush()

    expect(harness.setPage).toHaveBeenCalledWith(3)

    wrapper.unmount()
  })

  it('disables Previous on first page', async () => {
    harness.total.value = 100
    harness.page.value = 1

    const wrapper = mountPage()
    await flush()

    const pagination = wrapper.find('[data-testid="execution-pagination"]')
    const prev = pagination.findAll('button').find((b) => b.text() === 'Previous')
    if (!prev) throw new Error('Previous button not rendered')
    expect(prev.attributes('disabled')).toBeDefined()

    await prev.trigger('click')
    await flush()

    expect(harness.setPage).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('disables Next on last page', async () => {
    harness.total.value = 100
    harness.page.value = 5

    const wrapper = mountPage()
    await flush()

    const pagination = wrapper.find('[data-testid="execution-pagination"]')
    const next = pagination.findAll('button').find((b) => b.text() === 'Next')
    if (!next) throw new Error('Next button not rendered')
    expect(next.attributes('disabled')).toBeDefined()

    await next.trigger('click')
    await flush()

    expect(harness.setPage).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('renders an Export CSV link that carries the active filters', async () => {
    harness.filters.value = {
      status: 'Completed',
      mode: 'RUN',
      entityName: 'LoanDisbursement',
      correlationId: 'corr-123',
    }

    const wrapper = mountPage()
    await flush()

    const link = wrapper.find('[data-testid="executions-export-csv"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe(
      '/api/v1/executions/export?status=Completed&mode=RUN&entityName=LoanDisbursement&correlationId=corr-123',
    )

    wrapper.unmount()
  })
})
