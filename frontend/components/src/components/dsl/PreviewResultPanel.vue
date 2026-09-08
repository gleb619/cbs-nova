<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { type ConstructType, useConstructSchema } from '../../composables/useConstructSchema'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import ResultTab from '../runner/ResultTab.vue'
import SchemaForm from './SchemaForm.vue'

const props = defineProps<{
  name: string
  type?: ConstructType
  output: RunnerOutput | null
  status: RunnerStatus
  endpoint?: 'preview' | 'run' | 'explain'
}>()

const activeTab = ref<'result' | 'output'>('result')

const { outputSchema, outputType, loading, error, hasOutputSchema } = useConstructSchema({
  name: () => props.name,
  type: () => props.type,
})

const outputFormValue = computed(() => {
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

function setTab(next: 'result' | 'output') {
  activeTab.value = next
}

watch(
  () => props.name,
  () => {
    activeTab.value = 'result'
  },
)
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
        <div class="flex items-center border border-line rounded-sm overflow-hidden shrink-0">
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="activeTab === 'result' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="tab-result"
            @click="setTab('result')"
          >
            Result
          </button>
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="activeTab === 'output' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="tab-output"
            @click="setTab('output')"
          >
            Output schema
          </button>
        </div>
        <span
          class="text-xs font-mono"
          :class="{
            'text-ink-muted': status === 'idle',
            'text-accent-500': status === 'success' || status === 'running',
            'text-danger': status === 'failed',
          }"
        >
          <span v-if="status === 'loading'">running…</span>
          <span v-else-if="status === 'success'">done</span>
          <span v-else-if="status === 'failed'">failed</span>
          <span v-else>idle</span>
        </span>
      </div>
    </header>

    <div class="flex-1 min-h-0 overflow-auto p-3">
      <div v-if="activeTab === 'result'">
        <div v-if="status === 'loading'" class="text-xs text-ink-muted font-mono">
          request in flight…
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
        <ResultTab v-else :result="output?.result" />
      </div>

      <div v-else-if="activeTab === 'output'">
        <div v-if="loading" class="text-xs text-ink-muted">Loading schema…</div>
        <div v-else-if="error" class="text-xs text-danger">Schema unavailable: {{ error }}</div>
        <div v-else-if="!outputSchema || !hasOutputSchema" class="text-xs text-ink-muted">
          Output schema unavailable.
        </div>
        <div v-else>
          <SchemaForm :schema="outputSchema" :model-value="outputFormValue" readonly />
        </div>
      </div>
    </div>
  </section>
</template>
