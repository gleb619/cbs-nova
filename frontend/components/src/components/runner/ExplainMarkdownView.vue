<script setup lang="ts">
import { marked } from 'marked'
import { computed, onMounted, onUpdated, ref } from 'vue'

defineOptions({ name: 'ExplainMarkdownView' })

const props = withDefaults(
  defineProps<{
    markdown: string | undefined
    /** Render hint surfaced through the default slot for custom renderers. */
    rendered?: string
    /** Render ```mermaid fenced code blocks as diagrams via the mermaid package (client-side only). */
    mermaid?: boolean
  }>(),
  { mermaid: false },
)

const root = ref<HTMLElement | null>(null)

const MERMAID_FIRST_LINE =
  /^(graph\b|flowchart\b|sequenceDiagram\b|stateDiagram(-v2)?\b|classDiagram\b|erDiagram\b|gantt\b|pie\b|journey\b|gitGraph\b|mindmap\b|timeline\b|quadrantChart\b|requirementDiagram\b|C4Context\b)/

let mermaidRender: ((definition: string) => Promise<string>) | null = null
let mermaidUnavailable = false
let mermaidSequence = 0

const computedHtml = computed(() => {
  const raw = props.markdown ?? ''
  if (!raw) return ''
  const source = raw.trim()
  if (props.mermaid && !source.includes('```') && MERMAID_FIRST_LINE.test(source)) {
    return marked.parse(`\`\`\`mermaid\n${source}\n\`\`\``, { async: false }) as string
  }
  return marked.parse(raw, { async: false }) as string
})

async function ensureMermaid() {
  if (mermaidRender || mermaidUnavailable) return
  try {
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({ startOnLoad: false })
    mermaidRender = async (definition) => {
      const { svg } = await mermaid.render(`explain-mmd-${++mermaidSequence}`, definition)
      return svg
    }
  } catch {
    mermaidUnavailable = true
  }
}

async function renderMermaidDiagrams() {
  if (!props.mermaid || mermaidUnavailable || !root.value) return
  const blocks = root.value.querySelectorAll('pre code.language-mermaid')
  if (blocks.length === 0) return
  await ensureMermaid()
  if (!mermaidRender) return
  for (const block of Array.from(blocks)) {
    if (block.getAttribute('data-mermaid-state')) continue
    const pre = block.parentElement
    if (!pre) continue
    try {
      const svg = await mermaidRender(block.textContent ?? '')
      const container = document.createElement('div')
      container.setAttribute('data-testid', 'explain-mermaid-diagram')
      container.innerHTML = svg
      pre.replaceWith(container)
    } catch {
      block.setAttribute('data-mermaid-state', 'failed')
    }
  }
}

onMounted(() => {
  void renderMermaidDiagrams()
})

onUpdated(() => {
  void renderMermaidDiagrams()
})
</script>

<template>
  <div ref="root" data-testid="explain-markdown-view" class="prose prose-sm max-w-none">
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
