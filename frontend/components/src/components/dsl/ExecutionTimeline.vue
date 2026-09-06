<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { TransactionExecutionDto } from '../../types/execution'
import ErrorBanner from '../ErrorBanner.vue'

/**
 * T326 — chronological timeline view for per-run transactions.
 *
 * Visual rules:
 * - Single-lane (one row per transaction) when there are ≤ SWIM_LANE_THRESHOLD
 *   distinct transaction names.
 * - Swim-lane (one row per unique transaction name) otherwise.
 * - Zoom: fit to span, 1 px/s, 1 px/min.
 * - Bar width is proportional to duration; zero-duration records render as a
 *   small diamond-shaped point marker so they remain visible.
 * - Colours are colour-blind safe and use the project palette.
 */

const SWIM_LANE_THRESHOLD = 6
const POINT_MARKER_WIDTH = 8
const LANE_HEIGHT = 32
const TRACK_PADDING_BOTTOM = 24

const props = defineProps<{
  transactions: TransactionExecutionDto[] | undefined
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<(e: 'select', tx: TransactionExecutionDto) => void>()

type ScaleMode = 'fit' | 'pxPerSecond' | 'pxPerMinute'

const containerRef = ref<HTMLDivElement>()
const containerWidth = ref(800)
const scaleMode = ref<ScaleMode>('fit')
const hoveredIdx = ref<number | null>(null)

function updateWidth() {
  containerWidth.value = containerRef.value?.getBoundingClientRect().width ?? 800
}

let resizeObserver: ResizeObserver | null = null
onMounted(() => {
  updateWidth()
  if (typeof ResizeObserver !== 'undefined' && containerRef.value) {
    resizeObserver = new ResizeObserver(updateWidth)
    resizeObserver.observe(containerRef.value)
  }
})
onUnmounted(() => {
  resizeObserver?.disconnect()
})

function ts(s?: string): number {
  return s ? new Date(s).valueOf() : 0
}

function durationMs(tx: TransactionExecutionDto): number {
  if (tx.duration !== undefined && tx.duration > 0) {
    return tx.duration
  }
  if (tx.startedAt && tx.finishedAt) {
    return Math.max(0, ts(tx.finishedAt) - ts(tx.startedAt))
  }
  if (tx.executedAt && tx.finishedAt) {
    return Math.max(0, ts(tx.finishedAt) - ts(tx.executedAt))
  }
  return 0
}

function startMs(tx: TransactionExecutionDto): number {
  return ts(tx.startedAt ?? tx.executedAt)
}

const sortedItems = computed(() => {
  const list = props.transactions ?? []
  return [...list].sort((a, b) => startMs(a) - startMs(b))
})

const laneNames = computed(() => {
  const names: string[] = []
  for (const tx of sortedItems.value) {
    if (!names.includes(tx.transactionName)) {
      names.push(tx.transactionName)
    }
  }
  return names
})

const useSwimLanes = computed(() => laneNames.value.length > SWIM_LANE_THRESHOLD)

const minStart = computed(() => {
  let min = Infinity
  for (const tx of sortedItems.value) {
    const s = startMs(tx)
    if (s < min) min = s
  }
  return isFinite(min) ? min : 0
})

const maxEnd = computed(() => {
  let max = -Infinity
  for (const tx of sortedItems.value) {
    const end = startMs(tx) + durationMs(tx)
    if (end > max) max = end
  }
  return isFinite(max) ? max : minStart.value
})

const spanMs = computed(() => {
  const span = maxEnd.value - minStart.value
  return span <= 0 ? 1 : span
})

const pixelsPerMs = computed(() => {
  switch (scaleMode.value) {
    case 'fit':
      return containerWidth.value / spanMs.value
    case 'pxPerSecond':
      return 1 / 1000
    case 'pxPerMinute':
      return 1 / (60 * 1000)
    default:
      return 0
  }
})

const totalWidth = computed(() => Math.max(containerWidth.value, spanMs.value * pixelsPerMs.value))

const laneByName = computed(() => {
  const map = new Map<string, number>()
  for (const [idx, name] of laneNames.value.entries()) {
    map.set(name, idx)
  }
  return map
})

type TimelineItem = {
  tx: TransactionExecutionDto
  start: number
  end: number
  durationMs: number
  lane: number
}

const items = computed<TimelineItem[]>(() => {
  return sortedItems.value.map((tx) => {
    const start = startMs(tx)
    const dur = durationMs(tx)
    const end = dur > 0 ? start + dur : start
    const lane = useSwimLanes.value ? (laneByName.value.get(tx.transactionName) ?? 0) : 0
    return { tx, start, end, durationMs: dur, lane }
  })
})

function leftOffset(start: number): number {
  return (start - minStart.value) * pixelsPerMs.value
}

function barWidth(duration: number): number {
  if (duration <= 0) {
    return POINT_MARKER_WIDTH
  }
  return Math.max(2, duration * pixelsPerMs.value)
}

function pointMarker(duration: number): boolean {
  return duration <= 0
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60_000).toFixed(1)}m`
}

function statusColor(status?: string): string {
  switch (status) {
    case 'SUCCESS':
      return 'bg-success-600'
    case 'FAILED':
      return 'bg-error-600'
    case 'COMPENSATED':
      return 'bg-warning-500'
    default:
      return 'bg-info-500'
  }
}

function statusLabel(status?: string): string {
  return status ?? 'UNKNOWN'
}

function formatTime(iso?: string): string {
  return iso ? new Date(iso).toLocaleString() : '-'
}

function setScale(mode: ScaleMode) {
  scaleMode.value = mode
}

function trackHeight(): number {
  const lanes = useSwimLanes.value ? laneNames.value.length : 1
  return lanes * LANE_HEIGHT + TRACK_PADDING_BOTTOM
}

function onBarClick(tx: TransactionExecutionDto) {
  emit('select', tx)
}

function onBarEnter(idx: number) {
  hoveredIdx.value = idx
}

function onBarLeave() {
  hoveredIdx.value = null
}

function isActive(mode: ScaleMode): boolean {
  return scaleMode.value === mode
}
</script>

<template>
  <div ref="containerRef" class="w-full" data-testid="execution-timeline">
    <div
      v-if="loading"
      class="text-center py-12 text-sm text-gray-500"
      data-testid="execution-timeline-loading"
    >
      Loading timeline…
    </div>
    <ErrorBanner v-else-if="error" :message="error" data-testid="execution-timeline-error" />
    <div
      v-else-if="!items.length"
      class="bg-white border border-gray-200 rounded-lg p-12 text-center text-sm text-gray-500"
      data-testid="execution-timeline-empty"
    >
      No transactions recorded for this run.
    </div>

    <div v-else class="space-y-2">
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-500">Zoom:</span>
        <button
          type="button"
          data-testid="execution-timeline-zoom-fit"
          class="px-2 py-1 text-xs rounded border transition-colors"
          :class="isActive('fit') ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'"
          @click="setScale('fit')"
        >
          Fit
        </button>
        <button
          type="button"
          data-testid="execution-timeline-zoom-px-per-second"
          class="px-2 py-1 text-xs rounded border transition-colors"
          :class="isActive('pxPerSecond') ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'"
          @click="setScale('pxPerSecond')"
        >
          1 px/s
        </button>
        <button
          type="button"
          data-testid="execution-timeline-zoom-px-per-minute"
          class="px-2 py-1 text-xs rounded border transition-colors"
          :class="isActive('pxPerMinute') ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'"
          @click="setScale('pxPerMinute')"
        >
          1 px/min
        </button>
        <span class="ml-auto text-xs text-gray-400" data-testid="execution-timeline-span">
          {{ formatTime(sortedItems[0]?.executedAt) }}
          → {{ formatTime(sortedItems[sortedItems.length - 1]?.executedAt) }}
        </span>
      </div>

      <div class="overflow-x-auto border border-gray-200 rounded-lg bg-white">
        <div
          class="relative"
          :style="{ width: `${totalWidth}px`, height: `${trackHeight()}px` }"
          data-testid="execution-timeline-track"
        >
          <!-- time axis -->
          <div class="absolute bottom-0 left-0 right-0 h-px bg-gray-300"></div>
          <div class="absolute bottom-1 left-0 text-[10px] text-gray-400">
            {{ formatTime(sortedItems[0]?.executedAt) }}
          </div>
          <div class="absolute bottom-1 right-0 text-[10px] text-gray-400">
            {{ formatTime(sortedItems[sortedItems.length - 1]?.executedAt) }}
          </div>

          <button
            v-for="(item, idx) in items"
            :key="idx"
            type="button"
            class="absolute cursor-pointer text-left bg-transparent border-none p-0"
            :style="{
              top: `${item.lane * LANE_HEIGHT + 4}px`,
              left: `${leftOffset(item.start) - (pointMarker(item.durationMs) ? POINT_MARKER_WIDTH / 2 : 0)}px`,
            }"
            data-testid="execution-timeline-item"
            @mouseenter="onBarEnter(idx)"
            @mouseleave="onBarLeave"
            @click="onBarClick(item.tx)"
          >
            <div
              class="h-5 rounded-sm shadow-sm hover:opacity-80 transition-opacity"
              :class="[statusColor(item.tx.status), pointMarker(item.durationMs) ? 'rotate-45' : '']"
              :style="{ width: `${barWidth(item.durationMs)}px` }"
              :data-testid="`execution-timeline-bar-${idx}`"
            ></div>

            <div
              v-if="hoveredIdx === idx"
              class="absolute z-10 left-0 bottom-full mb-2 w-56 rounded border border-gray-200 bg-white p-2 text-xs shadow-lg"
              data-testid="execution-timeline-tooltip"
            >
              <div class="font-semibold text-gray-800 truncate">{{ item.tx.transactionName }}</div>
              <div class="text-gray-500">Status: {{ statusLabel(item.tx.status) }}</div>
              <div class="text-gray-500">Duration: {{ formatDuration(item.durationMs) }}</div>
              <div class="text-gray-500">
                Start: {{ formatTime(item.tx.startedAt ?? item.tx.executedAt) }}
              </div>
              <div v-if="item.tx.error" class="mt-1 text-error-700 break-words">
                Error: {{ item.tx.error }}
              </div>
            </div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
