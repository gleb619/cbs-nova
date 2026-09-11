<script setup lang="ts">
import { computed, onBeforeUpdate, ref } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import { usePreviewHistory } from '../../composables/usePreviewHistory'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import PreviewInputPanel from './PreviewInputPanel.vue'
import PreviewResultPanel from './PreviewResultPanel.vue'

const props = defineProps<{
  name: string
  type?: ConstructType
  preview: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => Promise<RunnerOutput> | RunnerOutput
}>()

const emit = defineEmits<{
  submit: []
  format: [formatted: string]
}>()

const inputJson = ref<string>('{\n  \n}')
const output = ref<RunnerOutput | null>(null)
const status = ref<RunnerStatus>('idle')

let previousName = props.name

onBeforeUpdate(() => {
  if (props.name !== previousName) {
    previousName = props.name
    inputJson.value = '{\n  \n}'
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
  void run()
}

const inputPanelModel = computed({
  get: () => inputJson.value,
  set: (v: string) => {
    inputJson.value = v
  },
})
</script>

<template>
  <div class="h-full p-3 bg-surface">
    <div class="grid gap-3 h-full min-h-0 md:grid-cols-2 grid-cols-1">
      <PreviewInputPanel
        v-model="inputPanelModel"
        :name="name"
        :type="type"
        :busy="status === 'loading'"
        @submit="run"
      />
      <PreviewResultPanel
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
</template>
