<script setup lang="ts">
import type { ConstructType, DslConstruct } from '~/types/dsl'

const props = defineProps<{
  type: ConstructType
  constructs: DslConstruct[]
}>()

const emit = defineEmits<{ pick: [name: string] }>()

const search = ref('')

// simple compatibility: same type OR helper for any
const compatible = computed(() => {
  const q = search.value.trim().toLowerCase()
  return props.constructs.filter((c) => {
    const compatibleType = c.type === props.type || (props.type !== 'Helper' && c.type === 'Helper')
    if (!compatibleType) return false
    return !q || c.name.toLowerCase().includes(q)
  })
})
</script>

<template>
  <div class="bg-white border border-gray-200 rounded shadow-lg w-72 max-h-80 flex flex-col">
    <div class="p-2 border-b border-gray-200">
      <input
        v-model="search"
        type="text"
        :placeholder="`Search ${type}...`"
        class="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:border-blue-500"
      />
    </div>
    <ul class="flex-1 overflow-y-auto p-1">
      <li v-if="compatible.length === 0" class="px-2 py-2 text-sm text-gray-500 italic">
        No matches
      </li>
      <li v-for="c in compatible" :key="c.name">
        <button
          type="button"
          class="w-full text-left px-2 py-1.5 rounded text-sm hover:bg-gray-100 text-gray-800"
          @click="emit('pick', c.name)"
        >
          <span class="font-medium">{{ c.name }}</span>
          <span class="ml-2 text-xs text-gray-500">{{ c.type }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>