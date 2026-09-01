<script setup lang="ts">
import { DSL_TEMPLATES, type DslTemplate } from '../utils/dslTemplates'

const props = withDefaults(defineProps<{
  templates?: DslTemplate[]
}>(), {
  templates: () => DSL_TEMPLATES,
})

const emit = defineEmits<{
  select: [template: DslTemplate]
}>()

function selectTemplate(template: DslTemplate) {
  emit('select', template)
}
</script>

<template>
  <div
    class="grid grid-cols-1 sm:grid-cols-2 gap-3"
    data-testid="dsl-template-gallery"
    role="listbox"
    aria-label="Starter templates"
  >
    <button
      v-for="template in props.templates"
      :key="template.id"
      type="button"
      class="text-left p-3 rounded-lg border border-gray-200 bg-white hover:border-blue-400 hover:bg-blue-50 transition-colors"
      :data-testid="`dsl-template-${template.id}`"
      role="option"
      @click="selectTemplate(template)"
    >
      <div class="font-medium text-sm text-gray-900">{{ template.label }}</div>
      <div class="text-xs text-gray-500 mt-1 leading-relaxed">{{ template.description }}</div>
    </button>
  </div>
</template>
