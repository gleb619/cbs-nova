<script setup lang="ts">
import type { MetricsDiffRow } from '../../composables/usePreviewDiff'

defineOptions({ name: 'MetricsDiffTable' })

defineProps<{
  rows: MetricsDiffRow[]
}>()

function formatValue(row: MetricsDiffRow, side: 'baseline' | 'current'): string {
  const value = row[side]
  if (value === null) return '—'
  if (row.key === 'memoryUsedBytes') {
    return formatBytes(value)
  }
  if (row.key === 'executionDurationMs') {
    return `${value.toLocaleString()} ms`
  }
  return value.toLocaleString()
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(2)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
}

function formatPercent(value: number | null): string {
  if (value === null) return '—'
  const rounded = Math.round(value * 10) / 10
  if (rounded === 0) return '0%'
  if (rounded > 0) return `+${rounded}%`
  return `−${Math.abs(rounded)}%`
}

function formatDelta(delta: number | null, row: MetricsDiffRow): string {
  if (delta === null) return '—'
  if (delta === 0) return '0'
  const sign = delta > 0 ? '+' : '−'
  const abs = Math.abs(delta)
  if (row.key === 'memoryUsedBytes') return `${sign}${formatBytes(abs)}`
  if (row.key === 'executionDurationMs') return `${sign}${abs.toLocaleString()} ms`
  return `${sign}${abs.toLocaleString()}`
}

/**
 * Map a row's direction to a Tailwind class for the delta cell.
 * - `lowerIsBetter` (latency / memory / call counts): decrease = green, increase = red.
 * - missing/null baseline or current: gray.
 */
function deltaClass(row: MetricsDiffRow): string {
  if (row.delta === null || row.percentChange === null) {
    return 'text-gray-500'
  }
  if (row.delta === 0) return 'text-gray-500'
  const improving = row.lowerIsBetter ? row.delta < 0 : row.delta > 0
  return improving ? 'text-green-700 font-semibold' : 'text-red-700 font-semibold'
}

function arrow(delta: number | null, lowerIsBetter: boolean): string {
  if (delta === null || delta === 0) return '·'
  const improving = lowerIsBetter ? delta < 0 : delta > 0
  if (improving) return '↑'
  return '↓'
}

function arrowClass(delta: number | null, lowerIsBetter: boolean): string {
  if (delta === null || delta === 0) return 'text-gray-400'
  const improving = lowerIsBetter ? delta < 0 : delta > 0
  return improving ? 'text-green-600' : 'text-red-600'
}
</script>

<template>
  <div class="overflow-auto border border-gray-200 rounded-lg" data-testid="metrics-diff-table">
    <table class="w-full text-xs">
      <thead class="bg-gray-50 text-gray-600">
        <tr>
          <th class="text-left font-medium px-3 py-2">Metric</th>
          <th class="text-right font-medium px-3 py-2">Baseline</th>
          <th class="text-right font-medium px-3 py-2">Current</th>
          <th class="text-right font-medium px-3 py-2">Δ</th>
          <th class="text-right font-medium px-3 py-2">Change</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.key"
          class="border-t border-gray-100"
          :data-row-key="row.key"
        >
          <td class="px-3 py-2 text-gray-800">{{ row.label }}</td>
          <td class="px-3 py-2 text-right font-mono text-gray-700 tabular-nums">
            {{ formatValue(row, 'baseline') }}
          </td>
          <td class="px-3 py-2 text-right font-mono text-gray-700 tabular-nums">
            {{ formatValue(row, 'current') }}
          </td>
          <td class="px-3 py-2 text-right font-mono tabular-nums" :class="deltaClass(row)">
            <span class="mr-1" :class="arrowClass(row.delta, row.lowerIsBetter)">
              {{ arrow(row.delta, row.lowerIsBetter) }}
            </span>
            {{ formatDelta(row.delta, row) }}
          </td>
          <td class="px-3 py-2 text-right font-mono tabular-nums" :class="deltaClass(row)">
            {{ formatPercent(row.percentChange) }}
          </td>
        </tr>
        <tr v-if="rows.length === 0">
          <td colspan="5" class="px-3 py-3 text-center text-gray-500">
            No metrics available to compare.
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
