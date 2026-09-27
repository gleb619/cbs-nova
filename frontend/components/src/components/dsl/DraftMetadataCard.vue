<script setup lang="ts">
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { DraftsMetadata } from '../../types/drafts'

defineProps<{
  metadata: DraftsMetadata | null
  loading?: boolean
  error?: string | null
}>()

const useDraftMetadataStorage = createNamespacedLocalStorageState('cbs-nova:draft-metadata')
const expanded = useDraftMetadataStorage('expanded', false)

function toggle() {
  expanded.value = !expanded.value
}
</script>

<template>
  <section
    data-testid="draft-metadata-card"
    class="border-b border-gray-800 px-3 py-3 text-xs text-gray-400"
  >
    <div class="flex items-center justify-between">
      <span class="text-xs uppercase tracking-wide text-gray-500">Workspace</span>
      <button
        type="button"
        data-testid="draft-metadata-card-toggle"
        class="shrink-0 inline-flex items-center justify-center w-6 h-6 rounded-sm border border-gray-700 text-gray-300 hover:bg-gray-800"
        :aria-expanded="expanded"
        aria-controls="draft-metadata-card-details"
        :aria-label="expanded ? 'Collapse workspace info' : 'Expand workspace info'"
        :title="expanded ? 'Collapse' : 'Expand'"
        @click="toggle"
      >
        {{ expanded ? '▾' : '▸' }}
      </button>
    </div>

    <p v-if="error" class="text-red-400" data-testid="draft-metadata-card-error">
      {{ error }}
    </p>
    <p
      v-else-if="loading && !metadata"
      class="text-gray-500"
      data-testid="draft-metadata-card-loading"
    >
      Loading workspace info…
    </p>
    <dl
      v-else-if="metadata"
      v-show="expanded"
      id="draft-metadata-card-details"
      class="grid grid-cols-2 gap-x-3 gap-y-1"
      data-testid="draft-metadata-card-details"
    >
      <dt class="text-gray-500">Location</dt>
      <dd
        class="truncate font-mono text-gray-300 max-w-[150px] overflow-hidden flex justify-end"
        data-testid="draft-metadata-card-path"
        :title="metadata.sourcePath"
      >
        <span class="whitespace-nowrap select-all"> ...{{ metadata.sourcePath }} </span>
      </dd>

      <dt v-if="metadata.workspaceMode" class="text-gray-500">Mode</dt>
      <dd
        v-if="metadata.workspaceMode"
        class="font-mono text-gray-300"
        data-testid="draft-metadata-card-mode"
      >
        {{ metadata.workspaceMode }}
      </dd>

      <dt class="text-gray-500">Drafts</dt>
      <dd class="text-gray-300" data-testid="draft-metadata-card-count">
        {{ metadata.draftCount }}
      </dd>

      <dt class="text-gray-500">Size</dt>
      <dd class="text-gray-300" data-testid="draft-metadata-card-size">
        {{ metadata.sizeMb != null ? `${metadata.sizeMb} MB` : '—' }}
      </dd>

      <dt class="text-gray-500">Branch</dt>
      <dd class="font-mono text-gray-300" data-testid="draft-metadata-card-branch">
        {{ metadata.gitBranch ?? '—' }}
      </dd>

      <dt class="text-gray-500">Git</dt>
      <dd class="text-gray-300" data-testid="draft-metadata-card-git">
        {{ metadata.gitEnabled ? 'enabled' : 'disabled' }}
      </dd>

      <dt class="text-gray-500">Cache TTL</dt>
      <dd class="text-gray-300" data-testid="draft-metadata-card-cache-ttl">
        {{ metadata.statusCacheTtlSeconds }}s
      </dd>
    </dl>
  </section>
</template>
