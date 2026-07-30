import { computed, ref } from 'vue'

export interface HelperSearchResult {
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
  fetch: (filters: HelperSearchFilters) => Promise<HelperSearchResult[]> | HelperSearchResult[]
  debounceMs?: number
}

export function useHelperSearch(options: UseHelperSearchOptions) {
  const filters = ref<HelperSearchFilters>({ name: '', type: '', description: '' })
  const results = ref<HelperSearchResult[]>([])
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
        results.value = Array.isArray(data) ? data : []
      })
      .catch((err: unknown) => {
        error.value = (err as Error).message ?? 'Failed to search helpers'
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
    filters.value = { name: '', type: '', description: '' }
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
