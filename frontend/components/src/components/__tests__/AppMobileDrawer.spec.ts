import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { useSidebar } from '../../composables/useSidebar'
import AppMobileDrawer from '../AppMobileDrawer.vue'

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

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
    document.body.innerHTML = ''
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

  describe('useModalDialog integration', () => {
    const drawer = () => document.querySelector('aside[role="dialog"]') as HTMLElement

    it('closes when Escape is pressed inside the drawer', async () => {
      mountDrawer({ props: { items } })
      await nextTick()
      await flushPromises()

      drawer().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await nextTick()
      await flushPromises()

      expect(document.querySelector('aside[role="dialog"]')).toBeNull()
    })

    it('moves focus to the first tabbable element when opened', async () => {
      mountDrawer({ props: { items } })
      await nextTick()
      await flushPromises()

      expect(document.activeElement).toBe(
        document.querySelector('[data-testid="app-mobile-drawer-close"]'),
      )
    })

    it('traps focus cycling forward with Tab', async () => {
      mountDrawer({ props: { items } })
      await nextTick()
      await flushPromises()

      const first = document.querySelector(
        '[data-testid="app-mobile-drawer-close"]',
      ) as HTMLElement
      const last = drawerAnchors().at(-1) as HTMLAnchorElement

      last.focus()
      document.activeElement?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }),
      )
      await nextTick()

      expect(document.activeElement).toBe(first)
    })

    it('traps focus cycling backward with Shift+Tab', async () => {
      mountDrawer({ props: { items } })
      await nextTick()
      await flushPromises()

      const first = document.querySelector(
        '[data-testid="app-mobile-drawer-close"]',
      ) as HTMLElement
      const last = drawerAnchors().at(-1) as HTMLAnchorElement

      first.focus()
      document.activeElement?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }),
      )
      await nextTick()

      expect(document.activeElement).toBe(last)
    })

    it('makes background siblings inert while open and restores them on close', async () => {
      const sibling = document.createElement('div')
      document.body.appendChild(sibling)

      mountDrawer({ props: { items } })
      await nextTick()
      await flushPromises()

      expect(sibling.inert).toBe(true)
      expect(sibling.getAttribute('aria-hidden')).toBe('true')

      useSidebar().closeMobile()
      await nextTick()

      expect(sibling.inert).toBe(false)
      expect(sibling.hasAttribute('aria-hidden')).toBe(false)
    })

    it('returns focus to the previously focused element when closed', async () => {
      const trigger = document.createElement('button')
      document.body.appendChild(trigger)
      trigger.focus()

      mountDrawer({ props: { items } })
      await nextTick()
      await flushPromises()
      expect(document.activeElement).not.toBe(trigger)

      useSidebar().closeMobile()
      await nextTick()

      expect(document.activeElement).toBe(trigger)
    })
  })
})
