<script setup lang="ts">
import ErrorList from '../ErrorList.vue'

interface ErrorItem {
  message: string
  code?: string
}

defineProps<{ errors: ErrorItem[] | undefined }>()
</script>

<template>
  <div data-testid="runner-errors-tab">
    <ErrorList :items="errors" variant="runner" empty-text="No errors." code-style="badge" />
  </div>
</template>
