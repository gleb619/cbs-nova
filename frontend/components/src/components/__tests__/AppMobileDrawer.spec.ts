import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useSidebar } from '../../composables/useSidebar'
import AppMobileDrawer from '../AppMobileDrawer.vue'

type NavItem = { to: string; label: string; icon?: string; isActive?: boolean }

const items: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/workflows', label: 'Workflows', icon: '🧪' },
  { to: '/executions', label: 'Executions', isActive: true },
]

function resetSidebarState() {
  const { collapsed, mobileOpen } = useSidebar()
  collapsed.value = false
  mobileOpen.value = true
}

function collectLinks(root: ParentNode): Array<{ href: string; label: string }> {
  return Array.from(root.querySelectorAll('nav a')).map((a) => ({
    href: a.getAttribute('href') ?? '',
    label: (a.textContent ?? '').trim(),
  }))
}

function drawerAnchors(): HTMLAnchorElement[] {
  return Array.from(document.querySelectorAll('aside[role="dialog"] nav a'))
}

describe('AppMobileDrawer', () => {
  let wrappers: VueWrapper[] = []

  beforeEach(() => {
    resetSidebarState()
    wrappers = []
  })

  afterEach(() => {
    for (const w of wrappers) w.unmount()
    wrappers = []
  })

  function mountDrawer(props: Parameters<typeof mount>[1]) {
    const w = mount(AppMobileDrawer, props)
    wrappers.push(w)
    return w
  }

  it('renders the dialog aside when open', () => {
    mountDrawer({ props: { items } })
    expect(document.querySelector('aside[role="dialog"]')).not.toBeNull()
  })

  it('renders exactly one nav entry per item when open', () => {
    mountDrawer({ props: { items } })
    expect(collectLinks(document)).toHaveLength(items.length)
    expect(drawerAnchors()).toHaveLength(items.length)
    expect(drawerAnchors().length).toBeGreaterThanOrEqual(0)
  })

  it('forwards to + label from each item to its AppNavItem (rendered href + visible label)', () => {
    mountDrawer({ props: { items } })
    const anchors = drawerAnchors()
    expect(anchors).toHaveLength(items.length)
    for (let i = 0; i < items.length; i++) {
      expect(anchors[i].getAttribute('href')).toBe(items[i].to)
      expect((anchors[i].textContent ?? '').trim()).toContain(items[i].label)
    }
  })

  it('renders no nav entries when items is empty', () => {
    mountDrawer({ props: { items: [] } })
    expect(collectLinks(document)).toHaveLength(0)
    expect(drawerAnchors()).toHaveLength(0)
  })
})
