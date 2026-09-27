<script setup lang="ts">
import { computed, onBeforeUpdate, ref } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import { usePreviewHistory } from '../../composables/usePreviewHistory'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import RunInputPanel from './RunInputPanel.vue'
import RunResultPanel from './RunResultPanel.vue'

const props = withDefaults(
  defineProps<{
    name: string
    type?: ConstructType
    preview: (
      name: string,
      body: unknown,
      metadata?: Record<string, unknown>,
    ) => Promise<RunnerOutput> | RunnerOutput
    readOnly?: boolean
    saveStatus?: string
    lastSavedAt?: Date | null
    busy?: boolean
  }>(),
  {
    readOnly: false,
    saveStatus: 'idle',
    lastSavedAt: null,
    busy: false,
  },
)

const emit = defineEmits<{
  submit: []
  format: [formatted: string]
  save: [value: string]
}>()

const inputJson = ref<string>('{\n  \n}')
const output = ref<RunnerOutput | null>(null)
const status = ref<RunnerStatus>('idle')
const isDirty = ref(false)

let previousName = props.name

onBeforeUpdate(() => {
  if (props.name !== previousName) {
    previousName = props.name
    inputJson.value = '{\n  \n}'
    isDirty.value = false
    output.value = null
    status.value = 'idle'
  }
})

const history = usePreviewHistory(() => props.name)
const historyEntries = computed(() => history.entries.value)

function currentPayload(): unknown {
  const v = inputJson.value.trim()
  if (!v) return {}
  return JSON.parse(v)
}

function normalizeResponse(response: unknown): RunnerOutput {
  if (response && typeof response === 'object' && !Array.isArray(response)) {
    const r = response as Record<string, unknown>
    return { ...r, result: r.result ?? r.body ?? r.output } as RunnerOutput
  }
  return { result: response }
}

async function run() {
  status.value = 'loading'
  output.value = null
  const payload = currentPayload()
  try {
    const metadata = { startedFrom: 'workbench' }

    const raw = await props.preview(props.name, payload, metadata)

    output.value = normalizeResponse(raw)
    status.value = 'success'
    history.record({
      name: props.name,
      type: props.type,
      payload,
      output: output.value,
      status: 'success',
    })
  } catch (err) {
    const e = err as {
      data?: Partial<RunnerOutput> & {
        message?: string
        code?: string
        details?: unknown
        diagnostics?: unknown
      }
      statusMessage?: string
      message?: string
    }
    const data = e.data
    if (data && (Array.isArray(data.errors) || data.message)) {
      // BFF envelope (or legacy {errors:[]}) — surface as-is.
      output.value = {
        ...(data as RunnerOutput),
        errors: Array.isArray(data.errors)
          ? data.errors
          : [
              {
                message: data.message ?? e.statusMessage ?? e.message ?? 'Request failed',
                code: data.code,
              },
            ],
      }
    } else {
      output.value = {
        errors: [
          {
            message: e.statusMessage ?? e.message ?? 'Request failed',
            code: undefined,
          },
        ],
      }
    }
    status.value = 'failed'
    history.record({
      name: props.name,
      type: props.type,
      payload,
      output: output.value ?? undefined,
      status: 'failed',
    })
  }
}

function rerun(payload: unknown) {
  inputJson.value = `${JSON.stringify(payload ?? {}, null, 2)}\n`
  isDirty.value = false
  void run()
}

function onInputChange(next: string) {
  inputJson.value = next
  isDirty.value = true
}

function requestSave() {
  if (props.readOnly || props.busy) return
  if (!isDirty.value) return
  emit('save', inputJson.value)
  isDirty.value = false
}

const inputPanelModel = computed({
  get: () => inputJson.value,
  set: (v: string) => onInputChange(v),
})

const saveStatusClasses = computed(() => {
  switch (props.saveStatus) {
    case 'dirty':
      return 'bg-amber-50 text-amber-700 border border-amber-200'
    case 'saving':
      return 'bg-blue-50 text-blue-700 border border-blue-200'
    case 'saved':
      return 'bg-green-50 text-green-700 border border-green-200'
    case 'error':
      return 'bg-red-50 text-red-700 border border-red-200'
    default:
      return 'text-gray-500'
  }
})

const saveStatusText = computed(() => {
  switch (props.saveStatus) {
    case 'dirty':
      return 'Unsaved changes'
    case 'saving':
      return 'Saving…'
    case 'error':
      return 'Save failed — Retry'
    default:
      return ''
  }
})
</script>

<template>
  <div class="h-full flex flex-col bg-surface" data-testid="preview-tab">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-b border-neutral-200 bg-white"
      data-testid="preview-tab-header"
    >
      <div class="flex items-center gap-2 min-w-0">
        <span
          class="text-xs font-semibold text-neutral-700 truncate"
          data-testid="preview-tab-name"
        >
          {{ name || 'Preview' }}
        </span>
        <span
          v-if="type"
          class="text-[10px] uppercase tracking-wide text-neutral-500 rounded px-1.5 py-0.5 bg-neutral-100"
          data-testid="preview-tab-type"
        >
          {{ type }}
        </span>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <span
          v-if="saveStatusText"
          class="inline-flex items-center gap-1.5 text-xs rounded-full px-2.5 py-1"
          :class="saveStatusClasses"
          data-testid="preview-tab-save-status"
          role="status"
        >
          <span>{{ saveStatusText }}</span>
          <button
            v-if="saveStatus === 'error'"
            type="button"
            class="ml-1 underline hover:no-underline"
            data-testid="preview-tab-save-retry"
            @click="requestSave"
          >
            Retry
          </button>
        </span>
        <span
          v-if="isDirty"
          class="inline-flex items-center gap-1.5 text-xs text-amber-700"
          data-testid="preview-tab-dirty-indicator"
          role="status"
          aria-label="You have unsaved changes"
        >
          <span aria-hidden="true" class="inline-block h-2 w-2 rounded-full bg-amber-500"></span>
          <span>unsaved</span>
        </span>
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border"
          :class="isDirty
            ? 'border-blue-600 bg-blue-600 text-white hover:bg-blue-700'
            : 'border-neutral-300 text-neutral-400 cursor-not-allowed'"
          :disabled="!isDirty || busy"
          data-testid="preview-tab-save"
          @click="requestSave"
        >
          Save
        </button>
      </div>
    </div>
    <div class="flex-1 min-h-0 p-3">
      <div class="grid gap-3 h-full min-h-0 md:grid-cols-2 grid-cols-1">
        <RunInputPanel
          v-model="inputPanelModel"
          :name="name"
          :type="type"
          :busy="status === 'loading'"
          @submit="run"
        />
        <RunResultPanel
          :output="output"
          :status="status"
          :name="name"
          :type="type"
          :history="historyEntries"
          @rerun="rerun"
          @clear-history="history.clear"
          @format="emit('format', $event)"
        />
      </div>
    </div>
  </div>
</template>
