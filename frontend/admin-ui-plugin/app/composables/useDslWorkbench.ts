import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import {
  createNamespacedLoaderState,
  createNamespacedLocalStorageState,
  unwrapList,
} from '@cbs/components'
import { useState } from 'nuxt/app'
import { computed, readonly } from 'vue'
import type {
  CompileDiagnostic,
  ConstructStatus,
  ConstructType,
  DslConstruct,
  ValidationError,
} from '~/types'
import { createEmitter } from '../utils/createEmitter'
import { extractApiError } from '../utils/extractApiError'

interface WorkbenchState {
  constructs: DslConstruct[]
  selectedName: string | null
  validationErrors: ValidationError[]
  isDirty: boolean
  isSaving: boolean
  isLoading: boolean
}

// Shared across composable instances within one JS runtime. Dirty transitions
// only originate from user interactions, which never run during SSR rendering.
const dirtyEmitter = createEmitter<{ dirty: undefined; clean: undefined }>()

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

type CompileDiagnosticInput = Omit<CompileDiagnostic, 'line' | 'column'> & {
  line?: number | null
  column?: number | null
}

export function compileDiagnosticsToValidationErrors(
  diags: CompileDiagnosticInput[],
): ValidationError[] {
  return diags.map((d) => ({
    field: d.line != null ? `${basename(d.file ?? '')}:${d.line}` : basename(d.file ?? ''),
    message: d.message ?? '',
    severity: d.severity === 'warning' ? 'warning' : 'error',
    line: d.line ?? null,
    column: d.column ?? null,
  }))
}

export function useDslWorkbench() {
  const constructsLoading = useWorkbenchLoader('constructs')
  const useWorkbenchStorage = createNamespacedLocalStorageState('cbs-nova:dsl-workbench')
  const lastSelectedName = useWorkbenchStorage<string | null>('selected-construct-name', null)

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

  function setDirty(value: boolean): void {
    if (state.value.isDirty === value) return
    state.value.isDirty = value
    dirtyEmitter.emit(value ? 'dirty' : 'clean')
  }

  const selectedConstruct = computed<DslConstruct | null>(() => {
    if (!state.value.selectedName) return null
    return state.value.constructs.find((c) => c.name === state.value.selectedName) ?? null
  })

  async function loadConstructs() {
    constructsLoading.value = true
    try {
      const result = await api.getDefinitions()
      const rawList = unwrapList(result)
      const list = rawList.map((c) => normalizeConstruct(c as { name: string }))
      state.value.constructs = list
      if (list.length && !state.value.selectedName) {
        const restored = lastSelectedName.value
        state.value.selectedName = list.some((c) => c.name === restored) ? restored : list[0].name
      }
      log.info('constructs loaded', { count: list.length, selected: state.value.selectedName })
    } catch (err) {
      log.error('failed to load constructs', { error: extractApiError(err).message })
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

  function selectConstruct(name: string) {
    state.value.selectedName = name
    lastSelectedName.value = name
    state.value.validationErrors = []
    setDirty(false)
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
    lastSelectedName.value = name
    state.value.validationErrors = []
    setDirty(false)
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

  function buildDraftPayload(
    name: string,
    selected: DslConstruct | null,
    status: string,
  ): {
    name: string
    type?: string
    status?: string
    version?: string
    taskQueue?: string
    description?: string
  } {
    return {
      name,
      type: selected?.type,
      status,
      version: selected?.version,
      taskQueue: selected?.taskQueue,
      description: selected?.description,
    }
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
        setDirty(false)
        log.info('source file saved', { name: state.value.selectedName })
        return
      }

      const source = selected?.filePath ? undefined : content
      await api.saveDraft(state.value.selectedName, {
        ...buildDraftPayload(state.value.selectedName, selected, 'Draft'),
        ...(source !== undefined ? { source } : {}),
      })
      if (selected) {
        selected.status = 'Draft'
      }
      setDirty(false)
      log.info('draft saved', { name: state.value.selectedName })
    } catch (err) {
      log.error('failed to save construct', {
        name: state.value.selectedName,
        error: extractApiError(err).message,
      })
      throw err
    } finally {
      state.value.isSaving = false
    }
  }

  /**
   * T401 debounced server autosave. Uses the same payload builder as the
   * manual save plus the construct body as `source`; unlike `saveConstruct`
   * it leaves the server dirty flag untouched. Returns the raw API result so
   * the caller can read the server-echoed `savedAt`.
   */
  async function autosaveDraft(source: string): Promise<unknown> {
    const name = state.value.selectedName
    if (!name) return undefined
    const selected = selectedConstruct.value
    return api.saveDraft(name, {
      ...buildDraftPayload(name, selected, 'Draft'),
      source,
    })
  }

  async function publishConstruct() {
    if (!state.value.selectedName) {
      log.warn('publish called with no selection')
      return
    }
    state.value.isSaving = true
    try {
      const selected = selectedConstruct.value
      const result = await api.publishDraft(
        state.value.selectedName,
        buildDraftPayload(state.value.selectedName, selected, 'Published'),
      )
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
        error: extractApiError(err).message,
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
      const nextName = state.value.constructs.length ? state.value.constructs[0].name : null
      state.value.selectedName = nextName
      lastSelectedName.value = nextName
    }
    log.info('construct deleted', { name })
  }

  function markDirty() {
    setDirty(true)
  }

  function markClean() {
    setDirty(false)
    log.info('draft marked clean', { name: state.value.selectedName })
  }

  async function reloadDefinitions() {
    log.info('reload definitions started')
    try {
      await api.reload()
      await loadConstructs()
      log.info('reload definitions finished')
    } catch (err) {
      const diagnostics = extractApiError(err).diagnostics as CompileDiagnostic[] | undefined
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
    selectConstruct,
    createConstruct,
    saveConstruct,
    autosaveDraft,
    validateConstruct,
    publishConstruct,
    deleteConstruct,
    markDirty,
    markClean,
    onDirty: (handler: () => void) => dirtyEmitter.on('dirty', handler),
    onClean: (handler: () => void) => dirtyEmitter.on('clean', handler),
    reloadDefinitions,
  }
}
