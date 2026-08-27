<script setup lang="ts">
import type { CallNode } from '../../types/runner'

const props = defineProps<{
  tree: CallNode | undefined
}>()
</script>

<template>
  <div data-testid="call-tree-tab">
    <div v-if="!props.tree" class="text-sm text-gray-500">No call tree available.</div>
    <div v-else data-testid="call-tree">
      <CallTreeNode :node="props.tree" :depth="0" />
    </div>
  </div>
</template>
