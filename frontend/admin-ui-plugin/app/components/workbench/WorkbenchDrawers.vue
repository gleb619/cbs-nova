<script setup lang="ts">
import type { HelperCatalogEntry } from '@cbs/components'
import {
  CbsDrawer,
  DslDefinitionTestsPanel,
  DslDiagnosticsHistoryPanel,
  DslHelperCatalog,
} from '@cbs/components'
import DslHistoryPanel from '../DslHistoryPanel.vue'

// Four workbench drawers (helpers catalog, history, diagnostics, tests).
//
// Decision: extracted from `dsl-workbench.vue` so the page stays a thin
// orchestrator. Each drawer is rendered for real in tests (CbsDrawer
// applies data-testid from its `test-id` prop), so the existing
// `document.querySelector('[data-testid="history-drawer"]')` assertions
// continue to work without test changes.
//
// The drawer-open flags are owned by the page (`useWorkbenchPanels`) and
// passed in via `v-model` so opening/closing is observed by the page's
// dropdown labels without this component having to touch storage directly.

const helperCatalogOpen = defineModel<boolean>('helperCatalogOpen', { default: false })
const historyPanelOpen = defineModel<boolean>('historyPanelOpen', { default: false })
const diagnosticsPanelOpen = defineModel<boolean>('diagnosticsPanelOpen', { default: false })
const testsPanelOpen = defineModel<boolean>('testsPanelOpen', { default: false })

defineProps<{
  selectedName: string
  loadHelpersPage: (params: {
    search: string
    mode: string
    offset: number
    limit: number
  }) => Promise<{ items?: HelperCatalogEntry[] }>
  listPublishHistory: (name: string) => Promise<unknown>
  getHistoryEntry: (name: string, timestamp: string) => Promise<unknown>
  getHistoryDiff: (name: string, timestamp: string) => Promise<unknown>
  restorePublishHistory: (name: string, timestamp: string) => Promise<unknown>
  fetchDiagnostics: (params: { name: string }) => Promise<unknown>
  fetchDefinitionTests: (name: string) => Promise<unknown>
  saveDefinitionTests: (name: string, payload: unknown) => Promise<unknown>
  runDefinitionTests: (name: string) => Promise<unknown>
}>()

const emit = defineEmits<(event: 'history-restored') => void>()
</script>

<template>
  <CbsDrawer
    v-model:open="helperCatalogOpen"
    title="Helpers Catalog"
    test-id="helper-catalog-drawer"
    close-label="Close helper catalog"
    width-class="w-96"
  >
    <DslHelperCatalog :fetch="loadHelpersPage" />
  </CbsDrawer>

  <CbsDrawer
    v-model:open="historyPanelOpen"
    title="History"
    test-id="history-drawer"
    close-label="Close publish history"
    width-class="w-[28rem]"
  >
    <DslHistoryPanel
      :key="selectedName"
      :name="selectedName"
      :list-history="listPublishHistory"
      :get-entry="getHistoryEntry"
      :get-diff="getHistoryDiff"
      :restore="restorePublishHistory"
      @restored="emit('history-restored')"
    />
  </CbsDrawer>

  <CbsDrawer
    v-model:open="diagnosticsPanelOpen"
    title="Diagnostics"
    test-id="diagnostics-drawer"
    close-label="Close diagnostics history"
    width-class="w-[34rem]"
  >
    <DslDiagnosticsHistoryPanel :fetch-page="fetchDiagnostics" />
  </CbsDrawer>

  <CbsDrawer
    v-model:open="testsPanelOpen"
    title="Test cases"
    test-id="tests-drawer"
    close-label="Close definition test cases"
    width-class="w-[40rem]"
  >
    <DslDefinitionTestsPanel
      :key="selectedName"
      :name="selectedName"
      :fetch-tests="fetchDefinitionTests"
      :save-tests="saveDefinitionTests"
      :run-tests="runDefinitionTests"
    />
  </CbsDrawer>
</template>
