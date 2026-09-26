<script setup lang="ts">
import { computed } from 'vue'

import { useLocalStorageState } from '../../composables/useLocalStorageState'
import type { ConstructStatus, ConstructType, DslConstruct } from '../../types/dsl'
import ConstructStatusIcon from './ConstructStatusIcon.vue'

const props = defineProps<{
  constructs: DslConstruct[]
  selectedName: string | null
  onSelect?: (name: string) => void
  deletable?: boolean
  strikethroughOnBuildError?: boolean
  boldOnBuildError?: boolean
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

const BUILD_PROBLEM_STATUSES: ConstructStatus[] = ['Invalid']

const nameClass: Record<ConstructStatus, string> = {
  Draft: 'text-gray-300',
  Valid: 'text-green-300',
  Invalid: 'text-red-300',
  Published: 'text-sky-300',
  Modified: 'text-yellow-300',
  Added: 'text-emerald-300',
  Deleted: 'text-orange-300',
  Conflicting: 'text-red-400',
}

function isBuildProblem(status: ConstructStatus): boolean {
  return BUILD_PROBLEM_STATUSES.includes(status)
}

function nameClasses(status: ConstructStatus): string {
  const classes = [nameClass[status] ?? 'text-gray-300']
  if (isBuildProblem(status) && props.strikethroughOnBuildError) {
    classes.push('line-through')
  }
  if (isBuildProblem(status) && props.boldOnBuildError) {
    classes.push('font-bold')
  }
  return classes.join(' ')
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
        <li v-for="item in group.items" :key="item.name" class="flex items-center gap-2">
          <button
            type="button"
            class="flex-1 min-w-0 text-left px-2 py-1.5 rounded text-sm flex items-center justify-between gap-2 hover:bg-gray-800 transition-colors"
            :class="selectedName === item.name ? 'bg-gray-800' : ''"
            :data-testid="`plain-construct-list-item-${item.name}`"
            @click="handleClick(item.name)"
          >
            <span class="truncate" :class="nameClasses(item.status)">{{ item.name }}</span>
            <ConstructStatusIcon :status="item.status" />
          </button>
          <button
            v-if="deletable && item.status === 'Draft'"
            type="button"
            class="shrink-0 px-1.5 py-1.5 rounded text-sm text-gray-400 hover:text-red-400 hover:bg-gray-800 transition-colors"
            :data-testid="`plain-construct-list-item-delete-${item.name}`"
            aria-label="Delete draft"
            @click.stop="handleDelete(item.name)"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              class="w-5 h-5"
              aria-hidden="true"
            >
              <path
                d="M6 12L18 12"
                stroke="#ffffff"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
