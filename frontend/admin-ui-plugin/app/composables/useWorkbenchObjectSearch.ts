import type { HelperCatalogEntry, HelperSearchFilters, ObjectSearchResult } from '@cbs/components'
import { createNamespacedLocalStorageState, useHelperSearch } from '@cbs/components'
import { watch } from 'vue'

export interface UseWorkbenchObjectSearchOptions {
  /** Calls the backend DSL object-search endpoint. */
  searchObjects: (params: { query: string; mode: string; type?: string; size: number }) => Promise<{
    items?: ObjectSearchResult[]
  }>
  /** Calls the helpers catalog endpoint for the helper-catalog drawer. */
  listHelpers: (params: {
    search?: string
    mode?: string
    limit?: number
    offset?: number
  }) => Promise<{ items?: HelperCatalogEntry[] }>
}

export interface UseWorkbenchObjectSearchReturn {
  filters: ReturnType<typeof useHelperSearch>['filters']
  results: ReturnType<typeof useHelperSearch>['results']
  isLoading: ReturnType<typeof useHelperSearch>['isLoading']
  error: ReturnType<typeof useHelperSearch>['error']
  hasActiveFilters: ReturnType<typeof useHelperSearch>['hasActiveFilters']
  search: ReturnType<typeof useHelperSearch>['search']
  clearFilters: ReturnType<typeof useHelperSearch>['clearFilters']
  /** Persists the current filters as the saved working set and re-runs the search. */
  save: () => Promise<void>
  /** Clears the persisted working-set filters and re-runs the search. */
  clearSaved: () => Promise<void>
  loadHelpersPage: (params: {
    search: string
    mode: string
    offset: number
    limit: number
  }) => ReturnType<Required<UseWorkbenchObjectSearchOptions>['listHelpers']>
  fetchHelperCatalog: () => Promise<HelperCatalogEntry[]>
}

/**
 * Persisted object-search filters + the helper-catalog fetchers.
 *
 * Decision: extracted from `dsl-workbench.vue`. The composable owns the
 * `useHelperSearch` wiring (which already debounces + caches results) and the
 * persistence of the user's last filters in the workbench localStorage
 * namespace.
 *
 * Object-search filters are persisted so the user's previous query is
 * restored on the next visit. The workbench loads the DSL working set
 * (/api/dsl/working-set) with the same filters, so the saved search also
 * shapes the construct explorer.
 *
 * The drawer-open flags (`objectsSearchOpen`, helper-catalog / history /
 * diagnostics / tests open flags) live in `useWorkbenchPanels`; the page owns
 * those refs so the drawers component can stay declarative (props + v-model).
 */
export function useWorkbenchObjectSearch(
  options: UseWorkbenchObjectSearchOptions,
): UseWorkbenchObjectSearchReturn {
  const useWorkbenchStorage = createNamespacedLocalStorageState('cbs-nova:dsl-workbench')

  const objectSearchFilters = useWorkbenchStorage<HelperSearchFilters>('object-search-filters', {
    query: '',
    mode: 'exact',
    type: '',
  })

  const objectSearch = useHelperSearch({
    fetch: async (filters: HelperSearchFilters) => {
      const page = await options.searchObjects({
        query: filters.query,
        mode: filters.mode,
        type: filters.type || undefined,
        size: 100,
      })
      return (page.items ?? []) as ObjectSearchResult[]
    },
    debounceMs: 250,
    initialFilters: objectSearchFilters.value,
  })

  watch(
    objectSearch.filters,
    (filters) => {
      objectSearchFilters.value = { ...filters }
    },
    { deep: true },
  )

  async function save(): Promise<void> {
    objectSearchFilters.value = { ...objectSearch.filters.value }
    await objectSearch.execute()
  }

  async function clearSaved(): Promise<void> {
    objectSearchFilters.value = { query: '', mode: 'exact', type: '' }
    objectSearch.filters.value = { query: '', mode: 'exact', type: '' }
    await objectSearch.execute()
  }

  async function loadHelpersPage(params: {
    search: string
    mode: string
    offset: number
    limit: number
  }) {
    return options.listHelpers({
      search: params.search,
      mode: params.mode,
      limit: params.limit,
      offset: params.offset,
    })
  }

  async function fetchHelperCatalog(): Promise<HelperCatalogEntry[]> {
    const result = await options.listHelpers({ limit: 500 })
    return result.items ?? []
  }

  return {
    filters: objectSearch.filters,
    results: objectSearch.results,
    isLoading: objectSearch.isLoading,
    error: objectSearch.error,
    hasActiveFilters: objectSearch.hasActiveFilters,
    search: objectSearch.search,
    clearFilters: objectSearch.clearFilters,
    save,
    clearSaved,
    loadHelpersPage,
    fetchHelperCatalog,
  }
}
