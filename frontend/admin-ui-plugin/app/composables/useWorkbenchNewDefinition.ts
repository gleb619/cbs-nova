import type { DslConstruct, DslTemplate } from '@cbs/components'
import { computed, nextTick, ref } from 'vue'

export interface UseWorkbenchNewDefinitionOptions {
  /** Existing construct names (for collision detection). */
  constructs: { value: DslConstruct[] }
  /** Draft names already present (for collision detection). */
  drafts: { value: { name: string }[] }
}

export interface UseWorkbenchNewDefinitionReturn {
  showNewPanel: ReturnType<typeof ref<boolean>>
  newName: ReturnType<typeof ref<string>>
  selectedTemplate: ReturnType<typeof ref<DslTemplate | null>>
  newNameError: ReturnType<typeof computed<string | null>>
  openNewPanel: () => void
  closeNewPanel: () => void
  handleTemplateSelect: (template: DslTemplate) => void
  confirmCreate: (params: {
    name: string
    body: string
    createConstruct: (name: string, type: 'Process' | 'Transaction' | 'Function' | 'Helper') => void
    onBodySet: (body: string) => void
    markDirty: () => void
  }) => Promise<void>
}

const VALID_NAME_RE = /^[A-Za-z0-9._-]+$/

/**
 * Owns the new-definition modal: open state, name validation, template
 * selection, and the create-flow side effects (construct create + body seed
 * + dirty mark).
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. The composable surfaces a `newNameError` computed that drives
 * the inline error message and the create-button disabled state; the actual
 * creation (calling `createConstruct` + seeding the draft body) is deferred
 * to `confirmCreate` because the page owns the workbench wiring.
 */
export function useWorkbenchNewDefinition(
  options: UseWorkbenchNewDefinitionOptions,
): UseWorkbenchNewDefinitionReturn {
  const showNewPanel = ref(false)
  const newName = ref('')
  const selectedTemplate = ref<DslTemplate | null>(null)

  const newNameError = computed(() => {
    const name = newName.value.trim()
    if (!name) return null
    if (!VALID_NAME_RE.test(name)) {
      return 'Name may only contain letters, numbers, dots, dashes and underscores.'
    }
    const existsInConstructs = options.constructs.value.some((c) => c.name === name)
    const existsInDrafts = options.drafts.value.some((d) => d.name === name)
    if (existsInConstructs || existsInDrafts) {
      return `A definition or draft named "${name}" already exists.`
    }
    return null
  })

  function openNewPanel() {
    showNewPanel.value = true
    newName.value = ''
    selectedTemplate.value = null
  }

  function closeNewPanel() {
    showNewPanel.value = false
    newName.value = ''
    selectedTemplate.value = null
  }

  function handleTemplateSelect(template: DslTemplate) {
    selectedTemplate.value = template
  }

  async function confirmCreate(params: {
    name: string
    body: string
    createConstruct: (name: string, type: 'Process' | 'Transaction' | 'Function' | 'Helper') => void
    onBodySet: (body: string) => void
    markDirty: () => void
  }) {
    const name = newName.value.trim()
    if (!name || newNameError.value || !selectedTemplate.value) return
    const parsed = JSON.parse(selectedTemplate.value.body) as { type?: string }
    const type =
      (parsed.type as 'Process' | 'Transaction' | 'Function' | 'Helper' | undefined) ?? 'Process'
    params.createConstruct(name, type)
    void params.body // (kept for symmetry; the actual seed is selectedTemplate.value.body)
    await nextTick()
    params.onBodySet(selectedTemplate.value.body)
    params.markDirty()
    closeNewPanel()
  }

  return {
    showNewPanel,
    newName,
    selectedTemplate,
    newNameError,
    openNewPanel,
    closeNewPanel,
    handleTemplateSelect,
    confirmCreate,
  }
}
