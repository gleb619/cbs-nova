<script setup lang="ts">
import { computed, ref } from 'vue'
import type { HelperCatalogEntry } from '../../types/dsl'

const props = defineProps<{
  helpers: HelperCatalogEntry[]
  loading?: boolean
  error?: string | null
}>()

const search = ref('')

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return props.helpers
  return props.helpers.filter((h) => h.name.toLowerCase().includes(term))
})
</script>

<template>
  <div data-testid="helper-catalog" class="flex flex-col h-full overflow-hidden">
    <div class="px-4 py-3 border-b border-gray-800 flex items-center gap-3">
      <h2 class="font-semibold text-gray-100">Helper Catalog</h2>
      <input
        v-model="search"
        data-testid="helper-catalog-search"
        type="text"
        placeholder="Search helpers…"
        class="flex-1 min-w-0 px-3 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
      >
    </div>

    <div class="flex-1 overflow-y-auto p-4">
      <div v-if="loading" class="space-y-3" data-testid="helper-catalog-loading">
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

      <div
        v-else-if="filtered.length === 0"
        class="text-sm text-gray-500 italic text-center py-8"
        data-testid="helper-catalog-no-match"
      >
        No helpers match.
      </div>

      <ul v-else class="space-y-2">
        <li
          v-for="helper in filtered"
          :key="helper.name"
          data-testid="helper-catalog-item"
          class="px-3 py-2 rounded border border-gray-800 hover:bg-gray-800"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium text-gray-100 truncate">{{ helper.name }}</span>
            <span
              v-if="helper.hasSideEffects"
              data-testid="helper-catalog-sideeffect"
              class="text-[10px] px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700 whitespace-nowrap"
            >
              side effect
            </span>
          </div>
          <div class="text-xs text-gray-500 mt-1">
            {{ helper.inputType || '—' }}
            → {{ helper.outputType || '—' }}
          </div>
          <div v-if="helper.description" class="text-xs text-gray-400 mt-1">
            {{ helper.description }}
          </div>
          <div v-if="helper.previewBehavior" class="text-xs text-gray-500 mt-1">
            preview: {{ helper.previewBehavior }}
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>
