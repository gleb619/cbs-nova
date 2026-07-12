<script setup lang="ts">
definePageMeta({ layout: 'default' })

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
} = workbench

const explorerOpen = ref(true)

onMounted(() => {
  loadConstructs()
})
</script>

<template>
  <div class="flex flex-col h-full bg-gray-50">
    <header class="flex items-center justify-between px-4 py-2 bg-white border-b border-gray-200">
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="md:hidden p-1.5 rounded hover:bg-gray-100"
          aria-label="Toggle explorer"
          @click="explorerOpen = !explorerOpen"
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
      </div>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <aside v-show="explorerOpen" class="w-64 shrink-0 border-r border-gray-800">
        <DslConstructExplorer
          :constructs="state.constructs"
          :selected-name="state.selectedName"
          :loading="state.isLoading"
          @select="selectConstruct"
        />
      </aside>

      <main class="flex-1 flex flex-col overflow-hidden">
        <DslMetadataPanel :construct="selectedConstruct" />
        <div class="flex-1 overflow-hidden">
          <DslBodyEditor :construct="selectedConstruct" />
        </div>
        <DslProblemsPanel :errors="state.validationErrors" />
      </main>
    </div>
  </div>
</template>
