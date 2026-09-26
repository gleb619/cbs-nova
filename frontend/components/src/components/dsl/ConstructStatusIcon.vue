<script setup lang="ts">
import type { ConstructStatus } from '../../types/dsl'

const props = defineProps<{
  status: ConstructStatus
}>()

const iconPaths: Record<ConstructStatus, string> = {
  Published: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12 2.5 2.5 4.5-5"/>',
  Modified: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
  Added: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  Deleted: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  Conflicting:
    '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  Draft:
    '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/>',
  Valid: '<path d="M20 6 9 17l-5-5"/>',
  Invalid: '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/>',
}

const iconClass: Record<ConstructStatus, string> = {
  Published: 'text-sky-400',
  Modified: 'text-yellow-400',
  Added: 'text-emerald-400',
  Deleted: 'text-orange-400',
  Conflicting: 'text-red-500',
  Draft: 'text-gray-400',
  Valid: 'text-green-400',
  Invalid: 'text-red-400',
}
</script>

<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="w-3.5 h-3.5 shrink-0"
    :class="iconClass[props.status] ?? 'text-gray-400'"
    role="img"
    :aria-label="`Status: ${props.status}`"
    :data-testid="`construct-status-icon-${props.status}`"
  >
    <title>{{ props.status }}</title>
    <g v-html="iconPaths[props.status] ?? iconPaths.Published" />
  </svg>
</template>
