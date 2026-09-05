import { ref } from 'vue'
import type { HelperCatalogEntry } from '../types/dsl'
import { useLogger } from './useLogger'

export interface UseHelperCompletionOptions {
  fetch: () => Promise<HelperCatalogEntry[]>
}

export function useHelperCompletion(options: UseHelperCompletionOptions) {
  const log = useLogger('helper-completion')
  const cache = ref<HelperCatalogEntry[] | null>(null)
  const pending = ref<Promise<HelperCatalogEntry[]> | null>(null)

  // Catalog data is helper-name-level only (name + description + io types +
  // side-effect flag). There is no per-method signature or per-argument
  // metadata, so completions are plain-text inserts. We cache for the
  // composable instance's lifetime — no ETag-based invalidation, the
  // workbench refetches the catalog on demand if the user reopens it.
  async function getCatalog(): Promise<HelperCatalogEntry[]> {
    if (cache.value) return cache.value
    if (pending.value) return pending.value

    const promise = (async () => {
      try {
        const result = await options.fetch()
        const list = Array.isArray(result) ? result : []
        cache.value = list
        return list
      } catch (err) {
        log.warn('helper catalog fetch failed; offering no completions', {
          error: (err as Error).message,
        })
        return []
      } finally {
        pending.value = null
      }
    })()

    pending.value = promise
    return promise
  }

  return { getCatalog }
}
