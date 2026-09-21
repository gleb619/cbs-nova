<script setup lang="ts">
import { computed } from 'vue'
import type { ExplainReportNode } from '../../types/runner'

defineOptions({ name: 'ExplainReportChain' })

const props = defineProps<{
  report: ExplainReportNode
}>()

function buildPrimaryChain(node: ExplainReportNode): string[] {
  const chain: string[] = []
  const visited = new Set<string>()
  let current: ExplainReportNode | undefined = node
  while (current) {
    if (visited.has(current.name)) break
    visited.add(current.name)
    chain.push(current.name)
    current = current.children[0]
  }
  return chain
}

const chain = computed(() => buildPrimaryChain(props.report))
</script>

<template>
  <div
    v-if="chain.length > 0"
    data-testid="explain-report-chain"
    class="mb-4 p-3 rounded-sm border border-line bg-surface"
  >
    <div class="text-xs font-medium text-ink-muted mb-2">Execution chain</div>
    <div class="flex flex-wrap items-center gap-1">
      <template v-for="(name, index) in chain" :key="`${name}-${index}`">
        <span
          data-testid="explain-report-chain-node"
          class="inline-flex items-center px-2 py-1 rounded-sm border border-line bg-white font-mono text-xs text-ink"
        >
          {{ name }}
        </span>
        <span
          v-if="index < chain.length - 1"
          data-testid="explain-report-chain-arrow"
          class="text-xs text-ink-muted"
        >
          →
        </span>
      </template>
    </div>
  </div>
</template>
