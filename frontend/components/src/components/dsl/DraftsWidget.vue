<script setup lang="ts">
/**
 * Source-control panel for the DSL workspace.
 *
 * Looks and behaves like a regular git client: a header with remote operations
 * (Fetch / Pull / Rebase) and a change count, a body listing the changed files
 * with their git status, and a footer with the commit message box plus the
 * commit / discard actions.
 *
 * The panel is a positioned dropdown so the same component works in the admin
 * navbar and as a page section. Every operation is an emit: this component owns
 * no network access of its own.
 */
import { computed, onMounted, ref } from 'vue'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'
import type { DraftsMetadata } from '../../types/drafts'
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
    panelWidthClass?: string
    /** @deprecated use `title`; kept so existing call sites keep working. */
    drawerTitle?: string
    /** @deprecated use `panelWidthClass`. */
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
    title: '',
    panelWidthClass: '',
    drawerTitle: undefined,
    drawerWidthClass: undefined,
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
const expanded = useDraftsWidgetStorage('expanded', false)

const commitMessage = ref('')

const heading = computed(() => props.title || props.drawerTitle || 'Source Control')
const widthClass = computed(() => props.panelWidthClass || props.drawerWidthClass || 'w-96')

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

function togglePanel() {
  expanded.value = !expanded.value
  if (expanded.value) emit('open')
}

function handleSelect(name: string) {
  emit('select', name)
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
  <div data-testid="dsl-saved-drafts-widget" class="relative inline-block align-middle">
    <!-- Header -->
    <div
      class="flex items-center gap-2 rounded-md border border-neutral-300 bg-white px-2.5 py-1 text-xs text-neutral-700 shadow-sm"
    >
      <button
        type="button"
        data-testid="dsl-saved-drafts-widget-details"
        class="flex items-center gap-1.5 font-medium uppercase tracking-wide text-neutral-500 hover:text-neutral-800"
        :aria-expanded="expanded"
        aria-controls="dsl-saved-drafts-panel-body"
        aria-label="Toggle source control panel"
        :title="expanded ? 'Collapse source control' : 'Expand source control'"
        @click="togglePanel"
      >
        <span aria-hidden="true">{{ expanded ? '▾' : '▸' }}</span>
        {{ heading }}
      </button>
      <span
        class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-semibold"
        :class="error ? 'bg-red-100 text-red-700' : 'bg-neutral-200 text-neutral-700'"
        data-testid="dsl-saved-drafts-widget-count"
        :title="error ?? undefined"
      >
        {{ count }}
      </span>
      <span class="h-4 w-px bg-neutral-300" aria-hidden="true" />
      <button
        type="button"
        class="px-2 py-0.5 rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
        data-testid="dsl-saved-drafts-fetch"
        aria-label="Fetch from remote"
        title="Fetch from remote"
        :disabled="loading || remoteBusy || gitUnavailable"
        @click="emit('fetch')"
      >
        {{ fetching ? 'Fetching…' : 'Fetch' }}
      </button>
      <button
        type="button"
        class="px-2 py-0.5 rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
        data-testid="dsl-saved-drafts-pull"
        aria-label="Pull from remote"
        title="Pull from remote"
        :disabled="loading || remoteBusy || gitUnavailable"
        @click="emit('pull')"
      >
        {{ pulling ? 'Pulling…' : 'Pull' }}
      </button>
      <button
        type="button"
        class="px-2 py-0.5 rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
        data-testid="dsl-saved-drafts-rebase"
        aria-label="Rebase onto remote"
        title="Rebase onto remote"
        :disabled="loading || remoteBusy || gitUnavailable"
        @click="emit('rebase')"
      >
        {{ rebasing ? 'Rebasing…' : 'Rebase' }}
      </button>
      <button
        type="button"
        class="px-2 py-0.5 rounded border border-neutral-300 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
        data-testid="dsl-saved-drafts-drawer-refresh"
        aria-label="Refresh changes"
        :title="loading ? 'Refreshing' : 'Refresh'"
        :disabled="loading"
        @click="emit('refresh')"
      >
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>

    <!-- Body + footer: the git-client panel -->
    <section
      v-if="expanded"
      data-testid="dsl-saved-drafts-panel"
      class="absolute right-0 top-full z-40 mt-1 flex max-h-[70vh] flex-col overflow-hidden rounded-md border border-gray-800 bg-gray-900 text-gray-100 shadow-xl"
      :class="widthClass"
    >
      <div
        id="dsl-saved-drafts-panel-body"
        data-testid="dsl-saved-drafts-drawer"
        class="flex min-h-0 flex-1 flex-col overflow-y-auto"
      >
        <slot
          name="header"
          :drafts="drafts"
          :loading="loading"
          :error="error"
          :refresh="() => emit('refresh')"
        />

        <DraftMetadataCard :metadata="metadata" :loading="metadataLoading" :error="metadataError" />

        <p
          v-if="error"
          class="px-3 py-2 text-xs text-red-300 bg-red-950/40 border-b border-red-900"
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
            title="Commit the staged changes"
            class="flex-1 rounded border border-accent-500 bg-accent-500 px-2 py-1 text-xs font-medium text-white hover:bg-accent-600 disabled:opacity-50 disabled:cursor-not-allowed"
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
            class="flex-1 rounded border border-gray-700 px-2 py-1 text-xs text-gray-300 hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed"
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
    </section>
  </div>
</template>
