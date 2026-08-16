import { vi } from 'vitest'
import { computed, onUnmounted, readonly, ref, watch } from 'vue'

const g = globalThis as Record<string, unknown>

g.ref = ref
g.computed = computed
g.readonly = readonly
g.onUnmounted = onUnmounted
g.watch = watch

g.useState = (_key: string, init: () => unknown) => ref(init())

g.$fetch = vi.fn()

// Stub of h3's defineEventHandler. The real one returns an EventHandler
// wrapper object; for unit tests we treat it as identity so route files
// (`export default defineEventHandler(handler)`) expose the inner handler
// directly and tests can invoke it as a plain function.
g.defineEventHandler = <T>(handler: T) => handler

// Stub of h3's getHeader. The real one reads from the H3Event's internal
// node req, but for unit tests we don't need that — the test that needs
// header propagation overrides globalThis.getHeader directly.
g.getHeader = (_event: unknown, _name: string) => undefined

// Stub of Nuxt's useRuntimeConfig. Tests that need to assert specific values
// can override these by re-assigning globalThis.useRuntimeConfig before
// calling the SUT.
const defaultRuntimeConfig = {
  backendBaseUrl: 'http://localhost:8090',
  backendApiKey: '',
  backendTimeoutMs: 10000,
  public: { appName: 'CBS Nova Admin' },
}
g.useRuntimeConfig = vi.fn(() => defaultRuntimeConfig)

// Stub of the plugin's own useBackendConfig. The real one is exported from
// server/utils/config.ts and auto-imported by Nitro; in unit tests we expose
// the same shape as a bare global so httpClient.ts can call it.
g.useBackendConfig = vi.fn(() => ({
  baseUrl: defaultRuntimeConfig.backendBaseUrl,
  apiKey: defaultRuntimeConfig.backendApiKey,
  timeoutMs: defaultRuntimeConfig.backendTimeoutMs,
}))

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
})
