<script setup lang="ts">
const route = useRoute()
const id = computed(() => String(route.params.id))

const { selectedExecution, loadDetail, startPolling, stopPolling } = useExecutions()

const activeTab = ref<'diagram' | 'payload' | 'metadata' | 'logs' | 'errors'>('diagram')

await loadDetail(id.value)
if (selectedExecution.value?.status === 'Running') {
  startPolling(id.value)
}

const traceSteps = computed(() => selectedExecution.value?.trace ?? [])
const compensationSteps = computed(() => traceSteps.value.filter(s => s.isCompensation))
const regularSteps = computed(() => traceSteps.value.filter(s => !s.isCompensation))

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="p-6 space-y-4">
    <div v-if="!selectedExecution" class="text-sm text-gray-500">Loading…</div>
    <template v-else>
      <ExecutionsExecutionSummary :execution="selectedExecution" />

      <ExecutionsExecutionTrace v-if="regularSteps.length > 0" :steps="regularSteps" />
      <ExecutionsCompensationLane :steps="compensationSteps" />

      <div class="bg-white border border-gray-200 rounded-lg">
        <div class="border-b border-gray-200 flex overflow-x-auto">
          <button v-for="tab in (['diagram','payload','metadata','logs','errors'] as const)" :key="tab"
                  type="button"
                  :class="['px-4 py-2 text-sm font-medium border-b-2',
                           activeTab === tab ? 'border-blue-600 text-blue-700' : 'border-transparent text-gray-600 hover:text-gray-900']"
                  @click="activeTab = tab">
            {{ tab === 'diagram' ? 'Diagram' : tab === 'payload' ? 'I/O Payload' : tab[0].toUpperCase() + tab.slice(1) }}
          </button>
        </div>
        <div class="p-4">
          <ExecutionsDiagramTab v-if="activeTab === 'diagram'" :diagram="selectedExecution.mermaidDiagram" />
          <ExecutionsPayloadTab v-else-if="activeTab === 'payload'"
                                :input="selectedExecution.input" :output="selectedExecution.output" />
          <ExecutionsMetadataTab v-else-if="activeTab === 'metadata'"
                                  :metadata="selectedExecution.metadata" :execution="selectedExecution" />
          <ExecutionsLogsTab v-else-if="activeTab === 'logs'" :logs="selectedExecution.logs" />
          <ExecutionsErrorsTab v-else-if="activeTab === 'errors'" :errors="selectedExecution.errors" />
        </div>
      </div>
    </template>
  </div>
</template>
