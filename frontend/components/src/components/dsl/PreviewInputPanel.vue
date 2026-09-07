<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  name: string
  endpoint?: 'preview' | 'run' | 'explain'
  modelValue: string
  busy?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
}>()

const text = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const parseError = ref<string | null>(null)

function format() {
  try {
    text.value = JSON.stringify(JSON.parse(text.value), null, 2) + '\n'
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
}

watch(text, (v) => {
  if (!v.trim()) { parseError.value = null; return }
  try {
    JSON.parse(v)
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
})
</script>

<template>
  <section class="flex flex-col h-full min-h-0 border border-[#E1E4E8] rounded-sm bg-white">
    <header class="flex items-center justify-between px-3 py-2 border-b border-[#E1E4E8]">
      <div class="flex items-baseline gap-2 min-w-0">
        <span class="text-xs text-[#5A6470]">Input</span>
        <span class="font-mono text-xs text-[#0E1116] truncate">{{ name }}</span>
        <span class="text-xs text-[#5A6470]">· {{ endpoint ?? 'preview' }}</span>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <button
          type="button"
          class="text-xs px-2 py-1 border border-[#E1E4E8] hover:bg-[#F4F5F7] disabled:opacity-50"
          :disabled="busy"
          @click="format"
        >
          Format
        </button>
        <button
          type="button"
          class="text-xs px-3 py-1 bg-[#1F8F8A] text-white hover:bg-[#196E6A] disabled:opacity-50"
          :disabled="busy || !!parseError"
          @click="emit('submit')"
        >
          Run
        </button>
      </div>
    </header>

    <textarea
      v-model="text"
      spellcheck="false"
      autocomplete="off"
      autocapitalize="off"
      class="flex-1 min-h-0 p-3 font-mono text-xs leading-relaxed text-[#0E1116] bg-white resize-none focus:outline-none focus:ring-1 focus:ring-[#1F8F8A]"
    />

    <footer
      v-if="parseError"
      class="px-3 py-1.5 border-t border-[#E1E4E8] text-xs font-mono text-[#B42318] truncate"
    >
      {{ parseError }}
    </footer>
  </section>
</template>
