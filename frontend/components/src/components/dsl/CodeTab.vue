<script setup lang="ts">
import Prism from 'prismjs'
import 'prismjs/components/prism-java'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  code: string
  readOnly?: boolean
}>()

const emit = defineEmits<{
  'update:code': [value: string]
}>()

const localCode = ref(props.code)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const preRef = ref<HTMLPreElement | null>(null)

watch(
  () => props.code,
  (v) => {
    if (v !== localCode.value) localCode.value = v
  },
)
watch(localCode, (v) => {
  emit('update:code', v)
})

// Pre-formatted highlighted HTML for the editor surface. Recomputed only
// when the local code changes; the value comes from Prism, so SSR is safe.
// Returns a non-breaking-space placeholder for empty input so the line height
// stays stable and the textarea caret has somewhere to sit.
// biome-ignore lint/correctness/noUnusedVariables: consumed via v-html in the template below
const highlightedHtml = computed(() => {
  const code = localCode.value
  if (!code) return '&nbsp;'
  return Prism.highlight(code, Prism.languages.java, 'java')
})

const placeholder = computed(() => (props.readOnly ? 'No code available' : 'Write DSL here...'))

function handleInput(event: Event) {
  const target = event.target as HTMLTextAreaElement
  localCode.value = target.value
}

function handleScroll() {
  // Keep the visible highlight layer aligned with the textarea caret/scroll
  // when editing, so the user always sees the colored tokens for the lines
  // currently in view.
  const ta = textareaRef.value
  const pre = preRef.value
  if (!ta || !pre) return
  pre.scrollTop = ta.scrollTop
  pre.scrollLeft = ta.scrollLeft
}
</script>

<template>
  <div
    class="p-3 h-full"
    data-testid="code-tab"
  >
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

.code-editor-highlight .token.keyword { color: #c97c6b; font-weight: 600; }
.code-editor-highlight .token.string { color: #d4a373; }
.code-editor-highlight .token.comment { color: #948d80; font-style: italic; }
.code-editor-highlight .token.number { color: #7faa83; }
.code-editor-highlight .token.boolean { color: #d4937a; }
.code-editor-highlight .token.operator { color: #b3ada1; }
.code-editor-highlight .token.punctuation { color: #d1cdc4; }
.code-editor-highlight .token.class-name { color: #7f9aab; }
.code-editor-highlight .token.function { color: #d4937a; }
.code-editor-highlight .token.annotation { color: #c97c6b; }
</style>
