<script setup lang="ts">
import { computed, nextTick, onBeforeUpdate, ref, watch } from 'vue'
import { type ConstructType, useConstructSchema } from '../../composables/useConstructSchema'
import { useToast } from '../../composables/useToast'
import type {
  ExplainReportNode,
  PreviewHistoryEntry,
  RunnerOutput,
  RunnerStatus,
} from '../../types/runner'
import ExplainMarkdownView from '../runner/ExplainMarkdownView.vue'
import ExplainRawView from '../runner/ExplainRawView.vue'
import ExplainReportChain from '../runner/ExplainReportChain.vue'
import ExplainReportList from '../runner/ExplainReportList.vue'
import ResultTab from '../runner/ResultTab.vue'
import PreviewHistoryPanel from './PreviewHistoryPanel.vue'
import SchemaForm from './SchemaForm.vue'

const props = withDefaults(
  defineProps<{
    name: string
    type?: ConstructType
    output: RunnerOutput | null
    status: RunnerStatus
    endpoint?: 'preview' | 'run' | 'explain'
    history?: PreviewHistoryEntry[]
  }>(),
  { history: () => [] },
)

const emit = defineEmits<{
  rerun: [payload: unknown]
  clearHistory: []
  format: [formatted: string]
}>()

let previousName = props.name
let previousType = props.type
let previousOutput: RunnerOutput | null = props.output

type PanelMode = 'form' | 'json' | 'schema' | 'history' | 'view' | 'raw'

const isExplain = computed(() => props.endpoint === 'explain')

const toast = useToast()

const hasExplainContent = computed(() => {
  if (props.output?.explainReport) return true
  return !!props.output?.description
})
const explainMarkdown = computed(
  () => props.output?.explainReport?.markdown || props.output?.description,
)

function explainReportToMarkdown(report: ExplainReportNode): string {
  const lines: string[] = []
  function walk(node: ExplainReportNode, depth: number) {
    const heading = '#'.repeat(Math.min(depth + 1, 6))
    lines.push(`${heading} ${node.name}`)
    if (node.description) {
      lines.push(node.description)
    }
    if (node.markdown) {
      lines.push(node.markdown)
    }
    for (const child of node.children) walk(child, depth + 1)
  }
  walk(report, 0)
  return lines.join('\n\n')
}

function initialMode(): PanelMode {
  if (isExplain.value) return 'view'
  return 'json'
}

const mode = ref<PanelMode>(initialMode())
const selectedEntryId = ref<string | null>(null)
const showFullscreen = ref(false)

const { outputSchema, outputType, loading, error, hasOutputSchema, events } = useConstructSchema({
  name: () => props.name,
  type: () => props.type,
  mode: () => (props.endpoint === 'explain' ? 'explain' : undefined),
})

const schemaViewPayload = computed(() => outputSchema.value)

const effectiveMode = computed<PanelMode>(() => {
  if (isExplain.value) {
    if (mode.value === 'form' || mode.value === 'json') return 'view'
    return mode.value
  }
  if (mode.value === 'view' || mode.value === 'raw') return 'json'
  if (mode.value === 'schema' && schemaViewPayload.value == null) return 'json'
  return mode.value
})

const outputFormValue = computed(() => {
  const result = props.output?.result
  if (result !== undefined && result !== null) return result

  const s = outputSchema.value
  if (!s || !hasOutputSchema.value) return {}
  if (s.type === 'object' && s.properties) {
    const initial: Record<string, unknown> = {}
    for (const [key, propSchema] of Object.entries(s.properties)) {
      if (propSchema && typeof propSchema === 'object' && 'default' in propSchema) {
        initial[key] = propSchema.default
      }
    }
    return initial
  }
  return {}
})

const transient = ref<{ text: string; type: 'danger' | 'success' } | null>(null)
let transientTimeout: ReturnType<typeof setTimeout> | null = null

function setTransient(message: { text: string; type: 'danger' | 'success' }) {
  if (transientTimeout) clearTimeout(transientTimeout)
  transient.value = message
  transientTimeout = setTimeout(() => {
    transient.value = null
  }, 1500)
}

function clearTransient() {
  if (transientTimeout) clearTimeout(transientTimeout)
  transient.value = null
}

function formatResult() {
  const raw = props.output?.result
  if (raw === undefined || raw === null) return
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    const pretty = `${JSON.stringify(parsed, null, 2)}\n`
    emit('format', pretty)
    setTransient({ text: 'Formatted', type: 'success' })
  } catch (e) {
    setTransient({ text: `Format failed: ${(e as Error).message}`, type: 'danger' })
  }
}

function copyableText(): string | undefined {
  if (effectiveMode.value === 'view') return explainMarkdown.value
  if (effectiveMode.value === 'form') {
    const result = props.output?.result
    if (result !== undefined && result !== null) return JSON.stringify(result, null, 2)
    if (outputSchema.value) return JSON.stringify(outputFormValue.value, null, 2)
    return undefined
  }
  return undefined
}

async function copyExplain() {
  const report = props.output?.explainReport
  const text = report ? explainReportToMarkdown(report) : explainMarkdown.value
  if (!text) return
  try {
    if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
    await navigator.clipboard.writeText(text)
    toast.success('Copied to clipboard!')
  } catch (e) {
    toast.error(`Copy failed: ${(e as Error).message}`)
  }
}

async function copyFormOutput() {
  const text = copyableText()
  if (!text) return
  try {
    if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
    await navigator.clipboard.writeText(text)
    toast.success('Copied to clipboard!')
  } catch (e) {
    toast.error(`Copy failed: ${(e as Error).message}`)
  }
}

function closeFullscreen() {
  showFullscreen.value = false
}

function setMode(next: PanelMode) {
  if (next === mode.value) return
  mode.value = next
  if (next !== 'history') {
    selectedEntryId.value = null
  }
}

function toggleHistory() {
  setMode(mode.value === 'history' ? 'json' : 'history')
}

const effectiveSelectedEntry = computed(() => {
  if (!selectedEntryId.value) return undefined
  return props.history.find((entry) => entry.id === selectedEntryId.value)
})

const footerStatus = computed(() => {
  if (transient.value) return transient.value
  if (props.status === 'loading' || props.status === 'running') {
    return { text: 'Running…', type: 'accent' as const }
  }
  if (props.status === 'failed') {
    const count = props.output?.errors?.length ?? 0
    return { text: count > 0 ? `Failed — ${count} error(s)` : 'Failed', type: 'danger' as const }
  }
  if (props.status === 'success') return { text: 'Done', type: 'success' as const }
  if (effectiveMode.value === 'history') {
    if (effectiveSelectedEntry.value)
      return { text: 'History — stored result', type: 'muted' as const }
    return { text: `History — ${props.history.length} run(s)`, type: 'muted' as const }
  }
  if (effectiveMode.value === 'view') return { text: 'Explain markdown', type: 'muted' as const }
  if (effectiveMode.value === 'raw') return { text: 'Report JSON', type: 'muted' as const }
  if (effectiveMode.value === 'form') {
    if (loading.value) return { text: 'Loading schema…', type: 'muted' as const }
    if (error.value) return { text: `Schema unavailable: ${error.value}`, type: 'danger' as const }
    if (!hasOutputSchema.value) return { text: 'Output schema unavailable', type: 'muted' as const }
    return { text: 'Form output', type: 'muted' as const }
  }
  if (effectiveMode.value === 'schema') {
    return { text: 'Output schema', type: 'muted' as const }
  }
  return { text: 'Result JSON', type: 'muted' as const }
})

const canFormat = computed(() => {
  if (effectiveMode.value !== 'json') return false
  if (props.status === 'loading') return false
  const raw = props.output?.result
  return raw !== undefined && raw !== null
})

const showCopyButton = computed(() => {
  return effectiveMode.value === 'view' || effectiveMode.value === 'form'
})
const showShowButton = computed(() => {
  return isExplain.value && effectiveMode.value === 'view'
})
const showFormatButton = computed(() => {
  return !showCopyButton.value && !showShowButton.value
})
const canCopy = computed(() => {
  if (effectiveMode.value === 'view') return hasExplainContent.value && props.status !== 'loading'
  if (effectiveMode.value === 'form') return props.status !== 'loading'
  return false
})
const canShow = computed(() => {
  return hasExplainContent.value && props.status !== 'loading'
})

watch(showFullscreen, (open) => {
  if (!open) return
  const handler = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      showFullscreen.value = false
    }
  }
  document.addEventListener('keydown', handler)
  void nextTick(() => {
    const el = document.querySelector('[data-testid="fullscreen-markdown"]')
    if (el instanceof HTMLElement) el.focus()
  })
  return () => document.removeEventListener('keydown', handler)
})

const showFormJsonToggles = computed(() => !isExplain.value)

onBeforeUpdate(() => {
  if (props.name !== previousName || props.type !== previousType) {
    previousName = props.name
    previousType = props.type
    events.emit('change', { name: props.name, type: props.type })
    mode.value = initialMode()
    selectedEntryId.value = null
  }
  if (props.output !== previousOutput) {
    previousOutput = props.output
    clearTransient()
  }
})
</script>

<template>
  <section class="flex flex-col h-full min-h-0 border border-line rounded-sm bg-white">
    <header class="flex items-center justify-between px-3 py-2 border-b border-line">
      <div class="flex items-center gap-2 min-w-0">
        <span class="text-xs font-medium text-ink">Output</span>
        <span class="font-mono text-xs text-ink truncate">{{ outputType ?? name }}</span>
        <span class="text-xs text-ink-muted">| {{ endpoint ?? 'preview' }}</span>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <button
          type="button"
          class="text-xs px-3 py-1 bg-accent-500 text-white hover:bg-accent-600 disabled:opacity-50"
          data-testid="history-button"
          @click="toggleHistory"
        >
          <span>{{ mode === 'history' ? 'Close' : 'History' }}</span>
        </button>
      </div>
    </header>

    <div class="flex-1 min-h-0 overflow-hidden">
      <div v-if="effectiveMode === 'view'" class="h-full overflow-auto p-3">
        <ExplainReportChain v-if="output?.explainReport" :report="output.explainReport" />
        <ExplainReportList v-if="output?.explainReport" :report="output.explainReport" />
        <ExplainMarkdownView v-else :markdown="explainMarkdown" mermaid />
      </div>

      <div v-else-if="effectiveMode === 'raw'" class="h-full overflow-auto p-3">
        <ExplainRawView :report="output?.explainReport" :markdown="explainMarkdown" />
      </div>

      <div v-else-if="effectiveMode === 'json'" class="h-full overflow-auto p-3">
        <div v-if="status === 'loading'" class="space-y-2" data-testid="result-skeleton">
          <div v-for="i in 6" :key="i" class="h-3 bg-gray-200 rounded animate-pulse" />
        </div>
        <div v-else-if="output?.errors?.length" class="space-y-1">
          <p
            v-for="(err, i) in output.errors"
            :key="i"
            class="text-xs font-mono text-danger whitespace-pre-wrap"
          >
            {{ err.message }}
          </p>
        </div>
        <template v-else>
          <ExplainMarkdownView
            v-if="output?.explainReport?.markdown || output?.description || output?.mermaidDiagram"
            :markdown="explainMarkdown"
          />
          <ResultTab :result="output?.result" />
        </template>
      </div>

      <div v-else-if="effectiveMode === 'form'" class="h-full overflow-auto p-3">
        <div v-if="loading" class="space-y-2" data-testid="schema-skeleton">
          <div v-for="i in 6" :key="i" class="h-3 bg-gray-200 rounded animate-pulse" />
        </div>
        <div v-else-if="error" class="text-xs text-danger">Schema unavailable: {{ error }}</div>
        <div v-else-if="!outputSchema || !hasOutputSchema" class="text-xs text-ink-muted">
          Output schema unavailable.
        </div>
        <div v-else>
          <SchemaForm :schema="outputSchema" :model-value="outputFormValue" readonly />
        </div>
      </div>

      <div v-else-if="effectiveMode === 'schema'" class="h-full overflow-auto p-3">
        <pre
          data-testid="schema-view"
          class="w-full h-full min-h-0 overflow-auto font-mono text-xs leading-relaxed text-ink bg-white whitespace-pre-wrap break-words"
        >{{ JSON.stringify(schemaViewPayload, null, 2) }}</pre>
      </div>

      <PreviewHistoryPanel
        v-else
        :history="history"
        :selected-id="selectedEntryId"
        :status="status"
        @update:selected-id="selectedEntryId = $event"
        @rerun="emit('rerun', $event)"
      />
    </div>

    <footer class="flex items-center justify-between px-3 py-2 border-t border-line gap-3">
      <div class="flex items-center shrink-0 gap-2">
        <div
          v-if="showFormJsonToggles"
          class="flex items-center border border-line rounded-sm overflow-hidden"
        >
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="effectiveMode === 'form' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-form"
            @click="setMode('form')"
          >
            Form
          </button>
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="effectiveMode === 'json' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-json"
            @click="setMode('json')"
          >
            JSON
          </button>
        </div>

        <div
          v-else
          class="flex items-center border border-line rounded-sm overflow-hidden"
          data-testid="explain-mode-toggles"
        >
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="effectiveMode === 'view' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-view"
            @click="setMode('view')"
          >
            View
          </button>
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="effectiveMode === 'raw' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-raw"
            @click="setMode('raw')"
          >
            Raw
          </button>
        </div>

        <button
          v-if="hasOutputSchema"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          :class="effectiveMode === 'schema' ? 'bg-accent-500 text-white' : 'text-ink'"
          data-testid="mode-schema"
          @click="setMode('schema')"
        >
          Schema
        </button>
      </div>

      <div class="flex-1 min-w-0 text-center">
        <span
          data-testid="result-status"
          class="text-xs truncate"
          :class="{
            'text-accent-500': footerStatus.type === 'accent',
            'text-danger': footerStatus.type === 'danger',
            'text-success-600': footerStatus.type === 'success',
            'text-ink-muted': footerStatus.type === 'muted',
          }"
        >
          {{ footerStatus.text }}
        </span>
      </div>

      <div class="flex items-center gap-2 shrink-0">
        <button
          v-if="mode === 'history'"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          data-testid="clear-history"
          :disabled="history.length === 0"
          @click="emit('clearHistory')"
        >
          Clear
        </button>
        <button
          v-if="showShowButton"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          data-testid="show-result"
          :disabled="!canShow"
          @click="showFullscreen = true"
        >
          Show
        </button>
        <button
          v-if="showCopyButton"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          data-testid="copy-result"
          :disabled="!canCopy"
          @click="effectiveMode === 'form' ? copyFormOutput() : copyExplain()"
        >
          Copy
        </button>
        <button
          v-if="showFormatButton"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          data-testid="format-result"
          :disabled="!canFormat"
          @click="formatResult"
        >
          Format
        </button>
      </div>
    </footer>
    <div
      v-if="showFullscreen"
      class="fixed inset-0 z-50 flex flex-col bg-white"
      data-testid="fullscreen-markdown"
      tabindex="-1"
    >
      <header class="flex items-center justify-between px-4 py-3 border-b border-line bg-white">
        <span class="text-sm font-medium text-ink">Detailed view</span>
        <button
          type="button"
          class="text-xs px-3 py-1 border border-line hover:bg-surface"
          data-testid="close-fullscreen"
          @click="closeFullscreen"
        >
          Close
        </button>
      </header>
      <div class="flex-1 min-h-0 overflow-auto p-6 bg-white">
        <ExplainReportList v-if="output?.explainReport" :report="output.explainReport" />
        <ExplainMarkdownView v-else :markdown="explainMarkdown" mermaid />
      </div>
    </div>
  </section>
</template>
