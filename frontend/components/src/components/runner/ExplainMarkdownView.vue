<script setup lang="ts">
import { marked } from 'marked'
import { computed } from 'vue'

defineOptions({ name: 'ExplainMarkdownView' })

const props = defineProps<{
  markdown: string | undefined
  /** Render hint surfaced through the default slot for custom renderers. */
  rendered?: string
}>()

const computedHtml = computed(() => {
  const raw = props.markdown ?? ''
  if (!raw) return ''
  return marked.parse(raw, { async: false }) as string
})
</script>

<template>
  <div data-testid="explain-markdown-view" class="prose prose-sm max-w-none">
    <slot v-if="(rendered ?? computedHtml)" name="rendered" :html="rendered ?? computedHtml">
      <div
        data-testid="explain-markdown-rendered"
        class="markdown-body"
        v-html="rendered ?? computedHtml"
      />
    </slot>
    <slot v-else name="empty">
      <p data-testid="explain-markdown-empty" class="text-ink-muted text-sm">
        No markdown content.
      </p>
    </slot>
  </div>
</template>
