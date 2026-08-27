<script setup lang="ts">
import { computed } from 'vue'

import type { CallNode } from '../../types/runner'

type FlatCall = {
  node: { name: string; kind: string }
  call: Record<string, unknown>
}

const props = defineProps<{
  tree: CallNode | undefined
}>()

const flatten = (node: CallNode | undefined, acc: FlatCall[] = []): FlatCall[] => {
  if (!node) return acc
  for (const call of node.externalCalls ?? []) {
    acc.push({ node: { name: node.name, kind: node.kind }, call })
  }
  for (const child of node.children ?? []) {
    flatten(child, acc)
  }
  return acc
}

const list = computed(() => flatten(props.tree))

const typeClass = (type: string) => {
  switch (type) {
    case 'database':
      return 'text-blue-600 bg-blue-50'
    case 'http':
      return 'text-green-600 bg-green-50'
    case 'mq':
      return 'text-purple-600 bg-purple-50'
    case 'filesystem':
      return 'text-yellow-700 bg-yellow-50'
    case 'external_api':
      return 'text-pink-600 bg-pink-50'
    case 'microservice':
      return 'text-indigo-600 bg-indigo-50'
    case 'activity':
      return 'text-cyan-600 bg-cyan-50'
    default:
      return 'text-gray-500 bg-gray-100'
  }
}

const formatTimestamp = (ts: unknown): string => {
  if (typeof ts !== 'number' || !Number.isFinite(ts)) return String(ts ?? '')
  try {
    return new Date(ts).toISOString()
  } catch {
    return String(ts)
  }
}
</script>

<template>
  <div data-testid="external-calls-tab">
    <div v-if="list.length === 0" class="text-sm text-gray-500">No external calls captured.</div>

    <div v-else class="flex flex-col gap-2">
      <div class="flex items-center justify-between gap-2">
        <span
          class="text-xs font-medium px-2 py-1 rounded-full bg-gray-100 text-gray-700"
          data-testid="external-calls-count"
        >
          {{ list.length }}
          external call{{ list.length === 1 ? '' : 's' }}
        </span>
      </div>

      <ul
        class="max-h-[60vh] overflow-auto space-y-1 border border-gray-200 rounded-lg p-2"
        data-testid="external-calls-list"
      >
        <li
          v-for="(item, idx) in list"
          :key="idx"
          class="font-mono text-xs flex items-start gap-2 py-1"
          data-testid="external-calls-row"
        >
          <span
            class="px-1.5 py-0.5 rounded font-semibold shrink-0"
            :class="typeClass(String(item.call.type ?? ''))"
            :data-type="String(item.call.type ?? '')"
          >
            {{ String(item.call.type ?? 'other') }}
          </span>
          <span class="text-gray-700 shrink-0">{{ String(item.call.target ?? '') }}</span>
          <span class="text-gray-400 shrink-0">—</span>
          <span class="text-gray-900 break-words">{{ String(item.call.operation ?? '') }}</span>
          <span class="text-gray-500 shrink-0 ml-auto"
            >[{{ formatTimestamp(item.call.timestamp) }}]</span
          >
          <span class="text-gray-400 shrink-0">·</span>
          <span class="text-gray-600 shrink-0">{{ item.node.kind }}: {{ item.node.name }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
