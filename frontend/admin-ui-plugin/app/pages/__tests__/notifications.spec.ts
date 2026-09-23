import type {
  NotificationFireLogPage,
  NotificationRule,
  NotificationTestResult,
} from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import NotificationsPage from '../notifications.vue'

// ---------------------------------------------------------------------------
// Harness for `useNotifications()` consumed by the notifications page.
// ---------------------------------------------------------------------------

interface NotificationsHarness {
  rules: Ref<NotificationRule[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  fireLog: Ref<NotificationFireLogPage | null>
  fireLogLoading: Ref<boolean>
  fireLogError: Ref<string | null>
  testing: Ref<boolean>
  testResult: Ref<NotificationTestResult | null>
  load: ReturnType<typeof vi.fn>
  loadFireLog: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
  toggleEnabled: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  test: ReturnType<typeof vi.fn>
}

const { useNotificationsMock } = vi.hoisted(() => {
  const useNotificationsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __notificationsHarness?: NotificationsHarness })
      .__notificationsHarness
    if (!harness) throw new Error('notifications harness not installed yet')
    return harness
  })
  return { useNotificationsMock: useNotificationsMockFn }
})

const harness: NotificationsHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    rules: vue.ref<NotificationRule[]>([]),
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    fireLog: vue.ref<NotificationFireLogPage | null>(null),
    fireLogLoading: vue.ref(false),
    fireLogError: vue.ref<string | null>(null),
    testing: vue.ref(false),
    testResult: vue.ref<NotificationTestResult | null>(null),
    load: vi.fn(),
    loadFireLog: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    toggleEnabled: vi.fn(),
    remove: vi.fn(),
    test: vi.fn(),
  }
})()

;(
  globalThis as unknown as { __notificationsHarness?: NotificationsHarness }
).__notificationsHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useNotifications', () => ({
  useNotifications: useNotificationsMock,
}))

// ---------------------------------------------------------------------------
// Probe stub for `DslNotificationRuleList` — records props and re-emits events.
// ---------------------------------------------------------------------------

const DslNotificationRuleListProbe = defineComponent({
  name: 'NotificationRuleList',
  props: [
    'rules',
    'loading',
    'error',
    'testing',
    'testResult',
    'fireLog',
    'fireLogLoading',
    'fireLogError',
  ],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'dsl-notification-rule-list' }, [
        h(
          'button',
          {
            'data-testid': 'probe-create-button',
            onClick: () =>
              emit('create', {
                name: 'rule-1',
                eventFilter: { eventType: 'RunFailed' },
                actions: [{ sink: 'webhook', url: 'https://hooks.example.com' }],
              }),
          },
          'Create',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-update-button',
            onClick: () =>
              emit('update', 7, {
                name: 'rule-1b',
                eventFilter: { eventType: 'RunFailed' },
                actions: [],
              }),
          },
          'Update',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-delete-button',
            onClick: () => emit('delete', 7),
          },
          'Delete',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-toggle-button',
            onClick: () => emit('toggle', 7, false),
          },
          'Toggle',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-test-button',
            onClick: () => emit('test', { eventType: 'RunFailed' }),
          },
          'Test',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-load-fire-log-button',
            onClick: () => emit('loadFireLog', { offset: 25, limit: 25 }),
          },
          'LoadFireLog',
        ),
        h('pre', { 'data-testid': 'probe-props' }, JSON.stringify(props)),
      ])
  },
})

const componentStubs = {
  NotificationRuleList: DslNotificationRuleListProbe,
}

function mountPage() {
  return mount(NotificationsPage, {
    global: { stubs: componentStubs },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}

describe('notifications.vue page wiring', () => {
  beforeEach(() => {
    harness.rules.value = []
    harness.loading.value = false
    harness.error.value = null
    harness.fireLog.value = null
    harness.fireLogLoading.value = false
    harness.fireLogError.value = null
    harness.testing.value = false
    harness.testResult.value = null
    harness.load.mockClear()
    harness.loadFireLog.mockClear()
    harness.create.mockClear()
    harness.update.mockClear()
    harness.toggleEnabled.mockClear()
    harness.remove.mockClear()
    harness.test.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('calls load and loadFireLog exactly once on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.load).toHaveBeenCalledTimes(1)
    expect(harness.loadFireLog).toHaveBeenCalledTimes(1)
    expect(harness.loadFireLog).toHaveBeenCalledWith(0)

    wrapper.unmount()
  })

  it('reflects harness ref changes on the stub props', async () => {
    const wrapper = mountPage()
    await flush()

    const rules: NotificationRule[] = [
      {
        id: 1,
        name: 'rule-1',
        enabled: true,
        eventFilter: { eventType: 'RunFailed' },
        actions: [{ sink: 'webhook' }],
        priority: 0,
        rateClass: 'default',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]
    const fireLog = { items: [], total: 0, offset: 0 }
    harness.rules.value = rules
    harness.loading.value = true
    harness.error.value = 'Backend unreachable'
    harness.fireLog.value = fireLog
    harness.fireLogLoading.value = true
    harness.fireLogError.value = 'log boom'
    harness.testing.value = true
    harness.testResult.value = { matchedRuleIds: [1], fireResults: [] }
    await flush()

    const probe = wrapper.findComponent(DslNotificationRuleListProbe)
    expect(probe.props('rules')).toEqual(rules)
    expect(probe.props('loading')).toBe(true)
    expect(probe.props('error')).toBe('Backend unreachable')
    expect(probe.props('fireLog')).toEqual(fireLog)
    expect(probe.props('fireLogLoading')).toBe(true)
    expect(probe.props('fireLogError')).toBe('log boom')
    expect(probe.props('testing')).toBe(true)
    expect(probe.props('testResult')).toEqual({ matchedRuleIds: [1], fireResults: [] })

    wrapper.unmount()
  })

  it('forwards create event to the create spy with payload', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-create-button"]').trigger('click')
    await flush()

    expect(harness.create).toHaveBeenCalledTimes(1)
    expect(harness.create).toHaveBeenCalledWith({
      name: 'rule-1',
      eventFilter: { eventType: 'RunFailed' },
      actions: [{ sink: 'webhook', url: 'https://hooks.example.com' }],
    })

    wrapper.unmount()
  })

  it('forwards update event to the update spy with id and payload', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-update-button"]').trigger('click')
    await flush()

    expect(harness.update).toHaveBeenCalledTimes(1)
    expect(harness.update).toHaveBeenCalledWith(7, {
      name: 'rule-1b',
      eventFilter: { eventType: 'RunFailed' },
      actions: [],
    })

    wrapper.unmount()
  })

  it('forwards delete event to the remove spy with id', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-delete-button"]').trigger('click')
    await flush()

    expect(harness.remove).toHaveBeenCalledTimes(1)
    expect(harness.remove).toHaveBeenCalledWith(7)

    wrapper.unmount()
  })

  it('forwards toggle event to the toggleEnabled spy with id and enabled', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-toggle-button"]').trigger('click')
    await flush()

    expect(harness.toggleEnabled).toHaveBeenCalledTimes(1)
    expect(harness.toggleEnabled).toHaveBeenCalledWith(7, false)

    wrapper.unmount()
  })

  it('forwards test event to the test spy with payload', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-test-button"]').trigger('click')
    await flush()

    expect(harness.test).toHaveBeenCalledTimes(1)
    expect(harness.test).toHaveBeenCalledWith({ eventType: 'RunFailed' })

    wrapper.unmount()
  })

  it('forwards loadFireLog event to the loadFireLog spy with the offset', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-load-fire-log-button"]').trigger('click')
    await flush()

    expect(harness.loadFireLog).toHaveBeenCalledTimes(2)
    expect(harness.loadFireLog).toHaveBeenLastCalledWith(25)

    wrapper.unmount()
  })
})
