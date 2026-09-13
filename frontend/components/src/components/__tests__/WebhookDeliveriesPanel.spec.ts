import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { WebhookDeliveryPage, WebhookDeliveryRecord } from '../../types/webhooks'
import WebhookDeliveriesPanel from '../WebhookDeliveriesPanel.vue'

function makeItem(overrides: Partial<WebhookDeliveryRecord> = {}): WebhookDeliveryRecord {
  return {
    id: 1,
    occurredAt: '2026-09-13T10:00:00.000Z',
    subscriptionId: 'OrderProcess',
    eventType: 'RunCompleted',
    url: 'https://partner.example.com/webhooks/nova?signature=secret',
    status: 'delivered',
    attempts: 1,
    lastError: null,
    durationMs: 120,
    ...overrides,
  }
}

function makePage(overrides: Partial<WebhookDeliveryPage> = {}): WebhookDeliveryPage {
  return {
    items: [makeItem(), makeItem({ id: 2, status: 'rejected', subscriptionId: 'RefundFlow' })],
    total: 2,
    offset: 0,
    limit: 25,
    ...overrides,
  }
}

function mountPanel(fetchPage = vi.fn().mockResolvedValue(makePage())) {
  const wrapper = mount(WebhookDeliveriesPanel, { props: { fetchPage } })
  return { wrapper, fetchPage }
}

describe('WebhookDeliveriesPanel', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('loads the first page on mount and renders its rows', async () => {
    const { wrapper, fetchPage } = mountPanel()
    await flushPromises()

    expect(fetchPage).toHaveBeenCalledWith({ subscriptionId: '', limit: 25, offset: 0 })
    const rows = wrapper.findAll('[data-testid="webhook-deliveries-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('OrderProcess')
    expect(rows[1].text()).toContain('RefundFlow')
  })

  it('renders the URL as host and path only, never the query string', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    const url = wrapper.find('[data-testid="webhook-deliveries-url"]')
    expect(url.text()).toBe('partner.example.com/webhooks/nova')
    expect(url.text()).not.toContain('signature')
  })

  it('renders relative time with the ISO timestamp as title and the attempt count', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    const firstRow = wrapper.findAll('[data-testid="webhook-deliveries-row"]')[0]
    expect(firstRow.find('td').attributes('title')).toBe('2026-09-13T10:00:00.000Z')
    expect(firstRow.text()).toContain('ago')
    expect(firstRow.find('[data-testid="webhook-deliveries-attempts"]').text()).toBe('1')
  })

  it('shows an empty last error cell for successful deliveries', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    const cells = wrapper.findAll('[data-testid="webhook-deliveries-last-error"]')
    expect(cells[0].text()).toBe('')
    expect(cells[0].attributes('title')).toBe('')
  })

  it('truncates a long last error with the full text as title', async () => {
    const longError = 'connection reset by peer: '.repeat(10)
    const { wrapper } = mountPanel(
      vi.fn().mockResolvedValue(makePage({ items: [makeItem({ lastError: longError })] })),
    )
    await flushPromises()

    const cell = wrapper.find('[data-testid="webhook-deliveries-last-error"]')
    expect(cell.attributes('title')).toBe(longError)
    expect(cell.classes()).toContain('truncate')
  })

  it('styles the status chip green for delivered and 2xx/3xx codes', async () => {
    const { wrapper } = mountPanel(
      vi.fn().mockResolvedValue(
        makePage({
          items: [makeItem({ status: 'delivered' }), makeItem({ id: 2, status: '204' })],
        }),
      ),
    )
    await flushPromises()

    const badges = wrapper.findAll('[data-testid="webhook-deliveries-status"]')
    expect(badges[0].classes()).toContain('bg-green-100')
    expect(badges[1].classes()).toContain('bg-green-100')
  })

  it('styles the status chip red for rejected, serialization_failed, failed and 5xx codes', async () => {
    const { wrapper } = mountPanel(
      vi.fn().mockResolvedValue(
        makePage({
          items: [
            makeItem({ status: 'rejected' }),
            makeItem({ id: 2, status: 'serialization_failed' }),
            makeItem({ id: 3, status: 'failed' }),
            makeItem({ id: 4, status: '500' }),
          ],
        }),
      ),
    )
    await flushPromises()

    const badges = wrapper.findAll('[data-testid="webhook-deliveries-status"]')
    for (const badge of badges) {
      expect(badge.classes()).toContain('bg-red-100')
    }
  })

  it('falls back to a neutral chip for unknown statuses', async () => {
    const { wrapper } = mountPanel(
      vi.fn().mockResolvedValue(makePage({ items: [makeItem({ status: 'queued' })] })),
    )
    await flushPromises()

    const badge = wrapper.find('[data-testid="webhook-deliveries-status"]')
    expect(badge.classes()).toContain('bg-gray-100')
  })

  it('round-trips the subscription filter through the server and resets to the first page', async () => {
    const { wrapper, fetchPage } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="webhook-deliveries-filter-subscription"]').setValue('Order')
    await wrapper.find('form').trigger('submit')

    expect(fetchPage).toHaveBeenLastCalledWith({ subscriptionId: 'Order', limit: 25, offset: 0 })
  })

  it('persists the subscription filter in localStorage', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="webhook-deliveries-filter-subscription"]').setValue('Order')
    await wrapper.find('form').trigger('submit')

    expect(localStorage.getItem('webhooks.filter.subscriptionId')).toBe('"Order"')
  })

  it('pages forward and back through the pager', async () => {
    const fetchPage = vi.fn().mockImplementation(async (params: { offset: number }) =>
      makePage({
        items: [makeItem({ id: params.offset + 1 })],
        total: 60,
        offset: params.offset,
        limit: 25,
      }),
    )
    const { wrapper } = mountPanel(fetchPage)
    await flushPromises()

    expect(wrapper.find('[data-testid="webhook-deliveries-range"]').text()).toBe('1–1 of 60')
    expect(
      wrapper.find('[data-testid="webhook-deliveries-pager-prev"]').attributes('disabled'),
    ).toBeDefined()

    await wrapper.find('[data-testid="webhook-deliveries-pager-next"]').trigger('click')
    await flushPromises()
    expect(fetchPage).toHaveBeenLastCalledWith({ subscriptionId: '', limit: 25, offset: 25 })
    expect(wrapper.find('[data-testid="webhook-deliveries-range"]').text()).toBe('26–26 of 60')

    await wrapper.find('[data-testid="webhook-deliveries-pager-prev"]').trigger('click')
    await flushPromises()
    expect(fetchPage).toHaveBeenLastCalledWith({ subscriptionId: '', limit: 25, offset: 0 })
    expect(wrapper.find('[data-testid="webhook-deliveries-range"]').text()).toBe('1–1 of 60')
  })

  it('disables next when the last page is shown', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    expect(
      wrapper.find('[data-testid="webhook-deliveries-pager-next"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('shows the empty state when no deliveries are recorded', async () => {
    const { wrapper } = mountPanel(vi.fn().mockResolvedValue(makePage({ items: [], total: 0 })))
    await flushPromises()

    expect(wrapper.find('[data-testid="webhook-deliveries-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="webhook-deliveries-row"]').exists()).toBe(false)
  })

  it('shows the error state with a retry that reloads', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error('backend down'))
    const { wrapper } = mountPanel(fetchPage)
    await flushPromises()

    const errorState = wrapper.find('[data-testid="webhook-deliveries-error"]')
    expect(errorState.exists()).toBe(true)
    expect(errorState.text()).toContain('backend down')

    fetchPage.mockResolvedValueOnce(makePage())
    await errorState.find('[data-testid="error-banner"]').find('button').trigger('click')
    await flushPromises()

    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="webhook-deliveries-row"]').exists()).toBe(true)
  })
})
