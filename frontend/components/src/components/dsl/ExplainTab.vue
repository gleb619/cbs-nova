<script setup lang="ts">
import { computed, onBeforeUpdate, ref } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import { useExplainHistory } from '../../composables/usePreviewHistory'
import type { ExplainReportNode, RunnerOutput, RunnerStatus } from '../../types/runner'
import RunInputPanel from './RunInputPanel.vue'
import RunResultPanel from './RunResultPanel.vue'

const props = defineProps<{
  name: string
  type?: ConstructType
  explain: (
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

const history = useExplainHistory(() => props.name)
const historyEntries = computed(() => history.entries.value)

function currentPayload(): unknown {
  const v = inputJson.value.trim()
  if (!v) return {}
  return JSON.parse(v)
}

function asExplainReport(value: unknown): ExplainReportNode | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const r = value as Record<string, unknown>
  if (typeof r.name !== 'string') return undefined
  return {
    name: r.name,
    description: typeof r.description === 'string' ? r.description : '',
    mermaid: typeof r.mermaid === 'string' ? r.mermaid : '',
    children: Array.isArray(r.children)
      ? r.children.map(asExplainReport).filter((c): c is ExplainReportNode => c !== undefined)
      : [],
  }
}

function normalizeResponse(response: unknown): RunnerOutput {
  if (response && typeof response === 'object' && !Array.isArray(response)) {
    const r = response as Record<string, unknown>
    const mermaid = (r.mermaid ?? r.mermaidDiagram) as string | undefined
    return {
      ...r,
      explainReport: asExplainReport(response),
      description: r.description as string | undefined,
      mermaidDiagram: mermaid,
      result: r.result ?? r.body ?? r.output,
    } as RunnerOutput
  }
  return { result: response }
}

async function run() {
  status.value = 'loading'
  output.value = null
  const payload = currentPayload()
  try {
    const metadata = { startedFrom: 'workbench' }

    const raw = await props.explain(props.name, payload, metadata)

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
      <RunInputPanel
        v-model="inputPanelModel"
        :name="name"
        :type="type"
        endpoint="explain"
        :busy="status === 'loading'"
        @submit="run"
      />
      <RunResultPanel
        :output="output"
        :status="status"
        :name="name"
        :type="type"
        endpoint="explain"
        :history="historyEntries"
        @rerun="rerun"
        @clear-history="history.clear"
        @format="emit('format', $event)"
      />
    </div>
  </div>
</template>
