import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, type Ref, ref } from 'vue'
import {
  createSidebarState,
  resetSidebarState,
  SIDEBAR_STATE_KEY,
  SIDEBAR_STORAGE_NAMESPACE,
  type SidebarStorageOptions,
  useSidebar,
} from '../useSidebar'

const COLLAPSED_KEY = `${SIDEBAR_STORAGE_NAMESPACE}:collapsed`
const HIDDEN_KEY = `${SIDEBAR_STORAGE_NAMESPACE}:hidden`
const MOBILE_OPEN_KEY = `${SIDEBAR_STORAGE_NAMESPACE}:mobile-open`

type Sidebar = ReturnType<typeof useSidebar>

function setViewport(width: number) {
  Object.defineProperty(window, 'innerWidth', { value: width, configurable: true, writable: true })
}

/** Mount a component that consumes the provided (per-app) sidebar state. */
function withProvidedState(options: SidebarStorageOptions = {}): Sidebar {
  let sidebar: Sidebar | undefined
  const Comp = defineComponent({
    setup() {
      sidebar = useSidebar()
      return () => h('div')
    },
  })
  mount(Comp, {
    global: { provide: { [SIDEBAR_STATE_KEY as symbol]: createSidebarState(options) } },
  })
  return sidebar as Sidebar
}

function fakeCookies() {
  const cookies = new Map<string, Ref<unknown>>()
  const useCookie = <T>(name: string, options?: { default?: () => T }): Ref<T> => {
    if (!cookies.has(name)) cookies.set(name, ref(options?.default?.()))
    return cookies.get(name) as Ref<T>
  }
  return { cookies, useCookie }
}

describe('useSidebar', () => {
  beforeEach(() => {
    window.localStorage.clear()
    resetSidebarState()
    setViewport(1280)
  })

  afterEach(() => {
    resetSidebarState()
  })

  it('toggles collapsed state', () => {
    const { collapsed, toggle } = useSidebar()
    const initial = collapsed.value
    toggle()
    expect(collapsed.value).toBe(!initial)
  })

  it('expands and collapses desktop sidebar', () => {
    const { collapsed, expand, collapse } = useSidebar()
    collapsed.value = true
    expand()
    expect(collapsed.value).toBe(false)
    collapse()
    expect(collapsed.value).toBe(true)
  })

  it('opens and closes mobile drawer', () => {
    const { mobileOpen, openMobile, closeMobile } = useSidebar()
    openMobile()
    expect(mobileOpen.value).toBe(true)
    closeMobile()
    expect(mobileOpen.value).toBe(false)
  })

  it('hide() forces collapsed and sets hidden', () => {
    const { collapsed, hidden, hide } = useSidebar()
    collapsed.value = false
    hide()
    expect(hidden.value).toBe(true)
    expect(collapsed.value).toBe(true)
  })

  it('unhide() clears hidden and keeps the rail collapsed', () => {
    const { collapsed, hidden, unhide } = useSidebar()
    collapsed.value = false
    hidden.value = true
    unhide()
    expect(hidden.value).toBe(false)
    expect(collapsed.value).toBe(true)
  })

  it('toggleHidden() flips between hidden and collapsed-restored', () => {
    const { collapsed, hidden, toggleHidden } = useSidebar()
    collapsed.value = false
    toggleHidden()
    expect(hidden.value).toBe(true)
    expect(collapsed.value).toBe(true)
    toggleHidden()
    expect(hidden.value).toBe(false)
    expect(collapsed.value).toBe(true)
  })

  it('persists hidden state to localStorage', async () => {
    useSidebar().hide()
    await nextTick()
    expect(window.localStorage.getItem(HIDDEN_KEY)).toBe('true')
  })

  it('restores hidden state from localStorage', () => {
    window.localStorage.setItem(HIDDEN_KEY, 'true')
    expect(useSidebar().hidden.value).toBe(true)
  })

  it('shares the fallback state between callers', () => {
    const first = useSidebar()
    const second = useSidebar()
    first.collapse()
    expect(second.collapsed.value).toBe(true)
  })

  it('persists collapsed state to localStorage', async () => {
    useSidebar().toggle()
    await nextTick()
    expect(window.localStorage.getItem(COLLAPSED_KEY)).toBe('true')
  })

  it('persists mobile drawer state to localStorage', async () => {
    useSidebar().openMobile()
    await nextTick()
    expect(window.localStorage.getItem(MOBILE_OPEN_KEY)).toBe('true')
  })

  it('restores collapsed state from localStorage', () => {
    window.localStorage.setItem(COLLAPSED_KEY, 'true')
    expect(useSidebar().collapsed.value).toBe(true)
  })

  it('keeps persisting after repeated updates', async () => {
    const { toggle } = useSidebar()
    toggle()
    await nextTick()
    toggle()
    await nextTick()
    expect(window.localStorage.getItem(COLLAPSED_KEY)).toBe('false')
  })

  it('prefers provided (per-app) state over the module fallback', () => {
    const fallback = useSidebar()
    const provided = withProvidedState()
    provided.collapse()
    expect(provided.collapsed.value).toBe(true)
    expect(fallback.collapsed.value).toBe(false)
  })

  it('mirrors state into the injected cookie factory', async () => {
    const { cookies, useCookie } = fakeCookies()

    withProvidedState({ useCookie }).toggle()
    await nextTick()

    expect(cookies.get(COLLAPSED_KEY)?.value).toBe(true)
  })

  it('reads the initial value from the cookie so SSR and client agree', () => {
    const useCookie = <T>(_name: string, _options?: { default?: () => T }): Ref<T> =>
      ref(true) as Ref<T>
    window.localStorage.setItem(COLLAPSED_KEY, 'false')

    expect(withProvidedState({ useCookie }).collapsed.value).toBe(true)
  })

  it('toggleResponsive opens the drawer below the breakpoint', () => {
    setViewport(500)
    const { mobileOpen, collapsed, toggleResponsive } = useSidebar()
    toggleResponsive()
    expect(mobileOpen.value).toBe(true)
    expect(collapsed.value).toBe(false)
    toggleResponsive()
    expect(mobileOpen.value).toBe(false)
  })

  it('toggleResponsive collapses the sidebar above the breakpoint', () => {
    const { mobileOpen, collapsed, toggleResponsive } = useSidebar()
    toggleResponsive()
    expect(collapsed.value).toBe(true)
    expect(mobileOpen.value).toBe(false)
  })

  it('honours a custom breakpoint', () => {
    setViewport(900)
    const { mobileOpen, toggleResponsive } = withProvidedState({ breakpoint: 1024 })
    toggleResponsive()
    expect(mobileOpen.value).toBe(true)
  })

  it('createSidebarState returns isolated states (no SSR cross-request leak)', () => {
    const a = createSidebarState()
    const b = createSidebarState()
    a.collapsed.value = true
    expect(b.collapsed.value).toBe(false)
    a.dispose()
    b.dispose()
  })
})
