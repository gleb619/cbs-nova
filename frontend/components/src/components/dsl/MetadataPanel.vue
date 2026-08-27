<script setup lang="ts">
import type { DslConstruct } from '../../types/dsl'

defineProps<{ construct: DslConstruct | null }>()
</script>

<template>
  <!-- TODO, use `frontend/components/src/composables/useLocalStorageState.ts` and add collapsible button -->
  <!-- TODO, refactor ux/ui, and add other fields from `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/model/DslIntrospectionModels.DefinitionMetaDto` -->
  <div class="p-4 border-b border-gray-200 bg-white">
    <div v-if="!construct" class="text-sm text-gray-500 italic">
      Select a construct to view metadata.
    </div>
    <dl v-else class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <dt class="text-gray-500">Name</dt>
      <dd class="text-gray-900 font-medium truncate">{{ construct.name }}</dd>

      <dt class="text-gray-500">Type</dt>
      <dd class="text-gray-900">{{ construct.type }}</dd>

      <dt class="text-gray-500">Status</dt>
      <dd class="text-gray-900">{{ construct.status }}</dd>

      <dt class="text-gray-500">Version</dt>
      <dd class="text-gray-900">{{ construct.version ?? '—' }}</dd>

      <dt class="text-gray-500">Task Queue</dt>
      <dd class="text-gray-900 truncate">{{ construct.taskQueue ?? '—' }}</dd>
    </dl>
    <p class="mt-3 text-xs text-gray-400 italic">Editable metadata fields coming soon.</p>
  </div>
</template>
