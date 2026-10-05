<script setup lang="ts">
import { computed, nextTick, onMounted, onUpdated, ref, watch } from 'vue'
import { useMermaidRender } from '../../composables/useMermaidRender'

defineOptions({ name: 'MermaidDiagram' })

const props = defineProps<{
  definition: string
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const svgHtml = ref('')
const failed = ref(false)

const { render, unavailable } = useMermaidRender()
const diagramId = computed(() => `mermaid-diagram-${Math.random().toString(36).slice(2, 9)}`)

async function draw() {
  if (!props.definition.trim()) {
    svgHtml.value = ''
    failed.value = false
    return
  }
  if (unavailable.value) {
    svgHtml.value = ''
    failed.value = true
    return
  }
  try {
    const svg = await render(props.definition, diagramId.value)
    svgHtml.value = svg
    failed.value = false
    await nextTick()
    preserveSvgSize()
  } catch {
    svgHtml.value = ''
    failed.value = true
  }
}

function preserveSvgSize() {
  const svg = containerRef.value?.querySelector('svg')
  if (!svg) return
  const viewBox = svg.getAttribute('viewBox')
  if (viewBox) {
    const parts = viewBox.split(/\s+/).map(Number)
    if (
      parts.length === 4 &&
      Number.isFinite(parts[2]) &&
      Number.isFinite(parts[3]) &&
      parts[2] > 0 &&
      parts[3] > 0
    ) {
      svg.style.width = `${parts[2]}px`
      svg.style.height = `${parts[3]}px`
    }
  }
  svg.removeAttribute('width')
  svg.style.maxWidth = 'none'
}

onMounted(() => {
  void draw()
})

onUpdated(() => {
  void draw()
})

watch(() => props.definition, () => {
  void draw()
})
</script>

<template>
  <div
    ref="containerRef"
    class="mermaid-diagram overflow-auto"
    data-testid="mermaid-diagram"
  >
    <div v-if="svgHtml" class="mermaid-diagram-svg" v-html="svgHtml" />
    <pre
      v-else-if="failed || unavailable"
      data-testid="mermaid-diagram-source"
      class="w-full h-full min-h-0 font-mono text-xs leading-relaxed text-ink whitespace-pre-wrap break-words border border-line rounded-sm bg-surface p-3"
    >{{ definition }}</pre>
  </div>
</template>

