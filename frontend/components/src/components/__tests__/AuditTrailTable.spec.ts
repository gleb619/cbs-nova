import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import type { AuditDto } from '../../types/audit'
import AuditTrailTable from '../AuditTrailTable.vue'

const ErrorBannerStub = defineComponent({
  name: 'ErrorBanner',
  props: ['message', 'retryLabel'],
  emits: ['retry'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'error-banner-stub' }, [
        props.message as string,
        h('button', { onClick: () => emit('retry') }, 'Retry'),
      ])
  },
})

function entry(overrides: Partial<AuditDto> = {}): AuditDto {
  return {
    id: 1,
    occurredAt: '2026-01-01T00:00:00Z',
    actor: 'operator',
    action: 'DEFINITION_PUBLISH',
    target: 'my-def',
    correlationId: 'corr-1',
    outcome: 'SUCCESS',
    detailsJson: '{"foo":"bar"}',
    ...overrides,
  }
}

function mountTable(props = {}) {
  return mount(AuditTrailTable, {
    props: {
      entries: [] as AuditDto[],
      loading: false,
      error: null,
      actionFilter: '',
      total: 0,
      offset: 0,
      limit: 50,
      hasPrev: false,
      hasNext: false,
      ...props,
    },
    global: { stubs: { ErrorBanner: ErrorBannerStub } },
    attachTo: document.body,
  })
}

describe('AuditTrailTable', () => {
  it('shows loading state', () => {
    const wrapper = mountTable({ loading: true })
    expect(wrapper.find('[data-testid="audit-trail-loading"]').exists()).toBe(true)
  })

  it('shows empty state', () => {
    const wrapper = mountTable()
    expect(wrapper.find('[data-testid="audit-trail-empty"]').text()).toBe(
      'No audit entries recorded.',
    )
  })

  it('shows empty state with filter message when actionFilter is set', () => {
    const wrapper = mountTable({ actionFilter: 'DEFINITION_PUBLISH' })
    expect(wrapper.find('[data-testid="audit-trail-empty"]').text()).toBe(
      'No audit entries match the current action filter.',
    )
  })

  it('shows error banner with retry', async () => {
    const wrapper = mountTable({ error: 'audit unavailable' })
    const stub = wrapper.findComponent(ErrorBannerStub)
    expect(stub.exists()).toBe(true)
    expect(stub.props('message')).toBe('audit unavailable')

    await stub.find('button').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })

  it('renders rows with status badge and actor/action/target', () => {
    const entries = [
      entry({ id: 1, outcome: 'SUCCESS' }),
      entry({ id: 2, outcome: 'FAILURE', actor: 'service', action: 'RUN', target: 'run-2' }),
    ]
    const wrapper = mountTable({ entries, total: 2, limit: 50 })

    const rows = wrapper.findAll('[data-testid="audit-trail-row"]')
    expect(rows.length).toBe(2)
    expect(rows[0].find('[data-testid="audit-trail-actor"]').text()).toBe('operator')
    expect(rows[0].find('[data-testid="audit-trail-action"]').text()).toBe('DEFINITION_PUBLISH')
    expect(rows[0].find('[data-testid="audit-trail-target"]').text()).toBe('my-def')

    const badges = wrapper.findAll('[data-testid="audit-trail-status"]')
    expect(badges[0].text()).toBe('SUCCESS')
    expect(badges[1].text()).toBe('FAILURE')
  })

  it('emits update:actionFilter and apply/clear events', async () => {
    const wrapper = mountTable({ actionFilter: 'FILTER' })

    const input = wrapper.find('[data-testid="audit-trail-filter-action"]')
    await input.setValue('NEW')
    await wrapper.find('[data-testid="audit-trail-filter-apply"]').trigger('click')

    expect(wrapper.emitted('update:actionFilter')).toHaveLength(1)
    expect(wrapper.emitted('update:actionFilter')![0]).toEqual(['NEW'])
    expect(wrapper.emitted('applyActionFilter')).toHaveLength(1)

    await wrapper.find('[data-testid="audit-trail-filter-clear"]').trigger('click')
    expect(wrapper.emitted('clearActionFilter')).toHaveLength(1)
  })

  it('emits prev/next/refresh events', async () => {
    const wrapper = mountTable({ hasPrev: true, hasNext: true })

    await wrapper.find('[data-testid="audit-trail-pager-prev"]').trigger('click')
    expect(wrapper.emitted('prev')).toHaveLength(1)

    await wrapper.find('[data-testid="audit-trail-pager-next"]').trigger('click')
    expect(wrapper.emitted('next')).toHaveLength(1)

    await wrapper.find('[data-testid="audit-trail-refresh"]').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })

  it('expands and collapses details row', async () => {
    const wrapper = mountTable({ entries: [entry({ id: 1 })], total: 1 })

    expect(wrapper.find('[data-testid="audit-trail-details-row"]').exists()).toBe(false)

    await wrapper.find('[data-testid="audit-trail-details-toggle"]').trigger('click')
    await flushPromises()

    const detailsRow = wrapper.find('[data-testid="audit-trail-details-row"]')
    expect(detailsRow.exists()).toBe(true)
    expect(detailsRow.find('[data-testid="audit-trail-details"]').text()).toContain('"foo": "bar"')

    await wrapper.find('[data-testid="audit-trail-details-toggle"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="audit-trail-details-row"]').exists()).toBe(false)
  })
})
