import type { DropdownMenuItem } from '@cbs/components'
import { type ComputedRef, computed, type Ref } from 'vue'

export interface WorkbenchMenuHandlers {
  shareLink: () => Promise<unknown> | undefined
  publish: () => Promise<unknown> | undefined
  submitApproval: () => Promise<unknown> | undefined
}

export interface UseWorkbenchMenusOptions {
  /** True when a construct is selected. Disables share-link / publish / submit-approval. */
  hasSelectedConstruct: ComputedRef<boolean>
  /** Server-side saving flag — disables publish + submit-approval while in flight. */
  isSaving: ComputedRef<boolean>
  /** True when the manifest guard allows the action (T551 defense-in-depth). */
  guardAllowed: (action: 'workbench-publish' | 'workbench-approve') => boolean
  /** Drawer-open flags (used to label the Misc entries as "Open X" / "Close X"). */
  historyPanelOpen: Ref<boolean>
  diagnosticsPanelOpen: Ref<boolean>
  testsPanelOpen: Ref<boolean>
  /** Side effects triggered by `runAction` — owned by the page (toast + log + refresh). */
  handlers: WorkbenchMenuHandlers
}

export interface UseWorkbenchMenusReturn {
  helpersMenuItems: ComputedRef<DropdownMenuItem[]>
  actionItems: ComputedRef<DropdownMenuItem[]>
  runHelpersMenu: (item: DropdownMenuItem) => void
  runAction: (item: DropdownMenuItem) => void
  toggleHistoryPanel: () => void
  toggleDiagnosticsPanel: () => void
  toggleTestsPanel: () => void
}

export type HelpersMenuValue = 'history' | 'diagnostics' | 'tests'
export type ActionValue = 'share-link' | 'publish' | 'submit-approval'

/**
 * Build the items + handlers for the page's Actions and Misc dropdowns.
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. `actionItems` and `helpersMenuItems` are computed refs that
 * recompute when the selection / saving / guard / drawer flags change;
 * `runAction` / `runHelpersMenu` route the picks to the corresponding
 * lifecycle (Share Link, Publish, Submit Approval, drawer toggles).
 *
 * The drawer toggles are returned separately so the page can wire them up
 * via `WorkbenchDrawers` without prop-drilling through the header.
 */
export function useWorkbenchMenus(options: UseWorkbenchMenusOptions): UseWorkbenchMenusReturn {
  const {
    hasSelectedConstruct,
    isSaving,
    guardAllowed,
    historyPanelOpen,
    diagnosticsPanelOpen,
    testsPanelOpen,
    handlers,
  } = options

  function toggleHistoryPanel() {
    historyPanelOpen.value = !historyPanelOpen.value
  }

  function toggleDiagnosticsPanel() {
    diagnosticsPanelOpen.value = !diagnosticsPanelOpen.value
  }

  function toggleTestsPanel() {
    testsPanelOpen.value = !testsPanelOpen.value
  }

  const helpersMenuItems = computed<DropdownMenuItem[]>(() => [
    // Objects / Helpers toggles moved to the ConstructExplorer footer.
    {
      label: historyPanelOpen.value ? 'Close History' : 'History',
      value: 'history',
      disabled: !hasSelectedConstruct.value,
    },
    {
      label: diagnosticsPanelOpen.value ? 'Close Diagnostics' : 'Diagnostics',
      value: 'diagnostics',
    },
    {
      label: testsPanelOpen.value ? 'Close Tests' : 'Tests',
      value: 'tests',
      disabled: !hasSelectedConstruct.value,
    },
  ])

  function runHelpersMenu(item: DropdownMenuItem) {
    switch (item.value as HelpersMenuValue) {
      case 'history':
        toggleHistoryPanel()
        break
      case 'diagnostics':
        toggleDiagnosticsPanel()
        break
      case 'tests':
        toggleTestsPanel()
        break
    }
  }

  const actionItems = computed<DropdownMenuItem[]>(() => [
    {
      label: 'Share Link',
      value: 'share-link',
      disabled: !hasSelectedConstruct.value,
    },
    {
      label: 'Publish',
      value: 'publish',
      disabled: !hasSelectedConstruct.value || isSaving.value || !guardAllowed('workbench-publish'),
      variant: 'primary',
    },
    {
      label: 'Submit for approval',
      value: 'submit-approval',
      disabled: !hasSelectedConstruct.value || isSaving.value || !guardAllowed('workbench-approve'),
    },
  ])

  function runAction(item: DropdownMenuItem) {
    switch (item.value as ActionValue) {
      case 'share-link':
        void handlers.shareLink()
        break
      case 'publish':
        void handlers.publish()
        break
      case 'submit-approval':
        void handlers.submitApproval()
        break
    }
  }

  return {
    helpersMenuItems,
    actionItems,
    runHelpersMenu,
    runAction,
    toggleHistoryPanel,
    toggleDiagnosticsPanel,
    toggleTestsPanel,
  }
}
