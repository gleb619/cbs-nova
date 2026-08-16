<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  code: string
  readOnly?: boolean
}>()

const emit = defineEmits<{
  'update:code': [value: string]
}>()

const localCode = ref(props.code)
watch(
  () => props.code,
  (v) => {
    if (v !== localCode.value) localCode.value = v
  },
)
watch(localCode, (v) => {
  emit('update:code', v)
})
</script>

<template>
  <div class="p-3 h-full">
    <textarea
      v-model="localCode"
      :readonly="props.readOnly"
      :placeholder="props.readOnly ? 'No code available' : 'Write DSL here...'"
      class="w-full h-64 md:h-full min-h-[300px] p-3 text-xs font-mono rounded border border-gray-700 bg-gray-900 text-gray-100 focus:outline-none focus:border-gray-500 resize-none"
      spellcheck="false"
    />
  </div>
</template>
