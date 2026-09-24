<script setup lang="ts">
import { computed } from 'vue'
import { useHotkeyOverlay } from '../composables/useHotkeyOverlay'

export type TooltipLocation = 'top' | 'bottom' | 'left' | 'right'

const props = defineProps<{
  keys?: string
  label?: string
  location?: TooltipLocation
}>()

const { visible } = useHotkeyOverlay()

const locationClasses: Record<TooltipLocation, string> = {
  top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
  bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
  left: 'right-full top-1/2 -translate-y-1/2 mr-2',
  right: 'left-full top-1/2 -translate-y-1/2 ml-2',
}

const arrowClasses: Record<TooltipLocation, string> = {
  top: 'top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-neutral-900',
  bottom: 'bottom-full left-1/2 -translate-x-1/2 border-4 border-transparent border-b-neutral-900',
  left: 'left-full top-1/2 -translate-y-1/2 border-4 border-transparent border-l-neutral-900',
  right: 'right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-neutral-900',
}

const resolvedLocation = computed<TooltipLocation>(() => props.location ?? 'bottom')
const tooltipClasses = computed(() => locationClasses[resolvedLocation.value])
const arrowClass = computed(() => arrowClasses[resolvedLocation.value])
</script>

<template>
  <span class="relative inline-block">
    <slot />
    <span
      v-if="visible"
      class="absolute z-[9999] px-2 py-1 text-xs font-medium text-white bg-neutral-900 rounded shadow-lg whitespace-nowrap"
      :class="tooltipClasses"
      data-testid="hotkey-tooltip"
    >
      {{ label }}
      {{ keys }}
      <span class="absolute" :class="arrowClass"></span>
    </span>
  </span>
</template>
