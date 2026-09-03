<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useCodeHighlight } from '../../composables/useCodeHighlight'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'

const props = withDefaults(
  defineProps<{
    code: string
    readOnly?: boolean
    language?: string
  }>(),
  { language: 'java' },
)

const emit = defineEmits<{
  'update:code': [value: string]
  save: [value: string]
}>()

const localCode = ref(props.code)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const preRef = ref<HTMLPreElement | null>(null)

// biome-ignore lint/correctness/noUnusedVariables: consumed via v-html in template
const { highlightedHtml } = useCodeHighlight(localCode, () => props.language)

const placeholder = computed(() => (props.readOnly ? 'No code available' : 'Write DSL here...'))

watch(
  () => props.code,
  (value) => {
    if (value !== localCode.value) {
      localCode.value = value
      lastSavedCode.value = value
    }
  },
)

const AUTOSAVE_INTERVALS: Record<string, number> = {
  '5s': 5000,
  '30s': 30000,
  '1min': 60000,
}

const useCodeTabStorage = createNamespacedLocalStorageState('cbs-nova:code-tab')
const autosaveMode = useCodeTabStorage<string>('autosave-mode', 'off')

const lastSavedCode = ref(props.code)
const isDirty = computed(() => localCode.value !== lastSavedCode.value)

let autosaveTimer: ReturnType<typeof setInterval> | undefined

function clearAutosaveTimer() {
  if (autosaveTimer !== undefined) {
    clearInterval(autosaveTimer)
    autosaveTimer = undefined
  }
}

function requestSave() {
  lastSavedCode.value = localCode.value
  emit('save', localCode.value)
}

watch(
  autosaveMode,
  (mode) => {
    clearAutosaveTimer()
    const interval = AUTOSAVE_INTERVALS[mode]
    if (interval !== undefined && !props.readOnly) {
      autosaveTimer = setInterval(() => {
        if (isDirty.value) requestSave()
      }, interval)
    }
  },
  { immediate: true },
)

onBeforeUnmount(clearAutosaveTimer)

function handleBlur() {
  if (autosaveMode.value === 'blur' && isDirty.value) requestSave()
}

const autosaveOptions = [
  { value: 'off', label: 'Off' },
  { value: '5s', label: '5s' },
  { value: '30s', label: '30s' },
  { value: '1min', label: '1 min' },
  { value: 'blur', label: 'On blur' },
]

watch(localCode, (value) => {
  emit('update:code', value)
})

function handleInput(event: Event) {
  localCode.value = (event.target as HTMLTextAreaElement).value
}

function handleScroll() {
  const textarea = textareaRef.value
  const pre = preRef.value
  if (!textarea || !pre) return
  pre.scrollTop = textarea.scrollTop
  pre.scrollLeft = textarea.scrollLeft
}
</script>

<template>
  <div class="flex flex-col h-full" data-testid="code-tab">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-b border-neutral-200 bg-white"
      data-testid="code-tab-toolbar"
    >
      <button
        type="button"
        class="px-3 py-1 text-xs font-medium rounded border"
        :class="isDirty
          ? 'border-blue-600 bg-blue-600 text-white hover:bg-blue-700'
          : 'border-neutral-300 text-neutral-400 cursor-not-allowed'"
        :disabled="!isDirty"
        data-testid="code-tab-save"
        @click="requestSave"
      >
        Save
      </button>
      <div class="flex items-center rounded border border-neutral-300 overflow-hidden">
        <button
          v-for="option in autosaveOptions"
          :key="option.value"
          type="button"
          class="px-2 py-1 text-xs"
          :class="autosaveMode === option.value
            ? 'bg-neutral-800 text-white'
            : 'text-neutral-600 hover:bg-neutral-100'"
          :data-testid="`code-tab-autosave-${option.value}`"
          :aria-pressed="autosaveMode === option.value"
          @click="autosaveMode = option.value"
        >
          {{ option.label }}
        </button>
      </div>
      <span
        v-if="isDirty"
        class="inline-flex items-center gap-1.5 text-xs text-amber-700"
        data-testid="code-tab-dirty"
        role="status"
      >
        <span aria-hidden="true" class="inline-block h-2 w-2 rounded-full bg-amber-500"></span>
        <span>unsaved</span>
      </span>
    </div>
    <div class="p-3 flex-1 min-h-0">
      <div
        data-testid="code-tab-editor"
        class="code-editor relative h-full min-h-[300px] md:h-full overflow-hidden rounded border"
        :class="readOnly ? 'border-neutral-200 bg-neutral-50' : 'border-neutral-700 bg-neutral-900'"
      >
        <pre
          ref="preRef"
          aria-hidden="true"
          :data-testid="readOnly ? 'code-tab-display' : 'code-tab-highlight'"
          class="code-editor-highlight m-0 p-3 text-xs font-mono leading-relaxed whitespace-pre"
          :class="readOnly ? 'text-neutral-800 overflow-auto h-full' : 'text-neutral-100 overflow-hidden'"
        ><code class="language-java" v-html="highlightedHtml" /></pre>
        <textarea
          ref="textareaRef"
          data-testid="code-tab-textarea"
          :value="localCode"
          :readonly="readOnly"
          :placeholder="placeholder"
          spellcheck="false"
          autocorrect="off"
          autocapitalize="off"
          class="code-editor-input absolute inset-0 w-full h-full p-3 text-xs font-mono leading-relaxed bg-transparent resize-none border-0 focus:outline-none whitespace-pre overflow-auto"
          :class="readOnly ? 'text-transparent caret-transparent' : 'text-transparent caret-neutral-100'"
          @input="handleInput"
          @scroll="handleScroll"
          @blur="handleBlur"
        />
      </div>
    </div>
  </div>
</template>

<style>
.code-editor-highlight,
.code-editor-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-variant-ligatures: none;
  tab-size: 2;
}

.code-editor-highlight .token.keyword {
  color: #c97c6b;
  font-weight: 600;
}
.code-editor-highlight .token.string {
  color: #d4a373;
}
.code-editor-highlight .token.comment {
  color: #948d80;
  font-style: italic;
}
.code-editor-highlight .token.number {
  color: #7faa83;
}
.code-editor-highlight .token.constant {
  color: #7f9aab;
}
.code-editor-highlight .token.operator {
  color: #b3ada1;
}
.code-editor-highlight .token.punctuation {
  color: #d1cdc4;
}
.code-editor-highlight .token.class-name {
  color: #7f9aab;
}
.code-editor-highlight .token.function {
  color: #d4937a;
}
.code-editor-highlight .token.annotation {
  color: #c97c6b;
}
</style>
