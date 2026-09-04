import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSidebar } from '../../composables/useSidebar'
import AppMobileDrawer from '../AppMobileDrawer.vue'
import AppNavItem from '../AppNavItem.vue'
import AppSidebar from '../AppSidebar.vue'
import AppSidebarHideButton from '../sidebar/AppSidebarHideButton.vue'

type NavItem = { to: string; label: string; icon?: string; isActive?: boolean }

const items: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/workflows', label: 'Workflows', icon: '🧪' },
  { to: '/executions', label: 'Executions', isActive: true },
]

function resetSidebarState() {
  const { collapsed, hidden, mobileOpen } = useSidebar()
  collapsed.value = false
  hidden.value = false
  mobileOpen.value = false
}

function collectLinks(root: ParentNode): Array<{ href: string; label: string }> {
  return Array.from(root.querySelectorAll('nav a')).map((a) => ({
    href: a.getAttribute('href') ?? '',
    label: (a.textContent ?? '').trim(),
  }))
}

describe('AppSidebar', () => {
  beforeEach(resetSidebarState)

  it('renders exactly one AppNavItem per item when expanded', () => {
    const wrapper = mount(AppSidebar, { props: { items } })
    expect(wrapper.findAllComponents(AppNavItem)).toHaveLength(items.length)
  })

  it('forwards to + label from each item to its AppNavItem (props + rendered href/label)', () => {
    const wrapper = mount(AppSidebar, { props: { items } })
    const navItems = wrapper.findAllComponents(AppNavItem)
    expect(navItems).toHaveLength(items.length)
    for (let i = 0; i < items.length; i++) {
      expect(navItems[i].props('to')).toBe(items[i].to)
      expect(navItems[i].props('label')).toBe(items[i].label)
      const anchor = navItems[i].find('a')
      expect(anchor.exists()).toBe(true)
      expect(anchor.attributes('href')).toBe(items[i].to)
      expect(anchor.text()).toContain(items[i].label)
    }
  })

  it('renders no nav entries when items is empty', () => {
    const wrapper = mount(AppSidebar, { props: { items: [] } })
    expect(wrapper.findAllComponents(AppNavItem)).toHaveLength(0)
    expect(wrapper.findAll('nav a')).toHaveLength(0)
  })

  it('contract (T76): renders the same links as AppMobileDrawer for the same items', () => {
    const sidebar = mount(AppSidebar, { props: { items } })
    const sidebarLinks = collectLinks(sidebar.element)

    const { openMobile } = useSidebar()
    openMobile()
    const _drawer = mount(AppMobileDrawer, { props: { items } })
    const drawerLinks = collectLinks(document)

    expect(sidebarLinks).toHaveLength(items.length)
    expect(drawerLinks).toHaveLength(items.length)
    expect(sidebarLinks).toEqual(drawerLinks)
  })

  it('renders the hide button only when collapsed', async () => {
    const wrapper = mount(AppSidebar, { props: { items } })
    expect(wrapper.findComponent(AppSidebarHideButton).exists()).toBe(false)

    const { collapse } = useSidebar()
    collapse()
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent(AppSidebarHideButton).exists()).toBe(true)
  })

  it('collapses to w-0 when hidden', async () => {
    const wrapper = mount(AppSidebar, { props: { items } })
    const { hide } = useSidebar()
    hide()
    await wrapper.vm.$nextTick()
    const aside = wrapper.find('[data-testid="app-sidebar"]')
    expect(aside.attributes('aria-hidden')).toBe('true')
    expect(aside.classes()).toContain('w-0')
  })

  it('hide button click hides the rail and keeps it collapsed', async () => {
    const wrapper = mount(AppSidebar, { props: { items } })
    const { collapse } = useSidebar()
    collapse()
    await wrapper.vm.$nextTick()

    const button = wrapper.findComponent(AppSidebarHideButton)
    button.vm.$emit('click')
    button.trigger('click')
    await wrapper.vm.$nextTick()

    const { hidden, collapsed } = useSidebar()
    expect(hidden.value).toBe(true)
    expect(collapsed.value).toBe(true)
    expect(wrapper.find('[data-testid="app-sidebar"]').classes()).toContain('w-0')
  })
})
