import { useSavedDrafts } from '@cbs/components'

export interface UseWorkbenchSavedDraftsOptions {
  /** Fetches the full list of drafts (e.g. `dslApi.listDrafts`). */
  listDrafts: () => Promise<unknown>
  /** Reports failures to the page logger. */
  logError: (message: string, fields?: Record<string, unknown>) => void
  /** Selects a construct (delegates to `workbench.selectConstruct`). */
  selectConstruct: (name: string) => void
}

/**
 * Ties `useSavedDrafts` into the page's existing selection flow.
 *
 * Decision: extracted from `dsl-workbench.vue`. The composable is a thin
 * adapter that wraps `useSavedDrafts` so the navbar widget's pick dispatches
 * land in `selectConstruct`. The `onError` / `onSelect` callbacks keep the
 * existing behaviour identical.
 */
export function useWorkbenchSavedDrafts(options: UseWorkbenchSavedDraftsOptions) {
  return useSavedDrafts({
    fetcher: () => options.listDrafts(),
    onError: (message) => options.logError('failed to load drafts', { error: message }),
    // The navbar widget dispatches picks here while this page is mounted.
    onSelect: (name) => options.selectConstruct(name),
  })
}
