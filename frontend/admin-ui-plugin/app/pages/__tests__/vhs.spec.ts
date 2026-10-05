import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import VhsPage from '../vhs.vue'

interface VhsHarness {
  tapes: Ref<unknown[]>
  total: Ref<number>
  offset: Ref<number>
  limit: Ref<number>
  hasPrev: Ref<boolean>
  hasNext: Ref<boolean>
  loading: Ref<boolean>
  error: Ref<string | null>
  filters: Ref<Record<string, string | undefined>>
  detail: Ref<null>
  detailLoading: Ref<boolean>
  detailError: Ref<string | null>
  deleting: Ref<Record<string, boolean>>
  loadTestRunning: Ref<boolean>
  loadTestReport: Ref<null>
  loadTestError: Ref<string | null>
  load: ReturnType<typeof vi.fn>
  applyFilters: ReturnType<typeof vi.fn>
  clearFilters: ReturnType<typeof vi.fn>
  prev: ReturnType<typeof vi.fn>
  next: ReturnType<typeof vi.fn>
  setLimit: ReturnType<typeof vi.fn>
  openDetail: ReturnType<typeof vi.fn>
  closeDetail: ReturnType<typeof vi.fn>
  deleteTape: ReturnType<typeof vi.fn>
  runLoadTest: ReturnType<typeof vi.fn>
}

const { useVhsTapesMock } = vi.hoisted(() => {
  const useVhsTapesMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __vhsHarness?: VhsHarness }).__vhsHarness
    if (!harness) throw new Error('vhs harness not installed yet')
    return harness
  })
  return { useVhsTapesMock: useVhsTapesMockFn }
})

const harness: VhsHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    tapes: vue.ref<unknown[]>([]),
    total: vue.ref(0),
    offset: vue.ref(0),
    limit: vue.ref(25),
    hasPrev: vue.ref(false),
    hasNext: vue.ref(false),
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    filters: vue.ref<Record<string, string | undefined>>({}),
    detail: vue.ref(null),
    detailLoading: vue.ref(false),
    detailError: vue.ref<string | null>(null),
    deleting: vue.ref<Record<string, boolean>>({}),
    loadTestRunning: vue.ref(false),
    loadTestReport: vue.ref(null),
    loadTestError: vue.ref<string | null>(null),
    load: vi.fn(),
    applyFilters: vi.fn(),
    clearFilters: vi.fn(),
    prev: vi.fn(),
    next: vi.fn(),
    setLimit: vi.fn(),
    openDetail: vi.fn(),
    closeDetail: vi.fn(),
    deleteTape: vi.fn(),
    runLoadTest: vi.fn(),
  }
})()

;(globalThis as unknown as { __vhsHarness?: VhsHarness }).__vhsHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useVhsTapes', () => ({
  useVhsTapes: useVhsTapesMock,
}))

vi.mock('@cbs/components', async () => {
  const actual = await vi.importActual<typeof import('@cbs/components')>('@cbs/components')
  return {
    ...actual,
    useToast: () => ({ success: vi.fn(), error: vi.fn() }),
  }
})

const VhsTapePanelProbe = defineComponent({
  name: 'VhsTapePanel',
  props: [
    'tapes',
    'total',
    'offset',
    'limit',
    'hasPrev',
    'hasNext',
    'loading',
    'error',
    'filters',
    'detail',
    'detailLoading',
    'detailError',
    'deleting',
    'loadTestRunning',
    'loadTestReport',
    'loadTestError',
  ],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'vhs-tape-panel-probe' }, [
        h('button', { 'data-testid': 'probe-refresh', onClick: () => emit('refresh') }, 'Refresh'),
        h('button', { 'data-testid': 'probe-prev', onClick: () => emit('prev') }, 'Prev'),
        h('button', { 'data-testid': 'probe-next', onClick: () => emit('next') }, 'Next'),
        h(
          'button',
          { 'data-testid': 'probe-delete', onClick: () => emit('delete', 'run-1') },
          'Delete',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-loadtest',
            onClick: () => emit('loadtest', { concurrency: 2 }),
          },
          'Load test',
        ),
        h('pre', { 'data-testid': 'probe-props' }, JSON.stringify(props)),
      ])
  },
})

function mountPage() {
  return mount(VhsPage, {
    global: { stubs: { VhsTapePanel: VhsTapePanelProbe } },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}

describe('vhs.vue page wiring', () => {
  beforeEach(() => {
    harness.tapes.value = []
    harness.total.value = 0
    harness.offset.value = 0
    harness.limit.value = 25
    harness.hasPrev.value = false
    harness.hasNext.value = false
    harness.loading.value = false
    harness.error.value = null
    harness.filters.value = {}
    harness.detail.value = null
    harness.detailLoading.value = false
    harness.detailError.value = null
    harness.deleting.value = {}
    harness.loadTestRunning.value = false
    harness.loadTestReport.value = null
    harness.loadTestError.value = null
    harness.load.mockClear()
    harness.applyFilters.mockClear()
    harness.clearFilters.mockClear()
    harness.prev.mockClear()
    harness.next.mockClear()
    harness.setLimit.mockClear()
    harness.openDetail.mockClear()
    harness.closeDetail.mockClear()
    harness.deleteTape.mockClear()
    harness.runLoadTest.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders the page header and panel probe', async () => {
    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="vhs-page"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="vhs-tape-panel-probe"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('VHS Tapes')

    wrapper.unmount()
  })

  it('calls load exactly once on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.load).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('reflects harness ref changes on the stub props', async () => {
    const wrapper = mountPage()
    await flush()

    harness.tapes.value = [{ run_id: 'run-1' }]
    harness.total.value = 1
    harness.offset.value = 25
    harness.limit.value = 10
    harness.hasPrev.value = true
    harness.hasNext.value = true
    harness.loading.value = true
    harness.error.value = 'Backend unreachable'
    harness.filters.value = { route: 'POST /api/foo' }
    harness.detail.value = { runId: 'run-1', events: [] }
    harness.detailLoading.value = true
    harness.detailError.value = 'detail error'
    harness.deleting.value = { 'run-1': true }
    harness.loadTestRunning.value = true
    harness.loadTestReport.value = { target: 'dry-run', totalCalls: 5 }
    harness.loadTestError.value = 'loadtest error'
    await flush()

    const probe = wrapper.findComponent(VhsTapePanelProbe)
    const shownProps = JSON.parse(probe.find('[data-testid="probe-props"]').text())
    expect(shownProps.tapes).toEqual([{ run_id: 'run-1' }])
    expect(shownProps.total).toBe(1)
    expect(shownProps.offset).toBe(25)
    expect(shownProps.limit).toBe(10)
    expect(shownProps.hasPrev).toBe(true)
    expect(shownProps.hasNext).toBe(true)
    expect(shownProps.loading).toBe(true)
    expect(shownProps.error).toBe('Backend unreachable')
    expect(shownProps.filters).toEqual({ route: 'POST /api/foo' })

    wrapper.unmount()
  })

  it('forwards panel events to composable handlers', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-refresh"]').trigger('click')
    await wrapper.find('[data-testid="probe-prev"]').trigger('click')
    await wrapper.find('[data-testid="probe-next"]').trigger('click')
    await wrapper.find('[data-testid="probe-delete"]').trigger('click')
    await wrapper.find('[data-testid="probe-loadtest"]').trigger('click')
    await flush()

    expect(harness.load).toHaveBeenCalled()
    expect(harness.prev).toHaveBeenCalledTimes(1)
    expect(harness.next).toHaveBeenCalledTimes(1)
    expect(harness.deleteTape).toHaveBeenCalledWith('run-1')
    expect(harness.runLoadTest).toHaveBeenCalledWith({ concurrency: 2 })

    wrapper.unmount()
  })
})
