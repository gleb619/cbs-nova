import type { DslConstruct, ValidationError } from '~/types'

interface WorkbenchState {
  constructs: DslConstruct[]
  selectedName: string | null
  validationErrors: ValidationError[]
  isDirty: boolean
  isSaving: boolean
  isLoading: boolean
}

export function useDslWorkbench() {
  const state = useState<WorkbenchState>('dsl-workbench', () => ({
    constructs: [],
    selectedName: null,
    validationErrors: [],
    isDirty: false,
    isSaving: false,
    isLoading: false,
  }))

  const api = useDslApi()

  const selectedConstruct = computed<DslConstruct | null>(() => {
    if (!state.value.selectedName) return null
    return state.value.constructs.find((c) => c.name === state.value.selectedName) ?? null
  })

  async function loadConstructs() {
    state.value.isLoading = true
    try {
      const result = await api.getDefinitions()
      const list = Array.isArray(result)
        ? result
        : ((result as { constructs?: DslConstruct[] }).constructs ?? [])
      state.value.constructs = list
      if (list.length && !state.value.selectedName) {
        state.value.selectedName = list[0].name
      }
    } finally {
      state.value.isLoading = false
    }
  }

  function selectConstruct(name: string) {
    state.value.selectedName = name
    state.value.validationErrors = []
    state.value.isDirty = false
  }

  async function validateConstruct() {
    if (!state.value.selectedName) return
    const result = await api.preview(state.value.selectedName, {})
    const errors = (result as { errors?: ValidationError[] }).errors ?? []
    state.value.validationErrors = errors
    return errors
  }

  async function saveConstruct() {
    if (!state.value.selectedName) return
    state.value.isSaving = true
    try {
      // stub — backend endpoint TBD
      await Promise.resolve({ success: true })
      state.value.isDirty = false
    } finally {
      state.value.isSaving = false
    }
  }

  async function publishConstruct() {
    if (!state.value.selectedName) return
    state.value.isSaving = true
    try {
      // stub — backend endpoint TBD
      await Promise.resolve({ success: true })
      const c = state.value.constructs.find((x) => x.name === state.value.selectedName)
      if (c) c.status = 'Published'
    } finally {
      state.value.isSaving = false
    }
  }

  function markDirty() {
    state.value.isDirty = true
  }

  return {
    state: readonly(state),
    selectedConstruct,
    loadConstructs,
    selectConstruct,
    saveConstruct,
    validateConstruct,
    publishConstruct,
    markDirty,
  }
}
