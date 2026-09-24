import { createNamespacedLocalStorageState } from '@cbs/components'
import { useCookie } from 'nuxt/app'

export interface UseWorkbenchPanelsReturn {
  /** Side explorer pane visibility. */
  explorerOpen: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
  /** Side explorer collapsed-rail flag (cookie-backed). */
  explorerCollapsed: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
  /** Object-search drawer open flag. */
  objectsSearchOpen: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
  /** Helpers catalog drawer open flag. */
  helperCatalogOpen: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
  /** Publish-history drawer open flag. */
  historyPanelOpen: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
  /** Diagnostics drawer open flag. */
  diagnosticsPanelOpen: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
  /** Definition-tests drawer open flag. */
  testsPanelOpen: ReturnType<typeof createNamespacedLocalStorageState<boolean>>
}

/**
 * All persisted panel-open flags owned by the workbench page.
 *
 * Decision: extracted from `dsl-workbench.vue`. Each flag is backed by the
 * shared `cbs-nova:dsl-workbench` localStorage namespace so they survive
 * reloads. Centralising them here keeps the page-side wiring uniform:
 *   - `WorkbenchDrawers.vue` receives four `v-model` props instead of reading
 *     the namespace itself.
 *   - The object-search toggle reads/writes the same ref the page does.
 *   - `explorerCollapsed` uses the cookie namespace so SSR can mirror it.
 */
export function useWorkbenchPanels(): UseWorkbenchPanelsReturn {
  const useWorkbenchStorage = createNamespacedLocalStorageState('cbs-nova:dsl-workbench')
  return {
    explorerOpen: useWorkbenchStorage<boolean>('explorer-open', true),
    explorerCollapsed: useWorkbenchStorage<boolean>('explorer-collapsed', false, { useCookie }),
    objectsSearchOpen: useWorkbenchStorage<boolean>('objects-search-open', false),
    helperCatalogOpen: useWorkbenchStorage<boolean>('helper-catalog-open', false),
    historyPanelOpen: useWorkbenchStorage<boolean>('history-panel-open', false),
    diagnosticsPanelOpen: useWorkbenchStorage<boolean>('diagnostics-panel-open', false),
    testsPanelOpen: useWorkbenchStorage<boolean>('tests-panel-open', false),
  }
}
