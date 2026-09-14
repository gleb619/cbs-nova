<script setup lang="ts">
import { computed } from 'vue'
import type { PreviewHistoryEntry, RunnerStatus } from '../../types/runner'
import ResultTab from '../runner/ResultTab.vue'

const props = defineProps<{
  history: PreviewHistoryEntry[]
  selectedId: string | null
  status: RunnerStatus
}>()

const emit = defineEmits<{
  'update:selectedId': [id: string | null]
  rerun: [payload: unknown]
}>()

// Effective selection ignores stale ids (entry no longer in history) without
// a watch — the computed self-corrects on every render when history mutates.
const effectiveSelectedId = computed(() => {
  if (!props.selectedId) return null
  return props.history.some((entry) => entry.id === props.selectedId) ? props.selectedId : null
})

const selectedEntry = computed(() =>
  effectiveSelectedId.value
    ? props.history.find((entry) => entry.id === effectiveSelectedId.value)
    : undefined,
)

function onView(id: string) {
  emit('update:selectedId', id)
}

function onBack() {
  emit('update:selectedId', null)
}

function onRerun(entry: PreviewHistoryEntry) {
  emit('rerun', entry.payload)
}

function payloadSummary(payload: unknown): string {
  let text: string
  try {
    text = JSON.stringify(payload)
  } catch {
    text = String(payload)
  }
  return text.length > 80 ? `${text.slice(0, 80)}…` : text
}

function entryTime(entry: PreviewHistoryEntry): string {
  const date = new Date(entry.startedAt)
  return Number.isNaN(date.getTime()) ? entry.startedAt : date.toLocaleString()
}
</script>

<template>
  <div class="h-full overflow-auto p-3" data-testid="history-panel">
    <p v-if="history.length === 0" class="text-sm text-gray-500" data-testid="history-empty">
      No runs yet.
    </p>

    <div v-else-if="selectedEntry" class="space-y-2" data-testid="history-detail">
      <div class="flex items-center justify-between gap-2">
        <button
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface"
          data-testid="history-back"
          @click="onBack"
        >
          Back
        </button>
        <div class="flex items-center gap-2 min-w-0">
          <span
            class="text-xs shrink-0"
            :class="selectedEntry.status === 'success' ? 'text-success-600' : 'text-danger'"
          >
            {{ selectedEntry.status === 'success' ? 'Success' : 'Failed' }}
          </span>
          <span class="text-xs text-ink-muted truncate">{{ entryTime(selectedEntry) }}</span>
        </div>
        <button
          type="button"
          class="text-xs px-2 py-1 bg-accent-500 text-white hover:bg-accent-600 disabled:opacity-50 shrink-0"
          data-testid="history-run-again"
          :disabled="status === 'loading' || status === 'running'"
          @click="onRerun(selectedEntry)"
        >
          Run again
        </button>
      </div>

      <div>
        <p class="text-xs font-medium text-ink mb-1">Input</p>
        <pre
          class="font-mono text-xs leading-relaxed text-ink whitespace-pre-wrap break-words bg-surface p-2"
          data-testid="history-detail-payload"
        >{{ JSON.stringify(selectedEntry.payload, null, 2) }}</pre>
      </div>

      <div>
        <p class="text-xs font-medium text-ink mb-1">Result</p>
        <div v-if="selectedEntry.output?.errors?.length" class="space-y-1">
          <p
            v-for="(err, i) in selectedEntry.output.errors"
            :key="i"
            class="text-xs font-mono text-danger whitespace-pre-wrap"
          >
            {{ err.message }}
          </p>
        </div>
        <ResultTab v-else :result="selectedEntry.output?.result" />
      </div>
    </div>

    <ul v-else class="space-y-1" data-testid="history-list">
      <li
        v-for="entry in history"
        :key="entry.id"
        class="flex items-center gap-2 border border-line px-2 py-1"
        data-testid="history-item"
      >
        <span
          class="text-xs shrink-0"
          :class="entry.status === 'success' ? 'text-success-600' : 'text-danger'"
        >
          {{ entry.status === 'success' ? 'OK' : 'ERR' }}
        </span>
        <span class="text-xs text-ink-muted shrink-0">{{ entryTime(entry) }}</span>
        <span class="flex-1 min-w-0 font-mono text-xs text-ink truncate">
          {{ payloadSummary(entry.payload) }}
        </span>
        <button
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface shrink-0"
          data-testid="history-view"
          @click="onView(entry.id)"
        >
          View
        </button>
        <button
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface shrink-0"
          data-testid="history-rerun"
          :disabled="status === 'loading' || status === 'running'"
          @click="onRerun(entry)"
        >
          Run again
        </button>
      </li>
    </ul>
  </div>
</template>
