<script setup lang="ts">
import type { DraftsMetadata } from '../../types/drafts'

defineProps<{
  metadata: DraftsMetadata | null
  loading?: boolean
  error?: string | null
}>()
</script>

<template>
  <section
    data-testid="draft-metadata-card"
    class="border-b border-gray-800 px-3 py-3 text-xs text-gray-400"
  >
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
      class="grid grid-cols-2 gap-x-3 gap-y-1"
      data-testid="draft-metadata-card-details"
    >
      <dt class="text-gray-500">Location</dt>
      <dd class="truncate font-mono text-gray-300" data-testid="draft-metadata-card-path">
        {{ metadata.sourcePath }}
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

      <dt class="text-gray-500">History limit</dt>
      <dd class="text-gray-300" data-testid="draft-metadata-card-history-limit">
        {{ metadata.historyLimit }}
      </dd>
    </dl>
  </section>
</template>
