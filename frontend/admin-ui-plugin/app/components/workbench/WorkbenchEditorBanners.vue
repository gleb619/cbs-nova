<script setup lang="ts">
import { DslDraftRestoreBanner, ErrorBanner } from '@cbs/components'

// Editor banners: the three top-of-main warnings / affordances that show up
// above the body editor:
//
//   - Draft restore banner (server-draft recovery).
//   - Autosave-offline banner (server autosave failed; local copy only).
//   - Delete error banner (last delete attempt failed, offers retry).
//
// Decision: extracted from `dsl-workbench.vue` so the page stays a thin
// orchestrator. The banners are purely presentational — the parent owns
// the state and the retry callback.

defineProps<{
  isFileBacked: boolean
  restoredFromDraft: boolean
  draftSavedAt: number | null
  autosaveOffline: boolean
  deleteError: string | null
}>()

const emit = defineEmits<{
  (event: 'discard-draft'): void
  (event: 'retry-delete'): void
}>()
</script>

<template>
  <div v-if="restoredFromDraft && !isFileBacked" class="px-3 pt-2">
    <DslDraftRestoreBanner :saved-at="draftSavedAt" @discard="emit('discard-draft')" />
  </div>
  <div v-if="autosaveOffline && !isFileBacked" class="px-3 pt-2">
    <div
      role="status"
      class="flex items-center gap-2 px-3 py-2 text-sm rounded border border-amber-200 bg-amber-50 text-amber-800"
      data-testid="dsl-autosave-offline-banner"
    >
      <span aria-hidden="true">⚠</span>
      <span>
        Autosave offline — draft kept in this browser only. It will retry on the next edit or manual
        save.
      </span>
    </div>
  </div>

  <div v-if="deleteError" class="px-3 pt-2" data-testid="dsl-workbench-delete-error">
    <ErrorBanner :message="deleteError" @retry="emit('retry-delete')" />
  </div>
</template>
