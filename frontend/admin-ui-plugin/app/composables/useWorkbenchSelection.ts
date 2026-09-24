import type { DslConstruct } from '@cbs/components'
import type { ComputedRef, Ref } from 'vue'
import { computed, ref } from 'vue'

export interface UseWorkbenchSelectionOptions {
  /** Selected construct (read-only view) — `useDslWorkbench().selectedConstruct`. */
  selectedConstruct: ComputedRef<DslConstruct | null>
  /** Workbench selection — `useDslWorkbench().state.selectedName` (reactive). */
  selectedName: ComputedRef<string | null>
  /** Reads file-backed source from the backend (e.g. `dslApi.readDslFile`). */
  readDslFile: (name: string) => Promise<string>
  /** Reports failures to the page logger. */
  logError: (message: string, fields?: Record<string, unknown>) => void
  /** Workbench markDirty (so editors can flag the construct dirty on edit). */
  markDirty: () => void
  /** Workbench selectConstruct (writes the selection into the store). */
  selectConstruct: (name: string) => void
  /** Workbench isDirty getter (server-side dirty flag). */
  isDirty: ComputedRef<boolean>
  /** Side effects that follow every workbench selection change. */
  syncSelectionEffects: () => void
  /** Shared drafts selection ref — mirrored when selection changes. */
  draftsSelectedName: Ref<string | null>
  /** Body ref owned by the workbench draft composable. */
  draftBody: Ref<string>
  /** Drops the persisted local draft (clears the restore banner). */
  clearDraft: () => void
}

export interface UseWorkbenchSelectionReturn {
  fileCode: Ref<string>
  fileCodeLoading: Ref<boolean>
  isFileBacked: ComputedRef<boolean>
  editorCode: ComputedRef<string>
  selectedConstructLabel: ComputedRef<string>
  safeSelectConstruct: (name: string) => void
  mirrorSelectionToDrafts: () => void
  loadSourceFile: (construct: DslConstruct | null) => Promise<void>
  onCodeChange: (value: string) => void
}

/**
 * Selection side effects: source-file loading + dirty mirroring + label/title.
 *
 * Decision: extracted from `dsl-workbench.vue`. The composable owns the
 * `fileCode` / `fileCodeLoading` refs that previously sat inline on the page
 * and re-runs `readDslFile` whenever a file-backed construct is selected.
 * `safeSelectConstruct` keeps the unsaved-changes confirm prompt; `consumeObjectNameQuery`
 * stays here because it is selection-adjacent (it strips the deep-link query
 * after the explorer filter has consumed it).
 */
export function useWorkbenchSelection(
  options: UseWorkbenchSelectionOptions,
): UseWorkbenchSelectionReturn {
  const {
    selectedConstruct,
    selectedName,
    readDslFile,
    logError,
    markDirty,
    selectConstruct,
    isDirty,
    syncSelectionEffects,
    draftsSelectedName,
    draftBody,
    clearDraft,
  } = options

  const fileCode = ref('')
  const fileCodeLoading = ref(false)
  const isFileBacked = computed(() => !!selectedConstruct.value?.filePath)
  const editorCode = computed(() => (isFileBacked.value ? fileCode.value : draftBody.value))

  // Header label — show the source file basename for file-backed constructs
  // (e.g. `dsl/BatchProcessingDsl.java` → `BatchProcessingDsl.java`); fall back
  // to the construct name when no source file is associated (helpers/functions).
  const basename = (path: string) => path.split(/[\\/]/).pop() ?? path
  const selectedConstructLabel = computed(() => {
    const construct = selectedConstruct.value
    if (!construct) return ''
    return basename(construct.filePath ?? construct.name)
  })

  function safeSelectConstruct(name: string) {
    if (isDirty.value && !window.confirm('Discard unsaved changes to this construct?')) {
      return
    }
    selectConstruct(name)
    syncSelectionEffects()
  }

  // Side effects that follow every workbench selection change: mirroring the
  // selection into the shared drafts store (so the navbar widget can highlight
  // the active draft) and loading the source file for file-backed constructs.
  function mirrorSelectionToDrafts() {
    draftsSelectedName.value = selectedName.value ?? null
  }

  async function loadSourceFile(construct: DslConstruct | null) {
    if (!construct?.filePath) {
      fileCode.value = ''
      return
    }
    clearDraft()
    fileCode.value = ''
    fileCodeLoading.value = true
    try {
      const content = await readDslFile(construct.name)
      fileCode.value = content
      logError('source file loaded', { name: construct.name, path: construct.filePath })
    } catch (err) {
      logError('failed to load source file', {
        name: construct.name,
        error: (err as Error).message,
      })
    } finally {
      fileCodeLoading.value = false
    }
  }

  function onCodeChange(value: string) {
    if (isFileBacked.value) {
      fileCode.value = value
    } else {
      draftBody.value = value
    }
    if (selectedConstruct.value) markDirty()
  }

  return {
    fileCode,
    fileCodeLoading,
    isFileBacked,
    editorCode,
    selectedConstructLabel,
    safeSelectConstruct,
    mirrorSelectionToDrafts,
    loadSourceFile,
    onCodeChange,
  }
}
