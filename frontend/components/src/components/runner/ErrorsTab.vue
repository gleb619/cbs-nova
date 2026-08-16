<script setup lang="ts">
import { computed } from 'vue'

interface ErrorItem {
  message: string
  code?: string
}

const props = defineProps<{ errors: ErrorItem[] | undefined }>()

const list = computed(() => props.errors ?? [])
</script>

<template>
  <div>
    <div v-if="list.length === 0" class="text-sm text-gray-500">No errors.</div>
    <ul v-else class="space-y-2">
      <li
        v-for="(err, idx) in list"
        :key="idx"
        class="border border-red-200 bg-red-50 rounded-lg p-3"
      >
        <div class="flex items-center gap-2 mb-1">
          <span
            v-if="err.code"
            class="text-xs font-mono px-2 py-0.5 rounded bg-red-200 text-red-800"
            >{{ err.code }}</span
          >
        </div>
        <p class="text-sm text-red-900 whitespace-pre-wrap break-words">{{ err.message }}</p>
      </li>
    </ul>
  </div>
</template>
