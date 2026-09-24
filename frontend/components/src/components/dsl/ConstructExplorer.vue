<script setup lang="ts">
import { computed } from 'vue'
import type { DslConstruct } from '../../types/dsl'
import HotkeyTooltip from '../HotkeyTooltip.vue'
import DslPlainConstructListSkeleton from './PlainConstructListSkeleton.vue'

const props = defineProps<{
  constructs: DslConstruct[]
  selectedName: string | null
  loading?: boolean
  objectFiltersActive?: boolean
}>()

const emit = defineEmits<{
  select: [name: string]
  openObjects: []
  openHelpers: []
}>()

const collapsed = defineModel<boolean>('collapsed', { default: false })

// Exposed so the parent can pre-fill the filter (e.g. from `?objectName=...`
// on deep links). Two-way bound via `v-model:filter`.
const filter = defineModel<string>('filter', { default: '' })

const filteredConstructs = computed(() => {
  const q = filter.value.trim().toLowerCase()
  if (!q) return props.constructs
  return props.constructs.filter((c) => c.name.toLowerCase().includes(q))
})

function toggle() {
  collapsed.value = !collapsed.value
}

function handleSelect(name: string) {
  emit('select', name)
}

function openObjects() {
  emit('openObjects')
}

function openHelpers() {
  emit('openHelpers')
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-900 text-gray-100" data-testid="construct-explorer">
    <div
      class="flex items-center p-3 border-b border-gray-800"
      :class="collapsed ? 'justify-center' : 'justify-between'"
      data-testid="construct-explorer-header"
    >
      <h2 v-show="!collapsed" class="text-sm font-semibold text-gray-100">Constructs</h2>
      <button
        type="button"
        class="p-1.5 rounded hover:bg-gray-800 text-gray-400 hover:text-gray-100"
        :aria-label="collapsed ? 'Expand constructs' : 'Collapse constructs'"
        data-testid="construct-explorer-toggle"
        @click="toggle"
      >
        <span v-show="collapsed">»</span>
        <span v-show="!collapsed">«</span>
      </button>
    </div>

    <div v-show="!collapsed" class="p-3 border-b border-gray-800" data-testid="explorer-filter">
      <input
        v-model="filter"
        type="text"
        placeholder="Filter constructs..."
        class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
      >
    </div>

    <div v-show="!collapsed" class="flex-1 overflow-y-auto p-2" data-testid="explorer-list">
      <DslPlainConstructListSkeleton v-if="loading" />
      <slot
        v-else
        :constructs="filteredConstructs"
        :selected-name="selectedName"
        :on-select="handleSelect"
      />
    </div>

    <div
      v-if="!collapsed"
      class="p-2 border-t border-gray-800 flex items-center gap-2"
      data-testid="construct-explorer-footer"
    >
      <HotkeyTooltip label="Open object search" location="top" class="flex-1">
        <button
          type="button"
          class="w-full px-2 py-1.5 text-xs rounded bg-gray-800 text-gray-300 hover:bg-gray-700 hover:text-gray-100 flex items-center justify-center gap-1.5"
          :class="objectFiltersActive ? 'ring-1 ring-blue-500 text-blue-200' : ''"
          data-testid="construct-explorer-objects-btn"
          @click="openObjects"
        >
          <span>Objects</span>
          <span
            v-if="objectFiltersActive"
            class="w-2 h-2 rounded-full bg-blue-500"
            data-testid="objects-filter-active-indicator"
            aria-hidden="true"
          />
        </button>
      </HotkeyTooltip>
      <button
        type="button"
        class="flex-1 px-2 py-1.5 text-xs rounded bg-gray-800 text-gray-300 hover:bg-gray-700 hover:text-gray-100"
        data-testid="construct-explorer-helpers-btn"
        @click="openHelpers"
      >
        Helpers
      </button>
    </div>

    <div
      v-show="collapsed"
      class="flex-1 flex flex-col items-center py-2"
      data-testid="explorer-rail"
    >
      <span class="vertical-text text-xs font-semibold text-gray-400 mt-4">Constructs</span>
    </div>
  </div>
</template>

<style scoped>
.vertical-text {
  writing-mode: vertical-rl;
  text-orientation: mixed;
}
</style>
