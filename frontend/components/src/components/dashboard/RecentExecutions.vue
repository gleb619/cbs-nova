<script setup lang="ts">
import type { ExecutionStatus } from '../../types/execution'

interface RecentExecution {
  id: string
  entity: string
  status: ExecutionStatus
  startedAt: string
}

defineProps<{
  executions: RecentExecution[]
  loading?: boolean
}>()

const emit = defineEmits<(e: 'select', id: string) => void>()

function onSelect(id: string) {
  emit('select', id)
}

function statusClass(status: ExecutionStatus) {
  switch (status) {
    case 'SUCCESS':
      return 'bg-green-100 text-green-800'
    case 'FAILED':
      return 'bg-red-100 text-red-800'
    case 'RUNNING':
      return 'bg-yellow-100 text-yellow-800'
    default:
      return 'bg-gray-100 text-gray-800'
  }
}
</script>

<template>
  <div class="space-y-4">
    <h2 class="text-xl font-semibold text-gray-800">Recent Executions</h2>
    <div v-if="loading" class="text-gray-600">Loading...</div>
    <div v-else-if="!executions.length" class="text-gray-500">No recent executions.</div>
    <table v-else class="min-w-full divide-y divide-gray-200">
      <thead class="bg-gray-50">
        <tr>
          <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
          <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
          <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Timestamp</th>
        </tr>
      </thead>
      <tbody class="bg-white divide-y divide-gray-200">
        <tr
          v-for="exec in executions"
          :key="exec.id"
          class="hover:bg-gray-50 cursor-pointer"
          @click="onSelect(exec.id)"
        >
          <td class="px-4 py-2 text-sm text-gray-700">{{ exec.entity }}</td>
          <td class="px-4 py-2">
            <span
              :class="['px-2 inline-flex text-xs leading-5 font-semibold rounded-full', statusClass(exec.status)]"
            >
              {{ exec.status }}
            </span>
          </td>
          <td class="px-4 py-2 text-sm text-gray-500">
            {{ new Date(exec.startedAt).toLocaleString() }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
