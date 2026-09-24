import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useWorkbenchDelete } from '../useWorkbenchDelete'

function buildDelete() {
  const deleteConstruct = vi.fn(async () => undefined)
  const syncSelectionEffects = vi.fn()
  const refreshDrafts = vi.fn(async () => undefined)
  return {
    deleteConstruct,
    syncSelectionEffects,
    refreshDrafts,
    ...useWorkbenchDelete({
      deleteConstruct,
      syncSelectionEffects,
      refreshDrafts,
    }),
  }
}

describe('useWorkbenchDelete', () => {
  it('starts closed with no pending name and no error', () => {
    const { showDeleteModal, pendingDeleteName, deleteError } = buildDelete()
    expect(showDeleteModal.value).toBe(false)
    expect(pendingDeleteName.value).toBeNull()
    expect(deleteError.value).toBeNull()
  })

  it('opens the modal with the requested name and clears prior error', async () => {
    const { requestDelete, pendingDeleteName, showDeleteModal, deleteError } = buildDelete()
    // Force an error so we can verify requestDelete clears it.
    const harness = buildDelete()
    harness.confirmDelete().catch(() => undefined)
    await flushPromises()
    requestDelete('alpha')
    await nextTick()
    expect(pendingDeleteName.value).toBe('alpha')
    expect(showDeleteModal.value).toBe(true)
    // requestDelete must reset any previous error before showing the modal.
    expect(deleteError.value).toBeNull()
  })

  it('runs the configured delete + side effects on confirm', async () => {
    const { requestDelete, confirmDelete, deleteConstruct, syncSelectionEffects, refreshDrafts } =
      buildDelete()
    requestDelete('beta')
    await confirmDelete()
    expect(deleteConstruct).toHaveBeenCalledWith('beta')
    expect(syncSelectionEffects).toHaveBeenCalledTimes(1)
    expect(refreshDrafts).toHaveBeenCalledTimes(1)
  })

  it('captures the error message and stays in the modal on failure', async () => {
    const deleteConstruct = vi.fn(async () => {
      throw new Error('boom')
    })
    const harness = useWorkbenchDelete({
      deleteConstruct,
      syncSelectionEffects: vi.fn(),
      refreshDrafts: vi.fn(async () => undefined),
    })
    harness.requestDelete('gamma')
    await harness.confirmDelete().catch(() => undefined)
    await flushPromises()
    expect(harness.deleteError.value).toBe('boom')
    expect(harness.pendingDeleteName.value).toBe('gamma')
    expect(harness.showDeleteModal.value).toBe(true)
  })

  it('cancel clears the pending name without invoking the delete', () => {
    const { requestDelete, cancelDelete, deleteConstruct, pendingDeleteName } = buildDelete()
    requestDelete('delta')
    cancelDelete()
    expect(pendingDeleteName.value).toBeNull()
    expect(deleteConstruct).not.toHaveBeenCalled()
  })

  it('refuses to cancel while a delete is in flight', async () => {
    let resolveDelete!: () => void
    const deleteConstruct = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveDelete = resolve
        }),
    )
    const harness = useWorkbenchDelete({
      deleteConstruct,
      syncSelectionEffects: vi.fn(),
      refreshDrafts: vi.fn(async () => undefined),
    })
    harness.requestDelete('epsilon')
    const pending = harness.confirmDelete().catch(() => undefined)
    await flushPromises()
    expect(harness.isDeleting.value).toBe(true)
    harness.cancelDelete()
    expect(harness.pendingDeleteName.value).toBe('epsilon')
    resolveDelete()
    await pending
  })
})
