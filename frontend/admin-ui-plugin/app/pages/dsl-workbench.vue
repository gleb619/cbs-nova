<script setup lang="ts">
import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDraftDirty } from '@cbs/admin-ui-plugin/composables/useDraftDirty'
import { useDraftSave } from '@cbs/admin-ui-plugin/composables/useDraftSave'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { useWorkbenchDraft } from '@cbs/admin-ui-plugin/composables/useWorkbenchDraft'
import type {
  HelperCatalogEntry,
  HelperSearchFilters,
  HelpersResponse,
  ObjectSearchResult,
} from '@cbs/components'
import {
  CbsDrawer,
  createNamespacedLocalStorageState,
  DropdownMenu,
  type DropdownMenuItem,
  DslBodyEditor,
  DslConstructExplorer,
  DslDeleteDraftConfirmationModal,
  DslDraftRestoreBanner,
  DslHelperCatalog,
  DslHelperSearchPanel,
  DslMetadataPanel,
  DslPlainConstructList,
  DslProblemsPanel,
  ErrorBanner,
  useHelperSearch,
  useSavedDrafts,
} from '@cbs/components'
import { useEventListener } from '@vueuse/core'
import { useCookie, useRoute } from 'nuxt/app'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import type { RunnerOutput } from '~/types'
import DslTemplateGallery from '../components/DslTemplateGallery.vue'
import type { DslTemplate } from '../utils/dslTemplates'

const workbench = useDslWorkbench()
const route = useRoute()
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

const draftDirty = useDraftDirty()

const useWorkbenchStorage = createNamespacedLocalStorageState('cbs-nova:dsl-workbench')

const explorerOpen = useWorkbenchStorage<boolean>('explorer-open', true)
const explorerCollapsed = useWorkbenchStorage<boolean>('explorer-collapsed', false, {
  useCookie,
})
const helperSearchOpen = useWorkbenchStorage<boolean>('helper-search-open', false)
const helperCatalogOpen = useWorkbenchStorage<boolean>('helper-catalog-open', false)

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
  if (state.isDirty && !window.confirm('Discard unsaved changes to this construct?')) {
    return
  }
  selectConstruct(name)
}

// Mirror the workbench selection into the shared store so the widget can
// highlight the active draft.
watch(
  () => state.selectedName,
  (name) => {
    draftsSelectedName.value = name ?? null
  },
  { immediate: true },
)

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

function toggleHelperCatalog() {
  helperCatalogOpen.value = !helperCatalogOpen.value
  if (helperCatalogOpen.value) {
    void loadHelpers()
  }
}

const draftName = computed(() => selectedConstruct.value?.name ?? '')
const {
  body: draftBody,
  clearDraft,
  lastSavedAt: draftSavedAt,
  restoredFromDraft,
} = useWorkbenchDraft(draftName)

// Source-file-backed constructs load their Java source from the backend.
const fileCode = ref('')
const fileCodeLoading = ref(false)
const isFileBacked = computed(() => !!selectedConstruct.value?.filePath)
const editorCode = computed(() => (isFileBacked.value ? fileCode.value : draftBody.value))

// Load source file content when a file-backed construct is selected.
watch(
  selectedConstruct,
  async (construct) => {
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
  },
  { immediate: true },
)

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
  if (!state.isDirty) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (state.isDirty && !window.confirm('You have unsaved changes. Leave anyway?')) {
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
  const existsInConstructs = state.constructs.some((c) => c.name === name)
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
  await nextTick()
  draftBody.value = selectedTemplate.value.body
  markDirty()
  closeNewPanel()
}

type ActionValue = 'refresh' | 'validate' | 'save' | 'publish'

const actionItems = computed<DropdownMenuItem[]>(() => [
  { label: 'Refresh', value: 'refresh', disabled: state.isLoading },
  {
    label: 'Validate',
    value: 'validate',
    disabled: !selectedConstruct.value || state.isSaving,
  },
  {
    label: isFileBacked.value ? 'Save File' : 'Save Draft',
    value: 'save',
    disabled: !selectedConstruct.value || state.isSaving || !state.isDirty,
  },
  {
    label: 'Publish',
    value: 'publish',
    disabled: !selectedConstruct.value || state.isSaving,
    variant: 'primary',
  },
])

function runAction(item: DropdownMenuItem) {
  switch (item.value as ActionValue) {
    case 'refresh':
      reloadDefinitions()
      refreshDrafts()
      break
    case 'validate':
      validateConstruct()
      break
    case 'save':
      draftSave.save().then(() => refreshDrafts())
      break
    case 'publish':
      publishConstruct().then(() => refreshDrafts())
      break
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

useEventListener(window, 'keydown', handleSaveShortcut)

function formatSavedTime(date: Date | null): string {
  if (!date) return ''
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  return `${Math.floor(minutes / 60)}h ago`
}

const saveStatusClasses = computed(() => {
  switch (draftSave.status.value) {
    case 'dirty':
      return 'bg-amber-50 text-amber-700 border border-amber-200'
    case 'saving':
      return 'bg-blue-50 text-blue-700 border border-blue-200'
    case 'saved':
      return 'bg-green-50 text-green-700 border border-green-200'
    case 'error':
      return 'bg-red-50 text-red-700 border border-red-200'
    default:
      return 'text-gray-500'
  }
})

const saveStatusText = computed(() => {
  switch (draftSave.status.value) {
    case 'dirty':
      return 'Unsaved changes'
    case 'saving':
      return 'Saving…'
    case 'saved':
      return `Saved ${formatSavedTime(draftSave.lastSavedAt.value)}`
    case 'error':
      return 'Save failed — Retry'
    default:
      return draftSave.lastSavedAt.value ? 'Saved' : ''
  }
})

onMounted(() => {
  loadConstructs()
  refreshDrafts()
  // A draft picked from the navbar widget on another route arrives as a query.
  const requested = route.query.draft
  const requestedName = Array.isArray(requested) ? requested[0] : requested
  if (requestedName) selectConstruct(String(requestedName))
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <div class="flex flex-col h-full bg-gray-50">
    <header class="flex items-center px-4 py-2 bg-white border-b border-gray-200">
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="md:hidden p-1.5 rounded hover:bg-gray-100"
          aria-label="Toggle explorer"
          @click="toggleExplorer"
        >
          ☰
        </button>
        <h1 class="text-lg font-semibold text-gray-900">DSL Workbench</h1>
        <span v-if="selectedConstruct" class="text-sm text-gray-500">
          / {{ selectedConstruct.name }}
        </span>
      </div>
      <div class="ml-auto flex items-center gap-3">
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="workbench-new-definition"
          @click="openNewPanel"
        >
          New
        </button>
        <span
          v-if="state.isDirty"
          class="inline-flex items-center gap-1.5 text-xs text-amber-700"
          data-testid="workbench-dirty-indicator"
          role="status"
          aria-label="You have unsaved changes"
        >
          <span aria-hidden="true" class="inline-block h-2 w-2 rounded-full bg-amber-500"></span>
          <span>unsaved changes</span>
        </span>
        <span
          v-if="saveStatusText"
          class="inline-flex items-center gap-1.5 text-xs rounded-full px-2.5 py-1"
          :class="saveStatusClasses"
          data-testid="draft-save-status"
          role="status"
        >
          <span
            v-if="draftSave.status.value === 'saving'"
            class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin"
            aria-hidden="true"
          ></span>
          <span>{{ saveStatusText }}</span>
          <button
            v-if="draftSave.status.value === 'error'"
            type="button"
            class="ml-1 underline hover:no-underline"
            data-testid="draft-save-retry"
            @click="draftSave.save()"
          >
            Retry
          </button>
        </span>
        <DropdownMenu label="Actions" align="right" :items="actionItems" @select="runAction" />
      </div>
      <button
        type="button"
        class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100"
        :class="helperSearchOpen ? 'bg-blue-50 text-blue-700 border-blue-300' : ''"
        @click="toggleHelperSearch"
      >
        {{ helperSearchOpen ? 'Close Objects' : 'Objects' }}
      </button>
      <button
        type="button"
        class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100"
        :class="helperCatalogOpen ? 'bg-blue-50 text-blue-700 border-blue-300' : ''"
        data-testid="workbench-toggle-helpers"
        @click="toggleHelperCatalog"
      >
        {{ helperCatalogOpen ? 'Close Helpers' : 'Helpers' }}
      </button>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <aside
        v-show="explorerOpen"
        :class="explorerCollapsed ? 'w-12' : 'w-64'"
        class="shrink-0 border-r border-gray-800 overflow-hidden"
      >
        <DslConstructExplorer
          v-model:collapsed="explorerCollapsed"
          :constructs="state.constructs"
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
        <DslMetadataPanel :construct="selectedConstruct" />
        <div v-if="restoredFromDraft && !isFileBacked" class="px-3 pt-2">
          <DslDraftRestoreBanner :saved-at="draftSavedAt" @discard="clearDraft" />
        </div>
        <div
          v-if="fileCodeLoading"
          class="px-3 pt-2 text-xs text-gray-500"
          data-testid="workbench-file-loading"
        >
          Loading source file…
        </div>
        <div v-if="deleteError" class="px-3 pt-2" data-testid="dsl-workbench-delete-error">
          <ErrorBanner :message="deleteError" @retry="confirmDelete" />
        </div>
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor
            :code="editorCode"
            :construct="selectedConstruct"
            :preview="runPreview"
            :explain="runExplain"
            @update:code="onCodeChange"
            @save="handleEditorSave"
          />
        </div>
        <DslProblemsPanel :errors="state.validationErrors" />
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
      />
    </div>

    <DslDeleteDraftConfirmationModal
      :show="showDeleteModal"
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
        <header class="px-6 py-4 border-b border-gray-200">
          <h2 id="workbench-new-title" class="text-lg font-semibold text-gray-900">
            New definition
          </h2>
          <p class="text-sm text-gray-600 mt-1">
            Choose a starter template and name for the new DSL definition.
          </p>
        </header>

        <div class="px-6 py-4 overflow-y-auto">
          <div class="mb-4">
            <label for="workbench-new-name" class="block text-sm font-medium text-gray-700 mb-1">
              Name
            </label>
            <input
              id="workbench-new-name"
              v-model="newName"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Definition name"
              data-testid="workbench-new-name"
            >
            <p
              v-if="newNameError"
              class="mt-1 text-xs text-red-600"
              data-testid="workbench-new-name-error"
            >
              {{ newNameError }}
            </p>
          </div>

          <DslTemplateGallery @select="handleTemplateSelect" />
        </div>

        <footer class="px-6 py-4 border-t border-gray-200 flex justify-end gap-2">
          <button
            type="button"
            class="px-4 py-2 rounded-lg text-sm font-medium border border-gray-300 text-gray-700 hover:bg-gray-100"
            data-testid="workbench-new-cancel"
            @click="closeNewPanel"
          >
            Cancel
          </button>
          <button
            type="button"
            class="px-4 py-2 rounded-lg text-sm font-medium text-white"
            :class="(!newName.trim() || newNameError || !selectedTemplate)
                ? 'bg-blue-300 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700'"
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
