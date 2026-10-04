import { useCrc32 } from '@cbs/components'
import { type ComputedRef, computed, ref } from 'vue'

export type ModelsSaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

export interface UseWorkbenchModelsOptions {
  readModels: (name: string) => Promise<string | null>
  writeModels: (name: string, content: string) => Promise<unknown>
  logError?: (message: string, fields?: Record<string, unknown>) => void
}

export interface UseWorkbenchModelsReturn {
  models: Ref<string>
  dirty: ComputedRef<boolean>
  saveStatus: Ref<ModelsSaveStatus>
  lastSavedAt: Ref<Date | null>
  loadModels: (name: string) => Promise<void>
  updateModels: (value: string) => void
  saveModels: () => Promise<void>
}

/**
 * Mirrors `useWorkbenchDoc` for Java model files (records, classes, enums under
 * `src/models/`). The backend `ByNamePathResolver` already routes the file-API
 * by-name endpoint to a model file when a matching type declaration exists, so
 * this composable reuses the same shape — load on selection, save on demand.
 */
export function useWorkbenchModels(options: UseWorkbenchModelsOptions): UseWorkbenchModelsReturn {
  const { calculateCrc32 } = useCrc32()

  const models = ref('')
  const savedHash = ref<number | null>(null)
  const saveStatus = ref<ModelsSaveStatus>('idle')
  const lastSavedAt = ref<Date | null>(null)
  // False until the model file has been written to disk at least once, so a
  // brand-new model file is created on the first save (even with no edits).
  const persisted = ref(false)
  let loadToken = 0
  let currentName = ''

  const dirty = computed(
    () =>
      currentName !== '' &&
      (persisted.value
        ? savedHash.value !== null && calculateCrc32(models.value) !== savedHash.value
        : true),
  )

  async function loadModels(name: string): Promise<void> {
    const token = ++loadToken
    if (!name) {
      models.value = ''
      savedHash.value = null
      saveStatus.value = 'idle'
      persisted.value = false
      return
    }
    let content: string | null = null
    try {
      content = await options.readModels(name)
    } catch (err) {
      options.logError?.('failed to load models', { error: (err as Error).message })
    }
    if (token !== loadToken) return
    const exists = Boolean(content && content.length > 0)
    persisted.value = exists
    // Brand-new model file: leave the editor blank so the operator writes
    // the model from scratch. We don't want to auto-seed a record stub here —
    // the model may need multiple records, enums, or nested types.
    models.value = exists ? (content as string) : ''
    savedHash.value = calculateCrc32(models.value)
    saveStatus.value = 'idle'
  }

  function updateModels(value: string): void {
    models.value = value
    if (saveStatus.value === 'saved' || saveStatus.value === 'error') {
      saveStatus.value = 'idle'
    }
  }

  async function saveModels(): Promise<void> {
    const name = currentName
    if (!name || !dirty.value) return
    saveStatus.value = 'saving'
    try {
      await options.writeModels(name, models.value)
      savedHash.value = calculateCrc32(models.value)
      persisted.value = true
      lastSavedAt.value = new Date()
      saveStatus.value = 'saved'
    } catch (err) {
      options.logError?.('failed to save models', { error: (err as Error).message })
      saveStatus.value = 'error'
    }
  }

  return {
    models,
    dirty,
    saveStatus,
    lastSavedAt,
    loadModels: async (name: string) => {
      currentName = name
      await loadModels(name)
    },
    updateModels,
    saveModels,
  }
}
