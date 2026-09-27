<script setup lang="ts">
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'

withDefaults(
  defineProps<{
    drafts: SavedDraftSummary[]
    loading?: boolean
    selectedName?: string | null
    testId?: string
    itemTestId?: string
    /** Git-client styling: a status badge per row instead of a plain type/status line. */
    statusBadge?: boolean
    /** Git-client styling: a per-row revert button that emits `discard`. */
    showRowActions?: boolean
    statusItemTestId?: string
    rowActionTestId?: string
    rowActionLabel?: string
  }>(),
  {
    loading: false,
    selectedName: null,
    testId: 'dsl-saved-drafts',
    itemTestId: 'dsl-saved-drafts-item',
    statusBadge: false,
    showRowActions: false,
    statusItemTestId: 'dsl-saved-drafts-item-status',
    rowActionTestId: 'dsl-saved-drafts-item-discard',
    rowActionLabel: 'Revert',
  },
)

const emit = defineEmits<{
  (event: 'select', name: string): void
  (event: 'discard', name: string): void
}>()

const STATUS_LABELS: Record<string, string> = {
  ADDED: 'Added',
  MODIFIED: 'Modified',
  DELETED: 'Deleted',
  CONFLICTING: 'Conflicting',
  PUBLISHED: 'Published',
}

const STATUS_BADGE_CLASSES: Record<string, string> = {
  ADDED: 'bg-green-900 text-green-300',
  MODIFIED: 'bg-amber-900 text-amber-300',
  DELETED: 'bg-red-900 text-red-300',
  CONFLICTING: 'bg-purple-900 text-purple-300',
}

/** Human label for a git-ish draft status, falling back to the raw value. */
function statusLabel(status?: string): string {
  const key = (status ?? '').toUpperCase()
  return STATUS_LABELS[key] ?? status ?? 'Draft'
}

function statusBadgeClasses(status?: string): string {
  return STATUS_BADGE_CLASSES[(status ?? '').toUpperCase()] ?? 'bg-gray-700 text-gray-300'
}

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
        class="flex items-center border-b border-gray-800"
        :class="showRowActions ? 'pr-2' : ''"
      >
        <button
          type="button"
          :data-testid="itemTestId"
          :aria-label="`Open draft ${draft.name}`"
          class="flex-1 min-w-0 cursor-pointer px-3 py-2 text-left text-sm hover:bg-gray-700 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-accent-500"
          :class="selectedName === draft.name ? 'bg-gray-700' : ''"
          @click="onRowActivate($event, draft.name)"
          @keydown.enter="onRowActivate($event, draft.name)"
          @keydown.space.prevent="onRowActivate($event, draft.name)"
        >
          <div class="font-medium text-gray-100 truncate">{{ draft.name }}</div>
          <div v-if="!statusBadge" class="text-xs text-gray-400">
            {{ draft.type ?? '—' }}
            · {{ draft.status ?? 'Draft' }}
          </div>
        </button>
        <span
          v-if="statusBadge"
          :data-testid="statusItemTestId"
          class="mr-1.5 shrink-0 rounded px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wide"
          :class="statusBadgeClasses(draft.status)"
        >
          {{ statusLabel(draft.status) }}
        </span>
        <button
          v-if="showRowActions"
          type="button"
          :data-testid="rowActionTestId"
          :aria-label="`${rowActionLabel} ${draft.name}`"
          :title="`${rowActionLabel} ${draft.name}`"
          class="shrink-0 rounded-sm border border-gray-700 px-1.5 py-0.5 text-xs text-gray-300 hover:bg-gray-800"
          @click="emit('discard', draft.name)"
        >
          {{ rowActionLabel }}
        </button>
      </li>
    </ul>
    <p v-else-if="loading" class="px-3 py-2 text-xs text-gray-400">Loading drafts…</p>
    <p v-else class="px-3 py-2 text-xs text-gray-500">No saved drafts yet.</p>
  </section>
</template>
