<script setup lang="ts">
import type { Execution } from '~/types/execution'

defineProps<{ executions: Execution[]; loading: boolean }>()

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
</script>

<template>
  <div class="bg-white border border-gray-200 rounded-lg overflow-hidden">
    <table class="min-w-full text-sm">
      <thead class="bg-gray-50 text-left text-xs uppercase text-gray-600">
        <tr>
          <th class="px-3 py-2">ID</th>
          <th class="px-3 py-2">Entity</th>
          <th class="px-3 py-2">Mode</th>
          <th class="px-3 py-2">Status</th>
          <th class="px-3 py-2">Started</th>
          <th class="px-3 py-2">Duration</th>
          <th class="px-3 py-2">Retries</th>
          <th class="px-3 py-2">Triggered by</th>
        </tr>
      </thead>
      <tbody>
        <template v-if="loading">
          <tr v-for="i in 5" :key="i" class="border-t border-gray-100">
            <td v-for="j in 8" :key="j" class="px-3 py-2">
              <div class="h-3 bg-gray-200 rounded animate-pulse" />
            </td>
          </tr>
        </template>
        <template v-else-if="executions.length === 0">
          <tr>
            <td colspan="8" class="px-3 py-12 text-center text-gray-500">
              No executions match current filters.
            </td>
          </tr>
        </template>
        <template v-else>
          <tr
            v-for="exec in executions"
            :key="exec.id"
            class="border-t border-gray-100 hover:bg-gray-50 cursor-pointer"
            @click="$emit('select', exec.id)"
          >
            <td class="px-3 py-2 font-mono text-xs">{{ truncate(exec.id) }}</td>
            <td class="px-3 py-2">
              {{ exec.entity }} <span class="text-gray-400 text-xs">({{ exec.entityType }})</span>
            </td>
            <td class="px-3 py-2 font-mono text-xs">{{ exec.mode }}</td>
            <td class="px-3 py-2"><ExecutionsStatusBadge :status="exec.status" /></td>
            <td class="px-3 py-2 text-xs">{{ formatDate(exec.startedAt) }}</td>
            <td class="px-3 py-2 text-xs">{{ formatDuration(exec.duration) }}</td>
            <td class="px-3 py-2 text-xs">{{ exec.retries ?? 0 }}</td>
            <td class="px-3 py-2 text-xs">{{ exec.triggeredBy ?? '—' }}</td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
