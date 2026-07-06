<script setup lang="ts">
const props = defineProps<{
  code: string
  readOnly?: boolean
}>()

const localCode = ref(props.code)
watch(() => props.code, (v) => { localCode.value = v })
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