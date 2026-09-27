<script setup lang="ts">
/**
 * Drafts navbar pill + git-client panel inside the drafts drawer.
 *
 * The pill (label + change count + "Details") lives in the navbar and opens a
 * drawer, exactly as before. Inside the drawer sits a full source-control panel:
 * header (title and the remote operations), body (compact workspace
 * info + the changed files with their git status), footer (commit message box
 * and the commit / discard actions). The drawer's header actions slot carries
 * the refresh button.
 *
 * Every operation is an emit: this component owns no network access of its own.
 */
import { computed, onMounted, ref } from 'vue'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'
import type { DraftsMetadata } from '../../types/drafts'
import CbsDrawer from '../CbsDrawer.vue'
import DraftMetadataCard from './DraftMetadataCard.vue'
import DraftsList from './DraftsList.vue'

const props = withDefaults(
  defineProps<{
    drafts: SavedDraftSummary[]
    loading?: boolean
    error?: string | null
    selectedName?: string | null
    autoLoad?: boolean
    metadata?: DraftsMetadata | null
    metadataLoading?: boolean
    metadataError?: string | null
    /** Panel heading, e.g. "Source Control" or "Changes". */
    title?: string
    drawerTitle?: string
    drawerWidthClass?: string
    /** Busy flags for the operations the host performs on our behalf. */
    fetching?: boolean
    pulling?: boolean
    rebasing?: boolean
    committing?: boolean
  }>(),
  {
    loading: false,
    error: null,
    selectedName: null,
    autoLoad: false,
    metadata: null,
    metadataLoading: false,
    metadataError: null,
    title: 'Source Control',
    drawerTitle: 'Drafts',
    drawerWidthClass: 'w-96',
    fetching: false,
    pulling: false,
    rebasing: false,
    committing: false,
  },
)

const emit = defineEmits<{
  (event: 'select', name: string): void
  (event: 'refresh'): void
  (event: 'open'): void
  (event: 'refresh-metadata'): void
  (event: 'fetch'): void
  (event: 'pull'): void
  (event: 'rebase'): void
  (event: 'commit', message: string): void
  (event: 'discard', name?: string): void
}>()

const useDraftsWidgetStorage = createNamespacedLocalStorageState('cbs-nova:drafts-widget')
const drawerOpen = useDraftsWidgetStorage('drawer-open', false)

const commitMessage = ref('')

const heading = computed(() => props.title || 'Source Control')
const count = computed(() =>
  props.loading && !props.drafts.length ? '…' : String(props.drafts.length),
)

const hasChanges = computed(() => props.drafts.length > 0)
/** Git operations only make sense when the workspace is a git work tree. */
const gitUnavailable = computed(() => props.metadata != null && !props.metadata.gitEnabled)
const remoteBusy = computed(() => props.fetching || props.pulling || props.rebasing)
const canCommit = computed(
  () =>
    hasChanges.value &&
    commitMessage.value.trim().length > 0 &&
    !props.loading &&
    !props.committing,
)
const canDiscard = computed(() => hasChanges.value && !props.loading)

function openDrawer() {
  drawerOpen.value = true
  emit('open')
}

function handleSelect(name: string) {
  emit('select', name)
  drawerOpen.value = false
}

function submitCommit() {
  if (!canCommit.value) return
  emit('commit', commitMessage.value.trim())
  commitMessage.value = ''
}

function discardAll() {
  if (!canDiscard.value) return
  emit('discard')
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
      {{ count }}
    </span>
    <button
      type="button"
      class="px-2 py-0.5 rounded text-xs font-medium border border-neutral-300 hover:bg-neutral-100"
      data-testid="dsl-saved-drafts-widget-details"
      aria-label="Open drafts"
      :title="`Open ${drawerTitle}`"
      @click="openDrawer"
    >
      Details
    </button>

    <CbsDrawer
      v-model:open="drawerOpen"
      :title="drawerTitle"
      close-label="Close drafts"
      test-id="dsl-saved-drafts-drawer"
      :width-class="drawerWidthClass"
    >
      <template #actions>
        <button
          type="button"
          class="p-1.5 rounded hover:bg-gray-800 text-gray-400 hover:text-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
          data-testid="dsl-saved-drafts-drawer-refresh"
          aria-label="Refresh changes"
          :title="loading ? 'Refreshing' : 'Refresh changes'"
          :disabled="loading"
          @click="emit('refresh')"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="h-4 w-4"
            :class="{ 'animate-spin': loading }"
            aria-hidden="true"
          >
            <path d="M21 12a9 9 0 1 1-2.64-6.36" />
            <path d="M21 3v6h-6" />
          </svg>
        </button>
      </template>

      <div data-testid="dsl-saved-drafts-panel" class="flex min-h-0 flex-1 flex-col">
        <slot
          name="header"
          :drafts="drafts"
          :loading="loading"
          :error="error"
          :refresh="() => emit('refresh')"
        />

        <!-- Header: title on the first row, remote operations on the second -->
        <div class="border-b border-gray-800 px-3 py-2" data-testid="dsl-saved-drafts-panel-header">
          <div class="flex items-center gap-2">
            <h4 class="text-xs font-semibold uppercase tracking-wide text-gray-300">
              {{ heading }}
            </h4>
          </div>
          <div class="mt-2 flex items-center gap-2">
            <button
              type="button"
              class="flex-1 rounded border border-gray-700 px-2 py-0.5 text-xs text-gray-200 hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
              data-testid="dsl-saved-drafts-fetch"
              aria-label="Fetch from remote"
              title="Fetch from remote"
              :disabled="loading || remoteBusy || gitUnavailable"
              @click="emit('fetch')"
            >
              {{ fetching ? 'Fetching\u2026' : 'Fetch' }}
            </button>
            <button
              type="button"
              class="flex-1 rounded border border-gray-700 px-2 py-0.5 text-xs text-gray-200 hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
              data-testid="dsl-saved-drafts-pull"
              aria-label="Pull from remote"
              title="Pull from remote"
              :disabled="loading || remoteBusy || gitUnavailable"
              @click="emit('pull')"
            >
              {{ pulling ? 'Pulling\u2026' : 'Pull' }}
            </button>
            <button
              type="button"
              class="flex-1 rounded border border-gray-700 px-2 py-0.5 text-xs text-gray-200 hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
              data-testid="dsl-saved-drafts-rebase"
              aria-label="Rebase onto remote"
              title="Rebase onto remote"
              :disabled="loading || remoteBusy || gitUnavailable"
              @click="emit('rebase')"
            >
              {{ rebasing ? 'Rebasing\u2026' : 'Rebase' }}
            </button>
          </div>
        </div>

        <!-- Body: compact workspace info + the changed files -->
        <div
          class="flex min-h-0 flex-1 flex-col overflow-y-auto"
          data-testid="dsl-saved-drafts-panel-body"
        >
          <DraftMetadataCard
            :metadata="metadata"
            :loading="metadataLoading"
            :error="metadataError"
          />

          <p
            v-if="error"
            class="border-b border-red-900 bg-red-950/40 px-3 py-2 text-xs text-red-300"
            data-testid="dsl-saved-drafts-drawer-error"
          >
            {{ error }}
          </p>

          <DraftsList
            :drafts="drafts"
            :loading="loading"
            :selected-name="selectedName"
            status-badge
            show-row-actions
            @select="handleSelect"
            @discard="(name: string) => emit('discard', name)"
          />
        </div>

        <!-- Footer: commit message + commit / discard -->
        <footer
          class="border-t border-gray-800 bg-gray-900 px-3 py-2"
          data-testid="dsl-saved-drafts-commit-box"
        >
          <label
            for="dsl-saved-drafts-commit-message"
            class="block text-xs uppercase tracking-wide text-gray-500"
          >
            Message
          </label>
          <textarea
            id="dsl-saved-drafts-commit-message"
            v-model="commitMessage"
            data-testid="dsl-saved-drafts-commit-message"
            aria-label="Commit message"
            rows="2"
            placeholder="Describe your change"
            class="mt-1 w-full resize-none rounded border border-gray-700 bg-gray-800 px-2 py-1 text-xs text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-accent-500"
          />
          <div class="mt-2 flex items-center gap-2">
            <button
              type="button"
              data-testid="dsl-saved-drafts-commit"
              aria-label="Commit changes"
              title="Commit the listed changes"
              class="flex-1 rounded border border-accent-500 bg-accent-500 px-2 py-1 text-xs font-medium text-white hover:bg-accent-600 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!canCommit"
              @click="submitCommit"
            >
              {{ committing ? 'Committing…' : 'Commit' }}
            </button>
            <button
              type="button"
              data-testid="dsl-saved-drafts-revert-all"
              aria-label="Discard all changes"
              title="Discard every change in the workspace"
              class="flex-1 rounded border border-gray-700 px-2 py-1 text-xs text-gray-300 hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!canDiscard"
              @click="discardAll"
            >
              Discard all
            </button>
          </div>
        </footer>

        <slot
          name="footer"
          :drafts="drafts"
          :loading="loading"
          :error="error"
          :refresh="() => emit('refresh')"
        />
      </div>
    </CbsDrawer>
  </div>
</template>
