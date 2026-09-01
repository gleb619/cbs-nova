import { computed, type Ref } from 'vue'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'

export interface UseDraftDirtyReturn {
  /** True when the current construct has unsaved edits on the server. */
  isDirty: Ref<boolean>
  /** Mark the current server-side draft as dirty. */
  markDirty: () => void
  /** Mark the current server-side draft as clean. */
  markClean: () => void
}

/**
 * T325 dirty-state facade for the workbench save flow.
 *
 * Decision: the save-status pill reflects server-save dirty state
 * (`useDslWorkbench.state.isDirty`), not the localStorage draft dirty flag
 * (`useWorkbenchDraft.dirty`). The server is the source of truth for persisted
 * drafts; the localStorage layer is a recovery-only safety net (T201/T292).
 * This wrapper delegates every call to the existing workbench dirty source and
 * does not maintain any independent dirty state.
 */
export function useDraftDirty(): UseDraftDirtyReturn {
  const workbench = useDslWorkbench()

  const isDirty = computed(() => workbench.state.value.isDirty)

  function markDirty() {
    workbench.markDirty()
  }

  function markClean() {
    workbench.markClean()
  }

  return {
    isDirty,
    markDirty,
    markClean,
  }
}
