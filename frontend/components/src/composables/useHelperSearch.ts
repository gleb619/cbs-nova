import { computed, ref } from 'vue'

export interface ObjectSearchResult {
  name: string
  type: string
  description: string
  inputType: string
  outputType: string
}

export interface HelperSearchFilters {
  name: string
  type: string
  description: string
}

export interface UseHelperSearchOptions {
  fetch: (filters: HelperSearchFilters) => Promise<ObjectSearchResult[]> | ObjectSearchResult[]
  debounceMs?: number
  // TBD — seed filters from persisted values so a user's previous search is
  // restored on the next session. This is a preparatory step for the future
  // unified "working set" search endpoint.
  initialFilters?: Partial<HelperSearchFilters>
}

const DEFAULT_FILTERS: HelperSearchFilters = { name: '', type: '', description: '' }

function normalizeFilters(input?: Partial<HelperSearchFilters>): HelperSearchFilters {
  return {
    name: input?.name ?? DEFAULT_FILTERS.name,
    type: input?.type ?? DEFAULT_FILTERS.type,
    description: input?.description ?? DEFAULT_FILTERS.description,
  }
}

export function useHelperSearch(options: UseHelperSearchOptions) {
  const filters = ref<HelperSearchFilters>(normalizeFilters(options.initialFilters))
  const results = ref<ObjectSearchResult[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const hasActiveFilters = computed(
    () =>
      filters.value.name.trim() !== '' ||
      filters.value.type.trim() !== '' ||
      filters.value.description.trim() !== '',
  )

  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let pendingPromise: Promise<unknown> | null = null

  async function execute() {
    if (pendingPromise) return pendingPromise

    isLoading.value = true
    error.value = null

    const promise = Promise.resolve(options.fetch(filters.value))
      .then((data) => {
        // fetch contract guarantees an array (ObjectSearchResult[]); guard only for malformed/sync returns.
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
