import { computed, type Ref, watch } from 'vue'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { createEmitter } from '../utils/createEmitter'

interface DraftDirtyEvents {
  dirty: undefined
  clean: undefined
  [key: string]: unknown
}

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
 * This wrapper delegates every call to the existing workbench dirty source and
 * surfaces transitions as explicit events instead of forcing consumers to
 * `watch` the computed flag.
 */
export function useDraftDirty(): UseDraftDirtyReturn {
  const workbench = useDslWorkbench()
  const emitter = createEmitter<DraftDirtyEvents>()

  const isDirty = computed(() => workbench.state.value.isDirty)

  // Watch the single source of truth and emit discrete events on transitions.
  // This keeps `useDraftSave` event-driven while still reacting to any mutation
  // of the underlying state (including direct harness mutations in tests).
  watch(isDirty, (next) => {
    if (next) {
      emitter.emit('dirty')
    } else {
      emitter.emit('clean')
    }
  })

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
    onDirty: (handler) => emitter.on('dirty', handler),
    onClean: (handler) => emitter.on('clean', handler),
  }
}
