import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatCard from '../dashboard/StatCard.vue'

describe('StatCard', () => {
  it('renders label and count with the stat-card testid root', () => {
    const wrapper = mount(StatCard, {
      props: { label: 'Running', count: 7, to: '/executions' },
    })

    const root = wrapper.find('[data-testid="stat-card"]')
    expect(root.exists()).toBe(true)
    expect(root.text()).toContain('7')
    expect(root.text()).toContain('Running')
  })

  it('renders an anchor link to the target by default', () => {
    const wrapper = mount(StatCard, {
      props: { label: 'Failed', count: 3, to: '/executions?status=Failed' },
    })

    const link = wrapper.find('[data-testid="stat-card-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/executions?status=Failed')
    expect(link.text()).toContain('View')
  })

  it('renders the icon when provided', () => {
    const wrapper = mount(StatCard, {
      props: { label: 'Total', count: 42, to: '/executions', icon: '▶' },
    })

    expect(wrapper.text()).toContain('▶')
  })

  it('omits the icon element when not provided', () => {
    const wrapper = mount(StatCard, {
      props: { label: 'Total', count: 42, to: '/executions' },
    })

    expect(wrapper.find('span.text-3xl').exists()).toBe(false)
  })

  it('renders a custom link component (e.g. NuxtLink) instead of an anchor', () => {
    const wrapper = mount(StatCard, {
      props: {
        label: 'Stale',
        count: 1,
        to: '/executions?status=Stale',
        linkComponent: {
          name: 'RouterLinkStub',
          props: ['to'],
          template: '<a class="router-stub" :href="to"><slot /></a>',
        },
      },
    })

    // The custom component receives the to prop and renders its own element;
    // the plain <a> branch must not be taken.
    const stub = wrapper.find('a.router-stub')
    expect(stub.exists()).toBe(true)
    expect(stub.attributes('href')).toBe('/executions?status=Stale')
    expect(stub.text()).toContain('View')
  })
})
