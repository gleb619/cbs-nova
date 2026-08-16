// Development-only Nuxt config. Used by `nuxt dev` and `nuxt build` to run
// the admin-ui-plugin as a standalone Nuxt application for local development.
// In production the plugin is consumed via `modules: ['@cbs/admin-ui-plugin']`
// inside a host Nuxt app.
import AdminUiPlugin from './module'

export default defineNuxtConfig({
  modules: [AdminUiPlugin],
  devtools: { enabled: true },
  compatibilityDate: '2024-04-03',
  pages: true,
})
