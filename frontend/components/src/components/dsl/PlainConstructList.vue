<script setup lang="ts">
import { computed } from 'vue'

import { useLocalStorageState } from '../../composables/useLocalStorageState'
import type { ConstructType, DslConstruct } from '../../types/dsl'

const props = defineProps<{
  constructs: DslConstruct[]
  selectedName: string | null
  onSelect?: (name: string) => void
  deletable?: boolean
}>()

const emit = defineEmits<{
  select: [name: string]
  delete: [name: string]
}>()

const STORAGE_NAMESPACE = 'cbs-nova:dsl-plain-construct-list'

const collapsedByType = useLocalStorageState<Record<string, boolean>>(
  'collapsed-groups',
  {},
  { namespace: STORAGE_NAMESPACE },
)

const grouped = computed(() => {
  const types: ConstructType[] = ['Process', 'Transaction', 'Function', 'Helper']
  return types.map((t) => ({
    type: t,
    items: props.constructs.filter((c) => c.type === t),
  }))
})

const statusClass: Record<string, string> = {
  Draft: 'bg-gray-200 text-gray-700',
  Valid: 'bg-green-100 text-green-700',
  Invalid: 'bg-red-100 text-red-700',
  Published: 'bg-blue-100 text-blue-700',
}

function isCollapsed(type: string): boolean {
  return collapsedByType.value[type] ?? false
}

function toggleGroup(type: string) {
  collapsedByType.value = {
    ...collapsedByType.value,
    [type]: !isCollapsed(type),
  }
}

function handleClick(name: string) {
  emit('select', name)
  props.onSelect?.(name)
}

function handleDelete(name: string) {
  emit('delete', name)
}
</script>

<template>
  <div class="space-y-3" data-testid="plain-construct-list">
    <div v-for="group in grouped" :key="group.type">
      <button
        type="button"
        class="w-full flex items-center justify-between px-2 py-1 text-xs font-semibold uppercase text-gray-400 tracking-wide hover:text-gray-200"
        :aria-expanded="!isCollapsed(group.type)"
        :data-testid="`plain-construct-list-group-${group.type}`"
        @click="toggleGroup(group.type)"
      >
        <span>{{ group.type }} ({{ group.items.length }})</span>
        <span class="text-[10px]">{{ isCollapsed(group.type) ? '▸' : '▾' }}</span>
      </button>

      <ul v-show="!isCollapsed(group.type)" class="space-y-0.5">
        <li v-if="group.items.length === 0" class="px-2 py-1 text-xs text-gray-500 italic">none</li>
        <li
          v-for="item in group.items"
          :key="item.name"
          class="flex items-center gap-1"
        >
          <button
            type="button"
            class="flex-1 text-left px-2 py-1.5 rounded text-sm flex items-center justify-between gap-2 hover:bg-gray-800 transition-colors"
            :class="selectedName === item.name ? 'bg-gray-800 text-white' : 'text-gray-300'"
            :data-testid="`plain-construct-list-item-${item.name}`"
            @click="handleClick(item.name)"
          >
            <span class="truncate">{{ item.name }}</span>
            <span
              class="text-[10px] px-1.5 py-0.5 rounded-full whitespace-nowrap"
              :class="statusClass[item.status] ?? 'bg-gray-200 text-gray-700'"
            >
              {{ item.status }}
            </span>
          </button>
          <button
            v-if="deletable && item.status === 'Draft'"
            type="button"
            class="shrink-0 px-1.5 py-1.5 rounded text-sm text-gray-400 hover:text-red-400 hover:bg-gray-800 transition-colors"
            :data-testid="`plain-construct-list-item-delete-${item.name}`"
            aria-label="Delete draft"
            @click.stop="handleDelete(item.name)"
          >
            🗑
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
