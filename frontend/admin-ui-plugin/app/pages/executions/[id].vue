<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useExecutions } from '@cbs/admin-ui-plugin/composables/useExecutions'
import { useExecutionsApi } from '@cbs/admin-ui-plugin/composables/useExecutionsApi'
import { resolveStalePollMs } from '@cbs/admin-ui-plugin/composables/useStalePollInterval'
import { useTemporalLink } from '@cbs/admin-ui-plugin/composables/useTemporalLink'
import {
  DslExecutionTimeline,
  ErrorBanner,
  ExecutionsCancelConfirmationModal,
  ExecutionsCompensationLane,
  ExecutionsExecutionSummary,
  ExecutionsExecutionTrace,
  selectTransaction,
} from '@cbs/components'
import { useLocalStorageState } from '@cbs/components/composables'
import { navigateTo, useRoute } from 'nuxt/app'
import { computed, onUnmounted, ref } from 'vue'
import type { ExecutionMode, TransactionExecutionDto } from '~/types'
import { stashRunAgain } from '../../utils/runAgainHandoff'

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

type DetailTab = 'diagram' | 'payload' | 'metadata' | 'logs' | 'errors' | 'transactions'

const activeTab = ref<DetailTab>('diagram')

// T296 — the backend has no log source for production runs today, so the
// Logs tab only appears when the detail payload actually carries one. The
// user's tab choice is kept in `activeTab`; `visibleTab` falls back to
// 'diagram' while the chosen tab is not available, so the panel never goes
// blank when e.g. logs disappear after a refresh.
const hasLogs = computed(() => (selectedExecution.value?.logs?.length ?? 0) > 0)
const availableTabs = computed<DetailTab[]>(() => {
  const tabs: DetailTab[] = ['diagram', 'payload', 'metadata', 'transactions']
  if (hasLogs.value) tabs.push('logs')
  tabs.push('errors')
  return tabs
})
const visibleTab = computed<DetailTab>(() =>
  availableTabs.value.includes(activeTab.value) ? activeTab.value : 'diagram',
)

// T461 — live polling on the detail page is user-controllable and persisted
// (mirrors the T441 list-page pattern). Default stays ON so a user landing
// on a Running execution still gets live updates, but it is now pausable
// and remembered. The interval defaults to the shared resolved stalePollMs.
const defaultPollMs = resolveStalePollMs()
const isLivePollingEnabled = useLocalStorageState('executions.detail.livePolling.enabled', true)
const livePollingIntervalMs = useLocalStorageState(
  'executions.detail.livePolling.intervalMs',
  defaultPollMs,
)

await loadDetail(id.value)
if (selectedExecution.value?.status === 'Running' && isLivePollingEnabled.value) {
  startPolling(id.value, livePollingIntervalMs.value)
}

function onToggleLivePolling(enabled: boolean) {
  isLivePollingEnabled.value = enabled
  if (enabled && selectedExecution.value?.status === 'Running') {
    startPolling(id.value, livePollingIntervalMs.value)
  } else {
    stopPolling()
  }
}

function onLivePollingIntervalChange() {
  if (!isLivePollingEnabled.value) return
  if (selectedExecution.value?.status !== 'Running') return
  stopPolling()
  startPolling(id.value, livePollingIntervalMs.value)
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

// T302 — deep-link to the Temporal Web UI for this run's workflow (opt-in).
const temporal = useTemporalLink()
const workflowLink = computed(() => temporal.workflowUrl(selectedExecution.value?.workflowId ?? ''))

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

const transactions = ref<TransactionExecutionDto[]>([])
const transactionsLoading = ref(false)
const transactionsError = ref<string | null>(null)
const transactionsLoaded = ref(false)

function onTimelineSelect(tx: TransactionExecutionDto) {
  selectTransaction(tx)
}

async function loadTransactions() {
  if (transactionsLoaded.value) return
  transactionsLoading.value = true
  transactionsError.value = null
  try {
    transactions.value = await useExecutionsApi().getTransactions(id.value)
    transactionsLoaded.value = true
  } catch (err) {
    transactionsError.value = (err as Error)?.message ?? 'Failed to load transactions'
  } finally {
    transactionsLoading.value = false
  }
}

function onTabSelect(tab: DetailTab) {
  activeTab.value = tab
  if (tab === 'transactions') {
    void loadTransactions()
  }
}

if (selectedExecution.value?.entity) {
  await loadDiagram()
}
// T293 — map the execution's stored mode enum to the runner's mode strings.
// RunnerMode is 'preview' | 'run' | 'explain'; ExecutionMode is
// 'PREVIEW' | 'RUN' | 'EXPLAIN'. Unknown values fall back to the default.
function toRunnerMode(m: ExecutionMode): 'preview' | 'run' | 'explain' {
  switch (m) {
    case 'PREVIEW':
      return 'preview'
    case 'EXPLAIN':
      return 'explain'
    case 'RUN':
      return 'run'
    default:
      return 'run'
  }
}

function runAgain() {
  const execution = selectedExecution.value
  if (!execution) return
  stashRunAgain(execution.entity, execution.input)
  navigateTo({
    path: '/runner',
    query: { name: execution.entity, mode: toRunnerMode(execution.mode) },
  })
}

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div data-testid="execution-detail" class="p-6 space-y-4">
    <div v-if="error && !selectedExecution" class="text-sm text-ink-muted">
      <ErrorBanner :message="error" @retry="() => loadDetail(id)" />
    </div>
    <div v-else-if="!selectedExecution" class="text-sm text-ink-muted">Loading…</div>
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
            type="button"
            data-testid="execution-detail-live-polling-toggle"
            :aria-pressed="isLivePollingEnabled"
            class="px-3 py-1.5 text-xs font-medium rounded border transition-colors"
            :class="
              isLivePollingEnabled
                ? 'border-primary-300 bg-white text-primary-700 hover:bg-primary-50'
                : 'border-neutral-300 bg-white text-neutral-700 hover:bg-neutral-100'
            "
            @click="onToggleLivePolling(!isLivePollingEnabled)"
          >
            {{ isLivePollingEnabled ? 'Pause live updates' : 'Resume live updates' }}
          </button>
          <select
            v-model.number="livePollingIntervalMs"
            :disabled="!isLivePollingEnabled"
            data-testid="execution-detail-live-polling-interval"
            aria-label="Polling interval"
            class="px-2 py-1.5 rounded border text-xs font-medium border-neutral-300 bg-white text-neutral-800 focus:outline-none focus:ring-2 focus:ring-primary-300 disabled:bg-neutral-100 disabled:text-neutral-500"
            @change="onLivePollingIntervalChange"
          >
            <option :value="2000">2s</option>
            <option :value="5000">5s</option>
            <option :value="10000">10s</option>
            <option :value="30000">30s</option>
          </select>
          <button
            v-if="selectedExecution?.entity"
            type="button"
            data-testid="run-again-button"
            class="px-3 py-1.5 text-xs font-medium rounded border transition-colors border-neutral-300 bg-white text-neutral-700 hover:bg-neutral-100"
            @click="runAgain"
          >
            Run again
          </button>
          <button
            v-if="showCancelButton"
            type="button"
            data-testid="cancel-execution-button"
            class="px-3 py-1.5 text-xs font-medium rounded border transition-colors"
            :class="
              cancelling
                ? 'border-error-200 bg-error-50 text-error-400 cursor-not-allowed'
                : 'border-error-300 bg-white text-error-700 hover:bg-error-50'
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

      <div class="bg-white border border-line rounded-lg">
        <div class="border-b border-line flex overflow-x-auto">
          <button
            v-for="tab in availableTabs"
            :key="tab"
            type="button"
            :class="['px-4 py-2 text-sm font-medium border-b-2',
                           visibleTab === tab ? 'border-accent-500 text-accent-500' : 'border-transparent text-ink-muted hover:text-ink']"
            @click="onTabSelect(tab)"
          >
            {{ tab === 'diagram' ? 'Diagram' : tab === 'payload' ? 'I/O Payload' : tab === 'transactions' ? 'Transactions' : tab[0].toUpperCase() + tab.slice(1) }}
          </button>
        </div>
        <div class="p-4">
          <ExecutionsDiagramTab v-if="visibleTab === 'diagram'" :diagram="diagram" />
          <ExecutionsPayloadTab
            v-else-if="visibleTab === 'payload'"
            :input="selectedExecution.input"
            :output="selectedExecution.output"
          />
          <ExecutionsMetadataTab
            v-else-if="visibleTab === 'metadata'"
            :metadata="selectedExecution.metadata"
            :execution="selectedExecution"
            :workflow-link="workflowLink"
          />
          <div v-else-if="visibleTab === 'transactions'" class="space-y-4">
            <DslExecutionTimeline
              :transactions="transactions"
              :loading="transactionsLoading"
              :error="transactionsError"
              @select="onTimelineSelect"
            />
            <ExecutionsTransactionsTab
              :transactions="transactions"
              :loading="transactionsLoading"
              :error="transactionsError"
            />
          </div>
          <ExecutionsLogsTab v-else-if="visibleTab === 'logs'" :logs="selectedExecution.logs" />
          <ExecutionsErrorsTab
            v-else-if="visibleTab === 'errors'"
            :errors="selectedExecution.errors"
          />
        </div>
      </div>

      <ExecutionsCancelConfirmationModal
        v-if="showCancelModal"
        :execution-id="selectedExecution?.id"
        :busy="cancelling"
        @confirm="confirmCancel"
        @cancel="dismissCancelModal"
      />
    </template>
  </div>
</template>
