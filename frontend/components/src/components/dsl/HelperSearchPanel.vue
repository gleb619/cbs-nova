<script setup lang="ts">
import type { ObjectSearchResult } from '../../composables/useHelperSearch'

defineProps<{
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
}>()

const open = defineModel<boolean>('open', { default: false })

const name = defineModel<string>('name', { default: '' })
const type = defineModel<string>('type', { default: '' })
const description = defineModel<string>('description', { default: '' })

function onSearch() {
  emit('search')
}

function onClear() {
  emit('clear')
}

function closeDrawer() {
  open.value = false
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
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="open"
        class="fixed inset-0 z-30 bg-gray-900/50"
        aria-hidden="true"
        @click="closeDrawer"
      />
    </Transition>

    <Transition name="drawer">
      <aside
        v-if="open"
        class="fixed top-0 right-0 z-40 h-full w-80 bg-gray-900 text-gray-100 flex flex-col shadow-xl border-l border-gray-800"
        role="dialog"
        aria-modal="true"
        aria-label="Object search"
      >
        <div class="flex items-center justify-between p-3 border-b border-gray-800">
          <h3 class="text-sm font-semibold text-gray-100">Object Search</h3>
          <button
            type="button"
            class="p-1.5 rounded hover:bg-gray-800 text-gray-400 hover:text-gray-100"
            aria-label="Close object search"
            @click="closeDrawer"
          >
            ✕
          </button>
        </div>

        <div class="p-3 border-b border-gray-800">
          <div class="space-y-2">
            <input
              v-model="name"
              type="text"
              placeholder="Name"
              class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 placeholder-gray-500 border border-gray-700 focus:outline-none focus:border-gray-500"
             
            >
            <select
              v-model="type"
              class="w-full px-2 py-1.5 text-sm rounded bg-gray-800 text-gray-100 border border-gray-700 focus:outline-none focus:border-gray-500"
             
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
             
            >
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 px-3 py-1.5 text-sm rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
                :disabled="isLoading"
                @click="onSearch"
              >
                Search
              </button>
              <button
                type="button"
                class="px-3 py-1.5 text-sm rounded border border-gray-600 text-gray-300 hover:bg-gray-800"
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

          <div
            v-else-if="results.length === 0"
            class="text-sm text-gray-500 italic py-6 text-center"
          >
            No helpers found.
          </div>

          <table v-else class="w-full text-sm">
            <thead class="text-xs uppercase text-gray-400">
              <tr>
                <th class="text-left px-2 py-1.5">Name</th>
                <th class="text-left px-2 py-1.5">Type</th>
                <th class="text-left px-2 py-1.5 hidden lg:table-cell">In → Out</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-800">
              <tr
                v-for="result in results"
                :key="`${result.name}-${result.type}`"
                class="hover:bg-gray-800"
              >
                <td class="px-2 py-2 align-top">
                  <div class="text-gray-100 font-medium truncate" :title="result.name">
                    {{ result.name }}
                  </div>
                  <div
                    class="text-xs text-gray-500 mt-0.5 line-clamp-2"
                    :title="result.description"
                  >
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
        </div>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.2s;
}
.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(100%);
}
</style>
