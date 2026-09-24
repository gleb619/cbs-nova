<script setup lang="ts">
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'

withDefaults(
  defineProps<{
    drafts: SavedDraftSummary[]
    loading?: boolean
    selectedName?: string | null
    testId?: string
    itemTestId?: string
  }>(),
  {
    loading: false,
    selectedName: null,
    testId: 'dsl-saved-drafts',
    itemTestId: 'dsl-saved-drafts-item',
  },
)

const emit = defineEmits<(event: 'select', name: string) => void>()

function onRowActivate(_event: KeyboardEvent | MouseEvent, name: string) {
  emit('select', name)
}
</script>

<template>
  <section :data-testid="testId" class="flex flex-col">
    <ul v-if="drafts.length" class="flex flex-col">
      <li
        v-for="draft in drafts"
        :key="draft.name"
        :data-testid="itemTestId"
        :aria-label="`Open draft ${draft.name}`"
        tabindex="0"
        role="button"
        class="cursor-pointer border-b border-gray-800 px-3 py-2 text-sm hover:bg-gray-700 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-accent-500"
        :class="selectedName === draft.name ? 'bg-gray-700' : ''"
        @click="onRowActivate($event, draft.name)"
        @keydown.enter="onRowActivate($event, draft.name)"
        @keydown.space.prevent="onRowActivate($event, draft.name)"
      >
        <div class="font-medium text-gray-100">{{ draft.name }}</div>
        <div class="text-xs text-gray-400">
          {{ draft.type ?? '—' }}
          · {{ draft.status ?? 'Draft' }}
        </div>
      </li>
    </ul>
    <p v-else-if="loading" class="px-3 py-2 text-xs text-gray-400">Loading drafts…</p>
    <p v-else class="px-3 py-2 text-xs text-gray-500">No saved drafts yet.</p>
  </section>
</template>
