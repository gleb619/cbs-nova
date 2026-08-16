import { addLayout, createResolver, defineNuxtModule, extendPages, resolvePath } from '@nuxt/kit'

// ---------------------------------------------------------------------------
// @cbs/admin-ui-plugin
//
// A Nuxt module that mounts the full CBS Nova admin UI into any host Nuxt app.
//
// Usage in a host nuxt.config.ts:
//
//   export default defineNuxtConfig({
//     modules: ['@cbs/admin-ui-plugin'],
//     adminUiPlugin: {
//       // optional overrides (see ModuleOptions below)
//     },
//   })
//
// The module registers:
//   - Pinia for client-side state management
//   - The default shell layout (sidebar + top bar)
//   - All admin pages under the configured `routePrefix` (default: '/')
//   - Nitro server routes under /api/v1/** that proxy to the Spring Boot backend
//   - The global Tailwind CSS stylesheet
//   - Runtime config keys: backendBaseUrl, backendApiKey, backendTimeoutMs, public.appName
// ---------------------------------------------------------------------------

export interface ModuleOptions {
  /**
   * Prefix for all admin UI routes.
   * Defaults to '/' — pages are mounted at /, /runner, /dsl-workbench, /executions.
   * Set to e.g. '/admin' to mount them at /admin, /admin/runner, etc.
   */
  routePrefix?: string

  /**
   * Spring Boot backend base URL (server-side only).
   * Defaults to BACKEND_BASE_URL env var, then 'http://localhost:8090'.
   */
  backendBaseUrl?: string

  /**
   * Optional API key forwarded as X-Api-Key to the backend (server-side only).
   * Defaults to BACKEND_API_KEY env var.
   */
  backendApiKey?: string

  /**
   * Outbound request timeout (ms) for BFF -> backend calls.
   * Surfaced as a 504 BACKEND_TIMEOUT when exceeded. Defaults to 10000.
   */
  backendTimeoutMs?: number

  /**
   * Display name shown in the admin UI title bar.
   * Defaults to 'CBS Nova Admin'.
   */
  appName?: string
}

export default defineNuxtModule<ModuleOptions>({
  meta: {
    name: '@cbs/admin-ui-plugin',
    configKey: 'adminUiPlugin',
    compatibility: { nuxt: '>=3.0.0' },
  },

  defaults: {
    routePrefix: '/',
    backendBaseUrl: process.env.BACKEND_BASE_URL ?? 'http://localhost:8090',
    backendApiKey: process.env.BACKEND_API_KEY ?? '',
    backendTimeoutMs: Number(process.env.BACKEND_TIMEOUT_MS ?? 10000),
    appName: 'CBS Nova Admin',
  },

  async setup(options, nuxt) {
    const { resolve } = createResolver(import.meta.url)

    // -----------------------------------------------------------------------
    // Runtime config
    // Merged into the host app's runtimeConfig so BFF server utils can read
    // them via useRuntimeConfig() without any extra setup in the host.
    // -----------------------------------------------------------------------
    nuxt.options.runtimeConfig.backendBaseUrl =
      nuxt.options.runtimeConfig.backendBaseUrl || options.backendBaseUrl || 'http://localhost:8090'
    nuxt.options.runtimeConfig.backendApiKey =
      nuxt.options.runtimeConfig.backendApiKey || options.backendApiKey || ''
    nuxt.options.runtimeConfig.backendTimeoutMs =
      nuxt.options.runtimeConfig.backendTimeoutMs || options.backendTimeoutMs || 10000
    nuxt.options.runtimeConfig.public.appName =
      nuxt.options.runtimeConfig.public.appName || options.appName || 'CBS Nova Admin'

    // -----------------------------------------------------------------------
    // Global stylesheet (Tailwind base/components/utilities + body tokens)
    // -----------------------------------------------------------------------
    nuxt.options.css.push(resolve('./assets/css/main.css'))

    // -----------------------------------------------------------------------
    // Component resolution — plugin-local components and @cbs/components SFCs
    // -----------------------------------------------------------------------
    nuxt.options.components = nuxt.options.components || { dirs: [] }
    nuxt.options.components.dirs = nuxt.options.components.dirs || []
    const componentsPackageDir = await resolvePath('@cbs/components/src/components')
    nuxt.options.components.dirs.push(
      { path: resolve('./app/components'), pathPrefix: true },
      { path: componentsPackageDir, pathPrefix: true },
    )

    // -----------------------------------------------------------------------
    // Pinia
    // -----------------------------------------------------------------------
    if (!nuxt.options.modules?.includes('@pinia/nuxt')) {
      nuxt.options.modules = nuxt.options.modules || []
      nuxt.options.modules.push('@pinia/nuxt')
    }

    // -----------------------------------------------------------------------
    // Layouts — register the default shell layout from this plugin
    // -----------------------------------------------------------------------
    addLayout(
      { src: resolve('./app/layouts/default.vue'), filename: 'cbs-admin-default.vue' },
      'default',
    )

    // -----------------------------------------------------------------------
    // Pages — extend the host router with the admin UI pages
    // -----------------------------------------------------------------------
    const prefix = (options.routePrefix ?? '/').replace(/\/$/, '')

    extendPages((pages) => {
      pages.push(
        {
          name: 'cbs-admin-dashboard',
          path: `${prefix}/`,
          file: resolve('./app/pages/index.vue'),
        },
        {
          name: 'cbs-admin-runner',
          path: `${prefix}/runner`,
          file: resolve('./app/pages/runner.vue'),
        },
        {
          name: 'cbs-admin-dsl-workbench',
          path: `${prefix}/dsl-workbench`,
          file: resolve('./app/pages/dsl-workbench.vue'),
        },
        {
          name: 'cbs-admin-executions',
          path: `${prefix}/executions`,
          file: resolve('./app/pages/executions/index.vue'),
        },
        {
          name: 'cbs-admin-execution-detail',
          path: `${prefix}/executions/:id`,
          file: resolve('./app/pages/executions/[id].vue'),
        },
      )
    })

    // -----------------------------------------------------------------------
    // Composables auto-import — expose plugin composables to the host app
    // -----------------------------------------------------------------------
    nuxt.options.imports = nuxt.options.imports || {}
    nuxt.options.imports.dirs = nuxt.options.imports.dirs || []
    nuxt.options.imports.dirs.push(resolve('./app/composables'))

    // -----------------------------------------------------------------------
    // Nitro server routes (BFF) — proxy layer to the Spring Boot API
    // Merge the plugin's server/ directory into the host Nitro build so all
    // /api/v1/** routes are available without any host-side configuration.
    // -----------------------------------------------------------------------
    nuxt.hook('nitro:config', (nitroConfig) => {
      nitroConfig.scanDirs = nitroConfig.scanDirs || []
      nitroConfig.scanDirs.push(resolve('./server'))
    })

    // -----------------------------------------------------------------------
    // TypeScript path aliases — let plugin-internal code keep using ~/types
    // -----------------------------------------------------------------------
    nuxt.hook('prepare:types', ({ tsConfig }) => {
      tsConfig.compilerOptions = tsConfig.compilerOptions || {}
      tsConfig.compilerOptions.paths = tsConfig.compilerOptions.paths || {}
      tsConfig.compilerOptions.paths['~/types'] = [resolve('./app/types/index.ts')]
      tsConfig.compilerOptions.paths['~/types/*'] = [resolve('./app/types/*')]
    })
  },
})
