import { effectScope, getCurrentInstance, type InjectionKey, inject, type Ref } from 'vue'
import { createNamespacedLocalStorageState, type UseCookieFactory } from './useLocalStorageState'

export const SIDEBAR_STORAGE_NAMESPACE = 'cbs-nova:sidebar'
export const SIDEBAR_COLLAPSED_KEY = 'collapsed'
export const SIDEBAR_MOBILE_OPEN_KEY = 'mobile-open'
export const SIDEBAR_MOBILE_BREAKPOINT = 768

export interface SidebarStorageOptions {
  /**
   * Host-app cookie factory (e.g. Nuxt `useCookie`). When provided, sidebar
   * state is mirrored into a cookie so SSR renders the persisted layout.
   */
  useCookie?: UseCookieFactory
  /** Viewport width (px) below which the mobile drawer is used. */
  breakpoint?: number
}

export interface SidebarState {
  collapsed: Ref<boolean>
  mobileOpen: Ref<boolean>
  breakpoint: number
  /** Stops the persistence watchers. */
  dispose: () => void
}

/**
 * App-level injection so each SSR request gets its own state. Provide it from a
 * host plugin: `nuxtApp.vueApp.provide(SIDEBAR_STATE_KEY, createSidebarState({ useCookie }))`.
 */
export const SIDEBAR_STATE_KEY: InjectionKey<SidebarState> = Symbol('cbs-nova:sidebar-state')

const useSidebarStorage = createNamespacedLocalStorageState(SIDEBAR_STORAGE_NAMESPACE)

/**
 * Build a fresh, persisted sidebar state. Must be called once per Vue app —
 * module-level state would leak between SSR requests.
 */
export function createSidebarState(options: SidebarStorageOptions = {}): SidebarState {
  // Detached scope: persistence watchers must outlive whichever component or
  // plugin happened to create the state.
  const scope = effectScope(true)
  let collapsed: Ref<boolean> | undefined
  let mobileOpen: Ref<boolean> | undefined

  scope.run(() => {
    collapsed = useSidebarStorage<boolean>(SIDEBAR_COLLAPSED_KEY, false, {
      useCookie: options.useCookie,
    })
    mobileOpen = useSidebarStorage<boolean>(SIDEBAR_MOBILE_OPEN_KEY, false, {
      useCookie: options.useCookie,
    })
  })

  return {
    collapsed: collapsed as Ref<boolean>,
    mobileOpen: mobileOpen as Ref<boolean>,
    breakpoint: options.breakpoint ?? SIDEBAR_MOBILE_BREAKPOINT,
    dispose: () => scope.stop(),
  }
}

// Client-only fallback for hosts that never provide a state (plain SPA usage,
// unit tests). Never used during SSR, where `createSidebarState` is provided.
let fallbackState: SidebarState | undefined

function resolveState(): SidebarState {
  const injected = getCurrentInstance() ? inject(SIDEBAR_STATE_KEY, null) : null
  if (injected) return injected
  fallbackState ??= createSidebarState()
  return fallbackState
}

/** Drop the fallback state (tests, HMR). */
export function resetSidebarState(): void {
  fallbackState?.dispose()
  fallbackState = undefined
}

export function useSidebar() {
  const { collapsed, mobileOpen, breakpoint } = resolveState()

  function isMobileViewport(): boolean {
    if (typeof window === 'undefined') return false
    return window.innerWidth < breakpoint
  }

  function toggle() {
    collapsed.value = !collapsed.value
  }
  function collapse() {
    collapsed.value = true
  }
  function expand() {
    collapsed.value = false
  }
  function openMobile() {
    mobileOpen.value = true
  }
  function closeMobile() {
    mobileOpen.value = false
  }
  function toggleMobile() {
    mobileOpen.value = !mobileOpen.value
  }
  /** Mobile viewport → drawer; desktop → collapse/expand. */
  function toggleResponsive() {
    if (isMobileViewport()) {
      toggleMobile()
    } else {
      toggle()
    }
  }

  return {
    collapsed,
    mobileOpen,
    toggle,
    collapse,
    expand,
    openMobile,
    closeMobile,
    toggleMobile,
    toggleResponsive,
    isMobileViewport,
  }
}
