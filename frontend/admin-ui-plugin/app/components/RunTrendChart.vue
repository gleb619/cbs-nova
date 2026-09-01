<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardTimeseries } from '~/types'

interface Props {
  data: DashboardTimeseries | null
  loading?: boolean
  error?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
})

const emit = defineEmits<{
  (e: 'retry'): void
}>()

const statusColors: Record<string, string> = {
  Running: '#3b82f6',
  Completed: '#22c55e',
  Failed: '#ef4444',
  Stale: '#d4a373',
  Cancelled: '#6b7280',
  Compensated: '#f97316',
  Pending: '#d1d5db',
}

const statusOrder = ['Running', 'Completed', 'Failed', 'Stale', 'Cancelled', 'Compensated', 'Pending']

const buckets = computed(() => props.data?.buckets ?? [])

const visibleStatuses = computed(() => {
  const found = new Set<string>()
  for (const bucket of buckets.value) {
    for (const [status, count] of Object.entries(bucket.statusCounts)) {
      if (count > 0) found.add(status)
    }
  }
  return statusOrder.filter((s) => found.has(s))
})

const hasData = computed(() => visibleStatuses.value.length > 0)

const maxTotal = computed(() => {
  if (!hasData.value) return 1
  let max = 1
  for (const bucket of buckets.value) {
    const total = Object.values(bucket.statusCounts).reduce((sum, count) => sum + count, 0)
    if (total > max) max = total
  }
  return max
})

const svgWidth = 800
const svgHeight = 240
const margin = { top: 16, right: 16, bottom: 64, left: 40 }
const chartWidth = svgWidth - margin.left - margin.right
const chartHeight = svgHeight - margin.top - margin.bottom

const barGap = 4
const barWidth = computed(() => {
  const n = buckets.value.length || 1
  return Math.max(4, (chartWidth - barGap * (n - 1)) / n)
})

function barX(index: number): number {
  return margin.left + index * (barWidth.value + barGap)
}

function barY(total: number): number {
  return margin.top + chartHeight - (total / maxTotal.value) * chartHeight
}

function segmentHeight(count: number): number {
  return (count / maxTotal.value) * chartHeight
}

function cumulativeCount(bucketStatusCounts: Record<string, number>, upToStatus: string): number {
  const cutoff = visibleStatuses.value.indexOf(upToStatus)
  let sum = 0
  for (const [status, count] of Object.entries(bucketStatusCounts)) {
    if (visibleStatuses.value.indexOf(status) <= cutoff && cutoff >= 0) {
      sum += count
    }
  }
  return sum
}

function formatBucketLabel(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit' })
}
</script>

<template>
  <div
    class="bg-white rounded-lg shadow-sm border border-gray-200 p-4 space-y-4"
    data-testid="run-trend-chart"
  >
    <header class="flex items-center justify-between">
      <h3 class="text-lg font-semibold text-neutral-900">Run trend</h3>
      <button
        v-if="error"
        type="button"
        class="text-sm text-primary-600 hover:text-primary-700"
        data-testid="run-trend-chart-retry"
        @click="emit('retry')"
      >
        Retry
      </button>
    </header>

    <!-- Loading state -->
    <div
      v-if="loading"
      class="h-60 animate-pulse bg-neutral-100 rounded"
      data-testid="run-trend-chart-loading"
    />

    <!-- Error state -->
    <div
      v-else-if="error"
      class="h-60 flex items-center justify-center text-error-600 text-sm"
      data-testid="run-trend-chart-error"
    >
      {{ error }}
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!hasData"
      class="h-60 flex items-center justify-center text-neutral-500 text-sm"
      data-testid="run-trend-chart-empty"
    >
      No run data for the selected window.
    </div>

    <!-- Chart -->
    <div v-else class="overflow-x-auto">
      <svg
        :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
        class="w-full h-60"
        role="img"
        :aria-label="`Stacked bar chart of run counts by status over ${buckets.length} time buckets`"
      >
        <title>Run trend chart</title>
        <text :x="margin.left" :y="margin.top - 4" class="text-xs fill-neutral-500">
          Runs per bucket
        </text>

        <g data-testid="run-trend-chart-bars">
          <rect
            v-for="(_, i) in buckets"
            :key="`bg-${i}`"
            :x="barX(i)"
            :y="margin.top"
            :width="barWidth"
            :height="chartHeight"
            fill="#f3f4f6"
            rx="2"
          />

          <g v-for="(bucket, i) in buckets" :key="`bar-${i}`">
            <rect
              v-for="status in visibleStatuses"
              :key="`${i}-${status}`"
              :x="barX(i)"
              :y="barY(cumulativeCount(bucket.statusCounts, status))"
              :width="barWidth"
              :height="segmentHeight(bucket.statusCounts[status] || 0)"
              :fill="statusColors[status] || '#9ca3af'"
              rx="2"
              :aria-label="`${status}: ${bucket.statusCounts[status] || 0} runs at ${formatBucketLabel(bucket.bucketStart)}`"
            />
          </g>
        </g>

        <!-- Y axis ticks -->
        <g data-testid="run-trend-chart-y-axis">
          <line
            :x1="margin.left"
            :y1="margin.top"
            :x2="margin.left"
            :y2="margin.top + chartHeight"
            stroke="#e5e7eb"
          />
          <g v-for="tick in [0, 0.5, 1]" :key="`y-${tick}`">
            <line
              :x1="margin.left"
              :y1="margin.top + chartHeight * (1 - tick)"
              :x2="svgWidth - margin.right"
              :y2="margin.top + chartHeight * (1 - tick)"
              stroke="#e5e7eb"
              stroke-dasharray="4 4"
            />
            <text
              :x="margin.left - 8"
              :y="margin.top + chartHeight * (1 - tick) + 4"
              text-anchor="end"
              class="text-xs fill-neutral-500"
            >
              {{ Math.round(maxTotal * tick) }}
            </text>
          </g>
        </g>

        <!-- X axis labels -->
        <g data-testid="run-trend-chart-x-axis">
          <line
            :x1="margin.left"
            :y1="margin.top + chartHeight"
            :x2="svgWidth - margin.right"
            :y2="margin.top + chartHeight"
            stroke="#e5e7eb"
          />
          <text
            v-for="(bucket, i) in buckets"
            :key="`x-${i}`"
            :x="barX(i) + barWidth / 2"
            :y="margin.top + chartHeight + 16"
            text-anchor="middle"
            class="text-xs fill-neutral-500"
            :transform="`rotate(-30, ${barX(i) + barWidth / 2}, ${margin.top + chartHeight + 16})`"
          >
            {{ formatBucketLabel(bucket.bucketStart) }}
          </text>
        </g>
      </svg>

      <!-- Legend -->
      <ul
        class="flex flex-wrap gap-4 text-sm"
        data-testid="run-trend-chart-legend"
        aria-label="Status legend"
      >
        <li
          v-for="status in visibleStatuses"
          :key="`legend-${status}`"
          class="flex items-center gap-2"
        >
          <span
            class="inline-block w-3 h-3 rounded-sm"
            :style="{ backgroundColor: statusColors[status] || '#9ca3af' }"
          />
          <span class="text-neutral-700">{{ status }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
