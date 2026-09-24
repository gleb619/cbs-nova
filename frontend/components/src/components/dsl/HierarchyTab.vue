<script setup lang="ts">
import { computed, onBeforeUpdate, ref } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import { useHierarchyHistory } from '../../composables/usePreviewHistory'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'

/**
 * Hierarchy tab — structural snapshot of a definition and its references,
 * distinct from `ExplainTab` (free-form JSON payload + markdown/report
 * rendering) in two ways:
 *
 * 1. Input is a typed options panel (`depth` + `include*` toggles), NOT a
 *    free-form JSON textarea — hierarchy is computed from the construct,
 *    not from runtime arguments.
 * 2. Result rendering is raw JSON in a `<pre>` block for now. Pretty
 *    visualization (tree, indentation) is intentionally deferred until the
 *    backend payload shape is finalized.
 *
 * The component still mirrors `ExplainTab`'s lifecycle (name-change resets,
 * status state, history persistence) so the workbench treats it uniformly.
 */

const props = defineProps<{
  name: string
  type?: ConstructType
  hierarchy: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => Promise<RunnerOutput> | RunnerOutput
}>()

const emit = defineEmits<{
  submit: []
  format: [formatted: string]
}>()

const depth = ref(4)
const includeActivities = ref(true)
const includeSignals = ref(true)
const includeQueries = ref(true)

const output = ref<RunnerOutput | null>(null)
const status = ref<RunnerStatus>('idle')

let previousName = props.name

onBeforeUpdate(() => {
  if (props.name !== previousName) {
    previousName = props.name
    output.value = null
    status.value = 'idle'
  }
})

const history = useHierarchyHistory(() => props.name)
const historyEntries = computed(() => history.entries.value)

function currentOptions(): Record<string, unknown> {
  return {
    depth: depth.value,
    includeActivities: includeActivities.value,
    includeSignals: includeSignals.value,
    includeQueries: includeQueries.value,
  }
}

function normalizeResponse(response: unknown): RunnerOutput {
  if (response && typeof response === 'object' && !Array.isArray(response)) {
    const r = response as Record<string, unknown>
    return {
      ...r,
      result: r.result ?? r.body ?? r.output ?? r.hierarchy ?? r.tree ?? response,
    } as RunnerOutput
  }
  return { result: response }
}

async function run() {
  status.value = 'loading'
  output.value = null
  const payload = currentOptions()
  try {
    const metadata = { startedFrom: 'workbench', endpoint: 'hierarchy' }
    const raw = await props.hierarchy(props.name, payload, metadata)
    output.value = normalizeResponse(raw)
    status.value = 'success'
    history.record({
      name: props.name,
      type: props.type,
      payload,
      output: output.value,
      status: 'success',
    })
  } catch (err) {
    const e = err as {
      data?: Partial<RunnerOutput> & {
        message?: string
        code?: string
      }
      statusMessage?: string
      message?: string
    }
    const data = e.data
    if (data && (Array.isArray(data.errors) || data.message)) {
      output.value = {
        ...(data as RunnerOutput),
        errors: Array.isArray(data.errors)
          ? data.errors
          : [
              {
                message: data.message ?? e.statusMessage ?? e.message ?? 'Request failed',
                code: data.code,
              },
            ],
      }
    } else {
      output.value = {
        errors: [
          {
            message: e.statusMessage ?? e.message ?? 'Request failed',
            code: undefined,
          },
        ],
      }
    }
    status.value = 'failed'
    history.record({
      name: props.name,
      type: props.type,
      payload,
      output: output.value ?? undefined,
      status: 'failed',
    })
  }
}

function rerun(payload: unknown) {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    const p = payload as Record<string, unknown>
    if (typeof p.depth === 'number') depth.value = clampDepth(p.depth)
    if (typeof p.includeActivities === 'boolean') includeActivities.value = p.includeActivities
    if (typeof p.includeSignals === 'boolean') includeSignals.value = p.includeSignals
    if (typeof p.includeQueries === 'boolean') includeQueries.value = p.includeQueries
  }
  void run()
}

function clampDepth(n: number): number {
  if (!Number.isFinite(n)) return 4
  return Math.max(1, Math.min(16, Math.trunc(n)))
}

const resultText = computed(() => {
  if (!output.value) return ''
  if (output.value.errors?.length) {
    return JSON.stringify(output.value, null, 2)
  }
  return JSON.stringify(output.value.result ?? output.value, null, 2)
})

const statusLabel = computed(() => {
  if (status.value === 'loading') return 'running…'
  if (status.value === 'success') return 'done'
  if (status.value === 'failed') return 'failed'
  return 'idle'
})
</script>

<template>
  <div class="h-full p-3 bg-surface">
    <div class="grid gap-3 h-full min-h-0 md:grid-cols-2 grid-cols-1">
      <section
        class="flex flex-col h-full min-h-0 border border-line rounded-sm bg-white"
        data-testid="hierarchy-options-panel"
      >
        <header class="flex items-center justify-between px-3 py-2 border-b border-line">
          <div class="flex items-center gap-2 min-w-0">
            <span class="text-xs font-medium text-ink">Hierarchy options</span>
            <span class="font-mono text-xs text-ink truncate">{{ name }}</span>
            <span class="text-xs text-ink-muted">| hierarchy</span>
          </div>
          <button
            type="button"
            class="text-xs px-3 py-1 bg-accent-500 text-white hover:bg-accent-600 disabled:opacity-50"
            data-testid="run-button"
            :disabled="status === 'loading'"
            @click="run"
          >
            Run
          </button>
        </header>

        <div class="flex-1 min-h-0 overflow-auto p-3 space-y-3" data-testid="hierarchy-options">
          <label class="flex items-center gap-2 text-xs text-ink" data-testid="depth-label">
            <span class="w-24 shrink-0 text-ink-muted">Depth</span>
            <input
              type="range"
              min="1"
              max="16"
              step="1"
              data-testid="depth-input"
              class="flex-1"
              :value="depth"
              @input="depth = clampDepth(Number(($event.target as HTMLInputElement).value))"
            >
            <span class="font-mono w-6 text-right" data-testid="depth-value">{{ depth }}</span>
          </label>

          <label class="flex items-center gap-2 text-xs text-ink" data-testid="include-activities">
            <input v-model="includeActivities" type="checkbox">
            <span>Include activities</span>
          </label>
          <label class="flex items-center gap-2 text-xs text-ink" data-testid="include-signals">
            <input v-model="includeSignals" type="checkbox">
            <span>Include signals</span>
          </label>
          <label class="flex items-center gap-2 text-xs text-ink" data-testid="include-queries">
            <input v-model="includeQueries" type="checkbox">
            <span>Include queries</span>
          </label>
        </div>

        <footer class="flex items-center justify-between px-3 py-2 border-t border-line">
          <span class="text-xs text-ink-muted" data-testid="hierarchy-status"
            >{{ statusLabel }}</span
          >
          <span class="text-xs text-ink-muted font-mono">
            payload {{ JSON.stringify(currentOptions()) }}
          </span>
        </footer>
      </section>

      <section
        class="flex flex-col h-full min-h-0 border border-line rounded-sm bg-white"
        data-testid="hierarchy-result-panel"
      >
        <header class="flex items-center justify-between px-3 py-2 border-b border-line">
          <div class="flex items-center gap-2">
            <span class="text-xs font-medium text-ink">Result · hierarchy</span>
            <span class="text-xs text-ink-muted" data-testid="hierarchy-result-status"
              >{{ statusLabel }}</span
            >
          </div>
        </header>
        <div class="flex-1 min-h-0 overflow-auto p-3">
          <pre
            v-if="resultText"
            data-testid="hierarchy-result-text"
            class="w-full h-full min-h-0 font-mono text-xs leading-relaxed text-ink whitespace-pre-wrap break-words"
          >{{ resultText }}</pre>
          <p v-else class="text-xs text-ink-muted" data-testid="hierarchy-result-empty">
            No result yet. Press Run to fetch the structural snapshot.
          </p>
        </div>
      </section>
    </div>
  </div>
</template>
