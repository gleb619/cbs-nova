import { computed, onUnmounted, readonly, ref } from 'vue'
import { vi } from 'vitest'

const g = globalThis as Record<string, unknown>

g.ref = ref
g.computed = computed
g.readonly = readonly
g.onUnmounted = onUnmounted

g.useState = (_key: string, init: () => unknown) => ref(init())

g.$fetch = vi.fn()

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
