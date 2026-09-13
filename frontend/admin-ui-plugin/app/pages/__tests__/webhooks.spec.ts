import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import WebhooksPage from '../webhooks.vue'

const fetchWebhookDeliveries = vi.fn()

vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: () => ({ fetchWebhookDeliveries }),
}))

const WebhookDeliveriesPanelProbe = defineComponent({
  name: 'WebhookDeliveriesPanel',
  props: ['fetchPage'],
  render: () => h('div', { 'data-testid': 'webhook-deliveries-panel-probe' }),
})

function mountPage() {
  return mount(WebhooksPage, {
    global: {
      stubs: { WebhookDeliveriesPanel: WebhookDeliveriesPanelProbe },
    },
  })
}

describe('webhooks page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the page header', () => {
    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="webhooks-page"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Webhooks')
  })

  it('wires dslApi.fetchWebhookDeliveries into the panel as fetchPage', async () => {
    fetchWebhookDeliveries.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const wrapper = mountPage()

    const probe = wrapper.findComponent(WebhookDeliveriesPanelProbe)
    expect(probe.exists()).toBe(true)
    expect(probe.props('fetchPage')).toBe(fetchWebhookDeliveries)

    await probe.props('fetchPage')({ limit: 25, offset: 0 })
    expect(fetchWebhookDeliveries).toHaveBeenCalledWith({ limit: 25, offset: 0 })
    await flushPromises()
  })
})
