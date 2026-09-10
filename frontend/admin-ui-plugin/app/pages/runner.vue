<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { useRunner } from '@cbs/admin-ui-plugin/composables/useRunner'
import {
  RunnerDefinitionSelector,
  RunnerInputForm,
  RunnerModeSwitcher,
  RunnerOutputPanel,
  RunnerRunConfirmationModal,
  RunnerStatusIndicator,
} from '@cbs/components'
import { useRoute, useRouter } from 'nuxt/app'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { consumeRunAgain } from '../utils/runAgainHandoff'

import type { DefinitionMeta, RunnerMode } from '~/types'

const route = useRoute()
const router = useRouter()

const {
  selectedDefinition,
  mode,
  status,
  formData,
  output,
  baselineOutput,
  showConfirmModal,
  selectDefinition,
  setMode,
  submit,
  confirmRun,
  resetOutput,
  compareWithPrevious,
  clearBaseline,
} = useRunner()

const definitions = ref<DefinitionMeta[]>([])
const loadError = ref<string | null>(null)
const loadingDefinitions = ref(false)

async function loadDefinitions() {
  loadingDefinitions.value = true
  loadError.value = null
  try {
    const api = useDslApi()
    const response = (await api.getDefinitions()) as unknown
    const list = extractDefinitions(response)
    definitions.value = list
    syncFromQuery()
  } catch (err) {
    loadError.value = (err as Error).message ?? 'Failed to load definitions'
  } finally {
    loadingDefinitions.value = false
  }
}

function extractDefinitions(response: unknown): DefinitionMeta[] {
  if (!response) return []
  if (Array.isArray(response)) return response as DefinitionMeta[]
  const obj = response as { definitions?: DefinitionMeta[]; items?: DefinitionMeta[] }
  return obj.definitions ?? obj.items ?? []
}

function syncFromQuery() {
  const nameParam = route.query.name
  if (typeof nameParam === 'string' && nameParam) {
    if (selectedDefinition.value !== nameParam) {
      selectDefinition(nameParam)
      formData.value = {}
    }
  } else if (selectedDefinition.value === null && definitions.value.length > 0) {
    selectDefinition(definitions.value[0].name)
    formData.value = {}
  }

  const modeParam = route.query.mode
  if (modeParam === 'preview' || modeParam === 'run' || modeParam === 'explain') {
    setMode(modeParam)
  }

  // T293 — consume the run-again handoff (if any) left behind by the
  // execution detail page once a definition is selected. The form reset for
  // the newly selected definition happens synchronously above, so consuming
  // here cannot be wiped by it. Consuming is one-shot and a pure no-op when
  // there is no stash, so the normal flow is untouched.
  const name = selectedDefinition.value
  if (name === null) return
  const stashed = consumeRunAgain(name)
  if (stashed !== null && typeof stashed === 'object' && !Array.isArray(stashed)) {
    formData.value = stashed as Record<string, unknown>
  }
}

// The runner page stays mounted across in-app navigations that only change
// the query (back/forward, run-again handoffs), so listen for router
// navigation events instead of watching the route. Navigations to other
// pages are ignored — the page is about to unmount.
const unregisterRouteHook = router.afterEach((to) => {
  if (to.path !== route.path) return
  syncFromQuery()
})
onBeforeUnmount(unregisterRouteHook)

function pushQuery(name: string | null, nextMode: RunnerMode) {
  const query: Record<string, string> = {}
  if (name) query.name = name
  if (nextMode) query.mode = nextMode
  router.replace({ query })
}

function onSelectDefinition(name: string) {
  selectDefinition(name)
  formData.value = {}
  pushQuery(name, mode.value)
}

function onSetMode(next: RunnerMode) {
  setMode(next)
  pushQuery(selectedDefinition.value, next)
}

function onSubmit() {
  confirmRun()
}

async function onConfirmRun() {
  showConfirmModal.value = false
  await submit()
}

function onCancelRun() {
  showConfirmModal.value = false
}

const canCompareWithPrevious = computed(
  () =>
    mode.value === 'preview' &&
    output.value !== null &&
    status.value !== 'loading' &&
    status.value !== 'running',
)

async function onCompareWithPrevious() {
  await compareWithPrevious()
}

const selectedSchema = computed<Record<string, unknown> | undefined>(() => {
  const def = definitions.value.find((d) => d.name === selectedDefinition.value)
  return def?.inputSchema
})

onMounted(() => {
  loadDefinitions()
})
</script>

<template>
  <div class="flex flex-col gap-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-neutral-900">Runner</h1>
        <p class="text-sm text-neutral-600">Preview, run, or explain DSL definitions.</p>
      </div>
      <div class="flex items-center gap-3">
        <RunnerStatusIndicator :status="status" />
      </div>
    </header>

    <div class="flex flex-wrap items-end gap-4 bg-white border border-neutral-200 rounded-xl p-4">
      <div class="min-w-[16rem] flex-1">
        <RunnerDefinitionSelector
          :definitions="definitions"
          :model-value="selectedDefinition"
          @update:model-value="onSelectDefinition"
        />
        <p v-if="loadingDefinitions" class="text-xs text-neutral-500 mt-1">Loading definitions…</p>
        <p v-else-if="loadError" class="text-xs text-error-600 mt-1">{{ loadError }}</p>
      </div>

      <RunnerModeSwitcher :model-value="mode" @update:model-value="onSetMode" />

      <div class="ml-auto flex gap-2">
        <button
          v-if="mode === 'preview'"
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium border border-neutral-300 text-neutral-700 hover:bg-neutral-100 disabled:opacity-50"
          :disabled="!canCompareWithPrevious"
          data-testid="compare-with-previous-button"
          @click="onCompareWithPrevious"
        >
          Compare with previous
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium border border-neutral-300 text-neutral-700 hover:bg-neutral-100 disabled:opacity-50"
          :disabled="!selectedDefinition || status === 'loading' || status === 'running'"
          @click="resetOutput"
        >
          Reset
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 disabled:cursor-not-allowed"
          :disabled="!selectedDefinition || status === 'loading' || status === 'running'"
          @click="onSubmit"
        >
          {{ mode === 'run' ? 'Run' : mode === 'explain' ? 'Explain' : 'Preview' }}
        </button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <section class="bg-white border border-neutral-200 rounded-xl p-5">
        <h2 class="text-sm font-semibold text-neutral-700 mb-4">Input</h2>
        <RunnerInputForm
          :schema="selectedSchema"
          :model-value="formData"
          @update:model-value="(val) => (formData = val)"
        />
      </section>

      <section class="bg-white border border-neutral-200 rounded-xl p-5">
        <h2 class="text-sm font-semibold text-neutral-700 mb-4">Output</h2>
        <RunnerOutputPanel
          :output="output"
          :mode="mode"
          :status="status"
          :baseline-output="baselineOutput"
          @clear-baseline="clearBaseline"
        />
      </section>

      <section
        v-if="mode === 'preview'"
        class="bg-white border border-neutral-200 rounded-xl p-5 lg:col-span-2"
      >
        <h2 class="text-sm font-semibold text-neutral-700 mb-4">Faking external calls</h2>
        <p class="text-sm text-neutral-600">
          Preview requests no longer accept per-request mocks. To fake an external call's response,
          configure it ahead of time via
          <code class="text-xs bg-neutral-100 px-1 py-0.5 rounded">cbs.nova.fakes.config.entries</code>
          in <code class="text-xs bg-neutral-100 px-1 py-0.5 rounded">application.yml</code>
          (entries shaped
          <code class="text-xs bg-neutral-100 px-1 py-0.5 rounded"
            >{ type: helper, code: &lt;helperName&gt;, response: &lt;payload&gt; }</code
          >).
        </p>
      </section>
    </div>

    <RunnerRunConfirmationModal
      :show="showConfirmModal"
      :payload="formData"
      @confirm="onConfirmRun"
      @cancel="onCancelRun"
    />
  </div>
</template>
