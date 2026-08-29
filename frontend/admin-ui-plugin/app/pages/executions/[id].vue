<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useExecutions } from '@cbs/admin-ui-plugin/composables/useExecutions'
import {
  ErrorBanner,
  ExecutionsCancelConfirmationModal,
  ExecutionsCompensationLane,
  ExecutionsExecutionSummary,
  ExecutionsExecutionTrace,
} from '@cbs/components'
import { useRoute } from 'nuxt/app'
import { computed, onUnmounted, ref } from 'vue'

const route = useRoute()
const id = computed(() => String(route.params.id))

const {
  selectedExecution,
  error,
  loadDetail,
  startPolling,
  stopPolling,
  isStalePolling,
  isCancelling,
  cancelExecution,
} = useExecutions()

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

const isRunning = computed(() => selectedExecution.value?.status === 'Running')
const showCancelButton = computed(() => isRunning.value)
const cancelling = computed(() =>
  selectedExecution.value ? isCancelling(selectedExecution.value.id) : false,
)
const cancelError = ref<string | null>(null)
const showCancelModal = ref<boolean>(false)

function openCancelModal() {
  cancelError.value = null
  showCancelModal.value = true
}

function dismissCancelModal() {
  if (cancelling.value) return
  showCancelModal.value = false
}

async function confirmCancel() {
  const executionId = selectedExecution.value?.id
  if (!executionId) return
  try {
    await cancelExecution(executionId)
    showCancelModal.value = false
    cancelError.value = null
  } catch (err) {
    cancelError.value = (err as Error)?.message ?? 'Failed to cancel execution'
  }
}

const traceSteps = computed(() => selectedExecution.value?.trace ?? [])
const compensationSteps = computed(() => traceSteps.value.filter((s) => s.isCompensation))
const regularSteps = computed(() => traceSteps.value.filter((s) => !s.isCompensation))

// T266: completed runs don't carry a diagram field — fetch one for the
// underlying process definition by name and bind it to the Diagram tab.
const diagram = ref<string | undefined>(selectedExecution.value?.mermaidDiagram)
const diagramError = ref<string | null>(null)
const diagramLoading = ref(false)

async function loadDiagram() {
  const processName = selectedExecution.value?.entity
  if (!processName) {
    diagram.value = undefined
    return
  }
  diagramLoading.value = true
  diagramError.value = null
  try {
    const response = await useDslApi().getProcessDiagram(processName, 'mermaid')
    diagram.value = response?.diagram
  } catch (err) {
    diagramError.value = (err as Error)?.message ?? 'Failed to load diagram'
    diagram.value = undefined
  } finally {
    diagramLoading.value = false
  }
}

if (selectedExecution.value?.entity) {
  await loadDiagram()
}

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="p-6 space-y-4">
    <div v-if="error && !selectedExecution" class="text-sm text-gray-500">
      <ErrorBanner :message="error" @retry="() => loadDetail(id)" />
    </div>
    <div v-else-if="!selectedExecution" class="text-sm text-gray-500">Loading…</div>
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

      <ErrorBanner
        v-if="cancelError"
        :message="cancelError"
        :retry-label="'Dismiss'"
        @retry="cancelError = null"
      />

      <ExecutionsExecutionSummary :execution="selectedExecution">
        <template #actions>
          <button
            v-if="showCancelButton"
            type="button"
            data-testid="cancel-execution-button"
            class="px-3 py-1.5 text-xs font-medium rounded border transition-colors"
            :class="
              cancelling
                ? 'border-red-200 bg-red-50 text-red-400 cursor-not-allowed'
                : 'border-red-300 bg-white text-red-700 hover:bg-red-50'
            "
            :disabled="cancelling"
            @click="openCancelModal"
          >
            {{ cancelling ? 'Cancelling…' : 'Cancel execution' }}
          </button>
        </template>
      </ExecutionsExecutionSummary>

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
          <ExecutionsDiagramTab v-if="activeTab === 'diagram'" :diagram="diagram" />
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

      <ExecutionsCancelConfirmationModal
        :show="showCancelModal"
        :execution-id="selectedExecution?.id"
        :busy="cancelling"
        @confirm="confirmCancel"
        @cancel="dismissCancelModal"
      />
    </template>
  </div>
</template>
