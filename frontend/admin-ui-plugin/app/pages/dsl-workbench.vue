<script setup lang="ts">
import { useApprovals } from '@cbs/admin-ui-plugin/composables/useApprovals'
import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDraftDirty } from '@cbs/admin-ui-plugin/composables/useDraftDirty'
import { useDraftSave } from '@cbs/admin-ui-plugin/composables/useDraftSave'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { useManifestGuard } from '@cbs/admin-ui-plugin/composables/useManifestGuard'
import { useWorkbenchDraft } from '@cbs/admin-ui-plugin/composables/useWorkbenchDraft'
import type {
  DslConstruct,
  EditorMarker,
  HelperCatalogEntry,
  HelperSearchFilters,
  HelpersResponse,
  ObjectSearchResult,
  ObjectStructureDto,
  ValidationError,
} from '@cbs/components'
import {
  CbsDrawer,
  createNamespacedLocalStorageState,
  DropdownMenu,
  type DropdownMenuItem,
  DslBodyEditor,
  DslConstructExplorer,
  DslDefinitionTestsPanel,
  DslDeleteDraftConfirmationModal,
  DslDiagnosticsHistoryPanel,
  DslDraftRestoreBanner,
  DslHelperCatalog,
  DslHelperSearchPanel,
  DslMetadataPanel,
  DslPlainConstructList,
  ErrorBanner,
  HotkeyTooltip,
  useHelperSearch,
  useSavedDrafts,
} from '@cbs/components'
import { useToast } from '@cbs/components/composables'
import { useEventListener } from '@vueuse/core'
import { useCookie, useRoute } from 'nuxt/app'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import type { RunnerOutput } from '~/types'
import DslHistoryPanel from '../components/DslHistoryPanel.vue'
import DslTemplateGallery from '../components/DslTemplateGallery.vue'
import type { DslTemplate } from '../utils/dslTemplates'
import { buildHelperSnippet } from '../utils/helperSnippet'

const workbench = useDslWorkbench()
const route = useRoute()
// T568 — change-request approvals for the selected draft (submit + pending badge).
const { items: approvalItems, load: loadApprovals, submit: submitApproval } = useApprovals()
// T551 — defense-in-depth button guard: the backend resolves the verdict server-side
// (PieceGuardFilter stays the real gate); this only disables the Publish action when denied.
const { allowed: guardAllowed } = useManifestGuard()
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

const draftDirty = useDraftDirty()

const useWorkbenchStorage = createNamespacedLocalStorageState('cbs-nova:dsl-workbench')

const explorerOpen = useWorkbenchStorage<boolean>('explorer-open', true)
const explorerCollapsed = useWorkbenchStorage<boolean>('explorer-collapsed', false, {
  useCookie,
})
// Two-way bound to `<DslConstructExplorer>` so the `?objectName=<name>` deep
// link can pre-fill the filter alongside selecting the matched construct.
const explorerFilter = ref('')

// Seeded from `?activeTab=<name>` so deep links land on the requested tab.
// Implemented via `bodyEditorRef.setTab(...)` (event/listen style) rather
// than v-model — the editor never writes back, so URL stays stable on click.
const BODY_EDITOR_TABS = ['structure', 'code', 'preview', 'explain', 'problems'] as const
type BodyEditorTab = (typeof BODY_EDITOR_TABS)[number]
const helperSearchOpen = useWorkbenchStorage<boolean>('helper-search-open', false)
const helperCatalogOpen = useWorkbenchStorage<boolean>('helper-catalog-open', false)
const historyPanelOpen = useWorkbenchStorage<boolean>('history-panel-open', false)
const diagnosticsPanelOpen = useWorkbenchStorage<boolean>('diagnostics-panel-open', false)
const testsPanelOpen = useWorkbenchStorage<boolean>('tests-panel-open', false)

function toggleHistoryPanel() {
  historyPanelOpen.value = !historyPanelOpen.value
}

function toggleDiagnosticsPanel() {
  diagnosticsPanelOpen.value = !diagnosticsPanelOpen.value
}

function toggleTestsPanel() {
  testsPanelOpen.value = !testsPanelOpen.value
}

// Translate `state.value.validationErrors` into Monaco marker records. Scoped to the
// selected construct — no file filter needed today (YAGNI).
const editorMarkers = computed<EditorMarker[]>(() =>
  state.value.validationErrors.map((err) => ({
    line: err.line ?? null,
    column: err.column ?? null,
    message: err.message,
    severity: err.severity,
  })),
)

const bodyEditorRef = ref<InstanceType<typeof DslBodyEditor> | null>(null)

function onHelperSelect(result: ObjectSearchResult) {
  bodyEditorRef.value?.insertAtCursor(buildHelperSnippet(result))
}

function onHistoryRestored() {
  reloadDefinitions()
}

const dslApi = useDslApi()
const log = useClientLogger('dsl-workbench')

const savedDrafts = useSavedDrafts({
  fetcher: () => dslApi.listDrafts(),
  onError: (message) => log.error('failed to load drafts', { error: message }),
  // The navbar widget dispatches picks here while this page is mounted.
  onSelect: (name) => safeSelectConstruct(name),
})
const { drafts, refresh: refreshDrafts, selectedName: draftsSelectedName } = savedDrafts

function safeSelectConstruct(name: string) {
  if (state.value.isDirty && !window.confirm('Discard unsaved changes to this construct?')) {
    return
  }
  selectConstruct(name)
  syncSelectionEffects()
}

// Remove the objectName query param once we have consumed the deep link.
// Keeps reloads from re-overriding the user's persisted selection.
function consumeObjectNameQuery() {
  if (typeof window === 'undefined') return
  const url = new URL(window.location.href)
  if (!url.searchParams.has('objectName')) return
  url.searchParams.delete('objectName')
  window.history.replaceState({}, '', url.toString())
}

// Side effects that follow every workbench selection change: mirroring the
// selection into the shared drafts store (so the navbar widget can highlight
// the active draft) and loading the source file for file-backed constructs.
function mirrorSelectionToDrafts() {
  draftsSelectedName.value = state.value.selectedName ?? null
}

const helperSearch = useHelperSearch({
  fetch: async (filters: HelperSearchFilters) =>
    (await dslApi.searchObjects(filters)) as ObjectSearchResult[],
  debounceMs: 250,
})

const helpersCatalog = ref<HelperCatalogEntry[]>([])
const helpersLoading = ref(false)
const helpersError = ref<string | null>(null)

async function loadHelpers() {
  helpersLoading.value = true
  helpersError.value = null
  try {
    const result = (await dslApi.listHelpers()) as HelpersResponse
    helpersCatalog.value = result.helpers ?? []
  } catch (err) {
    helpersError.value = (err as Error).message
    helpersCatalog.value = []
  } finally {
    helpersLoading.value = false
  }
}

async function fetchHelperCatalog(): Promise<HelperCatalogEntry[]> {
  if (!helpersCatalog.value.length) await loadHelpers()
  return helpersCatalog.value
}

// Reuses the constructs already loaded into the workbench store (no extra
// fetch layer); loads them on first demand only.
async function fetchConstructs(): Promise<DslConstruct[]> {
  if (!state.value.constructs.length) await loadConstructs()
  return state.value.constructs as DslConstruct[]
}

function toggleHelperCatalog() {
  helperCatalogOpen.value = !helperCatalogOpen.value
  if (helperCatalogOpen.value) {
    void loadHelpers()
  }
}

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

// Source-file-backed constructs load their Java source from the backend.
const fileCode = ref('')
const fileCodeLoading = ref(false)
const isFileBacked = computed(() => !!selectedConstruct.value?.filePath)
const editorCode = computed(() => (isFileBacked.value ? fileCode.value : draftBody.value))

// Header label — show the source file basename for file-backed constructs
// (e.g. `dsl/BatchProcessingDsl.java` → `BatchProcessingDsl.java`); fall back
// to the construct name when no source file is associated (helpers/functions).
const basename = (path: string) => path.split(/[\\/]/).pop() ?? path
const selectedConstructLabel = computed(() => {
  const construct = selectedConstruct.value
  if (!construct) return ''
  return basename(construct.filePath ?? construct.name)
})

async function loadSourceFile(construct: typeof selectedConstruct.value) {
  if (!construct?.filePath) {
    fileCode.value = ''
    return
  }
  clearDraft()
  fileCode.value = ''
  fileCodeLoading.value = true
  try {
    const content = await dslApi.readDslFile(construct.name)
    fileCode.value = content
    log.info('source file loaded', { name: construct.name, path: construct.filePath })
  } catch (err) {
    log.error('failed to load source file', {
      name: construct.name,
      error: (err as Error).message,
    })
  } finally {
    fileCodeLoading.value = false
  }
}

function syncSelectionEffects() {
  mirrorSelectionToDrafts()
  workbenchDraft.setName(state.value.selectedName ?? '')
  void loadSourceFile(selectedConstruct.value)
  void loadStructure(state.value.selectedName ?? '')
}

// Introspected whole-object structure for the Structure tab, keyed by the
// selected construct name. Called from syncSelectionEffects (fetch-on-select);
// the 404 (unknown object) case resolves to `null` and renders a friendly
// empty state in the tab.
const structure = ref<ObjectStructureDto | null>(null)
const structureLoading = ref(false)
const structureError = ref<string | null>(null)
const structureName = ref<string | null>(null)

async function loadStructure(name: string, force = false) {
  if (!name) {
    structure.value = null
    structureError.value = null
    structureName.value = null
    return
  }
  if (!force && structureName.value === name) return
  structureName.value = name
  structureLoading.value = true
  structureError.value = null
  try {
    structure.value = await dslApi.fetchObjectStructure(name)
  } catch (err) {
    structure.value = null
    structureError.value = (err as Error).message
  } finally {
    structureLoading.value = false
  }
}

function retryStructure() {
  void loadStructure(structureName.value ?? '', true)
}

function onCodeChange(value: string) {
  if (isFileBacked.value) {
    fileCode.value = value
  } else {
    draftBody.value = value
  }
  if (selectedConstruct.value) markDirty()
}

function handleEditorSave() {
  draftSave.save().then(() => refreshDrafts())
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

function toggleExplorer() {
  explorerOpen.value = !explorerOpen.value
}

function toggleHelperSearch() {
  helperSearchOpen.value = !helperSearchOpen.value
}

const pendingDeleteName = ref<string | null>(null)
const isDeleting = ref(false)
const deleteError = ref<string | null>(null)
const showDeleteModal = computed(() => !!pendingDeleteName.value)

function requestDelete(name: string) {
  pendingDeleteName.value = name
  deleteError.value = null
}

async function confirmDelete() {
  if (!pendingDeleteName.value || isDeleting.value) return
  isDeleting.value = true
  deleteError.value = null
  try {
    await deleteConstruct(pendingDeleteName.value)
    pendingDeleteName.value = null
    syncSelectionEffects()
    await refreshDrafts()
  } catch (err) {
    deleteError.value = (err as Error).message
  } finally {
    isDeleting.value = false
  }
}

function cancelDelete() {
  if (isDeleting.value) return
  pendingDeleteName.value = null
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!state.value.isDirty) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (state.value.isDirty && !window.confirm('You have unsaved changes. Leave anyway?')) {
    return false
  }
  return true
})

const showNewPanel = ref(false)
const newName = ref('')
const selectedTemplate = ref<DslTemplate | null>(null)
const VALID_NAME_RE = /^[A-Za-z0-9._-]+$/

const newNameError = computed(() => {
  const name = newName.value.trim()
  if (!name) return null
  if (!VALID_NAME_RE.test(name)) {
    return 'Name may only contain letters, numbers, dots, dashes and underscores.'
  }
  const existsInConstructs = state.value.constructs.some((c) => c.name === name)
  const existsInDrafts = drafts.value.some((d) => d.name === name)
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

async function confirmCreate() {
  const name = newName.value.trim()
  if (!name || newNameError.value || !selectedTemplate.value) return
  const parsed = JSON.parse(selectedTemplate.value.body) as { type?: string }
  const type =
    (parsed.type as 'Process' | 'Transaction' | 'Function' | 'Helper' | undefined) ?? 'Process'
  createConstruct(name, type)
  syncSelectionEffects()
  await nextTick()
  draftBody.value = selectedTemplate.value.body
  markDirty()
  closeNewPanel()
}

type ActionValue = 'share-link' | 'publish' | 'submit-approval'
type HelpersMenuValue = 'objects' | 'helpers' | 'history' | 'diagnostics' | 'tests'

const helpersMenuItems = computed<DropdownMenuItem[]>(() => [
  { label: helperSearchOpen.value ? 'Close Objects' : 'Objects', value: 'objects' },
  { label: helperCatalogOpen.value ? 'Close Helpers' : 'Helpers', value: 'helpers' },
  {
    label: historyPanelOpen.value ? 'Close History' : 'History',
    value: 'history',
    disabled: !selectedConstruct.value,
  },
  {
    label: diagnosticsPanelOpen.value ? 'Close Diagnostics' : 'Diagnostics',
    value: 'diagnostics',
  },
  {
    label: testsPanelOpen.value ? 'Close Tests' : 'Tests',
    value: 'tests',
    disabled: !selectedConstruct.value,
  },
])

function runHelpersMenu(item: DropdownMenuItem) {
  switch (item.value as HelpersMenuValue) {
    case 'objects':
      toggleHelperSearch()
      break
    case 'helpers':
      toggleHelperCatalog()
      break
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
    disabled: !selectedConstruct.value,
  },
  {
    label: 'Publish',
    value: 'publish',
    disabled:
      !selectedConstruct.value || state.value.isSaving || !guardAllowed('workbench-publish'),
    variant: 'primary',
  },
  {
    label: 'Submit for approval',
    value: 'submit-approval',
    disabled:
      !selectedConstruct.value || state.value.isSaving || !guardAllowed('workbench-approve'),
  },
])

function runAction(item: DropdownMenuItem) {
  switch (item.value as ActionValue) {
    case 'share-link':
      void shareLink()
      break
    case 'publish':
      publishConstruct().then(() => refreshDrafts())
      break
    case 'submit-approval':
      submitApproval(state.value.selectedName ?? '')
        .then(() => {
          refreshDrafts()
          loadApprovals()
        })
        .catch((err: unknown) => {
          log.error('failed to submit for approval', { error: (err as Error).message })
        })
      break
  }
}

// Refresh and Validate moved to the Code tab toolbar (T589). Keeping the
// implementations here so the page owns the wiring (reload + sync selection
// effects + draft refresh; validation just delegates to the workbench).
async function onEditorRefresh() {
  await reloadDefinitions()
  syncSelectionEffects()
  refreshDrafts()
}

async function onEditorValidate() {
  await validateConstruct()
}

// Share Link — builds a deep-link URL for the current selection + open body
// editor tab and copies it to the clipboard. Active tab is read at click time
// from the body editor's localStorage (it owns that state and does not emit).
const toast = useToast()

function readActiveBodyEditorTab(): BodyEditorTab {
  if (typeof window === 'undefined') return 'structure'
  try {
    const raw = window.localStorage.getItem('cbs-nova:body-editor:active-tab')
    if (!raw) return 'structure'
    const parsed = JSON.parse(raw) as unknown
    if (typeof parsed === 'string' && (BODY_EDITOR_TABS as readonly string[]).includes(parsed)) {
      return parsed as BodyEditorTab
    }
  } catch {
    // fall through to default
  }
  return 'structure'
}

async function shareLink() {
  const name = state.value.selectedName
  if (!name) return
  const tab = readActiveBodyEditorTab()
  const url = new URL(window.location.href)
  url.search = ''
  url.searchParams.set('objectName', name)
  url.searchParams.set('activeTab', tab)
  const link = url.toString()
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(link)
    } else {
      throw new Error('Clipboard API unavailable')
    }
    toast.success(`Copied to a clipboard!`)
  } catch (err) {
    log.error('failed to copy share link', { error: (err as Error).message })
    toast.error('Could not copy share link to clipboard.')
  }
}

const isAnyModalOpen = computed(() => showDeleteModal.value || showNewPanel.value)

const draftSave = useDraftSave({ getContent: () => editorCode.value })

function handleSaveShortcut(event: KeyboardEvent) {
  if ((event.key !== 's' && event.key !== 'S') || (!event.metaKey && !event.ctrlKey)) {
    return
  }
  // Let Monaco / other focused editors consume the shortcut first.
  if (event.defaultPrevented) return
  // Only intercept when there are unsaved server-side changes and no modal is open.
  if (!draftDirty.isDirty.value || isAnyModalOpen.value) return
  event.preventDefault()
  void draftSave.save()
}

// Platform-aware modifier label — mirrors CodeTab's pattern so the tooltips
// stay consistent across the page (⌘ on Mac, Ctrl elsewhere).
const isMac =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')
const MOD = computed(() => (isMac ? '⌘' : 'Ctrl'))
const ALT = 'Alt'

const newShortcut = computed(() => `${MOD.value}+${ALT}+N`)
const actionsShortcut = computed(() => `${MOD.value}+${ALT}+A`)
const miscShortcut = computed(() => `${MOD.value}+${ALT}+M`)

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

// Dispatch a synthetic click on the named DropdownMenu trigger. In real DOM
// this opens the menu via the component's own toggle handler; in tests the
// DropdownMenu is stub-replaced so this becomes a no-op (the keydown handler
// is still verified to not throw).
function clickDropdownTrigger(testId: string) {
  if (typeof document === 'undefined') return
  const trigger = document.querySelector<HTMLButtonElement>(
    `[data-testid="${testId}"] [data-testid="dropdown-menu-trigger"]`,
  )
  trigger?.click()
}

function handleHeaderShortcuts(event: KeyboardEvent) {
  // Ctrl/Cmd + Alt + <N|A|M>. Avoids browser-reserved chords (Ctrl+N, Cmd+M, etc.).
  const mod = isMac ? event.metaKey : event.ctrlKey
  if (!mod) return
  if (!event.altKey) return
  if (event.shiftKey) return
  // Reject the mixed case where both modifiers are held — only one platform's chord is meaningful.
  if (event.metaKey && event.ctrlKey) return
  if (event.repeat) return
  // Let focused editors own the key first.
  if (event.defaultPrevented) return
  // Don't intercept typing inside form fields / contenteditable surfaces.
  if (isEditableTarget(event.target)) return
  // No header action while a blocking modal is open.
  if (isAnyModalOpen.value) return

  const key = event.key.toLowerCase()
  if (key === 'n') {
    event.preventDefault()
    openNewPanel()
    return
  }
  if (key === 'a') {
    event.preventDefault()
    clickDropdownTrigger('workbench-hotkey-actions')
    return
  }
  if (key === 'm') {
    event.preventDefault()
    clickDropdownTrigger('workbench-hotkey-misc')
  }
}

useEventListener(window, 'keydown', handleSaveShortcut)
useEventListener(window, 'keydown', handleHeaderShortcuts)

onMounted(async () => {
  // Deep-link target: `?objectName=<name>` (e.g. from CLI / search results).
  // Seeds the explorer filter and selects the matching construct once the
  // list has loaded — keeps ConstructExplorer free of URL navigation.
  const requestedObject = route.query.objectName
  const objectName = Array.isArray(requestedObject) ? requestedObject[0] : requestedObject
  if (objectName) {
    explorerFilter.value = String(objectName)
  }

  // Deep-link target: `?activeTab=<structure|code|preview|explain|problems>`
  // Emits a window CustomEvent; BodyEditor listens (event-bus pattern).
  const requestedTab = route.query.activeTab
  const tabCandidate = Array.isArray(requestedTab) ? requestedTab[0] : requestedTab
  if (
    typeof tabCandidate === 'string' &&
    (BODY_EDITOR_TABS as readonly string[]).includes(tabCandidate) &&
    typeof window !== 'undefined'
  ) {
    window.dispatchEvent(
      new CustomEvent<BodyEditorTab>('cbs:body-editor:set-tab', {
        detail: tabCandidate as BodyEditorTab,
      }),
    )
  }

  await loadConstructs()
  syncSelectionEffects()
  void loadApprovals()

  if (objectName) {
    safeSelectConstruct(String(objectName))
    consumeObjectNameQuery()
  }

  refreshDrafts()
  // A draft picked from the navbar widget on another route arrives as a query.
  const requested = route.query.draft
  const requestedName = Array.isArray(requested) ? requested[0] : requested
  if (requestedName) {
    selectConstruct(String(requestedName))
    syncSelectionEffects()
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <div class="flex flex-col h-full bg-surface">
    <header class="flex items-center px-4 py-2 bg-white border-b border-line">
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="md:hidden p-1.5 rounded hover:bg-surface"
          aria-label="Toggle explorer"
          @click="toggleExplorer"
        >
          ☰
        </button>
        <h1 class="text-lg font-semibold text-ink">DSL Workbench</h1>
        <span
          v-if="selectedConstruct"
          class="text-sm text-ink-muted"
          data-testid="workbench-selected-construct-label"
          :title="selectedConstruct.filePath ?? selectedConstruct.name"
        >
          / {{ selectedConstructLabel }}
        </span>
        <span
          v-if="selectedPendingApproval"
          class="px-2 py-0.5 text-xs font-medium rounded-full border border-warning-300 bg-warning-100 text-warning-800"
          data-testid="workbench-pending-approval"
        >
          Pending approval
        </span>
      </div>
      <div class="ml-auto flex items-center gap-3">
        <HotkeyTooltip :keys="newShortcut" data-testid="workbench-hotkey-new">
          <button
            type="button"
            class="px-3 py-1.5 text-sm rounded border border-line hover:bg-surface"
            data-testid="workbench-new-definition"
            @click="openNewPanel"
          >
            New
          </button>
        </HotkeyTooltip>
        <HotkeyTooltip :keys="actionsShortcut" data-testid="workbench-hotkey-actions">
          <DropdownMenu label="Actions" align="right" :items="actionItems" @select="runAction" />
        </HotkeyTooltip>
        <HotkeyTooltip :keys="miscShortcut" data-testid="workbench-hotkey-misc">
          <DropdownMenu
            label="Misc"
            align="right"
            :items="helpersMenuItems"
            @select="runHelpersMenu"
          />
        </HotkeyTooltip>
      </div>
    </header>

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
          @select="safeSelectConstruct"
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
        <div v-if="restoredFromDraft && !isFileBacked" class="px-3 pt-2">
          <DslDraftRestoreBanner :saved-at="draftSavedAt" @discard="clearDraft" />
        </div>
        <div v-if="autosaveOffline && !isFileBacked" class="px-3 pt-2">
          <div
            role="status"
            class="flex items-center gap-2 px-3 py-2 text-sm rounded border border-amber-200 bg-amber-50 text-amber-800"
            data-testid="dsl-autosave-offline-banner"
          >
            <span aria-hidden="true">⚠</span>
            <span>
              Autosave offline — draft kept in this browser only. It will retry on the next edit or
              manual save.
            </span>
          </div>
        </div>

        <div v-if="deleteError" class="px-3 pt-2" data-testid="dsl-workbench-delete-error">
          <ErrorBanner :message="deleteError" @retry="confirmDelete" />
        </div>
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor
            ref="bodyEditorRef"
            :code="editorCode"
            :construct="selectedConstruct"
            :save-status="draftSave.status.value"
            :last-saved-at="draftSave.lastSavedAt.value"
            :helper-catalog-fetch="fetchHelperCatalog"
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
            :structure="structure"
            :structure-loading="structureLoading"
            :structure-error="structureError"
            @update:code="onCodeChange"
            @save="handleEditorSave"
            @retry-structure="retryStructure"
          />
        </div>
      </main>

      <CbsDrawer
        v-model:open="helperCatalogOpen"
        title="Helpers"
        test-id="helper-catalog-drawer"
        close-label="Close helper catalog"
        width-class="w-96"
      >
        <DslHelperCatalog
          :helpers="helpersCatalog"
          :loading="helpersLoading"
          :error="helpersError"
        />
      </CbsDrawer>

      <CbsDrawer
        v-model:open="historyPanelOpen"
        title="History"
        test-id="history-drawer"
        close-label="Close publish history"
        width-class="w-[28rem]"
      >
        <DslHistoryPanel
          :key="selectedConstruct?.name ?? ''"
          :name="selectedConstruct?.name ?? ''"
          :list-history="dslApi.listPublishHistory"
          :get-entry="dslApi.getHistoryEntry"
          :get-diff="dslApi.getHistoryDiff"
          :restore="dslApi.restorePublishHistory"
          @restored="onHistoryRestored"
        />
      </CbsDrawer>

      <CbsDrawer
        v-model:open="diagnosticsPanelOpen"
        title="Diagnostics"
        test-id="diagnostics-drawer"
        close-label="Close diagnostics history"
        width-class="w-[34rem]"
      >
        <DslDiagnosticsHistoryPanel :fetch-page="dslApi.fetchDiagnostics" />
      </CbsDrawer>

      <CbsDrawer
        v-model:open="testsPanelOpen"
        title="Test cases"
        test-id="tests-drawer"
        close-label="Close definition test cases"
        width-class="w-[40rem]"
      >
        <DslDefinitionTestsPanel
          :key="selectedConstruct?.name ?? ''"
          :name="selectedConstruct?.name ?? ''"
          :fetch-tests="dslApi.fetchDefinitionTests"
          :save-tests="dslApi.saveDefinitionTests"
          :run-tests="dslApi.runDefinitionTests"
        />
      </CbsDrawer>

      <DslHelperSearchPanel
        v-model:open="helperSearchOpen"
        v-model:name="helperSearch.filters.value.name"
        v-model:type="helperSearch.filters.value.type"
        v-model:description="helperSearch.filters.value.description"
        :results="helperSearch.results.value"
        :is-loading="helperSearch.isLoading.value"
        :error="helperSearch.error.value"
        @search="helperSearch.search"
        @clear="helperSearch.clearFilters"
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

    <!-- biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click dismisses modal -->
    <div
      v-if="showNewPanel"
      class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="workbench-new-title"
      @click.self="closeNewPanel"
    >
      <div class="bg-white rounded-xl shadow-xl max-w-2xl w-full flex flex-col max-h-[90vh]">
        <header class="px-6 py-4 border-b border-line">
          <h2 id="workbench-new-title" class="text-lg font-semibold text-ink">New definition</h2>
          <p class="text-sm text-ink-muted mt-1">
            Choose a starter template and name for the new DSL definition.
          </p>
        </header>

        <div class="px-6 py-4 overflow-y-auto">
          <div class="mb-4">
            <label for="workbench-new-name" class="block text-sm font-medium text-ink mb-1">
              Name
            </label>
            <input
              id="workbench-new-name"
              v-model="newName"
              type="text"
              class="w-full px-3 py-2 border border-line rounded focus:outline-none focus:ring-2 focus:ring-accent-500"
              placeholder="Definition name"
              data-testid="workbench-new-name"
            >
            <p
              v-if="newNameError"
              class="mt-1 text-xs text-danger"
              data-testid="workbench-new-name-error"
            >
              {{ newNameError }}
            </p>
          </div>

          <DslTemplateGallery @select="handleTemplateSelect" />
        </div>

        <footer class="px-6 py-4 border-t border-line flex justify-end gap-2">
          <button
            type="button"
            class="px-4 py-2 rounded-lg text-sm font-medium border border-line text-ink hover:bg-surface"
            data-testid="workbench-new-cancel"
            @click="closeNewPanel"
          >
            Cancel
          </button>
          <button
            type="button"
            class="px-4 py-2 rounded-lg text-sm font-medium text-white"
            :class="(!newName.trim() || newNameError || !selectedTemplate)
                ? 'bg-accent-500/20 cursor-not-allowed'
                : 'bg-accent-500 hover:bg-accent-600'"
            :disabled="!newName.trim() || !!newNameError || !selectedTemplate"
            data-testid="workbench-new-create"
            @click="confirmCreate"
          >
            Create
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
