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

let _mermaidModule: typeof import('mermaid')['default'] | null = null
let _mermaidRender: ((definition: string) => Promise<string>) | null = null
let mermaidUnavailable = false
let mermaidSequence = 0

const computedHtml = computed(() => {
  const raw = props.markdown ?? ''
  if (!raw) return ''
  const source = raw.trim()
  if (props.mermaid && !source.includes('\u0060\u0060\u0060') && MERMAID_FIRST_LINE.test(source)) {
    return marked.parse(`\u0060\u0060\u0060mermaid\n${source}\n\u0060\u0060\u0060`, {
      async: false,
    }) as string
  }
  return marked.parse(raw, { async: false }) as string
})

async function ensureMermaid() {
  if (mermaidUnavailable) return null
  if (_mermaidRender) return _mermaidRender
  try {
    const mermaid = (await import('mermaid')).default
    _mermaidModule = mermaid
    mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', theme: 'default' })
    _mermaidRender = async (definition: string) => {
      const id = `explain-mmd-${++mermaidSequence}-${Math.random().toString(36).slice(2, 9)}`
      const { svg } = await _mermaidModule!.render(id, definition)
      return svg
    }
    return _mermaidRender
  } catch {
    mermaidUnavailable = true
    return null
  }
}

async function renderMermaidDiagrams() {
  if (!props.mermaid || mermaidUnavailable || !root.value) return
  const blocks = Array.from(root.value.querySelectorAll('pre code.language-mermaid'))
  if (blocks.length === 0) return
  const render = await ensureMermaid()
  if (!render) return
  for (const block of blocks) {
    const pre = block.parentElement
    if (!pre || pre.getAttribute('data-mermaid-state') === 'rendered') continue
    try {
      const definition = block.textContent ?? ''
      const svg = await render(definition)
      const container = document.createElement('div')
      container.setAttribute('data-testid', 'explain-mermaid-diagram')
      container.style.width = '100%'
      container.style.overflow = 'auto'
      container.innerHTML = svg
      const renderedSvg = container.querySelector('svg')
      if (renderedSvg) {
        // Let each diagram keep its natural viewBox size so it does not get
        // squashed into a narrow parent. The container provides scroll when
        // the diagram is wider than the available space.
        const viewBox = renderedSvg.getAttribute('viewBox')
        if (viewBox) {
          const parts = viewBox.split(/\s+/).map(Number)
          if (
            parts.length === 4 &&
            Number.isFinite(parts[2]) &&
            Number.isFinite(parts[3]) &&
            parts[2] > 0 &&
            parts[3] > 0
          ) {
            renderedSvg.style.width = `${parts[2]}px`
            renderedSvg.style.height = `${parts[3]}px`
          }
        }
        renderedSvg.removeAttribute('width')
        renderedSvg.style.maxWidth = 'none'
      }
      pre.replaceWith(container)
      pre.setAttribute('data-mermaid-state', 'rendered')
    } catch (e) {
      block.setAttribute('data-mermaid-state', 'failed')
      console.warn('Mermaid render failed', e)
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
  <div ref="root" data-testid="explain-markdown-view" class="markdown-body">
    <slot v-if="(rendered ?? computedHtml)" name="rendered" :html="rendered ?? computedHtml">
      <div
        data-testid="explain-markdown-rendered"
        class="markdown-rendered"
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

<style>
.markdown-body {
  color: #0e1116;
  font-size: 0.875rem;
  line-height: 1.6;
}

.markdown-body :first-child {
  margin-top: 0;
}

.markdown-body :last-child {
  margin-bottom: 0;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin-top: 1rem;
  margin-bottom: 0.5rem;
  font-weight: 600;
  line-height: 1.3;
  color: #0e1116;
}

.markdown-body h1 {
  font-size: 1.25rem;
  border-bottom: 1px solid #e1e4e8;
  padding-bottom: 0.25rem;
}

.markdown-body h2 {
  font-size: 1.125rem;
  border-bottom: 1px solid #e1e4e8;
  padding-bottom: 0.25rem;
}

.markdown-body h3 {
  font-size: 1rem;
}

.markdown-body h4 {
  font-size: 0.9375rem;
}

.markdown-body h5,
.markdown-body h6 {
  font-size: 0.875rem;
  color: #5a6470;
}

.markdown-body p {
  margin-top: 0.5rem;
  margin-bottom: 0.5rem;
}

.markdown-body ul,
.markdown-body ol {
  margin-top: 0.5rem;
  margin-bottom: 0.5rem;
  padding-left: 1.25rem;
}

.markdown-body ul {
  list-style-type: disc;
}

.markdown-body ol {
  list-style-type: decimal;
}

.markdown-body li + li {
  margin-top: 0.25rem;
}

.markdown-body code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8125rem;
  background-color: #f4f5f7;
  padding: 0.125rem 0.25rem;
  border-radius: 0.125rem;
  color: #b42318;
}

.markdown-body pre {
  margin-top: 0.75rem;
  margin-bottom: 0.75rem;
  padding: 0.75rem;
  background-color: #f4f5f7;
  border: 1px solid #e1e4e8;
  border-radius: 0.125rem;
  overflow: auto;
}

.markdown-body pre code {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  color: inherit;
}

.markdown-body blockquote {
  margin: 0.75rem 0;
  padding: 0.25rem 0.75rem;
  border-left: 3px solid #1f8f8a;
  color: #5a6470;
  background-color: #f9f8f6;
}

.markdown-body hr {
  margin: 1rem 0;
  border: 0;
  border-top: 1px solid #e1e4e8;
}

.markdown-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 0.75rem 0;
  font-size: 0.8125rem;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #e1e4e8;
  padding: 0.375rem 0.5rem;
  text-align: left;
}

.markdown-body th {
  background-color: #f4f5f7;
  font-weight: 600;
}

.markdown-body img {
  max-width: 100%;
  height: auto;
}

.markdown-body a {
  color: #1f8f8a;
  text-decoration: underline;
}
.markdown-body [data-testid="explain-mermaid-diagram"] {
  overflow: auto;
}

.markdown-body [data-testid="explain-mermaid-diagram"] svg {
  max-width: none !important;
}
</style>
