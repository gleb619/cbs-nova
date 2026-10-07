import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import AuditPage from '../audit.vue'

const { useAuditTrailMock, harness } = vi.hoisted(() => {
  const vue = require('vue') as typeof import('vue')
  const harness = {
    entries: vue.ref([]),
    loading: vue.ref(false),
    error: vue.ref(null),
    total: vue.ref(0),
    offset: vue.ref(0),
    limit: vue.ref(50),
    hasPrev: vue.ref(false),
    hasNext: vue.ref(false),
    actionFilter: vue.ref(''),
    actionInput: vue.ref(''),
    load: vi.fn(),
    applyActionFilter: vi.fn(),
    clearActionFilter: vi.fn(),
    goPrev: vi.fn(),
    goNext: vi.fn(),
    refresh: vi.fn(),
  }
  return {
    useAuditTrailMock: vi.fn(() => harness),
    harness,
  }
})

vi.mock('@cbs/admin-ui-plugin/composables/useAuditTrail', () => ({
  useAuditTrail: useAuditTrailMock,
}))

const AuditTrailTableProbe = defineComponent({
  name: 'AuditTrailTable',
  props: [
    'entries',
    'loading',
    'error',
    'actionFilter',
    'total',
    'offset',
    'limit',
    'hasPrev',
    'hasNext',
  ],
  emits: [
    'update:actionFilter',
    'applyActionFilter',
    'clearActionFilter',
    'prev',
    'next',
    'refresh',
  ],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'audit-trail-table-probe' }, [
        h('button', {
          'data-testid': 'probe-apply',
          onClick: () => emit('applyActionFilter'),
        }, 'Apply'),
        h('button', {
          'data-testid': 'probe-clear',
          onClick: () => emit('clearActionFilter'),
        }, 'Clear'),
        h('button', {
          'data-testid': 'probe-prev',
          onClick: () => emit('prev'),
        }, 'Prev'),
        h('button', {
          'data-testid': 'probe-next',
          onClick: () => emit('next'),
        }, 'Next'),
        h('button', {
          'data-testid': 'probe-refresh',
          onClick: () => emit('refresh'),
        }, 'Refresh'),
        h('pre', { 'data-testid': 'probe-props' }, JSON.stringify(props)),
      ])
  },
})

function mountPage() {
  return mount(AuditPage, {
    global: { stubs: { AuditTrailTable: AuditTrailTableProbe } },
    attachTo: document.body,
  })
}

describe('audit.vue page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    harness.entries.value = []
    harness.loading.value = false
    harness.error.value = null
    harness.total.value = 0
    harness.offset.value = 0
    harness.limit.value = 50
    harness.hasPrev.value = false
    harness.hasNext.value = false
    harness.actionFilter.value = ''
    harness.actionInput.value = ''
  })

  it('renders the audit-page root and calls load on mount', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="audit-page"]').exists()).toBe(true)
    expect(harness.load).toHaveBeenCalledWith(0)
  })

  it('wires composable state into AuditTrailTable props', async () => {
    harness.entries.value = [{ id: 1, occurredAt: '2026-01-01T00:00:00Z', actor: 'a', action: 'A', target: 't', outcome: 'SUCCESS' }]
    harness.loading.value = true
    harness.error.value = 'boom'
    harness.total.value = 5
    harness.offset.value = 50
    harness.limit.value = 25
    harness.hasPrev.value = true
    harness.hasNext.value = true
    harness.actionInput.value = 'FILTER'

    const wrapper = mountPage()
    await flushPromises()

    const probe = wrapper.findComponent(AuditTrailTableProbe)
    expect(probe.exists()).toBe(true)
    expect(probe.props('entries')).toEqual(harness.entries.value)
    expect(probe.props('loading')).toBe(true)
    expect(probe.props('error')).toBe('boom')
    expect(probe.props('total')).toBe(5)
    expect(probe.props('offset')).toBe(50)
    expect(probe.props('limit')).toBe(25)
    expect(probe.props('hasPrev')).toBe(true)
    expect(probe.props('hasNext')).toBe(true)
    expect(probe.props('actionFilter')).toBe('FILTER')
  })

  it('emits apply/clear/prev/next/refresh back to the composable', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="probe-apply"]').trigger('click')
    expect(harness.applyActionFilter).toHaveBeenCalled()

    await wrapper.find('[data-testid="probe-clear"]').trigger('click')
    expect(harness.clearActionFilter).toHaveBeenCalled()

    await wrapper.find('[data-testid="probe-prev"]').trigger('click')
    expect(harness.goPrev).toHaveBeenCalled()

    await wrapper.find('[data-testid="probe-next"]').trigger('click')
    expect(harness.goNext).toHaveBeenCalled()

    await wrapper.find('[data-testid="probe-refresh"]').trigger('click')
    expect(harness.refresh).toHaveBeenCalled()
  })
})
