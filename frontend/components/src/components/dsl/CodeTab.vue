<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useCodeHighlight } from '../../composables/useCodeHighlight'

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
    if (value !== localCode.value) localCode.value = value
  },
)

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
  <div class="p-3 h-full" data-testid="code-tab">
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
      />
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
