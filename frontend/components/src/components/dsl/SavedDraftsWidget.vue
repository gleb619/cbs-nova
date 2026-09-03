<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'
import CbsDrawer from '../CbsDrawer.vue'
import SavedDraftsList from './SavedDraftsList.vue'

const props = withDefaults(
  defineProps<{
    drafts: SavedDraftSummary[]
    loading?: boolean
    error?: string | null
    selectedName?: string | null
    autoLoad?: boolean
    drawerTitle?: string
    drawerWidthClass?: string
  }>(),
  {
    loading: false,
    error: null,
    selectedName: null,
    autoLoad: false,
    drawerTitle: 'Saved Drafts',
    drawerWidthClass: 'w-96',
  },
)

const emit = defineEmits<{
  (event: 'select', name: string): void
  (event: 'refresh'): void
  (event: 'open'): void
}>()

const drawerOpen = ref(false)

function open() {
  drawerOpen.value = true
  emit('open')
}

function handleSelect(name: string) {
  emit('select', name)
  drawerOpen.value = false
}

onMounted(() => {
  if (props.autoLoad) emit('refresh')
})
</script>

<template>
  <div
    data-testid="dsl-saved-drafts-widget"
    class="inline-flex items-center gap-2 rounded-md border border-neutral-300 bg-white px-2.5 py-1 text-xs text-neutral-700 shadow-sm"
  >
    <span class="font-medium uppercase tracking-wide text-neutral-500">Drafts</span>
    <span
      class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-semibold"
      :class="error ? 'bg-red-100 text-red-700' : 'bg-neutral-200 text-neutral-700'"
      data-testid="dsl-saved-drafts-widget-count"
      :title="error ?? undefined"
    >
      {{ loading && !drafts.length ? '…' : drafts.length }}
    </span>
    <button
      type="button"
      class="px-2 py-0.5 rounded text-xs font-medium border border-neutral-300 hover:bg-neutral-100"
      data-testid="dsl-saved-drafts-widget-details"
      aria-label="Open saved drafts"
      @click="open"
    >
      Details
    </button>

    <CbsDrawer
      v-model:open="drawerOpen"
      :title="drawerTitle"
      close-label="Close saved drafts"
      test-id="dsl-saved-drafts-drawer"
      :width-class="drawerWidthClass"
    >
      <slot
        name="header"
        :drafts="drafts"
        :loading="loading"
        :error="error"
        :refresh="() => emit('refresh')"
      />
      <div class="flex items-center justify-between px-3 py-2 border-b border-gray-800">
        <span class="text-xs uppercase tracking-wide text-gray-400">
          {{ drafts.length }}
          saved
        </span>
        <button
          type="button"
          class="px-2 py-0.5 rounded text-xs border border-gray-700 text-gray-300 hover:bg-gray-800"
          data-testid="dsl-saved-drafts-drawer-refresh"
          :disabled="loading"
          @click="emit('refresh')"
        >
          {{ loading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>
      <p
        v-if="error"
        class="px-3 py-2 text-xs text-red-300 bg-red-950/40 border-b border-red-900"
        data-testid="dsl-saved-drafts-drawer-error"
      >
        {{ error }}
      </p>
      <SavedDraftsList
        :drafts="drafts"
        :loading="loading"
        :selected-name="selectedName"
        @select="handleSelect"
      />
      <slot
        name="footer"
        :drafts="drafts"
        :loading="loading"
        :error="error"
        :refresh="() => emit('refresh')"
      />
    </CbsDrawer>
  </div>
</template>
