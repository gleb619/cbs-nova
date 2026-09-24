import type { DslConstruct } from '@cbs/components'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, nextTick, ref } from 'vue'
import { useWorkbenchSelection } from '../useWorkbenchSelection'

const fileConstruct: DslConstruct = {
  name: 'LoanDsl',
  type: 'Process',
  status: 'Published',
  filePath: 'dsl/LoanDsl.java',
}
const draftConstruct: DslConstruct = {
  name: 'helperOne',
  type: 'Helper',
  status: 'Draft',
}

function buildHarness(
  overrides: Partial<{
    readDslFile: ReturnType<typeof vi.fn>
  }> = {},
) {
  const selectedConstruct = ref<DslConstruct | null>(null)
  const selectedName = computed(() => selectedConstruct.value?.name ?? null)
  const draftBody = ref('')
  const draftsSelectedName = ref<string | null>(null)
  const readDslFile = overrides.readDslFile ?? vi.fn(async () => 'class LoanDsl {}')
  const markDirty = vi.fn()
  const selectConstruct = vi.fn()
  const syncSelectionEffects = vi.fn()
  const isDirty = ref(false)
  const clearDraft = vi.fn()

  const harness = useWorkbenchSelection({
    selectedConstruct,
    selectedName,
    readDslFile,
    logError: vi.fn(),
    markDirty,
    selectConstruct,
    isDirty,
    syncSelectionEffects,
    draftsSelectedName,
    draftBody,
    clearDraft,
  })
  return {
    ...harness,
    selectedConstruct,
    selectedName,
    draftBody,
    draftsSelectedName,
    readDslFile,
    markDirty,
    selectConstruct,
    syncSelectionEffects,
    isDirty,
  }
}

describe('useWorkbenchSelection — file-backed loading', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('exposes an empty editor when no construct is selected', () => {
    const { editorCode, isFileBacked, fileCode } = buildHarness()
    expect(isFileBacked.value).toBe(false)
    expect(fileCode.value).toBe('')
    expect(editorCode.value).toBe('')
  })

  it('loads source file when a file-backed construct is selected', async () => {
    const { selectedConstruct, loadSourceFile, fileCode, fileCodeLoading, readDslFile } =
      buildHarness()
    selectedConstruct.value = fileConstruct
    const promise = loadSourceFile(selectedConstruct.value)
    expect(fileCodeLoading.value).toBe(true)
    await promise
    expect(readDslFile).toHaveBeenCalledWith('LoanDsl')
    expect(fileCode.value).toBe('class LoanDsl {}')
    expect(fileCodeLoading.value).toBe(false)
  })

  it('skips the fetch when the construct has no filePath', async () => {
    const { selectedConstruct, loadSourceFile, readDslFile } = buildHarness()
    selectedConstruct.value = draftConstruct
    await loadSourceFile(selectedConstruct.value)
    expect(readDslFile).not.toHaveBeenCalled()
  })

  it('captures the error and clears the loading flag', async () => {
    const readDslFile = vi.fn(async () => {
      throw new Error('nope')
    })
    const { selectedConstruct, loadSourceFile, fileCodeLoading, fileCode } = buildHarness({
      readDslFile,
    })
    selectedConstruct.value = fileConstruct
    await loadSourceFile(selectedConstruct.value)
    expect(fileCodeLoading.value).toBe(false)
    expect(fileCode.value).toBe('')
  })

  it('uses draftBody as the editor source when the construct is not file-backed', async () => {
    const { selectedConstruct, draftBody, editorCode } = buildHarness()
    selectedConstruct.value = draftConstruct
    draftBody.value = 'draft body'
    await nextTick()
    expect(editorCode.value).toBe('draft body')
  })
})

describe('useWorkbenchSelection — safe select + dirty guard', () => {
  it('prompts the user when dirty and aborts on decline', () => {
    const confirm = vi.fn(() => false)
    vi.spyOn(window, 'confirm').mockImplementation(confirm)
    const { safeSelectConstruct, selectConstruct, isDirty } = buildHarness()
    isDirty.value = true
    safeSelectConstruct('alpha')
    expect(confirm).toHaveBeenCalledWith('Discard unsaved changes to this construct?')
    expect(selectConstruct).not.toHaveBeenCalled()
  })

  it('proceeds when clean', () => {
    const { safeSelectConstruct, selectConstruct, syncSelectionEffects } = buildHarness()
    safeSelectConstruct('beta')
    expect(selectConstruct).toHaveBeenCalledWith('beta')
    expect(syncSelectionEffects).toHaveBeenCalledTimes(1)
  })

  it('proceeds when dirty and user confirms', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { safeSelectConstruct, selectConstruct, isDirty } = buildHarness()
    isDirty.value = true
    safeSelectConstruct('gamma')
    expect(selectConstruct).toHaveBeenCalledWith('gamma')
  })
})

describe('useWorkbenchSelection — code change', () => {
  it('writes to fileCode + marks dirty when file-backed', () => {
    const { selectedConstruct, onCodeChange, fileCode, markDirty } = buildHarness()
    selectedConstruct.value = fileConstruct
    onCodeChange('edited source')
    expect(fileCode.value).toBe('edited source')
    expect(markDirty).toHaveBeenCalled()
  })

  it('writes to draftBody + marks dirty when not file-backed', () => {
    const { selectedConstruct, onCodeChange, draftBody, markDirty } = buildHarness()
    selectedConstruct.value = draftConstruct
    onCodeChange('edited draft')
    expect(draftBody.value).toBe('edited draft')
    expect(markDirty).toHaveBeenCalled()
  })
})

describe('useWorkbenchSelection — query helpers', () => {
  it('mirrors the selection into the drafts ref', () => {
    const { mirrorSelectionToDrafts, selectedConstruct, draftsSelectedName } = buildHarness()
    selectedConstruct.value = { ...draftConstruct, name: 'zeta' }
    mirrorSelectionToDrafts()
    expect(draftsSelectedName.value).toBe('zeta')
  })
})
