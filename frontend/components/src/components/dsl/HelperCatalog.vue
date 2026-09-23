<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { HelperCatalogEntry, HelpersResponse } from '../../types/dsl'

export interface HelperCatalogFetchParams {
  search: string
  mode: string
  offset: number
  limit: number
}

const props = defineProps<{
  fetch: (params: HelperCatalogFetchParams) => Promise<HelpersResponse>
}>()

const DEFAULT_LIMIT = 100
const MODES = [
  { value: 'exact', label: 'Exact' },
  { value: 'cosine', label: 'Cosine' },
  { value: 'fuzzy', label: 'Fuzzy' },
]

const search = ref('')
const mode = ref('exact')
const helpers = ref<HelperCatalogEntry[]>([])
const total = ref(0)
const offset = ref(0)
const limit = ref(DEFAULT_LIMIT)
const loading = ref(false)
const error = ref<string | null>(null)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

const hasMore = computed(() => helpers.value.length < total.value)
const showingText = computed(() => {
  const count = helpers.value.length
  if (total.value === 0) return ''
  return `${count} / ${total.value}`
})

async function load(append = false) {
  if (!append) {
    offset.value = 0
  }
  loading.value = true
  error.value = null
  try {
    const result = await props.fetch({
      search: search.value,
      mode: mode.value,
      offset: offset.value,
      limit: limit.value,
    })
    const items = result.items ?? []
    total.value = result.total ?? 0
    limit.value = result.limit ?? DEFAULT_LIMIT
    if (append) {
      helpers.value.push(...items)
    } else {
      helpers.value = items
    }
  } catch (err) {
    error.value = (err as Error).message ?? 'Failed to load helpers'
    if (!append) helpers.value = []
  } finally {
    loading.value = false
  }
}

function scheduleLoad() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    debounceTimer = null
    void load(false)
  }, 250)
}

function loadMore() {
  if (loading.value || !hasMore.value) return
  offset.value = helpers.value.length
  void load(true)
}

watch([search, mode], () => scheduleLoad(), { deep: true })

onMounted(() => load(false))
</script>

<template>
  <div data-testid="helper-catalog" class="flex flex-col h-full overflow-hidden">
    <div class="px-4 py-3 border-b border-gray-800 flex items-center gap-3">
      <input
        v-model="search"
        data-testid="helper-catalog-search"
        type="text"
        placeholder="Search helpers…"
        class="flex-1 min-w-0 px-3 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
      >
      <select
        v-model="mode"
        data-testid="helper-catalog-mode"
        class="px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 border border-gray-700 focus:outline-none"
      >
        <option v-for="m in MODES" :key="m.value" :value="m.value">
          {{ m.label }}
        </option>
      </select>
    </div>

    <div class="flex-1 overflow-y-auto p-4">
      <div
        v-if="loading && helpers.length === 0"
        class="space-y-3"
        data-testid="helper-catalog-loading"
      >
        <div v-for="i in 4" :key="i" class="h-16 bg-gray-800 rounded animate-pulse" />
      </div>

      <div v-else-if="error" class="text-sm text-red-400" data-testid="helper-catalog-error">
        {{ error }}
      </div>

      <div
        v-else-if="helpers.length === 0"
        class="text-sm text-gray-500 italic text-center py-8"
        data-testid="helper-catalog-empty"
      >
        No helpers registered.
      </div>

      <ul v-else class="space-y-2">
        <li
          v-for="helper in helpers"
          :key="helper.name"
          data-testid="helper-catalog-item"
          class="px-3 py-2 rounded border border-gray-800 hover:bg-gray-800"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium text-gray-100 truncate">{{ helper.name }}</span>
          </div>
          <div class="text-xs text-gray-500 mt-1">
            {{ helper.inputType || '—' }}
            → {{ helper.outputType || '—' }}
          </div>
          <div v-if="helper.description" class="text-xs text-gray-400 mt-1">
            {{ helper.description }}
          </div>
        </li>
      </ul>

      <div v-if="showingText" class="mt-3 text-xs text-gray-500">
        {{ showingText }}
      </div>

      <button
        v-if="hasMore"
        type="button"
        data-testid="helper-catalog-load-more"
        class="mt-3 w-full px-3 py-2 text-sm rounded border border-gray-700 text-gray-200 hover:bg-gray-800 disabled:opacity-50"
        :disabled="loading"
        @click="loadMore"
      >
        {{ loading ? 'Loading…' : 'Load more' }}
      </button>
    </div>
  </div>
</template>
