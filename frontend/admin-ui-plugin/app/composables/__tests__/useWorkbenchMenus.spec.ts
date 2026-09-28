import type { DropdownMenuItem } from '@cbs/components'
import { describe, expect, it, vi } from 'vitest'
import { computed, nextTick, ref } from 'vue'
import { useWorkbenchMenus } from '../useWorkbenchMenus'

type GuardAction = 'workbench-publish' | 'workbench-approve'

function buildHarness(
  overrides: Partial<{
    hasSelected: boolean
    isSaving: boolean
    allowedActions: GuardAction[]
    historyPanelOpen: boolean
    diagnosticsPanelOpen: boolean
    testsPanelOpen: boolean
  }> = {},
) {
  const hasSelectedConstruct = ref(overrides.hasSelected ?? true)
  const isSaving = ref(overrides.isSaving ?? false)
  const allowedActions = new Set(overrides.allowedActions ?? [])
  const guardAllowed = vi.fn((action: GuardAction) => allowedActions.has(action))
  const historyPanelOpen = ref(overrides.historyPanelOpen ?? false)
  const diagnosticsPanelOpen = ref(overrides.diagnosticsPanelOpen ?? false)
  const testsPanelOpen = ref(overrides.testsPanelOpen ?? false)
  const handlers = {
    shareLink: vi.fn(async () => undefined),
    publish: vi.fn(async () => undefined),
    submitApproval: vi.fn(async () => undefined),
  }

  const harness = useWorkbenchMenus({
    hasSelectedConstruct: computed(() => hasSelectedConstruct.value),
    isSaving: computed(() => isSaving.value),
    guardAllowed,
    historyPanelOpen,
    diagnosticsPanelOpen,
    testsPanelOpen,
    handlers,
  })
  return {
    ...harness,
    hasSelectedConstruct,
    isSaving,
    guardAllowed,
    historyPanelOpen,
    diagnosticsPanelOpen,
    testsPanelOpen,
    handlers,
  }
}

function itemFor(items: DropdownMenuItem[], value: string): DropdownMenuItem {
  const found = items.find((item) => item.value === value)
  if (!found) {
    throw new Error(`missing menu item: ${value}`)
  }
  return found
}

describe('useWorkbenchMenus — actionItems', () => {
  it('orders Share Link, Publish, Submit for approval', () => {
    const { actionItems } = buildHarness()
    expect(actionItems.value.map((item) => item.value)).toEqual([
      'share-link',
      'publish',
      'submit-approval',
    ])
    expect(actionItems.value.map((item) => item.label)).toEqual([
      'Share Link',
      'Publish',
      'Submit for approval',
    ])
  })

  it('marks Publish as the only primary variant', () => {
    const { actionItems } = buildHarness()
    expect(itemFor(actionItems.value, 'publish').variant).toBe('primary')
    expect(itemFor(actionItems.value, 'share-link').variant).toBeUndefined()
    expect(itemFor(actionItems.value, 'submit-approval').variant).toBeUndefined()
  })

  it('enables every action when a construct is selected, saving is idle and the guard allows', () => {
    const { actionItems } = buildHarness({
      allowedActions: ['workbench-publish', 'workbench-approve'],
    })
    expect(actionItems.value.map((item) => item.disabled)).toEqual([false, false, false])
  })

  it('disables every action when no construct is selected', () => {
    const { actionItems } = buildHarness({
      hasSelected: false,
      allowedActions: ['workbench-publish', 'workbench-approve'],
    })
    expect(actionItems.value.map((item) => item.disabled)).toEqual([true, true, true])
  })

  it('disables Publish + Submit for approval while saving, leaving Share Link enabled', () => {
    const { actionItems } = buildHarness({
      isSaving: true,
      allowedActions: ['workbench-publish', 'workbench-approve'],
    })
    expect(itemFor(actionItems.value, 'share-link').disabled).toBe(false)
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(true)
    expect(itemFor(actionItems.value, 'submit-approval').disabled).toBe(true)
  })

  it('honours the manifest guard per action', () => {
    const { actionItems, guardAllowed } = buildHarness({ allowedActions: ['workbench-publish'] })
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(false)
    expect(itemFor(actionItems.value, 'submit-approval').disabled).toBe(true)
    expect(guardAllowed).toHaveBeenCalledWith('workbench-publish')
    expect(guardAllowed).toHaveBeenCalledWith('workbench-approve')
  })

  it('short-circuits the guard when the selection is already empty', () => {
    const { actionItems, guardAllowed } = buildHarness({ hasSelected: false })
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(true)
    expect(guardAllowed).not.toHaveBeenCalled()
  })

  it('short-circuits the guard while a save is in flight', () => {
    const { actionItems, guardAllowed } = buildHarness({ isSaving: true })
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(true)
    expect(guardAllowed).not.toHaveBeenCalled()
  })
})

describe('useWorkbenchMenus — helpersMenuItems', () => {
  it('orders History, Diagnostics, Tests', () => {
    const { helpersMenuItems } = buildHarness()
    expect(helpersMenuItems.value.map((item) => item.value)).toEqual([
      'history',
      'diagnostics',
      'tests',
    ])
  })

  it('labels closed drawers with the bare name', () => {
    const { helpersMenuItems } = buildHarness()
    expect(helpersMenuItems.value.map((item) => item.label)).toEqual([
      'History',
      'Diagnostics',
      'Tests',
    ])
  })

  it('labels open drawers with a Close prefix', () => {
    const { helpersMenuItems } = buildHarness({
      historyPanelOpen: true,
      diagnosticsPanelOpen: true,
      testsPanelOpen: true,
    })
    expect(helpersMenuItems.value.map((item) => item.label)).toEqual([
      'Close History',
      'Close Diagnostics',
      'Close Tests',
    ])
  })

  it('disables History + Tests without a selection and leaves Diagnostics ungated', () => {
    const { helpersMenuItems } = buildHarness({ hasSelected: false })
    expect(itemFor(helpersMenuItems.value, 'history').disabled).toBe(true)
    expect(itemFor(helpersMenuItems.value, 'tests').disabled).toBe(true)
    // Characterization: Diagnostics carries no `disabled` key at all.
    expect('disabled' in itemFor(helpersMenuItems.value, 'diagnostics')).toBe(false)
  })

  it('enables History + Tests with a selection, regardless of the save flag', () => {
    const { helpersMenuItems } = buildHarness({ isSaving: true })
    expect(itemFor(helpersMenuItems.value, 'history').disabled).toBe(false)
    expect(itemFor(helpersMenuItems.value, 'tests').disabled).toBe(false)
  })
})

describe('useWorkbenchMenus — reactivity', () => {
  it('relabels a helper entry when its drawer flag flips', async () => {
    const { helpersMenuItems, historyPanelOpen } = buildHarness()
    expect(itemFor(helpersMenuItems.value, 'history').label).toBe('History')
    historyPanelOpen.value = true
    await nextTick()
    expect(itemFor(helpersMenuItems.value, 'history').label).toBe('Close History')
  })

  it('recomputes action disabled flags when the selection changes', async () => {
    const { actionItems, hasSelectedConstruct } = buildHarness({
      allowedActions: ['workbench-publish', 'workbench-approve'],
    })
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(false)
    hasSelectedConstruct.value = false
    await nextTick()
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(true)
    hasSelectedConstruct.value = true
    await nextTick()
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(false)
  })

  it('recomputes action disabled flags when the save flag changes', async () => {
    const { actionItems, isSaving } = buildHarness({
      allowedActions: ['workbench-publish', 'workbench-approve'],
    })
    isSaving.value = true
    await nextTick()
    expect(itemFor(actionItems.value, 'share-link').disabled).toBe(false)
    expect(itemFor(actionItems.value, 'publish').disabled).toBe(true)
  })
})

describe('useWorkbenchMenus — runAction', () => {
  it('routes each action value to its handler', () => {
    const { runAction, handlers } = buildHarness()
    runAction({ label: 'Share Link', value: 'share-link' })
    runAction({ label: 'Publish', value: 'publish' })
    runAction({ label: 'Submit for approval', value: 'submit-approval' })
    expect(handlers.shareLink).toHaveBeenCalledTimes(1)
    expect(handlers.publish).toHaveBeenCalledTimes(1)
    expect(handlers.submitApproval).toHaveBeenCalledTimes(1)
  })

  it('ignores unknown action values', () => {
    const { runAction, handlers } = buildHarness()
    runAction({ label: 'Nope', value: 'nope' })
    expect(handlers.shareLink).not.toHaveBeenCalled()
    expect(handlers.publish).not.toHaveBeenCalled()
    expect(handlers.submitApproval).not.toHaveBeenCalled()
  })
})

describe('useWorkbenchMenus — runHelpersMenu + toggles', () => {
  it('flips the drawer flags behind each helper entry', () => {
    const { runHelpersMenu, historyPanelOpen, diagnosticsPanelOpen, testsPanelOpen } =
      buildHarness()
    runHelpersMenu({ label: 'History', value: 'history' })
    runHelpersMenu({ label: 'Diagnostics', value: 'diagnostics' })
    runHelpersMenu({ label: 'Tests', value: 'tests' })
    expect(historyPanelOpen.value).toBe(true)
    expect(diagnosticsPanelOpen.value).toBe(true)
    expect(testsPanelOpen.value).toBe(true)

    runHelpersMenu({ label: 'Close History', value: 'history' })
    expect(historyPanelOpen.value).toBe(false)
  })

  it('ignores unknown helper values', () => {
    const { runHelpersMenu, historyPanelOpen, diagnosticsPanelOpen, testsPanelOpen } =
      buildHarness()
    runHelpersMenu({ label: 'Nope', value: 'nope' })
    expect(historyPanelOpen.value).toBe(false)
    expect(diagnosticsPanelOpen.value).toBe(false)
    expect(testsPanelOpen.value).toBe(false)
  })

  it('exposes standalone toggles the page wires to WorkbenchDrawers', () => {
    const {
      toggleHistoryPanel,
      toggleDiagnosticsPanel,
      toggleTestsPanel,
      historyPanelOpen,
      diagnosticsPanelOpen,
      testsPanelOpen,
    } = buildHarness()
    toggleHistoryPanel()
    expect(historyPanelOpen.value).toBe(true)
    toggleHistoryPanel()
    expect(historyPanelOpen.value).toBe(false)

    toggleDiagnosticsPanel()
    expect(diagnosticsPanelOpen.value).toBe(true)
    toggleTestsPanel()
    expect(testsPanelOpen.value).toBe(true)
  })
})
