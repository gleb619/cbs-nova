import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { createNamespacedLoaderState } from '@cbs/components'
import { useState } from 'nuxt/app'
import { computed, readonly } from 'vue'
import type { ConstructStatus, ConstructType, DslConstruct, ValidationError } from '~/types'

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
    status: (raw.status as ConstructStatus) ?? 'Draft',
    version: raw.version,
    taskQueue: raw.taskQueue,
  }
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

  function selectConstruct(name: string) {
    state.value.selectedName = name
    state.value.validationErrors = []
    state.value.isDirty = false
    log.info('construct selected', { name })
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

  async function saveConstruct() {
    if (!state.value.selectedName) {
      log.warn('save called with no selection')
      return
    }
    state.value.isSaving = true
    try {
      const selected = selectedConstruct.value
      await api.saveDraft(state.value.selectedName, {
        name: state.value.selectedName,
        type: selected?.type,
        status: 'Draft',
        version: selected?.version,
        taskQueue: selected?.taskQueue,
      })
      state.value.isDirty = false
      log.info('draft saved', { name: state.value.selectedName })
    } catch (err) {
      log.error('failed to save draft', {
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
      })
      const c = state.value.constructs.find((x) => x.name === state.value.selectedName)
      if (c) c.status = 'Published'
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

  async function reloadDefinitions() {
    log.info('reload definitions started')
    await api.reload()
    await loadConstructs()
    log.info('reload definitions finished')
  }

  return {
    state: readonly(state),
    selectedConstruct,
    loaders: readonly({ constructs: constructsLoading }),
    loadConstructs,
    selectConstruct,
    saveConstruct,
    validateConstruct,
    publishConstruct,
    deleteConstruct,
    markDirty,
    reloadDefinitions,
  }
}
