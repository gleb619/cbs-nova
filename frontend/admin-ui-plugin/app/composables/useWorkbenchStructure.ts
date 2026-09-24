import type { ObjectStructureDto } from '@cbs/components'
import type { ComputedRef } from 'vue'
import { ref } from 'vue'

export interface UseWorkbenchStructureOptions {
  /** Selected construct — needed to resolve the construct type for the fetch. */
  selectedConstruct: ComputedRef<{ type?: string } | null>
  /** Fetches the introspected structure for the selected construct. */
  fetchObjectStructure: (constructType: string, name: string) => Promise<ObjectStructureDto>
}

/**
 * Introspected whole-object structure for the Structure tab, keyed by the
 * selected construct name. Called from `syncSelectionEffects` (fetch-on-select);
 * the 404 (unknown object) case resolves to `null` and renders a friendly
 * empty state in the tab.
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. Behavior is identical to the inline implementation, including
 * the "skip when already loaded for this name" optimization.
 */
export function useWorkbenchStructure(options: UseWorkbenchStructureOptions) {
  const { selectedConstruct, fetchObjectStructure } = options

  const structure = ref<ObjectStructureDto | null>(null)
  const structureLoading = ref(false)
  const structureError = ref<string | null>(null)
  const structureName = ref<string | null>(null)

  async function loadStructure(name: string, force = false) {
    const constructType = selectedConstruct.value?.type
    if (!name || !constructType) {
      structure.value = null
      structureError.value = null
      structureName.value = null
      return
    }
    if (!force && structureName.value === name) return
    structureName.value = name
    structureLoading.value = true
    structureError.value = null
    try {
      structure.value = await fetchObjectStructure(constructType, name)
    } catch (err) {
      structure.value = null
      structureError.value = (err as Error).message
    } finally {
      structureLoading.value = false
    }
  }

  function retryStructure() {
    void loadStructure(structureName.value ?? '', true)
  }

  return {
    structure,
    structureLoading,
    structureError,
    structureName,
    loadStructure,
    retryStructure,
  }
}
