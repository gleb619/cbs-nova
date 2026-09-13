<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { ActivityFeed } from '@cbs/components'
import { resolveComponent } from 'vue'

const NuxtLink = resolveComponent('NuxtLink')

const dslApi = useDslApi()

function executionLink(aggregateId: string): string | null {
  return `/executions/${aggregateId}`
}
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col" data-testid="activity-page">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">Activity</h1>
      <p class="text-sm text-neutral-600">
        Domain events recorded by the engine — runs, drafts and reloads — newest first.
      </p>
    </header>

    <div class="flex-1 min-h-0 bg-white border border-neutral-200 rounded-lg overflow-hidden">
      <ActivityFeed
        :fetch-events="dslApi.fetchEvents"
        :execution-link="executionLink"
        :link-component="NuxtLink"
      />
    </div>
  </div>
</template>
