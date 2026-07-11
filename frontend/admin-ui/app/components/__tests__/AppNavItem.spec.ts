import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppNavItem from '../AppNavItem.vue'

describe('AppNavItem', () => {
  it('renders label text', () => {
    const wrapper = mount(AppNavItem, {
      props: { to: '/dashboard', label: 'Dashboard' },
      global: {
        stubs: { NuxtLink: { template: '<a><slot /></a>' } },
      },
    })
    expect(wrapper.text()).toContain('Dashboard')
  })
})
