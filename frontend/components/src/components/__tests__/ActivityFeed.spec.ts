import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DomainEvent, DomainEventPage } from '../../types/events'
import ActivityFeed from '../ActivityFeed.vue'

function makeEvent(overrides: Partial<DomainEvent> = {}): DomainEvent {
  return {
    id: 1,
    eventType: 'RunCompleted',
    aggregateType: 'run',
    aggregateId: 'run-abc',
    correlationId: 'corr-1',
    schemaVersion: 1,
    createdAt: '2026-09-13T10:00:00.000Z',
    payload: { status: 'Completed', durationMs: 42 },
    ...overrides,
  }
}

function makePage(overrides: Partial<DomainEventPage> = {}): DomainEventPage {
  return {
    items: [
      makeEvent(),
      makeEvent({
        id: 2,
        eventType: 'DraftSaved',
        aggregateType: 'draft',
        aggregateId: 'OrderProcess',
      }),
    ],
    total: 2,
    offset: 0,
    limit: 25,
    ...overrides,
  }
}

function mountFeed(fetchEvents = vi.fn().mockResolvedValue(makePage())) {
  const wrapper = mount(ActivityFeed, {
    props: {
      fetchEvents,
      executionLink: (aggregateId: string) => `/executions/${aggregateId}`,
    },
  })
  return { wrapper, fetchEvents }
}

describe('ActivityFeed', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('loads the first page on mount and renders its rows', async () => {
    const { wrapper, fetchEvents } = mountFeed()
    await flushPromises()

    expect(fetchEvents).toHaveBeenCalledWith({ limit: 25, offset: 0 })
    const rows = wrapper.findAll('[data-testid="activity-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('RunCompleted')
    expect(rows[1].text()).toContain('DraftSaved')
  })

  it('renders relative time with the ISO timestamp as title', async () => {
    const { wrapper } = mountFeed()
    await flushPromises()

    const time = wrapper.find('[data-testid="activity-time"]')
    expect(time.attributes('title')).toBe('2026-09-13T10:00:00.000Z')
    expect(time.text()).toContain('ago')
  })

  it('groups the eventType badge by prefix', async () => {
    const fetchEvents = vi.fn().mockResolvedValue(
      makePage({
        items: [
          makeEvent({ eventType: 'RunFailed' }),
          makeEvent({ id: 2, eventType: 'DraftPublished', aggregateType: 'draft' }),
          makeEvent({ id: 3, eventType: 'ReloadFailed', aggregateType: 'definition' }),
          makeEvent({ id: 4, eventType: 'SomethingNew', aggregateType: 'definition' }),
        ],
        total: 4,
      }),
    )
    const { wrapper } = mountFeed(fetchEvents)
    await flushPromises()

    const badges = wrapper.findAll('[data-testid="activity-event-badge"]')
    expect(badges[0].classes()).toContain('bg-sky-100')
    expect(badges[1].classes()).toContain('bg-green-100')
    expect(badges[2].classes()).toContain('bg-red-100')
    expect(badges[3].classes()).toContain('bg-gray-100')
  })

  it('links run aggregates to the execution detail page', async () => {
    const { wrapper } = mountFeed()
    await flushPromises()

    const link = wrapper.find('[data-testid="activity-aggregate-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/executions/run-abc')
    expect(wrapper.findAll('[data-testid="activity-aggregate-link"]')).toHaveLength(1)
  })

  it('applies the eventType filter through the server and resets to the first page', async () => {
    const { wrapper, fetchEvents } = mountFeed()
    await flushPromises()

    await wrapper.find('[data-testid="activity-filter-type"]').setValue('RunFailed')
    await flushPromises()

    expect(fetchEvents).toHaveBeenLastCalledWith({ limit: 25, offset: 0, type: 'RunFailed' })
  })

  it('applies the aggregateId filter through the server', async () => {
    const { wrapper, fetchEvents } = mountFeed()
    await flushPromises()

    await wrapper.find('[data-testid="activity-filter-aggregate"]').setValue('run-abc')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(fetchEvents).toHaveBeenLastCalledWith({
      limit: 25,
      offset: 0,
      aggregateId: 'run-abc',
    })
  })

  it('sends a since instant when a time preset is picked', async () => {
    const { wrapper, fetchEvents } = mountFeed()
    await flushPromises()

    await wrapper.find('[data-testid="activity-filter-since"]').setValue('24h')
    await flushPromises()

    const query = fetchEvents.mock.calls.at(-1)?.[0] as { since?: string }
    expect(query.since).toBeDefined()
    expect(Number.isNaN(new Date(query.since as string).getTime())).toBe(false)
  })

  it('filters by correlationId when its chip is clicked', async () => {
    const { wrapper, fetchEvents } = mountFeed()
    await flushPromises()

    await wrapper.find('[data-testid="activity-correlation"]').trigger('click')
    await flushPromises()

    expect(fetchEvents).toHaveBeenLastCalledWith({
      limit: 25,
      offset: 0,
      correlationId: 'corr-1',
    })
    expect(
      (wrapper.find('[data-testid="activity-filter-correlation"]').element as HTMLInputElement)
        .value,
    ).toBe('corr-1')
  })

  it('expands and collapses the pretty-printed payload', async () => {
    const { wrapper } = mountFeed()
    await flushPromises()

    expect(wrapper.find('[data-testid="activity-payload"]').exists()).toBe(false)

    await wrapper.find('[data-testid="activity-payload-toggle"]').trigger('click')
    const payload = wrapper.find('[data-testid="activity-payload"]')
    expect(payload.exists()).toBe(true)
    expect(payload.text()).toContain('"status": "Completed"')

    await wrapper.find('[data-testid="activity-payload-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="activity-payload"]').exists()).toBe(false)
  })

  it('pages forward and back through the pager', async () => {
    const fetchEvents = vi.fn().mockImplementation(
      async (query: { offset: number }): Promise<DomainEventPage> =>
        makePage({
          items: [makeEvent({ id: query.offset + 1 })],
          total: 60,
          offset: query.offset,
        }),
    )
    const { wrapper } = mountFeed(fetchEvents)
    await flushPromises()

    expect(wrapper.find('[data-testid="activity-range"]').text()).toBe('1–1 of 60')
    expect(wrapper.find('[data-testid="activity-pager-prev"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="activity-pager-next"]').trigger('click')
    await flushPromises()
    expect(fetchEvents).toHaveBeenLastCalledWith({ limit: 25, offset: 25 })
    expect(wrapper.find('[data-testid="activity-range"]').text()).toBe('26–26 of 60')

    await wrapper.find('[data-testid="activity-pager-prev"]').trigger('click')
    await flushPromises()
    expect(fetchEvents).toHaveBeenLastCalledWith({ limit: 25, offset: 0 })
  })

  it('shows the empty state when no events match', async () => {
    const { wrapper } = mountFeed(vi.fn().mockResolvedValue(makePage({ items: [], total: 0 })))
    await flushPromises()

    expect(wrapper.find('[data-testid="activity-empty"]').text()).toContain('No activity')
    expect(wrapper.find('[data-testid="activity-row"]').exists()).toBe(false)
  })

  it('shows the error state with a retry that reloads', async () => {
    const fetchEvents = vi.fn().mockRejectedValue(new Error('events store unavailable'))
    const { wrapper } = mountFeed(fetchEvents)
    await flushPromises()

    const errorState = wrapper.find('[data-testid="activity-error"]')
    expect(errorState.exists()).toBe(true)
    expect(errorState.text()).toContain('events store unavailable')

    fetchEvents.mockResolvedValueOnce(makePage())
    await errorState.find('[data-testid="error-banner"]').find('button').trigger('click')
    await flushPromises()

    expect(fetchEvents).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="activity-row"]').exists()).toBe(true)
  })
})
