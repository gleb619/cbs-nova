<script setup lang="ts">
import type { DslConstruct, StepDef } from '~/types/dsl'

const props = defineProps<{ construct: DslConstruct | null }>()

const tab = ref<'structure' | 'code'>('structure')

// stubs — future: derive from construct introspection
const steps = ref<StepDef[]>([])
const code = ref('')

watch(() => props.construct?.name, () => {
  steps.value = []
  code.value = ''
}, { immediate: true })
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
      <CodeTab v-show="tab === 'code'" :code="code" :read-only="!construct" />
    </div>
  </div>
</template>