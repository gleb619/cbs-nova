<script setup lang="ts">
import { computed } from 'vue'
import type { DslConstruct } from '../../types/dsl'
import { useLocalStorageState } from '../../composables/useLocalStorageState'

const props = defineProps<{ construct: DslConstruct | null }>()

const isCollapsed = useLocalStorageState<boolean>('metadata-panel-collapsed', false)

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
    { key: 'description', label: 'Description', value: formatValue(c.description) },
  ]
})

const summaryFields = computed<FieldRow[]>(() => fields.value.filter((f) => f.key === 'name' || f.key === 'type'))
</script>

<template>
  <section
    class="border-b border-neutral-200 bg-background"
    data-testid="metadata-panel"
  >
    <header class="flex items-center justify-between px-4 py-2 border-b border-neutral-200">
      <h2 class="text-sm font-semibold text-neutral-800">Metadata</h2>
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800 focus:outline-none focus:ring-2 focus:ring-primary-300"
        :aria-expanded="!isCollapsed"
        aria-controls="metadata-panel-body"
        data-testid="metadata-panel-toggle"
        @click="toggleCollapsed"
      >
        <span aria-hidden="true">{{ isCollapsed ? '▸' : '▾' }}</span>
        <span>{{ isCollapsed ? 'Expand' : 'Collapse' }}</span>
      </button>
    </header>

    <div v-if="!construct" class="px-4 py-3 text-sm text-neutral-500 italic">
      Select a construct to view metadata.
    </div>

    <dl
      v-else-if="!isCollapsed"
      id="metadata-panel-body"
      data-testid="metadata-panel-body"
      class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 px-4 py-3 text-sm"
    >
      <template v-for="field in fields" :key="field.key">
        <dt class="text-neutral-500">{{ field.label }}</dt>
        <dd
          class="text-neutral-800"
          :class="{ 'truncate': field.key !== 'description', 'whitespace-pre-wrap': field.key === 'description' }"
          :data-testid="`metadata-field-${field.key}`"
        >
          {{ field.value }}
        </dd>
      </template>
    </dl>

    <dl
      v-else
      id="metadata-panel-body"
      data-testid="metadata-panel-summary"
      class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 px-4 py-2 text-sm"
    >
      <template v-for="field in summaryFields" :key="field.key">
        <dt class="text-neutral-500">{{ field.label }}</dt>
        <dd
          class="text-neutral-800 font-medium truncate"
          :data-testid="`metadata-field-${field.key}`"
        >
          {{ field.value }}
        </dd>
      </template>
    </dl>
  </section>
</template>
