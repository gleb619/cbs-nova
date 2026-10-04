import { type ComputedRef, computed, onBeforeUnmount, onMounted, type Ref, ref } from 'vue'
import { useCrc32 } from './useCrc32'
import { createNamespacedLocalStorageState } from './useLocalStorageState'

export interface UseEditorTabStateOptions {
  /** Initial content snapshot at mount. Remount the tab (BodyEditor `:key`) to re-seed. */
  initialContent: string
  /** Local-storage namespace — namespaced with `:autosave-mode`. */
  namespace: string
  /** Caller's save handler. Receives the current content. */
  onSave: (value: string) => void
  /** Fires when the local content changes (used to propagate a v-model to the parent). */
  onChange?: (value: string) => void
  /** Read-only? Skips save, autosave, hotkeys. */
  isReadOnly?: () => boolean
  /** Save mid-flight? Disables refresh/validate hotkeys. */
  isBusy?: () => boolean
  /** Refresh callback — bound to Ctrl+Shift+R. */
  onRefresh?: () => void | Promise<void>
  /** Validate callback — bound to Ctrl+Enter. */
  onValidate?: () => void | Promise<void>
  /** Autosave intervals keyed by mode value (e.g. '5s', '30s', '1min'). */
  autosaveModes?: Record<string, number>
  /** Default autosave mode when nothing is persisted. */
  defaultAutosaveMode?: string
  /** Optional override for the baseline hash (parent knows the canonical saved hash). */
  savedHashOverride?: () => number | null
}

export interface UseEditorTabStateReturn {
  /** Mutable local content; bind to the editor's `:model-value`. */
  localContent: Ref<string>
  /** Update local content and notify the parent (v-model wiring). */
  setLocalContent: (value: string) => void
  /** True when local content diverges from the last saved hash. */
  isDirty: ComputedRef<boolean>
  /** Local content hash (crc32). */
  localHash: ComputedRef<number>
  /** Baseline hash (parent-provided when present, otherwise the local saved-hash snapshot). */
  baselineHash: ComputedRef<number>
  /** Internal baseline that updates on each save; falls back when the parent doesn't provide one. */
  savedHashInternal: Ref<number>
  /** Persisted autosave mode (local storage). */
  mode: Ref<string>
  /** Apply a new autosave mode and re-arm the timer. */
  setAutosaveMode: (mode: string) => void
  /** Re-arm the autosave timer based on the current mode. */
  applyAutosaveMode: () => void
  /** Stop the autosave timer. */
  clearAutosaveTimer: () => void
  /** Emit a save (no-op when read-only or busy). Updates the baseline hash. */
  requestSave: () => void
  /** Triggered by the editor's `focusout` event. */
  handleBlur: () => void
  /** Hotkey dispatcher (window-level keydown). */
  handleHotkey: (event: KeyboardEvent) => void
  /** Window-level save listener (`dsl:save` event). */
  handleGlobalSave: () => void
}

const DEFAULT_AUTOSAVE_INTERVALS: Record<string, number> = {
  '5s': 5000,
  '30s': 30000,
  '1min': 60000,
}

/**
 * Shared editor-tab state: dirty/crc32 tracking, autosave timer, platform-aware
 * hotkeys (save / refresh / validate), and the `dsl:save` window event hook.
 *
 * Both `CodeTab` (DSL files) and `ModelsTab` (model files) reuse this composable
 * because the only thing that varies is which file the save handler persists.
 */
export function useEditorTabState(options: UseEditorTabStateOptions): UseEditorTabStateReturn {
  const { calculateCrc32 } = useCrc32()

  const intervals = options.autosaveModes ?? DEFAULT_AUTOSAVE_INTERVALS
  const defaultMode = options.defaultAutosaveMode ?? 'off'

  const useStorage = createNamespacedLocalStorageState(options.namespace)
  const mode = useStorage<string>('autosave-mode', defaultMode)

  const localContent = ref(options.initialContent)
  const savedHashInternal = ref(calculateCrc32(options.initialContent))

  const localHash = computed(() => calculateCrc32(localContent.value))
  const baselineHash = computed(() => {
    const override = options.savedHashOverride?.()
    return typeof override === 'number' ? override : savedHashInternal.value
  })
  const isDirty = computed(() => localHash.value !== baselineHash.value)

  function setLocalContent(value: string) {
    localContent.value = value
    options.onChange?.(value)
  }

  let autosaveTimer: ReturnType<typeof setInterval> | undefined

  function clearAutosaveTimer() {
    if (autosaveTimer !== undefined) {
      clearInterval(autosaveTimer)
      autosaveTimer = undefined
    }
  }

  function requestSave() {
    if (options.isReadOnly?.()) return
    if (options.isBusy?.()) return
    savedHashInternal.value = localHash.value
    options.onSave(localContent.value)
  }

  function applyAutosaveMode() {
    clearAutosaveTimer()
    if (options.isReadOnly?.()) return
    const interval = intervals[mode.value]
    if (interval !== undefined) {
      autosaveTimer = setInterval(() => {
        if (isDirty.value) requestSave()
      }, interval)
    }
  }

  function setAutosaveMode(next: string) {
    mode.value = next
    applyAutosaveMode()
  }

  function handleBlur() {
    if (mode.value === 'blur' && isDirty.value) requestSave()
  }

  function handleGlobalSave() {
    if (options.isReadOnly?.() || options.isBusy?.()) return
    requestSave()
  }

  function runRefresh() {
    if (options.isBusy?.()) return
    if (!options.onRefresh) return
    void options.onRefresh()
  }

  function runValidate() {
    if (options.isBusy?.()) return
    if (!options.onValidate) return
    void options.onValidate()
  }

  const isMac =
    typeof navigator !== 'undefined' &&
    /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')

  function isEditableTarget(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) return false
    const tag = target.tagName
    return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
  }

  function handleHotkey(event: KeyboardEvent) {
    if (options.isReadOnly?.()) return
    const mod = isMac ? event.metaKey : event.ctrlKey
    if (!mod) return
    if (isEditableTarget(event.target)) return

    const key = event.key.toLowerCase()

    if (key === 's' && !event.shiftKey) {
      event.preventDefault()
      requestSave()
      return
    }

    if (key === 'r' && event.shiftKey) {
      event.preventDefault()
      runRefresh()
      return
    }

    if (key === 'enter') {
      event.preventDefault()
      runValidate()
    }
  }

  onMounted(() => {
    window.addEventListener('dsl:save', handleGlobalSave)
    window.addEventListener('keydown', handleHotkey)
    applyAutosaveMode()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('dsl:save', handleGlobalSave)
    window.removeEventListener('keydown', handleHotkey)
    clearAutosaveTimer()
  })

  return {
    localContent,
    setLocalContent,
    isDirty,
    localHash,
    baselineHash,
    savedHashInternal,
    mode,
    setAutosaveMode,
    applyAutosaveMode,
    clearAutosaveTimer,
    requestSave,
    handleBlur,
    handleHotkey,
    handleGlobalSave,
  }
}
