<script setup lang="ts">
import { useHotkeyOverlay } from '../composables/useHotkeyOverlay'

defineProps<{
  keys: string
}>()

const { visible } = useHotkeyOverlay()
</script>

<template>
  <span class="relative inline-block">
    <slot />
    <span
      v-if="visible"
      class="absolute z-[1000] bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs font-medium text-white bg-neutral-900 rounded shadow-lg whitespace-nowrap"
      data-testid="hotkey-tooltip"
    >
      {{ keys }}
      <span
        class="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-neutral-900"
      ></span>
    </span>
  </span>
</template>
