<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { groupDiagnosticsByCode } from '../../composables/useDiagnosticCodeRegistry'
import type { DiagnosticsPage, PersistedCompileDiagnostic } from '../../types/dsl'
import ErrorBanner from '../ErrorBanner.vue'

const PAGE_SIZE = 100

const props = defineProps<{
  fetchPage: (params: {
    definition?: string
    limit: number
    offset: number
  }) => Promise<DiagnosticsPage>
  definition: string
}>()

const emit = defineEmits<{
  navigate: [payload: { line: number | null; column: number | null }]
  count: [total: number]
}>()

const page = ref<DiagnosticsPage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const groups = computed(() => groupDiagnosticsByCode(page.value?.items ?? []))

async function loadAll() {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    const first = await props.fetchPage({
      definition: props.definition,
      limit: PAGE_SIZE,
      offset: 0,
    })
    let items = [...first.items]
    let offset = first.items.length
    while (offset < first.total) {
      const next = await props.fetchPage({
        definition: props.definition,
        limit: PAGE_SIZE,
        offset,
      })
      items.push(...next.items)
      offset += next.items.length
      if (next.items.length === 0) break
    }
    page.value = { ...first, items, offset: 0, total: items.length }
    emit('count', items.length)
  } catch (err) {
    error.value = (err as Error).message
    page.value = null
    emit('count', 0)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.definition,
  () => {
    void loadAll()
  },
)

onMounted(() => {
  void loadAll()
})

function formatLocation(item: PersistedCompileDiagnostic): string {
  if (!item.file) return ''
  return [item.file, item.line, item.column]
    .filter((part) => part !== null && part !== undefined)
    .join(':')
}

function onRowClick(item: PersistedCompileDiagnostic) {
  if (item.line == null || item.line <= 0) return
  emit('navigate', { line: item.line, column: item.column ?? null })
}

const severityStyles: Record<string, string> = {
  error: 'bg-red-100 text-red-800',
  warning: 'bg-yellow-100 text-yellow-800',
  info: 'bg-sky-100 text-sky-800',
}

function severityClass(severity: string): string {
  return severityStyles[severity.toLowerCase()] ?? 'bg-gray-100 text-gray-800'
}
</script>

<template>
  <div class="flex flex-col h-full" data-testid="problems-panel">
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="text-base font-semibold text-gray-900">Problems</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Current diagnostics for the selected definition grouped by code.
      </p>
    </div>

    <div v-if="loading" class="px-4 py-3 text-sm text-gray-500" data-testid="problems-loading">
      Loading problems…
    </div>
    <div v-else-if="error" class="px-4 py-3" data-testid="problems-error">
      <ErrorBanner :message="error" retry-label="Retry" @retry="loadAll" />
    </div>
    <div
      v-else-if="groups.length === 0"
      class="px-4 py-3 text-sm text-gray-500"
      data-testid="problems-empty"
    >
      No problems.
    </div>
    <div v-else class="flex-1 overflow-auto">
      <div
        v-for="group in groups"
        :key="group.code"
        class="border-b border-gray-100"
        data-testid="problems-group"
      >
        <div class="px-4 py-2 bg-gray-50 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-sm font-semibold text-gray-900" data-testid="problems-group-title">
              {{ group.info.label }}
            </span>
            <span class="text-xs text-gray-500" data-testid="problems-group-code">
              {{ group.code }}
            </span>
            <span
              class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
              :class="severityClass(group.info.severity)"
              data-testid="problems-group-severity"
            >
              {{ group.info.severity }}
            </span>
          </div>
          <span class="text-xs font-medium text-gray-600" data-testid="problems-group-count">
            {{ group.items.length }}
          </span>
        </div>
        <ul class="divide-y divide-gray-100">
          <li
            v-for="item in group.items"
            :key="item.id"
            class="px-4 py-2 text-sm"
            data-testid="problems-row"
          >
            <button
              v-if="item.line != null && item.line > 0"
              type="button"
              class="w-full text-left bg-transparent border-0 p-0 cursor-pointer"
              data-testid="problems-row-button"
              @click="onRowClick(item)"
            >
              <div class="flex items-start gap-2">
                <span
                  v-if="formatLocation(item)"
                  class="font-mono text-xs text-gray-500 shrink-0"
                  data-testid="problems-row-location"
                >
                  {{ formatLocation(item) }}
                </span>
                <span class="text-gray-700" data-testid="problems-row-message">
                  {{ item.message }}
                </span>
              </div>
            </button>
            <div v-else class="text-gray-700" data-testid="problems-row-message">
              {{ item.message }}
            </div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
