<script setup lang="ts">
import { RunnerDiffLine, useDiffLines } from '@cbs/components'
import { onMounted, ref } from 'vue'

export interface DefinitionHistoryEntry {
  timestamp: string
  timestampMillis: number
  sizeBytes: number
  lastModifiedMillis: number
}

export interface HistoryDiffResponse {
  name: string
  timestamp: string
  before: string | null
  after: string
  hunks: Array<{
    beforeStart: number
    beforeLines: number
    afterStart: number
    afterLines: number
    lines: string[]
  }>
  truncated: boolean
}

const props = defineProps<{
  name: string
  listHistory: (name: string) => Promise<DefinitionHistoryEntry[]>
  getEntry: (name: string, timestamp: string) => Promise<unknown>
  getDiff: (name: string, timestamp: string) => Promise<HistoryDiffResponse>
  restore: (name: string, timestamp: string) => Promise<unknown>
}>()

const emit = defineEmits<{
  restored: [timestamp: string]
}>()

const entries = ref<DefinitionHistoryEntry[]>([])
const listLoading = ref(false)
const listError = ref<string | null>(null)

const selectedTimestamp = ref<string | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const entryContent = ref<unknown | null>(null)
const diff = ref<HistoryDiffResponse | null>(null)

const diffBefore = ref('')
const diffAfter = ref('')
const diffLines = useDiffLines(
  () => diffBefore.value,
  () => diffAfter.value,
)

const hasDiff = computed(() => diff.value !== null && !detailLoading.value)
const noPublishedBaseline = computed(() => diff.value?.before === null)
const entrySummary = computed(() => {
  const entry = entryContent.value as { type?: string; version?: string } | null
  if (!entry) return ''
  return [entry.type, entry.version].filter(Boolean).join(' · ')
})

async function loadEntries(): Promise<void> {
  if (!props.name) {
    entries.value = []
    return
  }
  listLoading.value = true
  listError.value = null
  try {
    entries.value = (await props.listHistory(props.name)) ?? []
  } catch (err) {
    listError.value = (err as Error).message
    entries.value = []
  } finally {
    listLoading.value = false
  }
}

async function selectEntry(timestamp: string): Promise<void> {
  if (!props.name || detailLoading.value) return
  selectedTimestamp.value = timestamp
  detailLoading.value = true
  detailError.value = null
  diff.value = null
  entryContent.value = null
  try {
    const [entry, diffResult] = await Promise.all([
      props.getEntry(props.name, timestamp),
      props.getDiff(props.name, timestamp),
    ])
    // Ignore stale responses after a quick re-click on another row.
    if (selectedTimestamp.value !== timestamp) return
    entryContent.value = entry
    diff.value = diffResult
    diffBefore.value = diffResult.before ?? ''
    diffAfter.value = diffResult.after
  } catch (err) {
    detailError.value = (err as Error).message
  } finally {
    if (selectedTimestamp.value === timestamp) {
      detailLoading.value = false
    }
  }
}

// Two-step restore: the button arms first, the second click executes.
const restoreArmed = ref(false)
const restoreBusy = ref(false)
const restoreError = ref<string | null>(null)

function onRestoreClick(): void {
  if (!hasDiff.value || restoreBusy.value) return
  if (!restoreArmed.value) {
    restoreArmed.value = true
    return
  }
  void confirmRestore()
}

async function confirmRestore(): Promise<void> {
  const timestamp = selectedTimestamp.value
  if (!props.name || !timestamp || restoreBusy.value) return
  restoreBusy.value = true
  restoreError.value = null
  try {
    await props.restore(props.name, timestamp)
    restoreArmed.value = false
    emit('restored', timestamp)
    await loadEntries()
  } catch (err) {
    restoreError.value = (err as Error).message
  } finally {
    restoreBusy.value = false
  }
}

function cancelRestore(): void {
  if (restoreBusy.value) return
  restoreArmed.value = false
}

// The panel is remounted (via `:key` on the parent) whenever the construct
// name changes, so mounting is the only moment that needs a load — a fresh
// mount also starts from clean selection/restore state, which is exactly the
// reset the previous prop watcher performed.
onMounted(() => {
  void loadEntries()
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="history-panel">
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="text-base font-semibold text-gray-900">Publish history</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Snapshots taken before each publish — click an entry to compare it with the current
        published definition before restoring.
      </p>
    </div>

    <div class="flex-1 overflow-y-auto">
      <div v-if="listLoading" class="px-4 py-3 text-sm text-gray-500" data-testid="history-loading">
        Loading history…
      </div>
      <div v-else-if="listError" class="px-4 py-3 text-sm text-red-600" data-testid="history-error">
        {{ listError }}
      </div>
      <div
        v-else-if="!name || entries.length === 0"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="history-empty"
      >
        No publish history for this construct yet. History entries appear after the first
        re-publish.
      </div>

      <ul v-else class="divide-y divide-gray-100" data-testid="history-entry-list">
        <li v-for="entry in entries" :key="entry.timestamp">
          <button
            type="button"
            class="w-full text-left px-4 py-2.5 hover:bg-gray-50"
            :class="selectedTimestamp === entry.timestamp ? 'bg-blue-50' : ''"
            :data-timestamp="entry.timestamp"
            data-testid="history-entry-row"
            @click="selectEntry(entry.timestamp)"
          >
            <div class="text-sm font-medium text-gray-900">
              {{ new Date(entry.timestampMillis).toLocaleString() }}
            </div>
            <div class="text-xs text-gray-500">
              {{ entry.sizeBytes }} bytes · modified
              {{ new Date(entry.lastModifiedMillis).toLocaleString() }}
            </div>
          </button>
        </li>
      </ul>

      <div v-if="selectedTimestamp" class="border-t border-gray-200" data-testid="history-detail">
        <div class="px-4 py-3">
          <h3 class="text-sm font-semibold text-gray-900 mb-2">Diff vs current published</h3>

          <div
            v-if="detailLoading"
            class="text-sm text-gray-500"
            data-testid="history-detail-loading"
          >
            Loading diff…
          </div>
          <div
            v-else-if="detailError"
            class="text-sm text-red-600"
            data-testid="history-detail-error"
          >
            {{ detailError }}
          </div>
          <template v-else-if="diff">
            <p
              v-if="entrySummary"
              class="text-xs text-gray-500 mb-2"
              data-testid="history-entry-content"
            >
              Entry content: {{ entrySummary }}
            </p>
            <div
              v-if="noPublishedBaseline"
              class="text-xs text-yellow-800 bg-yellow-50 border border-yellow-200 rounded p-2 mb-2"
              data-testid="history-no-published"
            >
              No published definition exists for this construct. Restoring this entry would publish
              it outright.
            </div>
            <div
              v-if="diff.truncated"
              class="text-xs text-gray-600 bg-gray-100 rounded p-2 mb-2"
              data-testid="history-diff-truncated"
            >
              Diff is large — showing the first part only.
            </div>
            <div
              class="bg-gray-50 border border-gray-200 rounded-lg p-2 overflow-auto max-h-[50vh]"
              data-testid="history-diff-view"
            >
              <RunnerDiffLine
                v-for="(line, index) in diffLines"
                :key="index"
                :kind="line.kind"
                :text="line.text"
              />
            </div>
          </template>

          <div class="flex items-center gap-2 mt-3">
            <button
              v-if="!restoreArmed"
              type="button"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              :disabled="!hasDiff"
              data-testid="history-restore-button"
              @click="onRestoreClick"
            >
              Restore this version…
            </button>
            <template v-else>
              <button
                type="button"
                class="px-3 py-1.5 text-sm rounded text-white bg-red-600 hover:bg-red-700 disabled:opacity-50"
                :disabled="restoreBusy"
                data-testid="history-restore-confirm"
                @click="onRestoreClick"
              >
                {{ restoreBusy ? 'Restoring…' : 'Confirm restore' }}
              </button>
              <button
                type="button"
                class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100"
                :disabled="restoreBusy"
                data-testid="history-restore-cancel"
                @click="cancelRestore"
              >
                Cancel
              </button>
            </template>
          </div>
          <p
            v-if="restoreError"
            class="text-xs text-red-600 mt-2"
            data-testid="history-restore-error"
          >
            {{ restoreError }}
          </p>
          <p v-if="!hasDiff && !detailLoading && !detailError" class="text-xs text-gray-500 mt-2">
            Review the diff above before restoring.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
