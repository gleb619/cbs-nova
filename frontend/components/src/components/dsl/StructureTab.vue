<script setup lang="ts">
import type { ObjectStructureDto } from '../../types/dsl'
import CbsSpinner from '../CbsSpinner.vue'
import ErrorBanner from '../ErrorBanner.vue'
import StructureFieldsTable from './StructureFieldsTable.vue'

withDefaults(
  defineProps<{
    structure?: ObjectStructureDto | null
    structureLoading?: boolean
    structureError?: string | null
  }>(),
  {
    structure: null,
    structureLoading: false,
    structureError: null,
  },
)

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="p-3" data-testid="structure-tab">
    <div v-if="structureLoading" class="flex justify-center py-6" data-testid="structure-loading">
      <CbsSpinner size="sm" label="Loading structure…" class="text-gray-500" />
    </div>
    <div v-else-if="structureError" class="py-2" data-testid="structure-error">
      <ErrorBanner :message="structureError" @retry="emit('retry')" />
    </div>
    <template v-else>
      <StructureFieldsTable v-if="structure" :structure="structure" />
      <div v-else class="text-sm text-gray-500 italic py-6 text-center">
        No structure available for this object.
      </div>
    </template>
  </div>
</template>
