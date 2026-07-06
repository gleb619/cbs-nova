<script setup lang="ts">
import type { TraceStep } from '~/types/execution'

const props = defineProps<{ steps: TraceStep[] }>()

interface TreeNode { step: TraceStep; children: TreeNode[] }

const tree = computed<TreeNode[]>(() => {
  const byId = new Map<string, TreeNode>()
  props.steps.forEach(s => byId.set(s.id, { step: s, children: [] }))
  const roots: TreeNode[] = []
  props.steps.forEach(s => {
    const node = byId.get(s.id)!
    if (s.parentId && byId.has(s.parentId)) {
      byId.get(s.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})
</script>

<template>
  <div class="bg-white border border-gray-200 rounded-lg p-3">
    <h2 class="text-sm font-semibold text-gray-700 mb-2">Trace</h2>
    <div v-if="tree.length === 0" class="text-sm text-gray-500 px-2 py-6 text-center">
      No trace steps recorded.
    </div>
    <template v-else>
      <template v-for="node in tree" :key="node.step.id">
        <ExecutionsTraceNode :step="node.step" :depth="0" />
        <ExecutionsTraceNode v-for="child in flatten(node)" :key="child.step.id" :step="child.step" :depth="child.depth" />
      </template>
    </template>
  </div>
</template>

<script lang="ts">
function flatten(node: { step: any; children: any[] }, depth = 0, out: { step: any; depth: number }[] = []): { step: any; depth: number }[] {
  for (const child of node.children) {
    out.push({ step: child.step, depth: depth + 1 })
    flatten(child, depth + 1, out)
  }
  return out
}
</script>
