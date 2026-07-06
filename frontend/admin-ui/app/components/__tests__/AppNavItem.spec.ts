import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
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