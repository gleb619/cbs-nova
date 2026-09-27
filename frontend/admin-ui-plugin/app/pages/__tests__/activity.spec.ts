import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import ActivityPage from '../activity.vue'

const fetchEvents = vi.fn()

vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: () => ({ fetchEvents }),
}))

const NuxtLinkStub = defineComponent({
  name: 'NuxtLink',
  render: () => h('a', 'link'),
})

const ActivityFeedProbe = defineComponent({
  name: 'ActivityFeed',
  props: ['fetchEvents', 'executionLink', 'linkComponent'],
  render: () => h('div', { 'data-testid': 'activity-feed-probe' }),
})

function mountPage() {
  return mount(ActivityPage, {
    global: {
      stubs: {
        NuxtLink: NuxtLinkStub,
        ActivityFeed: ActivityFeedProbe,
      },
    },
    attachTo: document.body,
  })
}

describe('activity page (T662)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders the activity-page root', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('[data-testid="activity-page"]').exists()).toBe(true)
  })

  it('wires dslApi.fetchEvents into ActivityFeed as fetch-events', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const probe = wrapper.findComponent(ActivityFeedProbe)
    expect(probe.exists()).toBe(true)
    expect(probe.props('fetchEvents')).toBe(fetchEvents)
  })

  it('passes executionLink that returns /executions/:aggregateId', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const executionLink = wrapper.findComponent(ActivityFeedProbe).props(
      'executionLink',
    ) as (aggregateId: string) => string | null
    expect(executionLink('agg-123')).toBe('/executions/agg-123')
  })

  it('resolves link-component to NuxtLink', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const probe = wrapper.findComponent(ActivityFeedProbe)
    expect(probe.props('linkComponent')).toMatchObject({ name: 'NuxtLink' })
  })
})
