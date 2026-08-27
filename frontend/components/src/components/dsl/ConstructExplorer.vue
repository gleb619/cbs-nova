<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DslConstruct } from '../../types/dsl'
import DslPlainConstructListSkeleton from './PlainConstructListSkeleton.vue'

const props = defineProps<{
  constructs: DslConstruct[]
  selectedName: string | null
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [name: string]
}>()

const collapsed = defineModel<boolean>('collapsed', { default: false })

const search = ref('')

const filteredConstructs = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return props.constructs
  return props.constructs.filter((c) => c.name.toLowerCase().includes(q))
})

function toggle() {
  collapsed.value = !collapsed.value
}

function handleSelect(name: string) {
  emit('select', name)
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-900 text-gray-100">
    <div
      class="flex items-center p-3 border-b border-gray-800"
      :class="collapsed ? 'justify-center' : 'justify-between'"
    >
      <h2 v-show="!collapsed" class="text-sm font-semibold text-gray-100">Constructs</h2>
      <button
        type="button"
        class="p-1.5 rounded hover:bg-gray-800 text-gray-400 hover:text-gray-100"
        :aria-label="collapsed ? 'Expand constructs' : 'Collapse constructs'"
        @click="toggle"
      >
        <span v-show="collapsed">»</span>
        <span v-show="!collapsed">«</span>
      </button>
    </div>

    <div v-show="!collapsed" class="p-3 border-b border-gray-800" data-testid="explorer-search">
      <input
        v-model="search"
        type="text"
        placeholder="Search constructs..."
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
