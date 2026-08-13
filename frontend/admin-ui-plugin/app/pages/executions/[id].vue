<script setup lang="ts">
const route = useRoute()
const id = computed(() => String(route.params.id))

const { selectedExecution, loadDetail, startPolling, stopPolling, isStalePolling } = useExecutions()

const activeTab = ref<'diagram' | 'payload' | 'metadata' | 'logs' | 'errors'>('diagram')

await loadDetail(id.value)
if (selectedExecution.value?.status === 'Running') {
  startPolling(id.value)
}

// T199: loadDetail already auto-starts stale polling if the backend
// returns Stale. We expose `isStalePolling(id)` so the banner below can
// render only while polling is actively in flight (not just because the
// status field reads Stale — the backend may have just transitioned
// the run out between the last fetch and the user clicking the row).
const showStaleBanner = computed(
  () => selectedExecution.value?.status === 'Stale' && isStalePolling(selectedExecution.value.id),
)

const traceSteps = computed(() => selectedExecution.value?.trace ?? [])
const compensationSteps = computed(() => traceSteps.value.filter((s) => s.isCompensation))
const regularSteps = computed(() => traceSteps.value.filter((s) => !s.isCompensation))

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="p-6 space-y-4">
    <div v-if="!selectedExecution" class="text-sm text-gray-500">Loading…</div>
    <template v-else>
      <!--
        T199: visible while the run is Stale and the backend is being
        polled. The banner disappears as soon as the run transitions to
        a non-Stale state (useStalePolling stops the loop and the
        computed above flips false).
      -->
      <div
        v-if="showStaleBanner"
        class="flex items-center gap-2 rounded-md border border-warning-300 bg-warning-100 px-3 py-2 text-sm text-warning-900"
        role="status"
        aria-live="polite"
        data-testid="stale-banner"
      >
        <span
          class="inline-block w-3 h-3 border-2 border-warning-900 border-t-transparent rounded-full animate-spin"
          aria-hidden="true"
        />
        <span>Stale — refreshing…</span>
      </div>

      <ExecutionsExecutionSummary :execution="selectedExecution" />

      <ExecutionsExecutionTrace v-if="regularSteps.length > 0" :steps="regularSteps" />
      <ExecutionsCompensationLane :steps="compensationSteps" />

      <div class="bg-white border border-gray-200 rounded-lg">
        <div class="border-b border-gray-200 flex overflow-x-auto">
          <button
            v-for="tab in (['diagram','payload','metadata','logs','errors'] as const)"
            :key="tab"
            type="button"
            :class="['px-4 py-2 text-sm font-medium border-b-2',
                           activeTab === tab ? 'border-blue-600 text-blue-700' : 'border-transparent text-gray-600 hover:text-gray-900']"
            @click="activeTab = tab"
          >
            {{ tab === 'diagram' ? 'Diagram' : tab === 'payload' ? 'I/O Payload' : tab[0].toUpperCase() + tab.slice(1) }}
          </button>
        </div>
        <div class="p-4">
          <ExecutionsDiagramTab
            v-if="activeTab === 'diagram'"
            :diagram="selectedExecution.mermaidDiagram"
          />
          <ExecutionsPayloadTab
            v-else-if="activeTab === 'payload'"
            :input="selectedExecution.input"
            :output="selectedExecution.output"
          />
          <ExecutionsMetadataTab
            v-else-if="activeTab === 'metadata'"
            :metadata="selectedExecution.metadata"
            :execution="selectedExecution"
          />
          <ExecutionsLogsTab v-else-if="activeTab === 'logs'" :logs="selectedExecution.logs" />
          <ExecutionsErrorsTab
            v-else-if="activeTab === 'errors'"
            :errors="selectedExecution.errors"
          />
        </div>
      </div>
    </template>
  </div>
</template>
