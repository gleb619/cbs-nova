<script setup lang="ts">
import ErrorList from '../ErrorList.vue'

defineProps<{
  errors: Array<{ message: string; code?: string; stackTrace?: string }> | undefined
}>()
</script>

<template>
  <div data-testid="executions-errors-tab">
    <ErrorList
      :items="errors"
      variant="executions"
      empty-text="No errors recorded."
      code-style="text"
      :show-stack-trace="true"
    />
  </div>
</template>
