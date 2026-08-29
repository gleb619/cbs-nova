<script setup lang="ts">
import type { Execution } from '../../types/execution'

const props = withDefaults(
  defineProps<{
    executions: Execution[]
    loading: boolean
    /**
     * T199: ids of rows whose backend is currently being polled because
     * they are in the Stale state. The list renders a pulse indicator
     * on the status badge for these rows so the operator can see that
     * auto-refresh is in flight.
     */
    stalePollingIds?: string[] | Set<string>
    /**
     * T281: ids of rows with an in-flight cancel request. The list dims
     * the cancel button on those rows while the BFF round-trip is pending.
     */
    cancellingIds?: string[] | Set<string>
  }>(),
  {
    stalePollingIds: () => [] as string[],
    cancellingIds: () => [] as string[],
  },
)

defineEmits<{
  select: [id: string]
  cancel: [id: string]
}>()

function truncate(id: string, len = 8) {
  return id.length > len ? `${id.slice(0, len)}…` : id
}

function formatDate(s?: string) {
  if (!s) return '—'
  return new Date(s).toLocaleString()
}

function formatDuration(ms?: number) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

function isStalePolling(id: string): boolean {
  const ids = props.stalePollingIds
  if (!ids) return false
  if (Array.isArray(ids)) return ids.includes(id)
  return ids.has(id)
}

function isCancelling(id: string): boolean {
  const ids = props.cancellingIds
  if (!ids) return false
  if (Array.isArray(ids)) return ids.includes(id)
  return ids.has(id)
}
</script>

<template>
  <div
    data-testid="execution-list"
    class="bg-white border border-gray-200 rounded-lg overflow-hidden"
  >
    <table class="min-w-full text-sm">
      <caption class="sr-only">
        Executions
      </caption>
      <thead class="bg-gray-50 text-left text-xs uppercase text-gray-600">
        <tr>
          <th scope="col" class="px-3 py-2">ID</th>
          <th scope="col" class="px-3 py-2">Entity</th>
          <th scope="col" class="px-3 py-2">Mode</th>
          <th scope="col" class="px-3 py-2">Status</th>
          <th scope="col" class="px-3 py-2">Started</th>
          <th scope="col" class="px-3 py-2">Duration</th>
          <th scope="col" class="px-3 py-2">Retries</th>
          <th scope="col" class="px-3 py-2">Triggered by</th>
          <th scope="col" class="px-3 py-2 text-right">Actions</th>
        </tr>
      </thead>
      <tbody>
        <template v-if="loading">
          <tr v-for="i in 5" :key="i" class="border-t border-gray-100">
            <td v-for="j in 9" :key="j" class="px-3 py-2">
              <div class="h-3 bg-gray-200 rounded animate-pulse" />
            </td>
          </tr>
        </template>
        <template v-else-if="executions.length === 0">
          <tr>
            <td colspan="9" class="px-3 py-12 text-center text-gray-500">
              No executions match current filters.
            </td>
          </tr>
        </template>
        <template v-else>
          <tr
            v-for="exec in executions"
            :key="exec.id"
            :data-testid="`execution-list-row-${exec.id}`"
            class="border-t border-gray-100 hover:bg-gray-50 cursor-pointer"
            @click="$emit('select', exec.id)"
          >
            <td class="px-3 py-2 font-mono text-xs">{{ truncate(exec.id) }}</td>
            <td class="px-3 py-2">
              {{ exec.entity }} <span class="text-gray-400 text-xs">({{ exec.entityType }})</span>
            </td>
            <td class="px-3 py-2 font-mono text-xs">{{ exec.mode }}</td>
            <td class="px-3 py-2">
              <ExecutionsStatusBadge :status="exec.status" :polling="isStalePolling(exec.id)" />
            </td>
            <td class="px-3 py-2 text-xs">{{ formatDate(exec.startedAt) }}</td>
            <td class="px-3 py-2 text-xs">{{ formatDuration(exec.duration) }}</td>
            <td class="px-3 py-2 text-xs">{{ exec.retries ?? 0 }}</td>
            <td class="px-3 py-2 text-xs">{{ exec.triggeredBy ?? '—' }}</td>
            <td class="px-3 py-2 text-right">
              <button
                v-if="exec.status === 'Running'"
                type="button"
                :data-testid="`execution-list-row-cancel-${exec.id}`"
                class="px-2 py-1 text-xs font-medium rounded border transition-colors"
                :class="
                  isCancelling(exec.id)
                    ? 'border-red-200 bg-red-50 text-red-400 cursor-not-allowed'
                    : 'border-red-300 bg-white text-red-700 hover:bg-red-50'
                "
                :disabled="isCancelling(exec.id)"
                @click.stop="$emit('cancel', exec.id)"
              >
                {{ isCancelling(exec.id) ? 'Cancelling…' : 'Cancel' }}
              </button>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
