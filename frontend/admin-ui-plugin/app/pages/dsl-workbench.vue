<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { useWorkbenchDraft } from '@cbs/admin-ui-plugin/composables/useWorkbenchDraft'
import type { HelperSearchFilters, ObjectSearchResult } from '@cbs/components'
import {
  DslBodyEditor,
  DslConstructExplorer,
  DslDraftRestoreBanner,
  DslHelperSearchPanel,
  DslMetadataPanel,
  DslProblemsPanel,
  useHelperSearch,
  useLocalStorageState,
} from '@cbs/components'
import { computed, onMounted, watch } from 'vue'

const workbench = useDslWorkbench()
const {
  state,
  selectedConstruct,
  loadConstructs,
  selectConstruct,
  saveConstruct,
  validateConstruct,
  publishConstruct,
  reloadDefinitions,
  markDirty,
} = workbench

const STORAGE_PREFIX = 'cbs-nova:dsl-workbench'

const explorerOpen = useLocalStorageState<boolean>(`${STORAGE_PREFIX}:explorer-open`, true)
const explorerCollapsed = useLocalStorageState<boolean>(
  `${STORAGE_PREFIX}:explorer-collapsed`,
  false,
)
const helperSearchOpen = useLocalStorageState<boolean>(
  `${STORAGE_PREFIX}:helper-search-open`,
  false,
)

const dslApi = useDslApi()
const helperSearch = useHelperSearch({
  fetch: async (filters: HelperSearchFilters) =>
    (await dslApi.searchObjects(filters)) as ObjectSearchResult[],
  debounceMs: 250,
})

const draftName = computed(() => selectedConstruct.value?.name ?? '')
const {
  body: draftBody,
  clearDraft,
  lastSavedAt: draftSavedAt,
  restoredFromDraft,
} = useWorkbenchDraft(draftName)

watch(draftBody, () => {
  if (selectedConstruct.value) markDirty()
})

onMounted(() => {
  loadConstructs()
  void helperSearch.execute()
})

function toggleExplorer() {
  explorerOpen.value = !explorerOpen.value
}

function toggleHelperSearch() {
  helperSearchOpen.value = !helperSearchOpen.value
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-50">
    <header class="flex items-center justify-between px-4 py-2 bg-white border-b border-gray-200">
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
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
          :disabled="state.isLoading"
          @click="reloadDefinitions"
        >
          Refresh
        </button>
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
          :disabled="!selectedConstruct || state.isSaving"
          @click="validateConstruct"
        >
          Validate
        </button>
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
          :disabled="!selectedConstruct || state.isSaving || !state.isDirty"
          @click="saveConstruct"
        >
          Save Draft
        </button>
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          :disabled="!selectedConstruct || state.isSaving"
          @click="publishConstruct"
        >
          Publish
        </button>
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100"
          :class="helperSearchOpen ? 'bg-blue-50 text-blue-700 border-blue-300' : ''"
          @click="toggleHelperSearch"
        >
          {{ helperSearchOpen ? 'Close Objects' : 'Objects' }}
        </button>
      </div>
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
          :loading="state.isLoading"
          @select="selectConstruct"
        />
      </aside>

      <main class="flex-1 flex flex-col overflow-hidden">
        <DslMetadataPanel :construct="selectedConstruct" />
        <div v-if="restoredFromDraft" class="px-3 pt-2">
          <DslDraftRestoreBanner :saved-at="draftSavedAt" @discard="clearDraft" />
        </div>
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor v-model:code="draftBody" :construct="selectedConstruct" />
        </div>
        <DslProblemsPanel :errors="state.validationErrors" />
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
  </div>
</template>
