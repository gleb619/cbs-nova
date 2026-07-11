<script setup lang="ts">
import type { ConstructType, DslConstruct } from '~/types/dsl'

const props = defineProps<{
  constructs: DslConstruct[]
  selectedName: string | null
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [name: string]
}>()

const search = ref('')

const grouped = computed(() => {
  const types: ConstructType[] = ['Process', 'Transaction', 'Function', 'Helper']
  const q = search.value.trim().toLowerCase()
  return types.map((t) => ({
    type: t,
    items: props.constructs.filter((c) => c.type === t && (!q || c.name.toLowerCase().includes(q))),
  }))
})

const statusClass: Record<string, string> = {
  Draft: 'bg-gray-200 text-gray-700',
  Valid: 'bg-green-100 text-green-700',
  Invalid: 'bg-red-100 text-red-700',
  Published: 'bg-blue-100 text-blue-700',
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-900 text-gray-100">
    <div class="p-3 border-b border-gray-800">
      <input
        v-model="search"
        type="text"
        placeholder="Search constructs..."
        class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
      >
    </div>

    <div v-if="loading" class="p-3 space-y-2">
      <div v-for="i in 6" :key="i" class="h-8 bg-gray-800 rounded animate-pulse" />
    </div>

    <div v-else class="flex-1 overflow-y-auto p-2 space-y-3">
      <div v-for="group in grouped" :key="group.type">
        <div class="px-2 py-1 text-xs font-semibold uppercase text-gray-400 tracking-wide">
          {{ group.type }}
          ({{ group.items.length }})
        </div>
        <ul class="space-y-0.5">
          <li v-if="group.items.length === 0" class="px-2 py-1 text-xs text-gray-500 italic">
            none
          </li>
          <li v-for="item in group.items" :key="item.name">
            <button
              type="button"
              class="w-full text-left px-2 py-1.5 rounded text-sm flex items-center justify-between gap-2 hover:bg-gray-800 transition-colors"
              :class="selectedName === item.name ? 'bg-gray-800 text-white' : 'text-gray-300'"
              @click="emit('select', item.name)"
            >
              <span class="truncate">{{ item.name }}</span>
              <span
                class="text-[10px] px-1.5 py-0.5 rounded-full whitespace-nowrap"
                :class="statusClass[item.status] ?? 'bg-gray-200 text-gray-700'"
              >
                {{ item.status }}
              </span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
