<script setup lang="ts">
import type { Execution } from '../../types/execution'

withDefaults(
  defineProps<{
    executions: Execution[]
    loading?: boolean
  }>(),
  { loading: false },
)

const emit = defineEmits<{
  select: [id: string]
}>()

function onRowActivate(_event: KeyboardEvent | MouseEvent, id: string) {
  emit('select', id)
}

function formatDate(s?: string) {
  if (!s) return '—'
  return new Date(s).toLocaleString()
}
</script>

<template>
  <div
    data-testid="recent-runs-table"
    class="bg-white border border-gray-200 rounded-lg overflow-hidden"
  >
    <table class="min-w-full text-sm">
      <thead class="bg-gray-50 text-left text-xs uppercase text-gray-600">
        <tr>
          <th class="px-3 py-2">Process</th>
          <th class="px-3 py-2">Status</th>
          <th class="px-3 py-2">Started</th>
          <th class="px-3 py-2">Finished</th>
        </tr>
      </thead>
      <tbody>
        <template v-if="loading">
          <tr v-for="i in 5" :key="i" class="border-t border-gray-100">
            <td v-for="j in 4" :key="j" class="px-3 py-2">
              <div class="h-3 bg-gray-200 rounded animate-pulse" />
            </td>
          </tr>
        </template>
        <template v-else-if="executions.length === 0">
          <tr>
            <td colspan="4" class="px-3 py-12 text-center text-gray-500">
              No recent executions.
            </td>
          </tr>
        </template>
        <template v-else>
          <tr
            v-for="exec in executions"
            :key="exec.id"
            :data-testid="`recent-runs-table-row-${exec.id}`"
            :aria-label="`Open run ${exec.id}`"
            tabindex="0"
            role="button"
            class="border-t border-gray-100 hover:bg-gray-50 cursor-pointer focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-accent-500"
            @click="onRowActivate($event, exec.id)"
            @keydown.enter="onRowActivate($event, exec.id)"
            @keydown.space.prevent="onRowActivate($event, exec.id)"
          >
            <td class="px-3 py-2">{{ exec.entity }}</td>
            <td class="px-3 py-2">
              <ExecutionsStatusBadge :status="exec.status" />
            </td>
            <td class="px-3 py-2 text-xs">{{ formatDate(exec.startedAt) }}</td>
            <td class="px-3 py-2 text-xs">{{ formatDate(exec.completedAt) }}</td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
