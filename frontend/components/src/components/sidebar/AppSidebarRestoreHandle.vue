<script setup lang="ts">
import { useSidebar } from '../../composables/useSidebar'

withDefaults(
  defineProps<{
    label?: string
  }>(),
  {
    label: 'Show sidebar',
  },
)

const { unhide } = useSidebar()
</script>

<template>
  <button
    type="button"
    data-testid="app-sidebar-restore"
    :aria-label="label"
    class="fixed top-1/2 left-0 -translate-y-1/2 z-30
           flex items-center justify-center w-5 h-12
           rounded-r-lg bg-primary-500 text-white shadow-lg
           hover:bg-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-300
           transition-colors"
    @click="unhide"
  >
    <!-- Single chevron pointing right: pulls the rail back into view. -->
    <svg
      class="w-3 h-3"
      fill="none"
      stroke="currentColor"
      stroke-width="2.5"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  </button>
</template>