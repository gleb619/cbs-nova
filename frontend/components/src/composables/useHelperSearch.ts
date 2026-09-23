import { computed, ref } from 'vue'

export interface ObjectSearchResult {
  name: string
  type: string
  description: string
  inputType: string
  outputType: string
}

export interface HelperSearchFilters {
  query: string
  mode: string
}

export interface UseHelperSearchOptions {
  fetch: (filters: HelperSearchFilters) => Promise<ObjectSearchResult[]> | ObjectSearchResult[]
  debounceMs?: number
  initialFilters?: Partial<HelperSearchFilters>
}

const DEFAULT_FILTERS: HelperSearchFilters = { query: '', mode: 'exact' }

function normalizeFilters(input?: Partial<HelperSearchFilters>): HelperSearchFilters {
  return {
    query: input?.query ?? DEFAULT_FILTERS.query,
    mode: input?.mode ?? DEFAULT_FILTERS.mode,
  }
}

export function useHelperSearch(options: UseHelperSearchOptions) {
  const filters = ref<HelperSearchFilters>(normalizeFilters(options.initialFilters))
  const results = ref<ObjectSearchResult[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const hasActiveFilters = computed(
    () => filters.value.query.trim() !== '' || filters.value.mode.trim() !== 'exact',
  )

  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let pendingPromise: Promise<unknown> | null = null

  async function execute() {
    if (pendingPromise) return pendingPromise

    isLoading.value = true
    error.value = null

    const promise = Promise.resolve(options.fetch(filters.value))
      .then((data) => {
        results.value = Array.isArray(data) ? data : []
      })
      .catch((err: unknown) => {
        error.value = (err as Error).message ?? 'Failed to search objects'
        results.value = []
      })
      .finally(() => {
        isLoading.value = false
        pendingPromise = null
      })

    pendingPromise = promise
    return promise
  }

  function search() {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      debounceTimer = null
      void execute()
    }, options.debounceMs ?? 250)
  }

  function clearFilters() {
    filters.value = { ...DEFAULT_FILTERS }
    void execute()
  }

  return {
    filters,
    results,
    isLoading,
    error,
    hasActiveFilters,
    execute,
    search,
    clearFilters,
  }
}
