import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, Suspense, type Ref } from 'vue'
import DashboardPage from '../index.vue'
import type { DashboardStats, Execution } from '~/types'

// ---------------------------------------------------------------------------
// Harness for `useDashboardStats()` consumed by the dashboard page.
// ---------------------------------------------------------------------------

interface DashboardHarness {
  stats: Ref<DashboardStats | null>
  recentRuns: Ref<Execution[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  load: ReturnType<typeof vi.fn>
}

const { useDashboardStatsMock, navigateTo } = vi.hoisted(() => {
  const navigateToSpy = vi.fn()
  const useDashboardStatsMockFn = vi.fn(() => {
    const harness = (
      globalThis as unknown as { __dashboardHarness?: DashboardHarness }
    ).__dashboardHarness
    if (!harness) throw new Error('dashboard harness not installed yet')
    return harness
  })
  return {
    useDashboardStatsMock: useDashboardStatsMockFn,
    navigateTo: navigateToSpy,
  }
})

const harness: DashboardHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    stats: vue.ref<DashboardStats | null>(null),
    recentRuns: vue.ref<Execution[]>([]),
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    load: vi.fn(),
  }
})()

;(globalThis as unknown as { __dashboardHarness?: DashboardHarness }).__dashboardHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useDashboardStats', () => ({
  useDashboardStats: useDashboardStatsMock,
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
// `DashboardStatCard` resolves to `StatCard`), so stubs are keyed by the
// actual component name.
// ---------------------------------------------------------------------------

const makeStub = (testId: string) =>
  defineComponent({
    name: testId,
    setup() {
      return () => h('div', { 'data-testid': testId })
    },
  })

const statCardStub = defineComponent({
  name: 'StatCard',
  props: ['count', 'label', 'icon', 'to', 'linkComponent'],
  setup(props) {
    return () =>
      h(
        'div',
        { 'data-testid': `dashboard-stat-${props.label as string}` },
        String(props.count),
      )
  },
})

const recentRunsStub = defineComponent({
  name: 'RecentRunsTable',
  props: ['executions', 'loading'],
  setup(_props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'dashboard-recent-runs' }, [
        h(
          'button',
          { 'data-testid': 'select-run-button', onClick: () => emit('select', 'run-1') },
          'Select run',
        ),
      ])
  },
})

const errorBannerStub = defineComponent({
  name: 'ErrorBanner',
  props: ['message'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'error-banner' }, [
        h('span', { 'data-testid': 'error-message' }, props.message as string),
        h(
          'button',
          { 'data-testid': 'retry-button', onClick: () => emit('retry') },
          'Retry',
        ),
      ])
  },
})

const componentStubs = {
  StatCard: statCardStub,
  RecentRunsTable: recentRunsStub,
  ErrorBanner: errorBannerStub,
  NuxtLink: makeStub('NuxtLink'),
}

function mountPage() {
  // The page uses top-level `await load()` in `<script setup>`, so it must
  // render inside a `<Suspense>` boundary under vue-test-utils.
  const WrappingComponent = defineComponent({
    setup() {
      return () => h(Suspense, null, { default: () => h(DashboardPage) })
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

describe('index.vue dashboard page', () => {
  beforeEach(() => {
    harness.stats.value = null
    harness.recentRuns.value = []
    harness.loading.value = false
    harness.error.value = null
    harness.load.mockClear()
    navigateTo.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders stats with status counts, failure rate and top processes', async () => {
    harness.stats.value = {
      totalRuns: 100,
      statusCounts: { Running: 3, Completed: 45, Failed: 2, Stale: 1 },
      windowRuns: 50,
      windowFailedRuns: 5,
      windowFailureRate: 0.12345,
      windowHours: 24,
      topProcesses: [
        { processName: 'alpha', runCount: 42 },
        { processName: 'beta', runCount: 7 },
      ],
    }
    harness.recentRuns.value = [
      {
        id: 'run-1',
        entity: 'alpha',
        entityType: 'Process',
        mode: 'RUN',
        status: 'Completed',
        startedAt: '2026-01-01T00:00:00Z',
      },
    ]

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="dashboard"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dashboard-stats"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dashboard-stats-skeleton"]').exists()).toBe(false)

    expect(wrapper.find('[data-testid="dashboard-stat-Running"]').text()).toBe('3')
    expect(wrapper.find('[data-testid="dashboard-stat-Completed"]').text()).toBe('45')
    expect(wrapper.find('[data-testid="dashboard-stat-Failed"]').text()).toBe('2')
    expect(wrapper.find('[data-testid="dashboard-stat-Stale"]').text()).toBe('1')

    // Math.round(0.12345 * 1000) / 10 === 12.3
    expect(wrapper.find('[data-testid="dashboard-stat-Failure rate (24h, %)"]').text()).toBe(
      '12.3',
    )

    expect(wrapper.find('[data-testid="dashboard-top-processes"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dashboard-top-process-alpha"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dashboard-top-process-alpha"]').text()).toContain('42')
    expect(wrapper.find('[data-testid="dashboard-top-process-beta"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('shows skeleton while loading and stats not yet loaded', async () => {
    harness.loading.value = true
    harness.stats.value = null

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="dashboard-stats-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dashboard-stats"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('renders ErrorBanner when error is set and retry calls load', async () => {
    harness.error.value = 'Backend unreachable'

    const wrapper = mountPage()
    await flush()

    const banner = wrapper.find('[data-testid="error-banner"]')
    expect(banner.exists()).toBe(true)
    expect(banner.find('[data-testid="error-message"]').text()).toBe('Backend unreachable')

    await banner.find('[data-testid="retry-button"]').trigger('click')
    await flush()

    expect(harness.load).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('renders zero counts and hides top-processes when stats are empty', async () => {
    harness.stats.value = {
      totalRuns: 0,
      statusCounts: {},
      windowRuns: 0,
      windowFailedRuns: 0,
      windowFailureRate: 0,
      windowHours: 24,
      topProcesses: [],
    }

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="dashboard-stats"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dashboard-stat-Running"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="dashboard-stat-Completed"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="dashboard-stat-Failed"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="dashboard-stat-Stale"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="dashboard-stat-Failure rate (24h, %)"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="dashboard-top-processes"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('navigates to execution detail when a recent run is selected', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="select-run-button"]').trigger('click')
    await flush()

    expect(navigateTo).toHaveBeenCalledWith('/executions/run-1')

    wrapper.unmount()
  })
})
