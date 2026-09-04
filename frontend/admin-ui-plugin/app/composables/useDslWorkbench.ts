import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { createNamespacedLoaderState } from '@cbs/components'
import { useState } from 'nuxt/app'
import { computed, readonly } from 'vue'
import type {
  CompileDiagnostic,
  ConstructStatus,
  ConstructType,
  DslConstruct,
  ValidationError,
} from '~/types'

interface WorkbenchState {
  constructs: DslConstruct[]
  selectedName: string | null
  validationErrors: ValidationError[]
  isDirty: boolean
  isSaving: boolean
  isLoading: boolean
}

const constructTypeMap: Record<string, ConstructType> = {
  process: 'Process',
  transaction: 'Transaction',
  function: 'Function',
  helper: 'Helper',
}

const useWorkbenchLoader = createNamespacedLoaderState('cbs-nova:dsl-workbench')

function normalizeConstruct(raw: Partial<DslConstruct> & { name: string }): DslConstruct {
  const lowerType = (raw.type ?? '').toString().toLowerCase()
  return {
    name: raw.name,
    type: constructTypeMap[lowerType] ?? (raw.type as ConstructType) ?? 'Helper',
    status: (raw.status as ConstructStatus) ?? 'Published',
    version: raw.version,
    taskQueue: raw.taskQueue,
    inputType: raw.inputType,
    outputType: raw.outputType,
    hasCompensation: raw.hasCompensation,
    description: raw.description,
    filePath: raw.filePath,
  }
}

function basename(path: string): string {
  const index = path.lastIndexOf('/')
  return index >= 0 ? path.slice(index + 1) : path
}

function compileDiagnosticsToValidationErrors(diags: CompileDiagnostic[]): ValidationError[] {
  return diags.map((d) => ({
    field: d.line != null ? `${basename(d.file)}:${d.line}` : basename(d.file),
    message: d.message,
    severity: d.severity === 'warning' ? 'warning' : 'error',
  }))
}

export function useDslWorkbench() {
  const constructsLoading = useWorkbenchLoader('constructs')

  const state = useState<WorkbenchState>('dsl-workbench', () => ({
    constructs: [],
    selectedName: null,
    validationErrors: [],
    isDirty: false,
    isSaving: false,
    get isLoading() {
      return constructsLoading.value
    },
  }))

  const api = useDslApi()
  const log = useClientLogger('dsl')

  const selectedConstruct = computed<DslConstruct | null>(() => {
    if (!state.value.selectedName) return null
    return state.value.constructs.find((c) => c.name === state.value.selectedName) ?? null
  })

  async function loadConstructs() {
    constructsLoading.value = true
    try {
      const result = await api.getDefinitions()
      const rawList = Array.isArray(result)
        ? result
        : ((result as { constructs?: DslConstruct[] }).constructs ?? [])
      const list = rawList.map((c) => normalizeConstruct(c as { name: string }))
      state.value.constructs = list
      if (list.length && !state.value.selectedName) {
        state.value.selectedName = list[0].name
      }
      log.info('constructs loaded', { count: list.length, selected: state.value.selectedName })
    } catch (err) {
      log.error('failed to load constructs', { error: (err as Error).message })
      throw err
    } finally {
      constructsLoading.value = false
    }
  }

  function updateConstruct(name: string, patch: Partial<DslConstruct>) {
    const index = state.value.constructs.findIndex((c) => c.name === name)
    if (index < 0) return
    state.value.constructs[index] = { ...state.value.constructs[index], ...patch }
  }

  async function updateDescription(name: string, description: string) {
    if (!name) return
    updateConstruct(name, { description })
    try {
      await api.updateDescription(name, description)
      log.info('description updated', { name })
    } catch (err) {
      log.error('failed to update description', { name, error: (err as Error).message })
    }
  }

  function selectConstruct(name: string) {
    state.value.selectedName = name
    state.value.validationErrors = []
    state.value.isDirty = false
    log.info('construct selected', { name })
  }

  function createConstruct(name: string, type?: ConstructType) {
    const normalizedType = type ?? 'Helper'
    const existing = state.value.constructs.find((c) => c.name === name)
    if (existing) {
      selectConstruct(name)
      return
    }
    const newConstruct: DslConstruct = {
      name,
      type: normalizedType,
      status: 'Draft',
    }
    state.value.constructs = [...state.value.constructs, newConstruct]
    state.value.selectedName = name
    state.value.validationErrors = []
    state.value.isDirty = false
    log.info('construct created', { name, type: normalizedType })
  }

  async function validateConstruct() {
    if (!state.value.selectedName) {
      log.warn('validate called with no selection')
      return
    }
    log.info('validate started', { name: state.value.selectedName })
    const result = await api.preview(state.value.selectedName, {})
    const errors = (result as { errors?: ValidationError[] }).errors ?? []
    state.value.validationErrors = errors
    log.info('validate finished', { name: state.value.selectedName, errors: errors.length })
    return errors
  }

  async function saveConstruct(content?: string) {
    if (!state.value.selectedName) {
      log.warn('save called with no selection')
      return
    }
    state.value.isSaving = true
    try {
      const selected = selectedConstruct.value
      if (selected?.filePath && content !== undefined) {
        await api.writeDslFile(state.value.selectedName, content)
        state.value.isDirty = false
        log.info('source file saved', { name: state.value.selectedName })
        return
      }

      await api.saveDraft(state.value.selectedName, {
        name: state.value.selectedName,
        type: selected?.type,
        status: 'Draft',
        version: selected?.version,
        taskQueue: selected?.taskQueue,
        description: selected?.description,
      })
      if (selected) {
        selected.status = 'Draft'
      }
      state.value.isDirty = false
      log.info('draft saved', { name: state.value.selectedName })
    } catch (err) {
      log.error('failed to save construct', {
        name: state.value.selectedName,
        error: (err as Error).message,
      })
      throw err
    } finally {
      state.value.isSaving = false
    }
  }

  async function publishConstruct() {
    if (!state.value.selectedName) {
      log.warn('publish called with no selection')
      return
    }
    state.value.isSaving = true
    try {
      const selected = selectedConstruct.value
      const result = await api.publishDraft(state.value.selectedName, {
        name: state.value.selectedName,
        type: selected?.type,
        status: 'Published',
        version: selected?.version,
        taskQueue: selected?.taskQueue,
        description: selected?.description,
      })
      const diags = (result as { diagnostics?: CompileDiagnostic[] }).diagnostics
      const reloaded = (result as { reloaded?: boolean }).reloaded === true

      if (!reloaded) {
        if (diags?.length) {
          state.value.validationErrors = compileDiagnosticsToValidationErrors(diags)
        }
        const reloadError = (result as { reloadError?: string }).reloadError
        if (reloadError) {
          log.warn('publish reload failed', { name: state.value.selectedName, reloadError })
        }
        // Draft marker is kept when reload fails, so status stays Draft.
        return
      }

      state.value.validationErrors = []
      await loadConstructs()
      log.info('construct published', { name: state.value.selectedName, result })
    } catch (err) {
      log.error('failed to publish construct', {
        name: state.value.selectedName,
        error: (err as Error).message,
      })
      throw err
    } finally {
      state.value.isSaving = false
    }
  }

  async function deleteConstruct(name: string) {
    log.info('deleting construct', { name })
    await api.deleteDraft(name)
    await loadConstructs()
    if (state.value.selectedName === name) {
      state.value.selectedName = state.value.constructs.length
        ? state.value.constructs[0].name
        : null
    }
    log.info('construct deleted', { name })
  }

  function markDirty() {
    state.value.isDirty = true
  }

  function markClean() {
    state.value.isDirty = false
    log.info('draft marked clean', { name: state.value.selectedName })
  }

  async function reloadDefinitions() {
    log.info('reload definitions started')
    try {
      await api.reload()
      await loadConstructs()
      log.info('reload definitions finished')
    } catch (err) {
      const diagnostics = (err as { data?: { diagnostics?: CompileDiagnostic[] } }).data
        ?.diagnostics
      if (diagnostics) {
        state.value.validationErrors = compileDiagnosticsToValidationErrors(diagnostics)
      }
      throw err
    }
  }

  return {
    state: readonly(state),
    selectedConstruct,
    loaders: readonly({ constructs: constructsLoading }),
    loadConstructs,
    updateConstruct,
    updateDescription,
    selectConstruct,
    createConstruct,
    saveConstruct,
    validateConstruct,
    publishConstruct,
    deleteConstruct,
    markDirty,
    markClean,
    reloadDefinitions,
  }
}
