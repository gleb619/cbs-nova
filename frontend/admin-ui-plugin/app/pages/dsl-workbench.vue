<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useDslWorkbench } from '@cbs/admin-ui-plugin/composables/useDslWorkbench'
import { useWorkbenchDraft } from '@cbs/admin-ui-plugin/composables/useWorkbenchDraft'
import type { HelperSearchFilters, ObjectSearchResult } from '@cbs/components'
import {
  createNamespacedLocalStorageState,
  DropdownMenu,
  type DropdownMenuItem,
  DslBodyEditor,
  DslConstructExplorer,
  DslDraftRestoreBanner,
  DslHelperSearchPanel,
  DslMetadataPanel,
  DslPlainConstructList,
  DslProblemsPanel,
  useHelperSearch,
} from '@cbs/components'
import { useCookie } from 'nuxt/app'
import { computed, onMounted } from 'vue'
import type { RunnerOutput } from '~/types'

const workbench = useDslWorkbench()
const {
  state,
  selectedConstruct,
  loaders,
  loadConstructs,
  selectConstruct,
  saveConstruct,
  validateConstruct,
  publishConstruct,
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
      break
    case 'validate':
      validateConstruct()
      break
    case 'save':
      saveConstruct()
      break
    case 'publish':
      publishConstruct()
      break
  }
}

onMounted(() => {
  loadConstructs()
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
      <div class="ml-auto">
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
        <DslConstructExplorer
          v-model:collapsed="explorerCollapsed"
          :constructs="state.constructs"
          :selected-name="state.selectedName"
          :loading="loaders.constructs"
          @select="selectConstruct"
        >
          <template #default="{ constructs, selectedName, onSelect }">
            <DslPlainConstructList
              :constructs="constructs"
              :selected-name="selectedName"
              :on-select="onSelect"
            />
          </template>
        </DslConstructExplorer>
      </aside>

      <main class="flex-1 flex flex-col overflow-hidden">
        <DslMetadataPanel :construct="selectedConstruct" />
        <div v-if="restoredFromDraft" class="px-3 pt-2">
          <DslDraftRestoreBanner :saved-at="draftSavedAt" @discard="clearDraft" />
        </div>
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor
            :code="draftBody"
            :construct="selectedConstruct"
            :preview="runPreview"
            :explain="runExplain"
            @update:code="onCodeChange"
          />
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
