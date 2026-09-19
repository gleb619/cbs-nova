<script setup lang="ts">
import { computed } from 'vue'
import type { PromoteEntryResult, PromotionOutcome } from '../../types/promotion'

const props = defineProps<{
  results: PromoteEntryResult[]
  loading?: boolean
}>()

const LABELS: Record<PromotionOutcome, string> = {
  created: 'CREATED',
  updated: 'UPDATED',
  unchanged: 'UNCHANGED',
  skipped: 'SKIPPED',
  published: 'PUBLISHED',
  failed: 'FAILED',
}

const BADGE_CLASS: Record<PromotionOutcome, string> = {
  created: 'bg-success-100 text-success-800',
  updated: 'bg-warning-100 text-warning-800',
  unchanged: 'bg-neutral-100 text-neutral-600',
  skipped: 'bg-neutral-100 text-neutral-500',
  published: 'bg-success-100 text-success-800',
  failed: 'bg-error-100 text-error-800',
}

const sorted = computed(() =>
  [...props.results].sort((a, b) => a.name.localeCompare(b.name)),
)
</script>

<template>
  <div class="overflow-hidden rounded-lg border border-neutral-200 bg-white">
    <table class="min-w-full divide-y divide-neutral-200" data-testid="promotion-diff-table">
      <thead class="bg-neutral-50">
        <tr>
          <th class="px-4 py-2 text-left text-xs font-semibold text-neutral-600">Definition</th>
          <th class="px-4 py-2 text-left text-xs font-semibold text-neutral-600">Outcome</th>
          <th class="px-4 py-2 text-left text-xs font-semibold text-neutral-600">Message</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-neutral-100">
        <tr v-if="loading" data-testid="promotion-diff-loading">
          <td colspan="3" class="px-4 py-3 text-sm text-neutral-500">Loading diff…</td>
        </tr>
        <tr v-else-if="sorted.length === 0" data-testid="promotion-diff-empty">
          <td colspan="3" class="px-4 py-3 text-sm text-neutral-500">
            No definitions selected for promotion.
          </td>
        </tr>
        <tr v-for="row in sorted" :key="row.name" :data-testid="`promotion-diff-row-${row.name}`">
          <td class="px-4 py-2 text-sm text-neutral-900">{{ row.name }}</td>
          <td class="px-4 py-2 text-sm">
            <span
              class="inline-flex rounded-full px-2 py-0.5 text-xs font-semibold"
              :class="BADGE_CLASS[row.outcome]"
              :data-testid="`promotion-outcome-${row.outcome}`"
            >
              {{ LABELS[row.outcome] }}
            </span>
          </td>
          <td class="px-4 py-2 text-sm text-neutral-600">{{ row.message ?? '' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
