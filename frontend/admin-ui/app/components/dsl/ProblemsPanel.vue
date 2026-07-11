<script setup lang="ts">
import type { ValidationError } from '~/types/dsl'

defineProps<{ errors: ValidationError[] }>()
</script>

<template>
  <div class="border-t border-gray-200 bg-white">
    <div class="px-4 py-2 text-xs font-semibold uppercase text-gray-500 border-b border-gray-200">
      Problems ({{ errors.length }})
    </div>
    <div v-if="errors.length === 0" class="px-4 py-3 text-sm text-gray-500 italic">
      No problems detected.
    </div>
    <ul v-else class="divide-y divide-gray-100 max-h-40 overflow-y-auto">
      <li v-for="(err, idx) in errors" :key="idx" class="px-4 py-2 text-sm flex items-start gap-2">
        <span
          class="inline-block w-2 h-2 rounded-full mt-1.5 shrink-0"
          :class="err.severity === 'error' ? 'bg-red-500' : 'bg-yellow-500'"
        />
        <div class="flex-1">
          <div class="font-mono text-xs text-gray-500">{{ err.field }}</div>
          <div class="text-gray-800">{{ err.message }}</div>
        </div>
      </li>
    </ul>
  </div>
</template>
