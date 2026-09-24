import { computed, ref } from 'vue'

export interface UseWorkbenchDeleteOptions {
  /** Deletes the given construct. The page wires this to `workbench.deleteConstruct`. */
  deleteConstruct: (name: string) => Promise<unknown>
  /** Re-runs the workbench selection side effects (mirror drafts + load source). */
  syncSelectionEffects: () => void
  /** Refreshes the shared drafts list after a successful delete. */
  refreshDrafts: () => Promise<unknown> | undefined
}

export interface UseWorkbenchDeleteReturn {
  pendingDeleteName: ReturnType<typeof ref<string | null>>
  isDeleting: ReturnType<typeof ref<boolean>>
  deleteError: ReturnType<typeof ref<string | null>>
  showDeleteModal: ReturnType<typeof computed<boolean>>
  requestDelete: (name: string) => void
  confirmDelete: () => Promise<void>
  cancelDelete: () => void
}

/**
 * Owns the delete-confirmation modal flow for the DSL workbench.
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. The composable wraps the workbench's `deleteConstruct` with a
 * pending-name ref + busy/error flags and chains the post-delete selection
 * refresh + draft re-list. Behavior is identical to the inline implementation.
 */
export function useWorkbenchDelete(options: UseWorkbenchDeleteOptions): UseWorkbenchDeleteReturn {
  const { deleteConstruct, syncSelectionEffects, refreshDrafts } = options

  const pendingDeleteName = ref<string | null>(null)
  const isDeleting = ref(false)
  const deleteError = ref<string | null>(null)
  const showDeleteModal = computed(() => !!pendingDeleteName.value)

  function requestDelete(name: string) {
    pendingDeleteName.value = name
    deleteError.value = null
  }

  async function confirmDelete() {
    if (!pendingDeleteName.value || isDeleting.value) return
    isDeleting.value = true
    deleteError.value = null
    try {
      await deleteConstruct(pendingDeleteName.value)
      pendingDeleteName.value = null
      syncSelectionEffects()
      await refreshDrafts()
    } catch (err) {
      deleteError.value = (err as Error).message
    } finally {
      isDeleting.value = false
    }
  }

  function cancelDelete() {
    if (isDeleting.value) return
    pendingDeleteName.value = null
  }

  return {
    pendingDeleteName,
    isDeleting,
    deleteError,
    showDeleteModal,
    requestDelete,
    confirmDelete,
    cancelDelete,
  }
}
