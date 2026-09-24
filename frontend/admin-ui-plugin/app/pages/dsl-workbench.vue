<script setup lang="ts">
import { useApprovals } from '@cbs/admin-ui-plugin/composables/useApprovals'
import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDraftDirty } from '@cbs/admin-ui-plugin/composables/useDraftDirty'
import { useDraftSave } from '@cbs/admin-ui-plugin/composables/useDraftSave'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { useManifestGuard } from '@cbs/admin-ui-plugin/composables/useManifestGuard'
import { useWorkbenchBeforeUnload } from '@cbs/admin-ui-plugin/composables/useWorkbenchBeforeUnload'
import { useWorkbenchDeepLink } from '@cbs/admin-ui-plugin/composables/useWorkbenchDeepLink'
import { useWorkbenchDelete } from '@cbs/admin-ui-plugin/composables/useWorkbenchDelete'
import { useWorkbenchDraft } from '@cbs/admin-ui-plugin/composables/useWorkbenchDraft'
import { useWorkbenchMenus } from '@cbs/admin-ui-plugin/composables/useWorkbenchMenus'
import { useWorkbenchNewDefinition } from '@cbs/admin-ui-plugin/composables/useWorkbenchNewDefinition'
import { useWorkbenchObjectSearch } from '@cbs/admin-ui-plugin/composables/useWorkbenchObjectSearch'
import { useWorkbenchPanels } from '@cbs/admin-ui-plugin/composables/useWorkbenchPanels'
import { useWorkbenchSavedDrafts } from '@cbs/admin-ui-plugin/composables/useWorkbenchSavedDrafts'
import { useWorkbenchSelection } from '@cbs/admin-ui-plugin/composables/useWorkbenchSelection'
import { useWorkbenchShareLink } from '@cbs/admin-ui-plugin/composables/useWorkbenchShareLink'
import { useWorkbenchShortcuts } from '@cbs/admin-ui-plugin/composables/useWorkbenchShortcuts'
import { useWorkbenchStructure } from '@cbs/admin-ui-plugin/composables/useWorkbenchStructure'
import type {
  DslConstruct,
  EditorMarker,
  ObjectSearchResult,
  ValidationError,
} from '@cbs/components'
import {
  DslBodyEditor,
  DslConstructExplorer,
  DslDeleteDraftConfirmationModal,
  DslMetadataPanel,
  DslObjectsSearchPanel,
  DslPlainConstructList,
  useToast,
} from '@cbs/components'
import { useRoute } from 'nuxt/app'
import { computed, onMounted, ref, watch } from 'vue'
import type { RunnerOutput } from '~/types'
import WorkbenchDrawers from '../components/workbench/WorkbenchDrawers.vue'
import WorkbenchEditorBanners from '../components/workbench/WorkbenchEditorBanners.vue'
import WorkbenchHeader from '../components/workbench/WorkbenchHeader.vue'
import WorkbenchNewDefinitionModal from '../components/workbench/WorkbenchNewDefinitionModal.vue'
import { buildHelperSnippet } from '../utils/helperSnippet'

// ---------------------------------------------------------------------------
// Top-level dependencies (workbench store, DSL API, approvals, guard, logger).
// ---------------------------------------------------------------------------

const workbench = useDslWorkbench()
const dslApi = useDslApi()
const log = useClientLogger('dsl-workbench')
const toast = useToast()
const route = useRoute()
const draftDirty = useDraftDirty()
const { allowed: guardAllowed } = useManifestGuard()
const { items: approvalItems, load: loadApprovals, submit: submitApproval } = useApprovals()

const {
  state,
  selectedConstruct,
  loaders,
  loadConstructs,
  selectConstruct,
  createConstruct,
  validateConstruct,
  publishConstruct,
  deleteConstruct,
  reloadDefinitions,
  markDirty,
} = workbench

const displayConstructs = computed(() => state.value.constructs as DslConstruct[])
const displayValidationErrors = computed(() => state.value.validationErrors as ValidationError[])

// T568 — true when the selected construct has a PENDING change request.
const selectedPendingApproval = computed(() => {
  const name = state.value.selectedName
  if (!name) return false
  return approvalItems.value.some((r) => r.definitionName === name && r.status === 'PENDING')
})

const editorMarkers = computed<EditorMarker[]>(() =>
  state.value.validationErrors.map((err) => ({
    line: err.line ?? null,
    column: err.column ?? null,
    message: err.message,
    severity: err.severity,
  })),
)

// ---------------------------------------------------------------------------
// Persisted UI state (explorer + drawers). All seven flags live in
// `useWorkbenchPanels` so the page owns them and the drawers component can
// stay declarative (props + v-model).
// ---------------------------------------------------------------------------

const {
  explorerOpen,
  explorerCollapsed,
  objectsSearchOpen,
  helperCatalogOpen,
  historyPanelOpen,
  diagnosticsPanelOpen,
  testsPanelOpen,
} = useWorkbenchPanels()

// Two-way bound to `<DslConstructExplorer>` so the `?objectName=<name>` deep
// link can pre-fill the filter alongside selecting the matched construct.
const explorerFilter = ref('')

function toggleExplorer() {
  explorerOpen.value = !explorerOpen.value
}

// ---------------------------------------------------------------------------
// Saved-drafts store (navbar widget picks land here). The selection is
// created later in this script, so the savedDrafts `selectConstruct` callback
// resolves through a lazy ref that points at `safeSelectConstruct` once the
// selection composable has returned.
// ---------------------------------------------------------------------------

let safeSelectConstructRef: ((name: string) => void) | undefined
const savedDrafts = useWorkbenchSavedDrafts({
  listDrafts: () => dslApi.listDrafts(),
  logError: (message, fields) => log.error(message, fields),
  selectConstruct: (name) =>
    (safeSelectConstructRef ?? ((fallback: string) => selectConstruct(fallback)))(name),
})
const { drafts, refresh: refreshDrafts, selectedName: draftsSelectedName } = savedDrafts

// ---------------------------------------------------------------------------
// Editor + draft body + autosave (server-side autosave via workbench).
// ---------------------------------------------------------------------------

const workbenchDraft = useWorkbenchDraft(state.value.selectedName ?? '', {
  server: {
    save: async (body: string) => {
      const result = (await workbench.autosaveDraft(body)) as { savedAt?: number } | undefined
      return result?.savedAt
    },
    load: async (name: string) => {
      const result = (await dslApi.readDraft(name)) as { source?: string; savedAt?: number } | null
      if (typeof result?.source !== 'string' || typeof result.savedAt !== 'number') return null
      return { body: result.source, savedAt: result.savedAt }
    },
  },
})
const {
  body: draftBody,
  clearDraft,
  lastSavedAt: draftSavedAt,
  restoredFromDraft,
  autosaveOffline,
} = workbenchDraft

// ---------------------------------------------------------------------------
// Selection: source-file loading + draft mirroring + safe select.
// ---------------------------------------------------------------------------

const selection = useWorkbenchSelection({
  selectedConstruct,
  selectedName: computed(() => state.value.selectedName),
  readDslFile: (name) => dslApi.readDslFile(name),
  logError: (message, fields) => log.error(message, fields),
  markDirty: () => markDirty(),
  selectConstruct: (name) => selectConstruct(name),
  isDirty: computed(() => state.value.isDirty),
  syncSelectionEffects: () => syncSelectionEffects(),
  draftsSelectedName,
  draftBody,
  clearDraft: () => clearDraft(),
})
const {
  fileCodeLoading,
  isFileBacked,
  editorCode,
  selectedConstructLabel,
  safeSelectConstruct,
  mirrorSelectionToDrafts,
  loadSourceFile,
  onCodeChange,
} = selection

// Now that the selection composable has returned, point the lazy
// savedDrafts callback at the safe path so navbar-widget picks honour the
// dirty-changes confirm prompt.
safeSelectConstructRef = safeSelectConstruct

const draftSave = useDraftSave({ getContent: () => editorCode.value })

// ---------------------------------------------------------------------------
// Structure tab: fetch-on-select with retry.
// ---------------------------------------------------------------------------

const structureApi = useWorkbenchStructure({
  selectedConstruct,
  fetchObjectStructure: (constructType, name) => dslApi.fetchObjectStructure(constructType, name),
})

// ---------------------------------------------------------------------------
// Side effects that follow every selection change: mirror drafts + load
// source file + load structure. Bound to `safeSelectConstruct` and the deep
// link resolver below.
// ---------------------------------------------------------------------------

function syncSelectionEffects() {
  mirrorSelectionToDrafts()
  workbenchDraft.setName(state.value.selectedName ?? '')
  void loadSourceFile(selectedConstruct.value)
  void structureApi.loadStructure(state.value.selectedName ?? '')
}

// ---------------------------------------------------------------------------
// Object search + helper catalog fetchers.
// ---------------------------------------------------------------------------

const objectSearch = useWorkbenchObjectSearch({
  searchObjects: (params) => dslApi.searchObjects(params),
  listHelpers: (params) => dslApi.listHelpers(params),
})

function onHelperSelect(result: ObjectSearchResult) {
  bodyEditorRef.value?.insertAtCursor(buildHelperSnippet(result))
}

function toggleHelperCatalog() {
  helperCatalogOpen.value = !helperCatalogOpen.value
}

function toggleObjectsSearch() {
  objectsSearchOpen.value = !objectsSearchOpen.value
}
watch(objectsSearchOpen, (open) => {
  if (!open) {
    void workbench.loadConstructs()
  }
})

async function handleSaveSearch() {
  await objectSearch.save()
  objectsSearchOpen.value = false
}

async function handleClearSavedSearch() {
  await objectSearch.clearSaved()
  objectsSearchOpen.value = false
}

// Reuses the constructs already loaded into the workbench store (no extra
// fetch layer); loads them on first demand only.
async function fetchConstructs(): Promise<DslConstruct[]> {
  if (!state.value.constructs.length) await loadConstructs()
  return state.value.constructs as DslConstruct[]
}

async function runPreview(
  name: string,
  body: unknown,
  metadata?: Record<string, unknown>,
): Promise<RunnerOutput> {
  return (await dslApi.preview(name, body, metadata)) as RunnerOutput
}

async function runExplain(
  name: string,
  body: unknown,
  metadata?: Record<string, unknown>,
): Promise<RunnerOutput> {
  return (await dslApi.explain(name, body, metadata)) as RunnerOutput
}

// Refresh and Validate moved to the Code tab toolbar (T589). The page owns
// the wiring (reload + sync selection effects + draft refresh; validation
// just delegates to the workbench).
async function onEditorRefresh() {
  await reloadDefinitions()
  syncSelectionEffects()
  refreshDrafts()
}

async function onEditorValidate() {
  await validateConstruct()
}

// ---------------------------------------------------------------------------
// Delete modal.
// ---------------------------------------------------------------------------

const deletion = useWorkbenchDelete({
  deleteConstruct: (name) => deleteConstruct(name),
  syncSelectionEffects: () => syncSelectionEffects(),
  refreshDrafts: () => refreshDrafts(),
})
const {
  pendingDeleteName,
  isDeleting,
  deleteError,
  showDeleteModal,
  requestDelete,
  confirmDelete,
  cancelDelete,
} = deletion

// ---------------------------------------------------------------------------
// Menus (Actions / Misc) + Share Link + Publish + Submit for approval.
// ---------------------------------------------------------------------------

const { shareLink } = useWorkbenchShareLink({
  toast,
  logError: (message, fields) => log.error(message, fields),
})

const menus = useWorkbenchMenus({
  hasSelectedConstruct: computed(() => !!selectedConstruct.value),
  isSaving: computed(() => state.value.isSaving),
  guardAllowed: (action) => guardAllowed(action),
  historyPanelOpen,
  diagnosticsPanelOpen,
  testsPanelOpen,
  handlers: {
    shareLink: () => shareLink(state.value.selectedName),
    publish: () => publishConstruct().then(() => refreshDrafts()),
    submitApproval: () =>
      submitApproval(state.value.selectedName ?? '')
        .then(() => {
          refreshDrafts()
          loadApprovals()
        })
        .catch((err: unknown) => {
          log.error('failed to submit for approval', { error: (err as Error).message })
        }),
  },
})
const { helpersMenuItems, actionItems, runHelpersMenu, runAction } = menus

// ---------------------------------------------------------------------------
// New-definition modal.
// ---------------------------------------------------------------------------

const newDefinition = useWorkbenchNewDefinition({
  constructs: computed(() => state.value.constructs as DslConstruct[]),
  drafts,
})
const {
  showNewPanel,
  newName,
  selectedTemplate,
  newNameError,
  openNewPanel,
  closeNewPanel,
  handleTemplateSelect,
  confirmCreate,
} = newDefinition

async function handleConfirmCreate() {
  await confirmCreate({
    name: newName.value,
    body: selectedTemplate.value?.body ?? '',
    createConstruct: (name, type) => createConstruct(name, type),
    onBodySet: (body) => {
      draftBody.value = body
    },
    markDirty: () => markDirty(),
  })
}

// ---------------------------------------------------------------------------
// Keyboard shortcuts + beforeunload + route leave guard.
// ---------------------------------------------------------------------------

const isAnyModalOpen = computed(() => showDeleteModal.value || showNewPanel.value)

const { newShortcut, actionsShortcut, miscShortcut } = useWorkbenchShortcuts({
  draftDirty,
  draftSave,
  isAnyModalOpen,
  openNewPanel,
})

useWorkbenchBeforeUnload({ isDirty: computed(() => state.value.isDirty) })

// ---------------------------------------------------------------------------
// Body editor ref + history-restore handler.
// ---------------------------------------------------------------------------

const bodyEditorRef = ref<InstanceType<typeof DslBodyEditor> | null>(null)

function onHistoryRestored() {
  reloadDefinitions()
}

function handleEditorSave() {
  draftSave.save().then(() => refreshDrafts())
}

// ---------------------------------------------------------------------------
// Deep-link wiring (mounted hook).
// ---------------------------------------------------------------------------

const deepLink = useWorkbenchDeepLink({
  routeQuery: route.query,
  setExplorerFilter: (value) => {
    explorerFilter.value = value
  },
  safeSelectConstruct: (name) => safeSelectConstruct(name),
  selectConstruct: (name) => selectConstruct(name),
})

onMounted(async () => {
  // Deep-link target: `?objectName=<name>` (e.g. from CLI / search results).
  // Seeds the explorer filter and selects the matching construct once the
  // list has loaded — keeps ConstructExplorer free of URL navigation.
  deepLink.applyObjectName()

  // Deep-link target: `?activeTab=<structure|code|preview|explain|problems>`
  // Emits a window CustomEvent; BodyEditor listens (event-bus pattern).
  deepLink.applyActiveTab()

  await loadConstructs()
  syncSelectionEffects()
  void loadApprovals()

  deepLink.resolveDeepLink()

  refreshDrafts()

  // A draft picked from the navbar widget on another route arrives as a
  // query. `applyDraft()` returns true only when a non-empty name is
  // present — so the trailing `syncSelectionEffects` matches the original
  // truthy-check (empty-string drafts no longer trigger it).
  if (deepLink.applyDraft()) {
    syncSelectionEffects()
  }
})
</script>

<template>
  <div class="flex flex-col h-full bg-surface">
    <WorkbenchHeader
      :selected-construct="selectedConstruct"
      :selected-construct-label="selectedConstructLabel"
      :selected-pending-approval="selectedPendingApproval"
      :new-shortcut="newShortcut"
      :actions-shortcut="actionsShortcut"
      :misc-shortcut="miscShortcut"
      :action-items="actionItems"
      :helpers-menu-items="helpersMenuItems"
      @toggle-explorer="toggleExplorer"
      @new-definition="openNewPanel"
      @select-action="runAction"
      @select-helpers-menu="runHelpersMenu"
    />

    <div class="flex flex-1 overflow-hidden">
      <aside
        v-show="explorerOpen"
        :class="explorerCollapsed ? 'w-12' : 'w-64'"
        class="shrink-0 border-r border-line overflow-hidden"
      >
        <DslConstructExplorer
          v-model:collapsed="explorerCollapsed"
          v-model:filter="explorerFilter"
          :constructs="displayConstructs"
          :selected-name="state.selectedName"
          :loading="loaders.constructs"
          :object-filters-active="objectSearch.hasActiveFilters.value"
          @select="safeSelectConstruct"
          @open-objects="toggleObjectsSearch"
          @open-helpers="toggleHelperCatalog"
        >
          <template #default="{ constructs, selectedName, onSelect }">
            <DslPlainConstructList
              :constructs="constructs"
              :selected-name="selectedName"
              :on-select="onSelect"
              deletable
              @delete="requestDelete"
            />
          </template>
        </DslConstructExplorer>
      </aside>

      <main class="flex-1 flex flex-col overflow-hidden">
        <DslMetadataPanel :construct="selectedConstruct" :loading="fileCodeLoading" />
        <WorkbenchEditorBanners
          :is-file-backed="isFileBacked"
          :restored-from-draft="restoredFromDraft"
          :draft-saved-at="draftSavedAt"
          :autosave-offline="autosaveOffline"
          :delete-error="deleteError"
          @discard-draft="clearDraft"
          @retry-delete="confirmDelete"
        />
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor
            ref="bodyEditorRef"
            :code="editorCode"
            :construct="selectedConstruct"
            :save-status="draftSave.status.value"
            :last-saved-at="draftSave.lastSavedAt.value"
            :helper-catalog-fetch="objectSearch.fetchHelperCatalog"
            :constructs-fetch="fetchConstructs"
            :preview="runPreview"
            :explain="runExplain"
            :refresh="onEditorRefresh"
            :validate="onEditorValidate"
            :busy="state.isSaving"
            :markers="editorMarkers"
            :errors="displayValidationErrors"
            :diagnostics-fetch="dslApi.fetchDiagnostics"
            :diagnostics-definition="selectedConstruct?.name ?? ''"
            :structure="structureApi.structure.value"
            :structure-loading="structureApi.structureLoading.value"
            :structure-error="structureApi.structureError.value"
            @update:code="onCodeChange"
            @save="handleEditorSave"
            @retry-structure="structureApi.retryStructure"
          />
        </div>
      </main>

      <WorkbenchDrawers
        v-model:helper-catalog-open="helperCatalogOpen"
        v-model:history-panel-open="historyPanelOpen"
        v-model:diagnostics-panel-open="diagnosticsPanelOpen"
        v-model:tests-panel-open="testsPanelOpen"
        :selected-name="selectedConstruct?.name ?? ''"
        :load-helpers-page="objectSearch.loadHelpersPage"
        :list-publish-history="dslApi.listPublishHistory"
        :get-history-entry="dslApi.getHistoryEntry"
        :get-history-diff="dslApi.getHistoryDiff"
        :restore-publish-history="dslApi.restorePublishHistory"
        :fetch-diagnostics="dslApi.fetchDiagnostics"
        :fetch-definition-tests="dslApi.fetchDefinitionTests"
        :save-definition-tests="dslApi.saveDefinitionTests"
        :run-definition-tests="dslApi.runDefinitionTests"
        @history-restored="onHistoryRestored"
      />

      <DslObjectsSearchPanel
        v-model:open="objectsSearchOpen"
        v-model:query="objectSearch.filters.value.query"
        v-model:mode="objectSearch.filters.value.mode"
        v-model:type="objectSearch.filters.value.type"
        :results="objectSearch.results.value"
        :is-loading="objectSearch.isLoading.value"
        :error="objectSearch.error.value"
        @search="objectSearch.search"
        @clear="objectSearch.clearFilters"
        @save="handleSaveSearch"
        @clear-saved="handleClearSavedSearch"
        @select="onHelperSelect"
      />
    </div>

    <DslDeleteDraftConfirmationModal
      v-if="showDeleteModal"
      :show="true"
      :draft-name="pendingDeleteName ?? ''"
      :busy="isDeleting"
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />

    <WorkbenchNewDefinitionModal
      :open="showNewPanel"
      :new-name="newName"
      :selected-template="selectedTemplate"
      :new-name-error="newNameError"
      @update:new-name="(value: string) => (newName = value)"
      @select-template="handleTemplateSelect"
      @cancel="closeNewPanel"
      @create="handleConfirmCreate"
    />
  </div>
</template>
