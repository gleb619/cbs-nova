<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ObjectSearchResult } from '../../composables/useHelperSearch'
import CbsDrawer from '../CbsDrawer.vue'

const props = defineProps<{
  results: ObjectSearchResult[]
  isLoading?: boolean
  error?: string | null
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:type': [value: string]
  'update:description': [value: string]
  search: []
  clear: []
  select: [result: ObjectSearchResult]
}>()

const open = defineModel<boolean>('open', { default: false })

const name = defineModel<string>('name', { default: '' })
const type = defineModel<string>('type', { default: '' })
const description = defineModel<string>('description', { default: '' })

function onSearch() {
  activeIndex.value = -1
  emit('search')
}

function onClear() {
  activeIndex.value = -1
  emit('clear')
}

const typeClass: Record<string, string> = {
  process: 'bg-blue-100 text-blue-700',
  transaction: 'bg-purple-100 text-purple-700',
  helper: 'bg-green-100 text-green-700',
  function: 'bg-yellow-100 text-yellow-700',
}

function rowTypeClass(resultType: string): string {
  return typeClass[resultType.toLowerCase()] ?? 'bg-gray-100 text-gray-700'
}

const activeIndex = ref(-1)

const activeResult = computed<ObjectSearchResult | null>(
  () => props.results[activeIndex.value] ?? null,
)

function choose(index: number) {
  const result = props.results[index]
  if (!result) return
  activeIndex.value = index
  emit('select', result)
}

function move(delta: number) {
  if (props.results.length === 0) return
  const first = delta > 0 ? 0 : props.results.length - 1
  const next = activeIndex.value < 0 ? first : activeIndex.value + delta
  activeIndex.value = Math.min(Math.max(next, 0), props.results.length - 1)
}

function closePanel() {
  open.value = false
}
</script>

<template>
  <CbsDrawer
    v-model:open="open"
    title="Object Search"
    aria-label="Object search"
    test-id="objects-search-panel"
    close-label="Close object search"
    width-class="w-96"
  >
    <div class="p-3 border-b border-gray-800">
      <div class="space-y-2">
        <input
          v-model="name"
          type="text"
          placeholder="Name"
          class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
          data-testid="objects-search-name-input"
        >
        <select
          v-model="type"
          class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 border border-gray-700 focus:outline-none focus:border-gray-500"
          data-testid="objects-search-type-select"
        >
          <option value="">All types</option>
          <option value="process">Process</option>
          <option value="transaction">Transaction</option>
          <option value="helper">Helper</option>
          <option value="function">Function</option>
        </select>
        <input
          v-model="description"
          type="text"
          placeholder="Description"
          class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
          data-testid="objects-search-description-input"
        >
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 px-3 py-1.5 text-sm rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
            :disabled="isLoading"
            data-testid="objects-search-search-button"
            @click="onSearch"
          >
            Search
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-sm rounded border border-gray-600 text-gray-300 hover:bg-gray-800"
            data-testid="objects-search-clear-button"
            @click="onClear"
          >
            Clear
          </button>
        </div>
      </div>
      <p v-if="error" class="mt-2 text-xs text-red-400">{{ error }}</p>
    </div>

    <div class="flex-1 overflow-y-auto p-2">
      <div v-if="isLoading" class="space-y-2">
        <div v-for="i in 4" :key="i" class="h-16 bg-gray-800 rounded animate-pulse" />
      </div>

      <div v-else-if="results.length === 0" class="text-sm text-gray-500 italic py-6 text-center">
        No objects found.
      </div>

      <table
        v-else
        class="w-full text-sm"
        aria-label="Object search results"
        data-testid="objects-search-results"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.esc="closePanel"
      >
        <thead class="text-xs uppercase text-gray-400">
          <tr>
            <th class="text-left px-2 py-1.5">Name</th>
            <th class="text-left px-2 py-1.5">Type</th>
            <th class="text-left px-2 py-1.5 hidden lg:table-cell">In → Out</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-800">
          <tr
            v-for="(result, index) in results"
            :key="`${result.name}-${result.type}`"
            class="cursor-pointer hover:bg-gray-800 focus:outline-none focus:bg-gray-800"
            :class="index === activeIndex ? 'bg-gray-800' : ''"
            :data-testid="`objects-search-result-row-${result.name}`"
            tabindex="0"
            :aria-selected="index === activeIndex"
            @click="choose(index)"
            @keydown.enter.prevent="choose(index)"
            @focus="activeIndex = index"
          >
            <td class="px-2 py-2 align-top">
              <div class="text-gray-100 font-medium truncate" :title="result.name">
                {{ result.name }}
              </div>
              <div class="text-xs text-gray-500 mt-0.5 line-clamp-2" :title="result.description">
                {{ result.description || '—' }}
              </div>
            </td>
            <td class="px-2 py-2 align-top">
              <span
                class="text-[10px] px-1.5 py-0.5 rounded-full uppercase whitespace-nowrap"
                :class="rowTypeClass(result.type)"
              >
                {{ result.type }}
              </span>
            </td>
            <td class="px-2 py-2 align-top hidden lg:table-cell text-xs text-gray-400">
              {{ result.inputType || '—' }}
              → {{ result.outputType || '—' }}
            </td>
          </tr>
        </tbody>
      </table>

      <div
        v-if="activeResult"
        class="mt-2 rounded border border-gray-800 bg-gray-800/60 px-2 py-2"
        data-testid="objects-search-active-detail"
      >
        <div class="text-xs text-gray-300 font-mono">
          {{ activeResult.inputType || '—' }}
          → {{ activeResult.outputType || '—' }}
        </div>
        <div class="text-xs text-gray-400 mt-1">
          {{ activeResult.description || 'No description' }}
        </div>
      </div>
    </div>
  </CbsDrawer>
</template>
