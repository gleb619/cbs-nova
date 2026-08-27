<script setup lang="ts">
import type { DslConstruct } from '../../types/dsl'

defineProps<{ construct: DslConstruct | null }>()
</script>

<template>
  <!-- TODO, use `frontend/components/src/composables/useLocalStorageState.ts` and add collapsible button -->
  <!-- TODO, refactor ux/ui, and add other fields from `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/model/DslIntrospectionModels.DefinitionMetaDto` -->
  <div class="p-4 border-b border-gray-200 bg-white" data-testid="metadata-panel">
    <div v-if="!construct" class="text-sm text-gray-500 italic">
      Select a construct to view metadata.
    </div>
    <dl v-else class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <dt class="text-gray-500">Name</dt>
      <dd class="text-gray-900 font-medium truncate" data-testid="metadata-field-name">{{ construct.name }}</dd>

      <dt class="text-gray-500">Type</dt>
      <dd class="text-gray-900" data-testid="metadata-field-type">{{ construct.type }}</dd>

      <dt class="text-gray-500">Status</dt>
      <dd class="text-gray-900" data-testid="metadata-field-status">{{ construct.status }}</dd>

      <dt class="text-gray-500">Version</dt>
      <dd class="text-gray-900" data-testid="metadata-field-version">{{ construct.version ?? '—' }}</dd>

      <dt class="text-gray-500">Task Queue</dt>
      <dd class="text-gray-900 truncate" data-testid="metadata-field-task-queue">{{ construct.taskQueue ?? '—' }}</dd>
    </dl>
  </div>
</template>
