<script setup lang="ts">
import type { GitInfo } from '../../types/buildInfo'

withDefaults(
  defineProps<{
    priority?: 1 | 2 | 3
    gitInfo: GitInfo | null
    commitTime: string
  }>(),
  { priority: 2 },
)
</script>

<template>
  <!-- Priority 2 is hidden below the sm breakpoint (640px). Branch/commit/time facts are secondary context that disappears on phones. -->
  <div
    v-if="gitInfo"
    :class="[
      'flex-wrap items-center justify-center gap-x-2 gap-y-1 text-xs text-neutral-500',
      priority === 3 ? 'hidden md:flex' : priority === 2 ? 'hidden sm:flex' : 'flex',
    ]"
    data-testid="app-footer-git"
    data-priority="2"
  >
    <span v-if="gitInfo.branch" class="font-medium">{{ gitInfo.branch }}</span>
    <code
      v-if="gitInfo.commit?.id"
      class="rounded bg-neutral-100 px-1 font-mono"
      :title="gitInfo.commit['id.full'] ?? ''"
    >
      {{ gitInfo.commit.id }}
    </code>
    <span
      v-if="gitInfo.commit?.message?.short"
      class="max-w-xs truncate"
      :title="gitInfo.commit.message.short"
    >
      {{ gitInfo.commit.message.short }}
    </span>
    <span v-if="commitTime" title="Commit time">({{ commitTime }})</span>
    <span v-if="gitInfo.dirty" class="text-warning-600">(dirty)</span>
  </div>
</template>
