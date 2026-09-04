<script setup lang="ts">
import { useSidebar } from '../../composables/useSidebar'

withDefaults(
  defineProps<{
    label?: string
  }>(),
  {
    label: 'Hide sidebar',
  },
)

const { hide } = useSidebar()
</script>

<template>
  <button
    type="button"
    data-testid="app-sidebar-hide"
    :aria-label="label"
    :class="[
      'flex items-center justify-center w-10 h-10 mx-auto rounded-lg text-neutral-100',
      'hover:bg-primary-600 hover:text-white focus:outline-none focus:ring-2 focus:ring-primary-300',
      'transition-colors',
    ]"
    @click="hide"
  >
    <!-- Double-chevron toward the left rail: hides the rail for max width. -->
    <svg
      class="w-5 h-5"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <path stroke-linecap="round" stroke-linejoin="round" d="M11 19l-7-7 7-7M20 19l-7-7 7-7" />
    </svg>
  </button>
</template>