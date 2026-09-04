<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useLocalStorageState } from '../../composables/useLocalStorageState'
import type { DslConstruct } from '../../types/dsl'
import CbsSpinner from '../CbsSpinner.vue'

const props = defineProps<{ construct: DslConstruct | null; loading?: boolean }>()
const emit = defineEmits<(e: 'update:description', description: string) => void>()

const isCollapsed = useLocalStorageState<boolean>('metadata-panel-collapsed', true)
const editedDescription = ref('')

watch(
  () => props.construct?.description,
  (description) => {
    editedDescription.value = description ?? ''
  },
  { immediate: true },
)

function toggleCollapsed() {
  isCollapsed.value = !isCollapsed.value
}

interface FieldRow {
  key: string
  label: string
  value: string
}

function formatValue(value: string | number | boolean | undefined | null): string {
  if (value === undefined || value === null || value === '') return '—'
  return String(value)
}

function formatBoolean(value: boolean | undefined | null): string {
  if (value === undefined || value === null) return '—'
  return value ? 'Yes' : 'No'
}

const summaryLabel = computed(() => {
  if (!props.construct) return 'Metadata'
  return `${props.construct.name} (${props.construct.type})`
})

const fields = computed<FieldRow[]>(() => {
  if (!props.construct) return []
  const c = props.construct
  return [
    { key: 'name', label: 'Name', value: formatValue(c.name) },
    { key: 'type', label: 'Type', value: formatValue(c.type) },
    { key: 'status', label: 'Status', value: formatValue(c.status) },
    { key: 'version', label: 'Version', value: formatValue(c.version) },
    { key: 'taskQueue', label: 'Task Queue', value: formatValue(c.taskQueue) },
    { key: 'inputType', label: 'Input Type', value: formatValue(c.inputType) },
    { key: 'outputType', label: 'Output Type', value: formatValue(c.outputType) },
    { key: 'hasCompensation', label: 'Has Compensation', value: formatBoolean(c.hasCompensation) },
  ]
})

function onDescriptionInput(event: Event) {
  const value = (event.target as HTMLTextAreaElement).value
  editedDescription.value = value
  emit('update:description', value)
}
</script>

<template>
  <section class="border-b border-neutral-200 bg-background" data-testid="metadata-panel">
    <header class="flex items-center justify-between gap-2 px-4 py-1 border-b border-neutral-200">
      <div
        class="flex items-center gap-2 text-sm text-neutral-800 min-w-0"
        data-testid="metadata-panel-summary"
        :aria-label="summaryLabel"
      >
        <h2
          v-if="!isCollapsed || !construct"
          class="text-sm font-semibold text-neutral-800"
        >
          Metadata
        </h2>
        <div v-else class="metadata-panel-summary-collapsed flex items-center gap-2">
          <span
            class="font-semibold truncate"
            :title="construct?.name"
            data-testid="metadata-field-name"
          >{{ construct?.name }}</span>
          <span class="text-neutral-300" aria-hidden="true">|</span>
          <span
            class="text-neutral-600 truncate"
            :title="construct?.type"
            data-testid="metadata-field-type"
          >{{ construct?.type }}</span>
        </div>
      </div>

      <CbsSpinner v-if="loading" size="sm" label="" />

      <button
        type="button"
        class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800 focus:outline-none focus:ring-2 focus:ring-primary-300"
        :aria-expanded="!isCollapsed"
        aria-controls="metadata-panel-body"
        data-testid="metadata-panel-toggle"
        @click="toggleCollapsed"
      >
        <span aria-hidden="true">{{ isCollapsed ? '▸' : '▾' }}</span>
        <span>{{ isCollapsed ? 'Expand' : 'Collapse' }}</span>
      </button>
    </header>

    <div v-if="!construct"/>

    <div
      v-else-if="!isCollapsed"
      id="metadata-panel-body"
      data-testid="metadata-panel-body"
      class="grid grid-cols-1 md:grid-cols-2 gap-4 px-4 py-3 text-sm"
    >
      <dl class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2">
        <template v-for="field in fields" :key="field.key">
          <dt class="text-neutral-500">{{ field.label }}</dt>
          <dd class="text-neutral-800 truncate" :data-testid="`metadata-field-${field.key}`">
            {{ field.value }}
          </dd>
        </template>
      </dl>

      <div class="flex flex-col gap-1 min-w-0" v-if="construct">
        <label for="metadata-description" class="text-neutral-500 text-sm">Description</label>
        <textarea
          id="metadata-description"
          v-model="editedDescription"
          data-testid="metadata-field-description"
          rows="6"
          class="w-full resize-y rounded border border-neutral-300 bg-surface px-3 py-2 text-neutral-800 placeholder:text-neutral-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
          placeholder="Add a description..."
          @input="onDescriptionInput"
        />
      </div>
    </div>
  </section>
</template>
