<script setup lang="ts">
import { computed, watch } from 'vue'
import type { JsonSchema } from '../../types/jsonSchema'
import SchemaFormField from './SchemaFormField.vue'

const props = defineProps<{
  schema: JsonSchema
  modelValue: unknown
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
}>()

const isObject = computed(() => props.schema.type === 'object')
const isArray = computed(() => props.schema.type === 'array')

const objectValue = computed(() => {
  if (
    props.modelValue &&
    typeof props.modelValue === 'object' &&
    !Array.isArray(props.modelValue)
  ) {
    return props.modelValue as Record<string, unknown>
  }
  return {}
})

const arrayValue = computed(() => {
  if (Array.isArray(props.modelValue)) return props.modelValue as unknown[]
  return []
})

function setField(name: string, value: unknown) {
  if (props.readonly) return
  const next = { ...objectValue.value, [name]: value }
  emit('update:modelValue', next)
}

function removeField(name: string) {
  if (props.readonly) return
  const next = { ...objectValue.value }
  delete next[name]
  emit('update:modelValue', next)
}

function addArrayItem() {
  if (props.readonly) return
  const itemSchema = props.schema.items
  const defaultItem = itemSchema?.type === 'object' ? {} : undefined
  const next = [...arrayValue.value, defaultItem]
  emit('update:modelValue', next)
}

function removeArrayItem(index: number) {
  if (props.readonly) return
  const next = [...arrayValue.value]
  next.splice(index, 1)
  emit('update:modelValue', next)
}

function updateArrayItem(index: number, value: unknown) {
  if (props.readonly) return
  const next = [...arrayValue.value]
  next[index] = value
  emit('update:modelValue', next)
}

function initializeFromDefaults() {
  if (props.readonly) return
  if (!isObject.value || !props.schema.properties) return
  let changed = false
  const next = { ...objectValue.value }
  for (const [key, propSchema] of Object.entries(props.schema.properties)) {
    if (next[key] === undefined && propSchema.default !== undefined) {
      next[key] = propSchema.default
      changed = true
    }
  }
  if (changed) {
    emit('update:modelValue', next)
  }
}

watch(() => props.schema, initializeFromDefaults, { immediate: true })
</script>

<template>
  <div data-testid="schema-form" class="flex flex-col gap-3">
    <template v-if="isObject">
      <SchemaFormField
        v-for="(propSchema, key) in schema.properties"
        :key="key"
        :name="String(key)"
        :schema="propSchema"
        :required="schema.required?.includes(String(key)) ?? false"
        :model-value="objectValue[key]"
        :readonly="readonly"
        @update:model-value="setField(String(key), $event)"
        @remove="removeField(String(key))"
      />
      <div
        v-if="!schema.properties || Object.keys(schema.properties).length === 0"
        class="text-xs text-ink-muted"
      >
        No schema properties to render.
      </div>
    </template>

    <template v-else-if="isArray">
      <div class="flex flex-col gap-2">
        <div
          v-for="(item, index) in arrayValue"
          :key="index"
          class="flex items-start gap-2 p-2 border border-line rounded-sm"
        >
          <div class="flex-1 min-w-0">
            <SchemaForm
              :schema="schema.items ?? { type: 'any' }"
              :model-value="item"
              :readonly="readonly"
              @update:model-value="updateArrayItem(index, $event)"
            />
          </div>
          <button
            v-if="!readonly"
            type="button"
            class="text-xs px-2 py-1 border border-line hover:bg-surface text-danger"
            :data-testid="`remove-array-item-${index}`"
            @click="removeArrayItem(index)"
          >
            ×
          </button>
        </div>
        <button
          v-if="!readonly"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface"
          data-testid="add-array-item"
          @click="addArrayItem()"
        >
          + Add item
        </button>
      </div>
    </template>

    <template v-else>
      <SchemaFormField
        :name="'value'"
        :schema="schema"
        :required="false"
        :model-value="modelValue"
        :readonly="readonly"
        @update:model-value="emit('update:modelValue', $event)"
      />
    </template>
  </div>
</template>
