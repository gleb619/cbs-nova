import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { type Ref, ref, watch } from 'vue'
import { useDraftDirty } from './useDraftDirty'

export type DraftSaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

export interface UseDraftSaveOptions {
  /** Optional source for the content being saved (e.g. the workbench editor). */
  getContent?: () => string
}

export interface UseDraftSaveReturn {
  status: Ref<DraftSaveStatus>
  lastSavedAt: Ref<Date | null>
  error: Ref<unknown>
  save: () => Promise<void>
}

const SAVED_RECENTLY_MS = 3_000

/**
 * T325 server-save controller for the workbench.
 *
 * Wraps the existing `useDslWorkbench.saveConstruct` (which already owns the
 * payload shape and the server dirty flag) and exposes a reactive save status
 * suitable for the header pill and the Ctrl+S shortcut.
 */
export function useDraftSave(options: UseDraftSaveOptions = {}): UseDraftSaveReturn {
  const workbench = useDslWorkbench()
  const dirty = useDraftDirty()

  const status = ref<DraftSaveStatus>('idle')
  const lastSavedAt = ref<Date | null>(null)
  const error = ref<unknown>(null)
  let savedRecentlyTimer: ReturnType<typeof setTimeout> | null = null

  function clearSavedTimer() {
    if (savedRecentlyTimer) {
      clearTimeout(savedRecentlyTimer)
      savedRecentlyTimer = null
    }
  }

  function enterSavedState() {
    status.value = 'saved'
    savedRecentlyTimer = setTimeout(() => {
      savedRecentlyTimer = null
      status.value = dirty.isDirty.value ? 'dirty' : 'idle'
    }, SAVED_RECENTLY_MS)
  }

  // Keep the public status in sync with the underlying server dirty flag.
  // 'error' and the short 'saved' window are managed by save() itself.
  watch(
    dirty.isDirty,
    (isDirty) => {
      if (isDirty) {
        if (status.value === 'idle') {
          status.value = 'dirty'
        }
        return
      }

      if (status.value === 'dirty') {
        status.value = 'idle'
      }
    },
    { immediate: true },
  )

  async function save() {
    if (!dirty.isDirty.value) return

    clearSavedTimer()
    status.value = 'saving'
    error.value = null

    try {
      const content = options.getContent?.()
      await workbench.saveConstruct(content)
      dirty.markClean()
      lastSavedAt.value = new Date()
      enterSavedState()
    } catch (err) {
      error.value = err
      status.value = 'error'
    }
  }

  return {
    status,
    lastSavedAt,
    error,
    save,
  }
}
