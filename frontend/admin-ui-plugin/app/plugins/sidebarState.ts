import { createSidebarState, SIDEBAR_STATE_KEY } from '@cbs/components'
import { defineNuxtPlugin, useCookie } from 'nuxt/app'

/**
 * Provide sidebar state per Vue app instance — on the server that means per
 * request, so collapsed/drawer state never leaks between users. State is
 * persisted in localStorage and mirrored into a cookie, so SSR renders the
 * layout the user last chose instead of flashing the default.
 */
export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.provide(SIDEBAR_STATE_KEY, createSidebarState({ useCookie }))
})
