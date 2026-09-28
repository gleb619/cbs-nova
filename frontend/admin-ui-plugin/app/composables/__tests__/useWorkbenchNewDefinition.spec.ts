import type { DslConstruct } from '@cbs/components'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useWorkbenchNewDefinition } from '../useWorkbenchNewDefinition'
import { DSL_TEMPLATES } from '../../utils/dslTemplates'

const processTemplate = DSL_TEMPLATES[0] ?? {
  id: 'plain-process',
  label: 'Plain process',
  description: 'fallback',
  body: JSON.stringify({ name: 'PlainProcess', type: 'Process' }, null, 2),
}
const transactionTemplate = DSL_TEMPLATES[1] ?? {
  id: 'saga',
  label: 'Saga transaction',
  description: 'fallback',
  body: JSON.stringify({ name: 'SagaOrder', type: 'Transaction' }, null, 2),
}

function buildHarness(
  overrides: Partial<{
    constructs: DslConstruct[]
    drafts: { name: string }[]
  }> = {},
) {
  const constructs = ref<DslConstruct[]>(overrides.constructs ?? [])
  const drafts = ref<{ name: string }[]>(overrides.drafts ?? [])
  const createConstruct = vi.fn()
  const onBodySet = vi.fn()
  const markDirty = vi.fn()

  const harness = useWorkbenchNewDefinition({
    constructs,
    drafts,
  })

  return {
    ...harness,
    constructs,
    drafts,
    createConstruct,
    onBodySet,
    markDirty,
  }
}

describe('useWorkbenchNewDefinition — panel state', () => {
  it('opens the panel with empty name and no selected template', () => {
    const { showNewPanel, newName, selectedTemplate, openNewPanel } = buildHarness()
    openNewPanel()
    expect(showNewPanel.value).toBe(true)
    expect(newName.value).toBe('')
    expect(selectedTemplate.value).toBeNull()
  })

  it('closes the panel and resets state', () => {
    const { showNewPanel, newName, selectedTemplate, openNewPanel, closeNewPanel } =
      buildHarness()
    openNewPanel()
    newName.value = 'Alpha'
    selectedTemplate.value = processTemplate
    closeNewPanel()
    expect(showNewPanel.value).toBe(false)
    expect(newName.value).toBe('')
    expect(selectedTemplate.value).toBeNull()
  })

  it('stores a selected template', () => {
    const { selectedTemplate, handleTemplateSelect } = buildHarness()
    handleTemplateSelect(processTemplate)
    expect(selectedTemplate.value).toStrictEqual(processTemplate)
  })
})

describe('useWorkbenchNewDefinition — name validation', () => {
  it('has no error while the name is empty', () => {
    const { newName, newNameError } = buildHarness()
    expect(newNameError.value).toBeNull()
    newName.value = ''
    expect(newNameError.value).toBeNull()
  })

  it('rejects names with illegal characters', () => {
    const { newName, newNameError } = buildHarness()
    newName.value = 'bad name!'
    expect(newNameError.value).toBe(
      'Name may only contain letters, numbers, dots, dashes and underscores.',
    )
  })

  it('rejects names that collide with an existing construct', () => {
    const { newName, newNameError } = buildHarness({
      constructs: [{ name: 'Existing', type: 'Process', status: 'Draft' }],
    })
    newName.value = 'Existing'
    expect(newNameError.value).toContain('already exists')
  })

  it('rejects names that collide with an existing draft', () => {
    const { newName, newNameError } = buildHarness({
      drafts: [{ name: 'DraftOne' }],
    })
    newName.value = 'DraftOne'
    expect(newNameError.value).toContain('already exists')
  })

  it('validates against the trimmed name', () => {
    const { newName, newNameError } = buildHarness()
    newName.value = '  bad name!  '
    expect(newNameError.value).toContain('letters, numbers, dots, dashes and underscores')
  })
})

describe('useWorkbenchNewDefinition — creation happy path', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('calls createConstruct with the trimmed name and template-derived type', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, showNewPanel } =
      buildHarness()
    newName.value = 'NewProcess'
    selectedTemplate.value = processTemplate

    await confirmCreate({
      name: newName.value,
      body: selectedTemplate.value.body,
      createConstruct,
      onBodySet: vi.fn(),
      markDirty: vi.fn(),
    })

    expect(createConstruct).toHaveBeenCalledWith('NewProcess', 'Process')
    expect(showNewPanel.value).toBe(false)
  })

  it('derives non-Process types from the template body', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct } = buildHarness()
    newName.value = 'NewTransaction'
    selectedTemplate.value = transactionTemplate

    await confirmCreate({
      name: newName.value,
      body: selectedTemplate.value.body,
      createConstruct,
      onBodySet: vi.fn(),
      markDirty: vi.fn(),
    })

    expect(createConstruct).toHaveBeenCalledWith('NewTransaction', 'Transaction')
  })

  it('seeds the body, marks dirty, and closes the panel after nextTick', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, onBodySet, markDirty } =
      buildHarness()
    newName.value = 'NewProcess'
    selectedTemplate.value = processTemplate

    await confirmCreate({
      name: newName.value,
      body: selectedTemplate.value.body,
      createConstruct,
      onBodySet,
      markDirty,
    })

    expect(onBodySet).toHaveBeenCalledWith(processTemplate.body)
    expect(markDirty).toHaveBeenCalled()
    expect(newName.value).toBe('')
    expect(selectedTemplate.value).toBeNull()
  })

  it('ignores params.name and params.body, using internal state instead', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, onBodySet } = buildHarness()
    newName.value = 'FromState'
    selectedTemplate.value = processTemplate

    await confirmCreate({
      name: 'FromParams',
      body: JSON.stringify({ name: 'Other', type: 'Helper' }),
      createConstruct,
      onBodySet,
      markDirty: vi.fn(),
    })

    expect(createConstruct).toHaveBeenCalledWith('FromState', 'Process')
    expect(onBodySet).toHaveBeenCalledWith(processTemplate.body)
  })
})

describe('useWorkbenchNewDefinition — creation guardrails', () => {
  it('does nothing when the name is empty', async () => {
    const { selectedTemplate, confirmCreate, createConstruct, onBodySet, markDirty } =
      buildHarness()
    selectedTemplate.value = processTemplate

    await confirmCreate({
      name: '',
      body: processTemplate.body,
      createConstruct,
      onBodySet,
      markDirty,
    })

    expect(createConstruct).not.toHaveBeenCalled()
    expect(onBodySet).not.toHaveBeenCalled()
    expect(markDirty).not.toHaveBeenCalled()
  })

  it('does nothing when the name is invalid', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, markDirty } = buildHarness()
    newName.value = 'bad name!'
    selectedTemplate.value = processTemplate

    await confirmCreate({
      name: newName.value,
      body: processTemplate.body,
      createConstruct,
      onBodySet: vi.fn(),
      markDirty,
    })

    expect(createConstruct).not.toHaveBeenCalled()
    expect(markDirty).not.toHaveBeenCalled()
  })

  it('does nothing when no template is selected', async () => {
    const { newName, confirmCreate, createConstruct, markDirty } = buildHarness()
    newName.value = 'Orphan'

    await confirmCreate({
      name: newName.value,
      body: '',
      createConstruct,
      onBodySet: vi.fn(),
      markDirty,
    })

    expect(createConstruct).not.toHaveBeenCalled()
    expect(markDirty).not.toHaveBeenCalled()
  })
})

describe('useWorkbenchNewDefinition — error path', () => {
  it('propagates createConstruct errors and leaves the panel open (reset is missing)', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, onBodySet, markDirty } =
      buildHarness()
    newName.value = 'Boom'
    selectedTemplate.value = processTemplate
    createConstruct.mockImplementation(() => {
      throw new Error('create failed')
    })

    await expect(
      confirmCreate({
        name: newName.value,
        body: processTemplate.body,
        createConstruct,
        onBodySet,
        markDirty,
      }),
    ).rejects.toThrow('create failed')

    expect(onBodySet).not.toHaveBeenCalled()
    expect(markDirty).not.toHaveBeenCalled()
  })

  it('propagates onBodySet errors and leaves the panel open', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, onBodySet, markDirty } =
      buildHarness()
    newName.value = 'BodyBoom'
    selectedTemplate.value = processTemplate
    onBodySet.mockImplementation(() => {
      throw new Error('body failed')
    })

    await expect(
      confirmCreate({
        name: newName.value,
        body: processTemplate.body,
        createConstruct,
        onBodySet,
        markDirty,
      }),
    ).rejects.toThrow('body failed')

    expect(createConstruct).toHaveBeenCalledWith('BodyBoom', 'Process')
    expect(markDirty).not.toHaveBeenCalled()
  })
})

describe('useWorkbenchNewDefinition — busy/loading state', () => {
  it('does not expose a loading flag and resolves after the create callbacks', async () => {
    const { newName, selectedTemplate, confirmCreate, createConstruct, onBodySet, markDirty } =
      buildHarness()
    newName.value = 'Plain'
    selectedTemplate.value = processTemplate

    const promise = confirmCreate({
      name: newName.value,
      body: processTemplate.body,
      createConstruct,
      onBodySet,
      markDirty,
    })

    expect(promise).toBeInstanceOf(Promise)
    await promise
    expect(createConstruct).toHaveBeenCalled()
    expect(onBodySet).toHaveBeenCalled()
    expect(markDirty).toHaveBeenCalled()
  })
})
