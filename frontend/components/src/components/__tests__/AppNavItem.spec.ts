import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppNavItem from '../AppNavItem.vue'

describe('AppNavItem', () => {
  it('renders label text', () => {
    const wrapper = mount(AppNavItem, {
      props: { to: '/dashboard', label: 'Dashboard' },
    })
    expect(wrapper.text()).toContain('Dashboard')
  })

  it('renders an anchor by default', () => {
    const wrapper = mount(AppNavItem, { props: { to: '/x', label: 'X' } })
    expect(wrapper.find('a').exists()).toBe(true)
    expect(wrapper.find('a').attributes('href')).toBe('/x')
  })
})
