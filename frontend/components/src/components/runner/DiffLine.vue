<script setup lang="ts">
import type { DiffLineKind } from '../../composables/useDiffLines'

defineOptions({ name: 'DiffLine' })

defineProps<{
  kind: DiffLineKind
  text: string
  lhsLineNumber?: number | null
  rhsLineNumber?: number | null
}>()

function lineClass(kind: DiffLineKind): string {
  switch (kind) {
    case 'lhs-only':
      return 'bg-red-50 border-l-4 border-red-400 text-red-900'
    case 'rhs-only':
      return 'bg-green-50 border-l-4 border-green-500 text-green-900'
    default:
      return 'border-l-4 border-transparent text-gray-700'
  }
}

function gutter(kind: DiffLineKind): string {
  switch (kind) {
    case 'lhs-only':
      return '−'
    case 'rhs-only':
      return '+'
    default:
      return ' '
  }
}
</script>

<template>
  <div
    :class="lineClass(kind)"
    class="font-mono text-xs whitespace-pre flex items-start gap-2 py-0.5"
    :data-kind="kind"
    data-testid="preview-diff-line"
  >
    <span class="text-gray-400 select-none w-8 text-right shrink-0 tabular-nums">
      {{ lhsLineNumber ?? '' }}
    </span>
    <span class="text-gray-400 select-none w-8 text-right shrink-0 tabular-nums">
      {{ rhsLineNumber ?? '' }}
    </span>
    <span class="select-none w-3 shrink-0 text-center">{{ gutter(kind) }}</span>
    <span class="flex-1 break-words">{{ text }}</span>
  </div>
</template>
