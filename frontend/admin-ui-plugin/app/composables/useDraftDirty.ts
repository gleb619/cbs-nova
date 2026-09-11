import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { computed, type Ref } from 'vue'

export interface UseDraftDirtyReturn {
  /** True when the current construct has unsaved edits on the server. */
  isDirty: Ref<boolean>
  /** Mark the current server-side draft as dirty. */
  markDirty: () => void
  /** Mark the current server-side draft as clean. */
  markClean: () => void
  /** Listen for dirty transitions. */
  onDirty: (handler: () => void) => () => void
  /** Listen for clean transitions. */
  onClean: (handler: () => void) => () => void
}

/**
 * T325 dirty-state facade for the workbench save flow.
 *
 * Decision: the save-status pill reflects server-save dirty state
 * (`useDslWorkbench.state.isDirty`), not the localStorage draft dirty flag
 * (`useWorkbenchDraft.dirty`). The server is the source of truth for persisted
 * drafts; the localStorage layer is a recovery-only safety net (T201/T292).
 * This wrapper delegates every call to the existing workbench dirty source,
 * whose transitions are emitted as explicit events by the workbench itself.
 */
export function useDraftDirty(): UseDraftDirtyReturn {
  const workbench = useDslWorkbench()

  const isDirty = computed(() => workbench.state.value.isDirty)

  return {
    isDirty,
    markDirty: () => workbench.markDirty(),
    markClean: () => workbench.markClean(),
    onDirty: (handler) => workbench.onDirty(handler),
    onClean: (handler) => workbench.onClean(handler),
  }
}
