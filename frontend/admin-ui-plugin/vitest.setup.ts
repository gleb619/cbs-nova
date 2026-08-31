import { vi } from 'vitest'
import { computed, onUnmounted, readonly, ref, watch } from 'vue'
import { __resetRouterStub } from './vitest.vue-router-stub'

if (typeof process !== 'undefined') {
  process.env.LOG_LEVEL = 'silent'
}

const g = globalThis as Record<string, unknown>

g.ref = ref
g.computed = computed
g.readonly = readonly
g.onUnmounted = onUnmounted
g.onMounted = (_fn: () => undefined | (() => unknown)) => undefined
g.watch = watch

g.useState = (_key: string, init: () => unknown) => ref(init())

// Expose a mocked $fetch on globalThis for tests that stub the outgoing backend calls.
const mockedFetch = vi.fn()
mockedFetch.raw = vi.fn()
g.$fetch = mockedFetch

// Stub of h3's defineEventHandler. The real one returns an EventHandler
// wrapper object; for unit tests we treat it as identity so route files
// (`export default defineEventHandler(handler)`) expose the inner handler
// directly and tests can invoke it as a plain function.
g.defineEventHandler = <T>(handler: T) => handler

// Stub of h3's createError used by proxyToBackend's catch block. Return a
// plain Error so assertions can match on .statusCode and .data.
type CreateErrorOptions = {
  statusCode?: number
  statusMessage?: string
  data?: unknown
}
g.createError = (opts: CreateErrorOptions = {}) => {
  const e = new Error(opts.statusMessage ?? 'Error') as Error & CreateErrorOptions
  e.statusCode = opts.statusCode
  e.statusMessage = opts.statusMessage
  e.data = opts.data
  return e
}

// Stub of Nuxt's useRuntimeConfig. Tests that need to assert specific values
// can re-assign globalThis.useRuntimeConfig before calling the SUT.
const defaultRuntimeConfig = {
  backendBaseUrl: 'http://localhost:8090',
  backendApiKey: '',
  backendTimeoutMs: 10000,
  authIssuer: '',
  authClientId: 'cbs-nova-bff',
  authClientSecret: '',
  authCallbackUrl: 'http://localhost:3000/api/v1/auth/callback',
  authPostLogoutRedirect: '/',
  public: { appName: 'CBS Nova Admin', authEnabled: false },
}
g.useRuntimeConfig = vi.fn(() => defaultRuntimeConfig)

// Stub of the plugin's own useBackendConfig/useAuthConfig. The real ones are
// exported from server/utils/config.ts and auto-imported by Nitro; in unit
// tests we expose the same shapes as bare globals so server utils can call
// them.
g.useBackendConfig = vi.fn(() => ({
  baseUrl: defaultRuntimeConfig.backendBaseUrl,
  apiKey: defaultRuntimeConfig.backendApiKey,
  timeoutMs: defaultRuntimeConfig.backendTimeoutMs,
}))

g.useAuthConfig = vi.fn(() => ({
  issuer: defaultRuntimeConfig.authIssuer,
  clientId: defaultRuntimeConfig.authClientId,
  clientSecret: defaultRuntimeConfig.authClientSecret,
  callbackUrl: defaultRuntimeConfig.authCallbackUrl,
  postLogoutRedirect: defaultRuntimeConfig.authPostLogoutRedirect,
  enabled: Boolean(defaultRuntimeConfig.authIssuer),
}))

// Layout header uses useAdminInfo; stub it with empty data so layout specs mount.
g.useAdminInfo = vi.fn(() => ({ data: ref({}) }))

// Client-side navigation / data stubs for composable specs.
let currentRoutePath = '/'
g.useRoute = vi.fn(() => ({ path: currentRoutePath }))
g.navigateTo = vi.fn((to: string, _opts?: unknown) => to)
g.useFetch = vi.fn((_url: string) => ({ data: ref({}) }))

let cachedExecutionsApi: { list: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn> } | null =
  null
const defaultExecutionsApi = () => {
  if (!cachedExecutionsApi) {
    cachedExecutionsApi = { list: vi.fn(), get: vi.fn() }
  }
  return cachedExecutionsApi
}
g.useExecutionsApi = vi.fn(defaultExecutionsApi)

let cachedDslApi: {
  getDefinitions: ReturnType<typeof vi.fn>
  preview: ReturnType<typeof vi.fn>
  run: ReturnType<typeof vi.fn>
  explain: ReturnType<typeof vi.fn>
  saveDraft: ReturnType<typeof vi.fn>
  validateConstruct: ReturnType<typeof vi.fn>
  reload: ReturnType<typeof vi.fn>
} | null = null
const defaultDslApi = () => {
  if (!cachedDslApi) {
    cachedDslApi = {
      getDefinitions: vi.fn(),
      preview: vi.fn(),
      run: vi.fn(),
      explain: vi.fn(),
      saveDraft: vi.fn(),
      validateConstruct: vi.fn(),
      reload: vi.fn(),
    }
  }
  return cachedDslApi
}
g.useDslApi = vi.fn(defaultDslApi)

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(g.useExecutionsApi as never).mockImplementation(defaultExecutionsApi)
  vi.mocked(g.useDslApi as never).mockImplementation(defaultDslApi)
  vi.mocked(g.useRuntimeConfig as never).mockImplementation(() => defaultRuntimeConfig)
  vi.mocked(g.useBackendConfig as never).mockImplementation(() => ({
    baseUrl: defaultRuntimeConfig.backendBaseUrl,
    apiKey: defaultRuntimeConfig.backendApiKey,
    timeoutMs: defaultRuntimeConfig.backendTimeoutMs,
  }))
  vi.mocked(g.useAuthConfig as never).mockImplementation(() => ({
    issuer: defaultRuntimeConfig.authIssuer,
    clientId: defaultRuntimeConfig.authClientId,
    clientSecret: defaultRuntimeConfig.authClientSecret,
    callbackUrl: defaultRuntimeConfig.authCallbackUrl,
    postLogoutRedirect: defaultRuntimeConfig.authPostLogoutRedirect,
    enabled: Boolean(defaultRuntimeConfig.authIssuer),
  }))
  vi.mocked(g.useAdminInfo as never).mockImplementation(() => ({ data: ref({}) }))
  vi.mocked(g.useFetch as never).mockImplementation(() => ({ data: ref({}) }))
  currentRoutePath = '/'
  // Reset the vue-router stub's captured guards so handlers from one spec do
  // not leak into the next.
  __resetRouterStub()
})
