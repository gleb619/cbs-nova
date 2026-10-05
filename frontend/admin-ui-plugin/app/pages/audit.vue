<script setup lang="ts">
import { useAuditTrail } from '@cbs/admin-ui-plugin/composables/useAuditTrail'
import { AuditTrailTable } from '@cbs/components'
import { onMounted } from 'vue'

const {
  entries,
  loading,
  error,
  total,
  offset,
  limit,
  hasPrev,
  hasNext,
  actionInput,
  load,
  applyActionFilter,
  clearActionFilter,
  goPrev,
  goNext,
  refresh,
} = useAuditTrail()

onMounted(() => {
  void load(0)
})
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col" data-testid="audit-page">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">Audit trail</h1>
      <p class="text-sm text-neutral-600">
        Append-only control-plane actions recorded by the engine, newest first.
      </p>
    </header>

    <div class="flex-1 min-h-0 bg-white border border-neutral-200 rounded-lg overflow-hidden">
      <AuditTrailTable
        :entries="entries"
        :loading="loading"
        :error="error"
        :action-filter="actionInput"
        :total="total"
        :offset="offset"
        :limit="limit"
        :has-prev="hasPrev"
        :has-next="hasNext"
        @update:action-filter="actionInput = $event"
        @apply-action-filter="applyActionFilter"
        @clear-action-filter="clearActionFilter"
        @prev="goPrev"
        @next="goNext"
        @refresh="refresh"
      />
    </div>
  </div>
</template>
