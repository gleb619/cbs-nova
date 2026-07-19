<script setup lang="ts">
import type { CallNode } from '../../types/runner'

const props = defineProps<{
  tree: CallNode | undefined
}>()
</script>

<template>
  <div>
    <div v-if="!props.tree" class="text-sm text-gray-500">No call tree available.</div>
    <CallTreeNode v-else :node="props.tree" :depth="0" />
  </div>
</template>
