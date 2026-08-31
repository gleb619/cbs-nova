/**
 * Test-only 'nuxt/app' stub, wired via the vitest alias in vitest.config.ts.
 *
 * The real 'nuxt/app' entry imports Nuxt build artifacts (`#build/*`) that only
 * exist inside a prepared `.nuxt` directory, so importing it under plain
 * vitest throws. Unit-tested composables only need `useState` (and pages may
 * need the router helpers); these stubs mirror the globals already exposed by
 * vitest.setup.ts.
 */
import { ref } from 'vue'

export const useState = (_key: string, init: () => unknown) => ref(init())
export const navigateTo = () => Promise.resolve()
export const useRoute = () => ({ path: '/', params: {}, query: {}, meta: {} })
export const useRouter = () => ({ push: () => Promise.resolve(), replace: () => Promise.resolve() })
export const useCookie = () => ({ value: undefined as unknown })
export const clearError = () => {}
export const defineNuxtPlugin = (fn: unknown) => fn
export const useRuntimeConfig = () => ({
  backendBaseUrl: 'http://localhost:8090',
  backendApiKey: '',
  backendTimeoutMs: 10000,
  public: {
    appName: 'CBS Nova Admin',
    temporalUiBaseUrl: '',
    temporalNamespace: 'default',
  },
})
