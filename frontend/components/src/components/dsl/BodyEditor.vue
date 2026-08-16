<script setup lang="ts">
import type { DslConstruct, StepDef } from '../../types/dsl'

const props = defineProps<{
  construct: DslConstruct | null
  /**
   * Optional controlled body content (e.g. driven by `useWorkbenchDraft`).
   * When omitted, the editor falls back to its own internal stub state so
   * existing callers keep working unchanged.
   */
  code?: string
}>()

const emit = defineEmits<{
  'update:code': [value: string]
}>()

const tab = ref<'structure' | 'code'>('structure')

// stub — future: derive from construct introspection
const steps = ref<StepDef[]>([])

// Internal fallback body, used only when the caller doesn't pass `code`.
const internalCode = ref('')

const isControlled = computed(() => props.code !== undefined)

const bodyCode = computed<string>({
  get: () => (isControlled.value ? (props.code ?? '') : internalCode.value),
  set: (value: string) => {
    if (isControlled.value) {
      emit('update:code', value)
    } else {
      internalCode.value = value
    }
  },
})

watch(
  () => props.construct?.name,
  () => {
    steps.value = []
    if (!isControlled.value) {
      internalCode.value = ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="flex flex-col h-full bg-white">
    <div class="flex items-center border-b border-gray-200 px-2">
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'structure' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        @click="tab = 'structure'"
      >
        Structure
      </button>
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'code' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        @click="tab = 'code'"
      >
        Code
      </button>
    </div>
    <div class="flex-1 overflow-auto">
      <StructureTab v-show="tab === 'structure'" :steps="steps" />
      <CodeTab v-show="tab === 'code'" v-model:code="bodyCode" :read-only="!construct" />
    </div>
  </div>
</template>
