<script setup lang="ts">
import type { LogicInfoDto, ObjectStructureDto } from '../../types/dsl'

defineProps<{ structure: ObjectStructureDto }>()

function isArrayElement(path: string): boolean {
  return path.includes('[')
}

function rowTestId(path: string): string {
  return `structure-field-${path.replaceAll('[', '-').replaceAll(']', '')}`
}

function logicTokenClass(entry: LogicInfoDto): string {
  if (entry.required && entry.status !== 'configured') {
    return 'bg-error-100 border-error-200 text-error-800'
  }
  if (entry.status === 'configured') {
    return 'bg-success-100 border-success-200 text-success-800'
  }
  return 'bg-gray-50 border-gray-200 text-gray-500'
}
</script>

<template>
  <div data-testid="structure-fields">
    <div class="flex items-center gap-2 mb-2" data-testid="structure-fields-header">
      <span class="text-sm font-medium text-gray-900">{{ structure.name }}</span>
      <span
        class="px-1.5 py-0.5 rounded bg-gray-50 border border-gray-200 text-[10px] uppercase text-gray-500"
      >
        {{ structure.type }}
      </span>
    </div>
    <div
      v-if="structure.logic?.length"
      class="flex flex-wrap items-center gap-1.5 mb-3"
      data-testid="structure-logic"
    >
      <span
        v-for="entry in structure.logic"
        :key="entry.kind"
        class="px-1.5 py-0.5 rounded border text-[10px] uppercase"
        :class="logicTokenClass(entry)"
        :title="entry.description"
        :data-testid="`structure-logic-${entry.kind}`"
      >
        {{ entry.kind }}: {{ entry.status }}
        <template v-if="entry.required && entry.status !== 'configured'">· required</template>
      </span>
    </div>
    <div v-if="structure.fields.length === 0" class="text-sm text-gray-500 italic py-6 text-center">
      No fields.
    </div>
    <table v-else class="w-full text-sm" data-testid="structure-fields-table">
      <thead>
        <tr class="text-left text-xs uppercase text-gray-500 border-b border-gray-200">
          <th class="py-1.5 pr-3 font-medium">Path</th>
          <th class="py-1.5 pr-3 font-medium">Value</th>
          <th class="py-1.5 pr-3 font-medium">Type</th>
          <th class="py-1.5 font-medium">Description</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="field in structure.fields"
          :key="field.path"
          class="border-b border-gray-100"
          :data-testid="rowTestId(field.path)"
        >
          <td
            class="py-1.5 pr-3 font-mono text-xs text-gray-900"
            :class="isArrayElement(field.path) ? 'pl-6' : ''"
          >
            {{ field.path }}
          </td>
          <td
            class="py-1.5 pr-3 text-xs text-gray-700 truncate max-w-64"
            :title="field.value ?? ''"
          >
            {{ field.value ?? '—' }}
          </td>
          <td class="py-1.5 pr-3 font-mono text-xs text-gray-500">{{ field.type }}</td>
          <td class="py-1.5 text-xs text-gray-600">{{ field.description }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
