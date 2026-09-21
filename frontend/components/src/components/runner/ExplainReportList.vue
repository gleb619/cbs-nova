<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ExplainReportNode } from '../../types/runner'
import ExplainMarkdownView from './ExplainMarkdownView.vue'

defineOptions({ name: 'ExplainReportList' })

const props = defineProps<{
  report: ExplainReportNode
}>()

interface ReportSection {
  name: string
  description: string
  markdown: string
  depth: number
  index: number
}

function flatten(node: ExplainReportNode, depth: number, sections: ReportSection[]) {
  sections.push({
    name: node.name,
    description: node.description,
    markdown: node.markdown,
    depth,
    index: sections.length,
  })
  for (const child of node.children) flatten(child, depth + 1, sections)
}

const sections = computed(() => {
  const result: ReportSection[] = []
  flatten(props.report, 0, result)
  return result
})

const collapsedByIndex = ref<Set<number>>(new Set())
const descriptionsExpanded = ref<Set<number>>(new Set())

function descendantIndices(index: number): number[] {
  const section = sections.value[index]
  if (!section) return []
  const result: number[] = []
  for (let i = index + 1; i < sections.value.length; i++) {
    const candidate = sections.value[i]
    if (!candidate) break
    if (candidate.depth <= section.depth) break
    result.push(i)
  }
  return result
}

function toggle(index: number) {
  if (collapsedByIndex.value.has(index)) {
    collapsedByIndex.value.delete(index)
    return
  }
  collapsedByIndex.value.add(index)
  for (const descendant of descendantIndices(index)) {
    collapsedByIndex.value.add(descendant)
  }
}

function expandAll() {
  collapsedByIndex.value = new Set()
}

function collapseAll() {
  collapsedByIndex.value = new Set(sections.value.map((s) => s.index))
}

function descriptionExpanded(index: number): boolean {
  return descriptionsExpanded.value.has(index)
}

function toggleDescription(index: number) {
  if (descriptionsExpanded.value.has(index)) {
    descriptionsExpanded.value.delete(index)
  } else {
    descriptionsExpanded.value.add(index)
  }
}

watch(
  () => props.report,
  () => {
    collapsedByIndex.value = new Set()
    descriptionsExpanded.value = new Set()
  },
)

function headingTag(depth: number): string {
  return `h${Math.min(depth + 2, 6)}`
}

function depthBadge(depth: number): string {
  return depth === 0 ? 'root' : `+${depth}`
}

const DEPTH_COLORS = [
  { border: 'border-l-accent-500', header: 'bg-accent-50' },
  { border: 'border-l-primary-500', header: 'bg-primary-50' },
  { border: 'border-l-success-500', header: 'bg-success-50' },
  { border: 'border-l-warning-500', header: 'bg-warning-50' },
  { border: 'border-l-info-500', header: 'bg-info-50' },
  { border: 'border-l-error-500', header: 'bg-error-50' },
]

function depthColor(depth: number) {
  return DEPTH_COLORS[depth % DEPTH_COLORS.length]
}

function sectionClasses(depth: number): string {
  const color = depthColor(depth)
  return `rounded-sm border border-line bg-white overflow-hidden border-l-4 ${color.border}`
}

function headerClasses(depth: number): string {
  const color = depthColor(depth)
  return `flex items-center justify-between gap-2 px-3 py-2 border-b border-line ${color.header}`
}

function chevronIcon(index: number): string {
  return collapsedByIndex.value.has(index) ? '▸' : '▾'
}
</script>

<template>
  <div data-testid="explain-report-list" class="space-y-3">
    <article
      v-for="section in sections"
      :key="`${section.name}-${section.index}`"
      data-testid="explain-report-section"
      :class="sectionClasses(section.depth)"
    >
      <header :class="headerClasses(section.depth)">
        <div class="flex items-center gap-2 min-w-0">
          <span
            data-testid="explain-report-section-depth"
            class="shrink-0 inline-flex items-center px-1.5 py-0.5 rounded-sm bg-white border border-line font-mono text-[10px] uppercase tracking-wider text-ink-muted"
          >
            {{ depthBadge(section.depth) }}
          </span>
          <component
            :is="headingTag(section.depth)"
            data-testid="explain-report-section-heading"
            class="font-mono text-sm font-semibold text-ink m-0 truncate"
          >
            {{ section.name }}
          </component>
        </div>
        <div class="flex items-center gap-1">
          <button
            v-if="section.depth === 0"
            type="button"
            data-testid="explain-report-expand-all"
            class="shrink-0 inline-flex items-center justify-center w-6 h-6 rounded-sm border border-line bg-white hover:bg-surface text-ink"
            aria-label="Expand all"
            title="Expand all"
            @click="expandAll"
          >
            ⊕
          </button>
          <button
            v-if="section.depth === 0"
            type="button"
            data-testid="explain-report-collapse-all"
            class="shrink-0 inline-flex items-center justify-center w-6 h-6 rounded-sm border border-line bg-white hover:bg-surface text-ink"
            aria-label="Collapse all"
            title="Collapse all"
            @click="collapseAll"
          >
            ⊖
          </button>
          <button
            v-if="section.depth > 0"
            type="button"
            data-testid="explain-report-section-toggle"
            class="shrink-0 inline-flex items-center justify-center w-6 h-6 rounded-sm border border-line bg-white hover:bg-surface text-ink"
            :aria-label="collapsedByIndex.has(section.index) ? 'Expand' : 'Collapse'"
            @click="toggle(section.index)"
          >
            {{ chevronIcon(section.index) }}
          </button>
        </div>
      </header>

      <div v-if="!collapsedByIndex.has(section.index)">
        <div
          v-if="section.description"
          data-testid="explain-report-section-description"
          class="px-3 py-2 text-xs text-ink-muted border-b border-line"
        >
          <button
            type="button"
            class="flex items-center gap-1 text-ink-muted hover:text-ink"
            @click="toggleDescription(section.index)"
          >
            <span>{{ descriptionExpanded(section.index) ? '▾' : '▸' }}</span>
            <span>{{ descriptionExpanded(section.index) ? 'Hide details' : 'Show details' }}</span>
          </button>
          <p v-if="descriptionExpanded(section.index)" class="mt-1">{{ section.description }}</p>
        </div>

        <div
          v-if="section.markdown"
          data-testid="explain-report-section-body"
          class="px-3 py-3 overflow-x-auto"
        >
          <ExplainMarkdownView :markdown="section.markdown" mermaid />
        </div>
      </div>
    </article>
  </div>
</template>
