<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { useWorkbenchDraft } from '@cbs/admin-ui-plugin/composables/useWorkbenchDraft'
import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import DslTemplateGallery from '../components/DslTemplateGallery.vue'
import type { DslTemplate } from '../utils/dslTemplates'
import type { HelperCatalogEntry, HelpersResponse, HelperSearchFilters, ObjectSearchResult, ScheduleSummary } from '@cbs/components'
import {
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
  DslScheduleList,
  ErrorBanner,
  useHelperSearch,
} from '@cbs/components'
import { useCookie } from 'nuxt/app'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import type { RunnerOutput } from '~/types'

const workbench = useDslWorkbench()
const {
  state,
  selectedConstruct,
  loaders,
  loadConstructs,
  selectConstruct,
  createConstruct,
  saveConstruct,
  validateConstruct,
  publishConstruct,
  deleteConstruct,
  reloadDefinitions,
  markDirty,
} = workbench

const useWorkbenchStorage = createNamespacedLocalStorageState('cbs-nova:dsl-workbench')

const explorerOpen = useWorkbenchStorage<boolean>('explorer-open', true)
const explorerCollapsed = useWorkbenchStorage<boolean>('explorer-collapsed', false, {
  useCookie,
})
const helperSearchOpen = useWorkbenchStorage<boolean>('helper-search-open', false)

const dslApi = useDslApi()
const log = useClientLogger('dsl-workbench')

interface DraftSummary {
  name: string
  type?: string
  status?: string
  version?: string
  updatedAt: number
}

const drafts = ref<DraftSummary[]>([])
const draftsLoading = ref(false)

async function refreshDrafts() {
  draftsLoading.value = true
  try {
    const result = (await dslApi.listDrafts()) as DraftSummary[]
    drafts.value = Array.isArray(result) ? result : []
  } catch (err) {
    log.error('failed to load drafts', { error: (err as Error).message })
    drafts.value = []
  } finally {
    draftsLoading.value = false
  }
}
const helperSearch = useHelperSearch({
  fetch: async (filters: HelperSearchFilters) =>
    (await dslApi.searchObjects(filters)) as ObjectSearchResult[],
  debounceMs: 250,
})

const activeTab = ref<'editor' | 'helpers' | 'schedules'>('editor')
const helpersCatalog = ref<HelperCatalogEntry[]>([])
const helpersLoading = ref(false)
const helpersError = ref<string | null>(null)
const schedules = ref<ScheduleSummary[]>([])
const schedulesLoading = ref(false)
const schedulesError = ref<string | null>(null)

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

async function loadSchedules() {
  schedulesLoading.value = true
  schedulesError.value = null
  try {
    const result = (await dslApi.listSchedules()) as ScheduleSummary[]
    schedules.value = Array.isArray(result) ? result : []
  } catch (err) {
    schedulesError.value = (err as Error).message
    schedules.value = []
  } finally {
    schedulesLoading.value = false
  }
}

function setTab(tab: 'editor' | 'helpers' | 'schedules') {
  activeTab.value = tab
  if (tab === 'helpers') {
    void loadHelpers()
  } else if (tab === 'schedules') {
    void loadSchedules()
  }
}

const draftName = computed(() => selectedConstruct.value?.name ?? '')
const {
  body: draftBody,
  clearDraft,
  lastSavedAt: draftSavedAt,
  restoredFromDraft,
} = useWorkbenchDraft(draftName)

function onCodeChange(value: string) {
  draftBody.value = value
  if (selectedConstruct.value) markDirty()
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

function safeSelectConstruct(name: string) {
  if (state.isDirty && !window.confirm('Discard unsaved changes to this construct?')) {
    return
  }
  selectConstruct(name)
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
  const type = (parsed.type as 'Process' | 'Transaction' | 'Function' | 'Helper' | undefined) ?? 'Process'
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
    label: 'Save Draft',
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
      saveConstruct().then(() => refreshDrafts())
      break
    case 'publish':
      publishConstruct().then(() => refreshDrafts())
      break
  }
}

onMounted(() => {
  loadConstructs()
  refreshDrafts()
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
    </header>

    <div class="flex flex-1 overflow-hidden">
      <aside
        v-show="explorerOpen"
        :class="explorerCollapsed ? 'w-12' : 'w-64'"
        class="shrink-0 border-r border-gray-800 overflow-hidden"
      >
        <section
          v-if="!explorerCollapsed"
          data-testid="dsl-draft-picker"
          class="border-b border-gray-800 bg-gray-900 text-gray-100"
        >
          <header class="flex items-center justify-between px-3 py-2 text-xs uppercase tracking-wide text-gray-400">
            <span>Saved Drafts</span>
            <span class="text-gray-500">{{ drafts.length }}</span>
          </header>
          <ul v-if="drafts.length" class="max-h-48 overflow-y-auto">
            <li
              v-for="draft in drafts"
              :key="draft.name"
              data-testid="dsl-draft-picker-item"
              class="cursor-pointer px-3 py-1.5 text-sm hover:bg-gray-700"
              :class="state.selectedName === draft.name ? 'bg-gray-700' : ''"
              @click="safeSelectConstruct(draft.name)"
              @keydown.enter="safeSelectConstruct(draft.name)"
            >
              <div class="font-medium">{{ draft.name }}</div>
              <div class="text-xs text-gray-400">
                {{ draft.type ?? '—' }} · {{ draft.status ?? 'Draft' }}
              </div>
            </li>
          </ul>
          <p v-else-if="draftsLoading" class="px-3 py-2 text-xs text-gray-400">
            Loading drafts…
          </p>
          <p v-else class="px-3 py-2 text-xs text-gray-500">
            No saved drafts yet.
          </p>
        </section>
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
        <div class="bg-white border-b border-gray-200 px-4 flex gap-4 text-sm">
          <button
            type="button"
            class="py-2 border-b-2 transition-colors"
            :class="activeTab === 'editor' ? 'border-blue-600 text-blue-700 font-medium' : 'border-transparent text-gray-600 hover:text-gray-900'"
            data-testid="dsl-workbench-tab-editor"
            @click="setTab('editor')"
          >
            Editor
          </button>
          <button
            type="button"
            class="py-2 border-b-2 transition-colors"
            :class="activeTab === 'helpers' ? 'border-blue-600 text-blue-700 font-medium' : 'border-transparent text-gray-600 hover:text-gray-900'"
            data-testid="dsl-workbench-tab-helpers"
            @click="setTab('helpers')"
          >
            Helpers
          </button>
          <button
            type="button"
            class="py-2 border-b-2 transition-colors"
            :class="activeTab === 'schedules' ? 'border-blue-600 text-blue-700 font-medium' : 'border-transparent text-gray-600 hover:text-gray-900'"
            data-testid="dsl-workbench-tab-schedules"
            @click="setTab('schedules')"
          >
            Schedules
          </button>
        </div>
        <div v-if="restoredFromDraft" class="px-3 pt-2">
          <DslDraftRestoreBanner :saved-at="draftSavedAt" @discard="clearDraft" />
        </div>
        <div v-if="deleteError" class="px-3 pt-2" data-testid="dsl-workbench-delete-error">
          <ErrorBanner :message="deleteError" @retry="confirmDelete" />
        </div>
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor
            v-if="activeTab === 'editor'"
            :code="draftBody"
            :construct="selectedConstruct"
            :preview="runPreview"
            :explain="runExplain"
            @update:code="onCodeChange"
          />
          <DslHelperCatalog
            v-else-if="activeTab === 'helpers'"
            :helpers="helpersCatalog"
            :loading="helpersLoading"
            :error="helpersError"
          />
          <DslScheduleList
            v-else-if="activeTab === 'schedules'"
            :schedules="schedules"
            :loading="schedulesLoading"
            :error="schedulesError"
            @create="(payload) => { dslApi.createSchedule(payload).then(() => loadSchedules()) }"
            @delete="(definition) => { dslApi.deleteSchedule(definition).then(() => loadSchedules()) }"
          />
        </div>
        <DslProblemsPanel v-if="activeTab === 'editor'" :errors="state.validationErrors" />
      </main>

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
              />
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
