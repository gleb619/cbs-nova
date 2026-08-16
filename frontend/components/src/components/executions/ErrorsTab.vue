<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  errors: Array<{ message: string; code?: string; stackTrace?: string }> | undefined
}>()

const expanded = ref<Set<number>>(new Set())

function toggle(i: number) {
  if (expanded.value.has(i)) expanded.value.delete(i)
  else expanded.value.add(i)
  // trigger reactivity
  expanded.value = new Set(expanded.value)
}
</script>

<template>
  <div class="space-y-3">
    <div
      v-if="!props.errors || props.errors.length === 0"
      class="bg-white border border-gray-200 rounded-lg p-12 text-center text-sm text-gray-500"
    >
      No errors recorded.
    </div>
    <div
      v-for="(err, idx) in props.errors"
      :key="idx"
      class="bg-white border border-red-200 rounded-lg p-4"
    >
      <div class="flex items-start gap-3">
        <span class="text-red-500 text-lg">⚠</span>
        <div class="flex-1 min-w-0">
          <div class="text-sm font-semibold text-red-700 break-all">{{ err.message }}</div>
          <div v-if="err.code" class="text-xs text-gray-500 mt-1">
            Code: <span class="font-mono">{{ err.code }}</span>
          </div>
          <div v-if="err.stackTrace" class="mt-2">
            <button
              type="button"
              class="text-xs text-blue-600 hover:underline"
              @click="toggle(idx)"
            >
              {{ expanded.has(idx) ? 'Hide stack trace' : 'Show stack trace' }}
            </button>
            <pre
              v-if="expanded.has(idx)"
              class="mt-2 text-[11px] bg-gray-50 border border-gray-200 rounded p-2 overflow-auto max-h-64 whitespace-pre"
            ><code>{{ err.stackTrace }}</code></pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
